package dev.mraz1visual.ui;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;

/**
 * Фигуры GUI одним квадом через свой шейдер (signed distance field): скруглённые прямоугольники с «непрерывными»
 * углами (суперэллипс, как в iOS), кромки с градиентом, мягкие тени и свечение. Сглаживание — по производной
 * расстояния, поэтому края ровные при любом масштабе. Раньше скругление собиралось из десятков полосок fill()
 * на каждую фигуру — тысячи элементов GUI за кадр; теперь одна фигура = один элемент.
 */
public final class Sdf {
    private Sdf() {}

    /** Режимы шейдера (младшие 3 бита UV2.y). */
    public static final int FILL = 0, RING = 1, SHADOW = 2;

    /** Позиция, цвет, локальная точка (UV0), полуразмеры (UV1, ×4), радиус и параметр (UV2, ×4). */
    public static final VertexFormat FORMAT = VertexFormat.builder()
            .add("Position", VertexFormatElement.POSITION)
            .add("Color", VertexFormatElement.COLOR)
            .add("UV0", VertexFormatElement.UV0)
            .add("UV1", VertexFormatElement.UV1)
            .add("UV2", VertexFormatElement.UV2)
            .build();

    public static final RenderPipeline PIPELINE = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("mraz1visual", "pipeline/sdf_rect"))
            .withVertexShader(Identifier.fromNamespaceAndPath("mraz1visual", "core/sdf_rect"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("mraz1visual", "core/sdf_rect"))
            .withBlend(BlendFunction.TRANSLUCENT)
            .withVertexFormat(FORMAT, VertexFormat.Mode.QUADS)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .build();

    /** Шейдер не собрался — дальше рисуем по-старому (полосками), чтобы GUI не пропал. */
    public static boolean broken;

    /**
     * Фигура в координатах текущей позы g. top/bottom — цвет сверху и снизу (вертикальный градиент).
     * extra: для RING — толщина кромки, для SHADOW — мягкость (на сколько тень расползается наружу).
     */
    public static void shape(GuiGraphics g, float x, float y, float w, float h, float r, int mode, float extra, int top, int bottom) {
        if (w <= 0 || h <= 0 || ((top | bottom) >>> 24) == 0) return;
        float pad = mode == SHADOW ? extra : 1f; // запас под сглаживание / размытие края
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2f));
        Matrix3x2f pose = new Matrix3x2f(g.pose());
        ScreenRectangle scissor = g.scissorStack.peek();
        g.guiRenderState.submitGuiElement(new Shape(pose, x - pad, y - pad, x + w + pad, y + h + pad,
                w / 2f, h / 2f, r, mode, extra, top, bottom, scissor, bounds(pose, x - pad, y - pad, w + 2 * pad, h + 2 * pad, scissor)));
    }

    public static void fill(GuiGraphics g, float x, float y, float w, float h, float r, int color) {
        shape(g, x, y, w, h, r, FILL, 0, color, color);
    }

    private static ScreenRectangle bounds(Matrix3x2fc pose, float x, float y, float w, float h, ScreenRectangle scissor) {
        ScreenRectangle b = new ScreenRectangle((int) Math.floor(x), (int) Math.floor(y), (int) Math.ceil(w) + 1, (int) Math.ceil(h) + 1).transformMaxBounds(pose);
        return scissor != null ? scissor.intersection(b) : b;
    }

    private record Shape(Matrix3x2fc pose, float x0, float y0, float x1, float y1, float hw, float hh, float r, int mode, float extra,
                         int top, int bottom, ScreenRectangle scissorArea, ScreenRectangle bounds) implements GuiElementRenderState {
        @Override
        public void buildVertices(VertexConsumer vc) {
            float cx = (x0 + x1) / 2f, cy = (y0 + y1) / 2f;
            int sw = Math.round(hw * 4), sh = Math.round(hh * 4);
            int rr = Math.round(r * 4), ex = (Math.min(4000, Math.round(extra * 4)) << 3) | mode;
            vc.addVertexWith2DPose(pose, x0, y0).setColor(top).setUv(x0 - cx, y0 - cy).setUv1(sw, sh).setUv2(rr, ex);
            vc.addVertexWith2DPose(pose, x0, y1).setColor(bottom).setUv(x0 - cx, y1 - cy).setUv1(sw, sh).setUv2(rr, ex);
            vc.addVertexWith2DPose(pose, x1, y1).setColor(bottom).setUv(x1 - cx, y1 - cy).setUv1(sw, sh).setUv2(rr, ex);
            vc.addVertexWith2DPose(pose, x1, y0).setColor(top).setUv(x1 - cx, y0 - cy).setUv1(sw, sh).setUv2(rr, ex);
        }

        @Override
        public RenderPipeline pipeline() { return PIPELINE; }

        @Override
        public TextureSetup textureSetup() { return TextureSetup.noTexture(); }
    }
}

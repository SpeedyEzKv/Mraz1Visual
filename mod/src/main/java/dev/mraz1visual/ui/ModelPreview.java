package dev.mraz1visual.ui;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.mraz1visual.models.Mesh;
import dev.mraz1visual.models.ModelDraw;
import net.fabricmc.fabric.api.client.rendering.v1.SpecialGuiElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * 3D-превью импортированной модели в меню: модель рисуется в свою текстуру (как сущность в инвентаре)
 * и вставляется в GUI картинкой. Поворот, наклон и поза анимации — у каждой карточки свои.
 */
public final class ModelPreview {
    private ModelPreview() {}

    public static void register() {
        SpecialGuiElementRegistry.register(pip -> new Renderer(pip.vertexConsumers()));
    }

    /** Нарисовать модель в прямоугольнике (координаты GUI). pose — поза костей (копируется) или null. */
    public static void draw(GuiGraphics g, Mesh m, float[] pose, float yaw, float pitch, boolean glow, int x, int y, int w, int h) {
        draw(g, m, pose, yaw, pitch, 0, 0.78f, glow, x, y, w, h);
    }

    /** roll — наклон по Z (оружие по диагонали, как иконка), fit — доля рамки под самый длинный размер модели. */
    public static void draw(GuiGraphics g, Mesh m, float[] pose, float yaw, float pitch, float roll, float fit, boolean glow, int x, int y, int w, int h) {
        if (m == null || m.texture() == null || w <= 2 || h <= 2) return;
        float dim = Math.max(m.sizeY(), Math.max(m.sizeX(), m.sizeZ()));
        float scale = Math.min(w, h) * fit / Math.max(0.05f, dim);
        ScreenRectangle scissor = g.scissorStack.peek();
        float[] copy = pose == null ? null : pose.clone();
        g.guiRenderState.submitPicturesInPictureState(new State(m, copy, yaw, pitch, roll, glow, x, y, x + w, y + h, scale, scissor,
                PictureInPictureRenderState.getBounds(x, y, x + w, y + h, scissor)));
    }

    public record State(Mesh mesh, float[] bonePose, float yaw, float pitch, float roll, boolean glow,
                        int x0, int y0, int x1, int y1, float scale,
                        ScreenRectangle scissorArea, ScreenRectangle bounds) implements PictureInPictureRenderState {
    }

    static final class Renderer extends PictureInPictureRenderer<State> {
        Renderer(MultiBufferSource.BufferSource buffers) {
            super(buffers);
        }

        @Override
        public Class<State> getRenderStateClass() {
            return State.class;
        }

        @Override
        protected String getTextureLabel() {
            return "mraz1visual model preview";
        }

        @Override
        protected void renderToTexture(State s, PoseStack ps) {
            Minecraft.getInstance().gameRenderer.getLighting().setupFor(Lighting.Entry.ITEMS_3D);
            Mesh m = s.mesh();
            float[] b = m.bounds;
            ps.pushPose();
            ps.scale(1, -1, 1); // вверх +Y
            ps.mulPose(Axis.ZP.rotationDegrees(s.roll()));
            ps.mulPose(Axis.XP.rotationDegrees(s.pitch()));
            ps.mulPose(Axis.YP.rotationDegrees(s.yaw()));
            ps.translate(-(b[0] + b[3]) / 2f, -(b[1] + b[4]) / 2f, -(b[2] + b[5]) / 2f);
            if (m.bones.length > 0) ModelDraw.bones(m, s.bonePose());
            int light = dev.mraz1visual.compat.Mc.FULL_BRIGHT, ov = OverlayTexture.NO_OVERLAY;
            ModelDraw.viewFromCamera(false);
            ModelDraw.draw(m, ps.last(), bufferSource.getBuffer(ModelDraw.solidType(m)), -1, ov, light, ModelDraw.SOLID);
            if (ModelDraw.rimStrength > 0.01f) ModelDraw.draw(m, ps.last(), bufferSource.getBuffer(ModelDraw.rimType()), -1, ov, light, ModelDraw.RIM);
            if (s.glow() && m.selfGlow) ModelDraw.draw(m, ps.last(), bufferSource.getBuffer(RenderTypes.eyes(m.texture())), -1, ov, light, ModelDraw.GLOW_SELF);
            if (s.glow() && m.atlas.glowTexture != null) ModelDraw.draw(m, ps.last(), bufferSource.getBuffer(RenderTypes.eyes(m.atlas.glowTexture)), -1, ov, light, ModelDraw.GLOW_LAYER);
            ps.popPose();
        }
    }
}

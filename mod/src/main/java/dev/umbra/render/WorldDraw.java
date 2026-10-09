package dev.umbra.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.umbra.Umbra;
import dev.umbra.core.ModuleManager;
import dev.umbra.core.WorldLayer;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Рисование в мире. Единственное место, где мод знает про рендер мира Minecraft —
 * при смене API правится только этот файл.
 *
 * Координаты — мировые (double), перевод в камерные делается здесь, в float,
 * чтобы не терять точность вдали от 0,0.
 * Всё рисуется обычными полупрозрачными квадами ванили: с проверкой глубины,
 * без своих шейдеров — совместимо с Sodium и Iris.
 */
public final class WorldDraw {
    private static final WorldDraw INSTANCE = new WorldDraw();
    private static final int SEG_MAX = 64;
    private static final float[] COS = new float[SEG_MAX + 1], SIN = new float[SEG_MAX + 1];

    private VertexConsumer vc;
    private Matrix4f pose;
    private double cx, cy, cz;

    private WorldDraw() {}

    public static void register() {
        WorldRenderEvents.BEFORE_TRANSLUCENT.register(WorldDraw::onRender);
    }

    private static void onRender(WorldRenderContext ctx) {
        WorldLayer[] layers = Umbra.MODULES.worldLayers();
        if (layers.length == 0) return; // ни одного включённого визуала — ноль работы
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        WorldDraw d = INSTANCE;
        Vec3 cam = ctx.worldState().cameraRenderState.pos;
        PoseStack ps = ctx.matrices();
        MultiBufferSource src = ctx.consumers();
        if (src == null) return;
        d.cx = cam.x;
        d.cy = cam.y;
        d.cz = cam.z;
        d.pose = ps.last().pose();
        d.vc = src.getBuffer(RenderTypes.debugQuads());
        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        for (WorldLayer l : layers) {
            try {
                l.renderWorld(d, pt);
            } catch (Throwable t) {
                ModuleManager.LOG.error("World layer {} failed", l.getClass().getSimpleName(), t);
            }
        }
        d.vc = null;
    }

    // ── примитивы ────────────────────────────────────────────

    private void v(double x, double y, double z, int argb) {
        vc.addVertex(pose, (float) (x - cx), (float) (y - cy), (float) (z - cz)).setColor(argb);
    }

    /** Четырёхугольник. Отсечения граней у этого типа нет — виден с обеих сторон. */
    public void quad(double x1, double y1, double z1, double x2, double y2, double z2,
                     double x3, double y3, double z3, double x4, double y4, double z4, int c1, int c2, int c3, int c4) {
        v(x1, y1, z1, c1); v(x2, y2, z2, c2); v(x3, y3, z3, c3); v(x4, y4, z4, c4);
    }

    /** Горизонтальное кольцо. */
    public void ring(double x, double y, double z, double rIn, double rOut, int segments, int colorIn, int colorOut) {
        int n = Math.min(SEG_MAX, Math.max(8, segments));
        for (int i = 0; i < n; i++) {
            float c0 = cos(i, n), s0 = sin(i, n), c1 = cos(i + 1, n), s1 = sin(i + 1, n);
            quad(x + c0 * rIn, y, z + s0 * rIn, x + c0 * rOut, y, z + s0 * rOut,
                    x + c1 * rOut, y, z + s1 * rOut, x + c1 * rIn, y, z + s1 * rIn, colorIn, colorOut, colorOut, colorIn);
        }
    }

    /** Конус остриём вверх (основание горизонтально). */
    public void cone(double x, double y, double z, double radius, double height, int segments, int colorBase, int colorTip) {
        int n = Math.min(SEG_MAX, Math.max(8, segments));
        double ty = y + height;
        for (int i = 0; i < n; i++) {
            float c0 = cos(i, n), s0 = sin(i, n), c1 = cos(i + 1, n), s1 = sin(i + 1, n);
            quad(x + c0 * radius, y, z + s0 * radius, x + c1 * radius, y, z + s1 * radius,
                    x, ty, z, x, ty, z, colorBase, colorBase, colorTip, colorTip);
        }
    }

    /** Вертикальная лента между двумя точками (сегмент следа). */
    public void ribbon(double x1, double y1, double z1, double x2, double y2, double z2, double h, int c1, int c2) {
        quad(x1, y1, z1, x2, y2, z2, x2, y2 + h, z2, x1, y1 + h, z1, c1, c2, c2, c1);
    }

    /** Залитый параллелепипед (6 граней). */
    public void box(double x1, double y1, double z1, double x2, double y2, double z2, int c) {
        quad(x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2, c, c, c, c); // низ
        quad(x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1, c, c, c, c); // верх
        quad(x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1, c, c, c, c); // север
        quad(x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2, c, c, c, c); // юг
        quad(x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1, c, c, c, c); // запад
        quad(x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2, c, c, c, c); // восток
    }

    /** Рёбра параллелепипеда толщиной t (каждое ребро — тонкий брусок). */
    public void edges(double x1, double y1, double z1, double x2, double y2, double z2, double t, int c) {
        double h = t / 2;
        // 4 вертикальных
        box(x1 - h, y1 - h, z1 - h, x1 + h, y2 + h, z1 + h, c);
        box(x2 - h, y1 - h, z1 - h, x2 + h, y2 + h, z1 + h, c);
        box(x1 - h, y1 - h, z2 - h, x1 + h, y2 + h, z2 + h, c);
        box(x2 - h, y1 - h, z2 - h, x2 + h, y2 + h, z2 + h, c);
        // 4 по X
        box(x1 + h, y1 - h, z1 - h, x2 - h, y1 + h, z1 + h, c);
        box(x1 + h, y2 - h, z1 - h, x2 - h, y2 + h, z1 + h, c);
        box(x1 + h, y1 - h, z2 - h, x2 - h, y1 + h, z2 + h, c);
        box(x1 + h, y2 - h, z2 - h, x2 - h, y2 + h, z2 + h, c);
        // 4 по Z
        box(x1 - h, y1 - h, z1 + h, x1 + h, y1 + h, z2 - h, c);
        box(x2 - h, y1 - h, z1 + h, x2 + h, y1 + h, z2 - h, c);
        box(x1 - h, y2 - h, z1 + h, x1 + h, y2 + h, z2 - h, c);
        box(x2 - h, y2 - h, z1 + h, x2 + h, y2 + h, z2 - h, c);
    }

    /** Вертикальный луч-маяк: 4 грани, снизу ярче, кверху тает. */
    public void beam(double x, double y, double z, double height, double w, int color) {
        int top = color & 0x00FFFFFF;
        double h = w / 2, y2 = y + height;
        quad(x - h, y, z - h, x + h, y, z - h, x + h, y2, z - h, x - h, y2, z - h, color, color, top, top);
        quad(x + h, y, z - h, x + h, y, z + h, x + h, y2, z + h, x + h, y2, z - h, color, color, top, top);
        quad(x + h, y, z + h, x - h, y, z + h, x - h, y2, z + h, x + h, y2, z + h, color, color, top, top);
        quad(x - h, y, z + h, x - h, y, z - h, x - h, y2, z - h, x - h, y2, z + h, color, color, top, top);
    }

    private static float cos(int i, int n) {
        return (float) Math.cos(i * Math.PI * 2 / n);
    }

    private static float sin(int i, int n) {
        return (float) Math.sin(i * Math.PI * 2 / n);
    }
}

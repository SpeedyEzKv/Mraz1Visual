package dev.mraz1visual.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.mraz1visual.Mraz1Visual;
import dev.mraz1visual.core.ModuleManager;
import dev.mraz1visual.core.WorldLayer;
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
    private MultiBufferSource src;
    private org.joml.Quaternionf rot = new org.joml.Quaternionf();
    /** Оси экрана в мире — считаются раз за кадр, а не в каждом billboard-вызове (меньше мусора для GC). */
    private final org.joml.Vector3f camRight = new org.joml.Vector3f(1, 0, 0), camUp = new org.joml.Vector3f(0, 1, 0);
    private final PoseStack labelStack = new PoseStack();

    private WorldDraw() {}

    public static void register() {
        WorldRenderEvents.BEFORE_TRANSLUCENT.register(WorldDraw::onRender);
    }

    private static void onRender(WorldRenderContext ctx) {
        WorldLayer[] layers = Mraz1Visual.MODULES.worldLayers();
        if (layers.length == 0 && FxParticles.isEmpty()) return; // ни одного включённого визуала — ноль работы
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        WorldDraw d = INSTANCE;
        Vec3 cam = ctx.worldState().cameraRenderState.pos;
        PoseStack ps = ctx.matrices();
        MultiBufferSource src = ctx.consumers();
        if (src == null) return;
        d.cx = d.eyeX = cam.x;
        d.cy = d.eyeY = cam.y;
        d.cz = d.eyeZ = cam.z;
        d.pose = ps.last().pose();
        d.src = src;
        org.joml.Quaternionf o = ctx.worldState().cameraRenderState.orientation;
        if (o != null) d.rot = o;
        d.camRight.set(1, 0, 0);
        d.rot.transform(d.camRight);
        d.camUp.set(0, 1, 0);
        d.rot.transform(d.camUp);
        d.vc = src.getBuffer(RenderTypes.debugQuads());
        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        for (WorldLayer l : layers) {
            try {
                l.renderWorld(d, pt);
            } catch (Throwable t) {
                ModuleManager.LOG.error("World layer {} failed", l.getClass().getSimpleName(), t);
            }
        }
        try {
            FxParticles.render(d);
        } catch (Throwable t) {
            ModuleManager.LOG.error("Particles failed", t);
        }
        d.vc = null;
        d.src = null;
    }

    // ── примитивы ────────────────────────────────────────────

    private void v(double x, double y, double z, int argb) {
        // локальный режим: два прохода, в каждом — только своя половина (обычные квады или свечение)
        if (local) {
            if (glowing != glowPass) return;
            // у модели игрока — типы сущностей (тот же проход, что и модель; дружит с Iris)
            if (glowPass) FxVertex.putGlow(vc, pose, (float) x, (float) y, (float) z, argb);
            else FxVertex.put(vc, pose, (float) x, (float) y, (float) z, argb);
            return;
        }
        vc.addVertex(pose, (float) (x - cx), (float) (y - cy), (float) (z - cz)).setColor(argb);
    }

    /**
     * Дальше — «светящиеся» квады: аддитивное смешивание (как у молнии), даёт мягкое свечение-bloom
     * без шейдеров. После — обязательно {@link #glowEnd()}.
     */
    public void glowBegin() {
        if (local) { glowing = true; return; }
        if (src != null) vc = src.getBuffer(RenderTypes.lightning());
    }

    /** Вернуться к обычным полупрозрачным квадам. */
    public void glowEnd() {
        if (local) { glowing = false; return; }
        if (src != null) vc = src.getBuffer(RenderTypes.debugQuads());
    }

    // ── локальный режим: рисование у модели игрока (слой рендера, инвентарь, превью) ──

    /** Что рисовать в локальном режиме. Вызывается дважды за кадр: обычный проход и проход свечения. */
    public interface Painter {
        void paint(WorldDraw d);
    }

    private static final WorldDraw LOCAL = new WorldDraw();
    private boolean local, glowing, glowPass, ortho;
    /** Точка камеры (для лент к камере) и направление взгляда для ортографии инвентаря. */
    private double eyeX, eyeY, eyeZ, viewX, viewY, viewZ;
    private final Matrix4f inv = new Matrix4f();
    private final org.joml.Vector3f tmp = new org.joml.Vector3f();

    /**
     * Рисует painter в координатах текущей позы ps (например, у тела модели). Координаты — как в мире,
     * только без камеры: {@link #camX()} и т. п. здесь 0. gui — модель в инвентаре (ортографическая камера).
     */
    public static void submitLocal(PoseStack ps, net.minecraft.client.renderer.SubmitNodeCollector c, boolean gui, Painter painter) {
        c.submitCustomGeometry(ps, FxVertex.solid(), (pose, vc) -> LOCAL.runLocal(pose.pose(), vc, gui, false, painter));
        c.submitCustomGeometry(ps, FxVertex.glow(), (pose, vc) -> LOCAL.runLocal(pose.pose(), vc, gui, true, painter));
    }

    private void runLocal(Matrix4f m, VertexConsumer consumer, boolean gui, boolean glow, Painter painter) {
        local = true;
        glowPass = glow;
        glowing = false;
        ortho = gui;
        vc = consumer;
        pose = m;
        src = null;
        cx = cy = cz = 0;
        inv.set(m).invert();
        // оси экрана: в мире поза без поворота камеры (он в modelview), в инвентаре — уже экранная
        camRight.set(1, 0, 0);
        camUp.set(0, 1, 0);
        if (!gui) {
            org.joml.Quaternionf q = Minecraft.getInstance().gameRenderer.getMainCamera().rotation();
            q.transform(camRight);
            q.transform(camUp);
        }
        inv.transformDirection(camRight).normalize();
        inv.transformDirection(camUp).normalize();
        if (gui) {
            inv.transformDirection(tmp.set(0, 0, 1)).normalize();
            viewX = tmp.x; viewY = tmp.y; viewZ = tmp.z;
        } else {
            inv.transformPosition(tmp.set(0, 0, 0));
            eyeX = tmp.x; eyeY = tmp.y; eyeZ = tmp.z;
        }
        try {
            painter.paint(this);
        } catch (Throwable t) {
            ModuleManager.LOG.error("Local layer failed", t);
        } finally {
            vc = null;
            glowing = false;
        }
    }

    /** Четырёхугольник. Отсечения граней у этого типа нет — виден с обеих сторон. */
    public void quad(double x1, double y1, double z1, double x2, double y2, double z2,
                     double x3, double y3, double z3, double x4, double y4, double z4, int c1, int c2, int c3, int c4) {
        v(x1, y1, z1, c1); v(x2, y2, z2, c2); v(x3, y3, z3, c3); v(x4, y4, z4, c4);
        // свечение у модели: тип с отсечением граней — рисуем обе стороны (зеркальное крыло иначе пропадает)
        if (local && glowing && glowPass && !facing) { v(x4, y4, z4, c4); v(x3, y3, z3, c3); v(x2, y2, z2, c2); v(x1, y1, z1, c1); }
    }

    /** Локальный режим: примитив не для текущего прохода (обычный / свечение) — не считать его вовсе. */
    private boolean skip() {
        return local && glowing != glowPass;
    }

    /** Сейчас рисуется примитив, развёрнутый к камере (точка, кольцо к камере): он и так лицом к камере — без второй стороны. */
    private boolean facing;

    /** Горизонтальное кольцо. */
    public void ring(double x, double y, double z, double rIn, double rOut, int segments, int colorIn, int colorOut) {
        if (skip()) return;
        int n = Math.min(SEG_MAX, Math.max(8, segments));
        for (int i = 0; i < n; i++) {
            float c0 = cos(i, n), s0 = sin(i, n), c1 = cos(i + 1, n), s1 = sin(i + 1, n);
            quad(x + c0 * rIn, y, z + s0 * rIn, x + c0 * rOut, y, z + s0 * rOut,
                    x + c1 * rOut, y, z + s1 * rOut, x + c1 * rIn, y, z + s1 * rIn, colorIn, colorOut, colorOut, colorIn);
        }
    }

    /** Конус остриём вверх (основание горизонтально). */
    public void cone(double x, double y, double z, double radius, double height, int segments, int colorBase, int colorTip) {
        if (skip()) return;
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
        if (skip()) return;
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

    /** Мировой режим: источник буферов кадра (null в локальном режиме) — для своих типов рендера (текстуры моделей). */
    public MultiBufferSource source() { return local ? null : src; }

    /** Мировой режим: поза кадра (координаты относительно камеры). */
    public Matrix4f framePose() { return pose; }

    /** После своих буферов вернуть обычные квады (смена типа рендера закрывает прошлый буфер). */
    public void restore() {
        if (!local && src != null) vc = src.getBuffer(RenderTypes.debugQuads());
    }

    public double camX() { return cx; }
    public double camY() { return cy; }
    public double camZ() { return cz; }

    /** Отрезок толщиной w, развёрнутый к камере. */
    public void line(double x1, double y1, double z1, double x2, double y2, double z2, double w, int c) {
        if (skip()) return;
        double dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
        double vx, vy, vz;
        if (ortho) { vx = viewX; vy = viewY; vz = viewZ; }
        else { vx = (x1 + x2) / 2 - eyeX; vy = (y1 + y2) / 2 - eyeY; vz = (z1 + z2) / 2 - eyeZ; }
        double px = dy * vz - dz * vy, py = dz * vx - dx * vz, pz = dx * vy - dy * vx;
        double len = Math.sqrt(px * px + py * py + pz * pz);
        if (len < 1e-6) return;
        double k = w / 2 / len;
        px *= k; py *= k; pz *= k;
        quad(x1 - px, y1 - py, z1 - pz, x1 + px, y1 + py, z1 + pz, x2 + px, y2 + py, z2 + pz, x2 - px, y2 - py, z2 - pz, c, c, c, c);
    }

    /**
     * Табличка с текстом над точкой (как ник), с проверкой глубины — сквозь стены не видна.
     * После текста буфер квадов берётся заново: смена типа рендера закрывает прошлый.
     */
    public void label(net.minecraft.network.chat.Component text, double x, double y, double z, float scale, int color, int background) {
        if (src == null || local) return;
        Minecraft mc = Minecraft.getInstance();
        PoseStack ps = labelStack;
        ps.setIdentity();
        ps.last().pose().set(pose);
        ps.translate((float) (x - cx), (float) (y - cy), (float) (z - cz));
        ps.mulPose(rot);
        float s = 0.025f * scale;
        ps.scale(s, -s, s);
        var font = mc.font;
        float w = font.width(text);
        font.drawInBatch(text, -w / 2f, 0, color, false, ps.last().pose(), src,
                net.minecraft.client.gui.Font.DisplayMode.NORMAL, background, 0xF000F0);
        vc = src.getBuffer(RenderTypes.debugQuads());
    }

    /** Кольцо, всегда развёрнутое к камере (как частица). */
    public void billboardRing(double x, double y, double z, double r, double w, int segments, int color) {
        if (skip()) return;
        facing = true;
        try {
        org.joml.Vector3f right = camRight;
        org.joml.Vector3f up = camUp;
        int n = Math.min(SEG_MAX, Math.max(8, segments));
        double ri = Math.max(0, r - w / 2), ro = r + w / 2;
        for (int i = 0; i < n; i++) {
            float c0 = cos(i, n), s0 = sin(i, n), c1 = cos(i + 1, n), s1 = sin(i + 1, n);
            double ax = right.x() * c0 + up.x() * s0, ay = right.y() * c0 + up.y() * s0, az = right.z() * c0 + up.z() * s0;
            double bx = right.x() * c1 + up.x() * s1, by = right.y() * c1 + up.y() * s1, bz = right.z() * c1 + up.z() * s1;
            quad(x + ax * ri, y + ay * ri, z + az * ri, x + ax * ro, y + ay * ro, z + az * ro,
                    x + bx * ro, y + by * ro, z + bz * ro, x + bx * ri, y + by * ri, z + bz * ri, color, color, color, color);
        }
    } finally {
            facing = false;
        }
    }

    /** Круглая «частица» к камере: яркий центр, прозрачный край. */
    public void billboardDot(double x, double y, double z, double r, int color) {
        billboardDot(x, y, z, r, color, 12);
    }

    /** То же с заданным числом сегментов: 6 — дёшево для тысяч частиц. */
    public void billboardDot(double x, double y, double z, double r, int color, int n) {
        if (skip()) return;
        facing = true;
        try {
        org.joml.Vector3f right = camRight;
        org.joml.Vector3f up = camUp;
        int edge = color & 0x00FFFFFF;
        for (int i = 0; i < n; i++) {
            float c0 = cos(i, n), s0 = sin(i, n), c1 = cos(i + 1, n), s1 = sin(i + 1, n);
            double ax = (right.x() * c0 + up.x() * s0) * r, ay = (right.y() * c0 + up.y() * s0) * r, az = (right.z() * c0 + up.z() * s0) * r;
            double bx = (right.x() * c1 + up.x() * s1) * r, by = (right.y() * c1 + up.y() * s1) * r, bz = (right.z() * c1 + up.z() * s1) * r;
            quad(x, y, z, x + ax, y + ay, z + az, x + bx, y + by, z + bz, x, y, z, color, edge, edge, color);
        }
    } finally {
            facing = false;
        }
    }

    /** Четыре уголка-скобки в плоскости экрана, повёрнутые на angle (рад). Маркер цели. */
    public void billboardCorners(double x, double y, double z, double size, double angle, double thick, int color) {
        if (skip()) return;
        org.joml.Vector3f r0 = camRight;
        org.joml.Vector3f u0 = camUp;
        double ca = Math.cos(angle), sa = Math.sin(angle);
        double rx = r0.x() * ca + u0.x() * sa, ry = r0.y() * ca + u0.y() * sa, rz = r0.z() * ca + u0.z() * sa;
        double ux = -r0.x() * sa + u0.x() * ca, uy = -r0.y() * sa + u0.y() * ca, uz = -r0.z() * sa + u0.z() * ca;
        double s = size, l = size * 0.45, t = thick;
        for (int i = 0; i < 4; i++) {
            int sx = (i & 1) == 0 ? -1 : 1, sy = i < 2 ? -1 : 1;
            // угол в точке (sx*s, sy*s), плечи внутрь
            double cx0 = sx * s, cy0 = sy * s;
            bar(x, y, z, rx, ry, rz, ux, uy, uz, cx0, cy0, cx0 - sx * l, cy0, t, color);
            bar(x, y, z, rx, ry, rz, ux, uy, uz, cx0, cy0, cx0, cy0 - sy * l, t, color);
        }
    }

    private void bar(double x, double y, double z, double rx, double ry, double rz, double ux, double uy, double uz,
                     double a0, double b0, double a1, double b1, double t, int c) {
        double da = a1 - a0, db = b1 - b0, len = Math.sqrt(da * da + db * db);
        if (len < 1e-6) return;
        double na = -db / len * t / 2, nb = da / len * t / 2;
        double ea = da / len * t / 2, eb = db / len * t / 2;
        // 4 угла в плоскости экрана (a — вдоль right, b — вдоль up) → мир; без временных массивов
        double a0a = a0 - na - ea, b0a = b0 - nb - eb, a0b = a0 + na - ea, b0b = b0 + nb - eb;
        double a1a = a1 + na, b1a = b1 + nb, a1b = a1 - na, b1b = b1 - nb;
        quad(x + rx * a0a + ux * b0a, y + ry * a0a + uy * b0a, z + rz * a0a + uz * b0a,
                x + rx * a0b + ux * b0b, y + ry * a0b + uy * b0b, z + rz * a0b + uz * b0b,
                x + rx * a1a + ux * b1a, y + ry * a1a + uy * b1a, z + rz * a1a + uz * b1a,
                x + rx * a1b + ux * b1b, y + ry * a1b + uy * b1b, z + rz * a1b + uz * b1b, c, c, c, c);
    }

    /** Кристалл-октаэдр, повёрнутый на yaw вокруг вертикали. */
    public void crystal(double x, double y, double z, double r, double h, double yaw, int cTop, int cMid) {
        if (skip()) return;
        double[] px = tmpX, pz = tmpZ;
        for (int i = 0; i < 4; i++) {
            double a = yaw + i * Math.PI / 2;
            px[i] = x + Math.cos(a) * r;
            pz[i] = z + Math.sin(a) * r;
        }
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) % 4;
            quad(px[i], y, pz[i], px[j], y, pz[j], x, y + h, z, x, y + h, z, cMid, cMid, cTop, cTop);
            quad(px[i], y, pz[i], px[j], y, pz[j], x, y - h, z, x, y - h, z, cMid, cMid, cTop, cTop);
        }
    }

    /** Кубик с центром (x,y,z), повёрнутый на yaw и pitch: грани цветом fill, рёбра цветом edge (0 — без рёбер). */
    public void cube(double x, double y, double z, double half, double yaw, double pitch, int fill, int edge) {
        if (skip()) return;
        double cy = Math.cos(yaw), sy = Math.sin(yaw), cp = Math.cos(pitch), sp = Math.sin(pitch);
        double[][] p = cubePts;
        for (int i = 0; i < 8; i++) {
            double lx = (i & 1) == 0 ? -half : half, ly = (i & 2) == 0 ? -half : half, lz = (i & 4) == 0 ? -half : half;
            double y1 = ly * cp - lz * sp, z1 = ly * sp + lz * cp;
            p[i][0] = x + lx * cy - z1 * sy;
            p[i][1] = y + y1;
            p[i][2] = z + lx * sy + z1 * cy;
        }
        if ((fill >>> 24) != 0) {
            for (int[] q : CUBE_FACES) {
                quad(p[q[0]][0], p[q[0]][1], p[q[0]][2], p[q[1]][0], p[q[1]][1], p[q[1]][2],
                        p[q[2]][0], p[q[2]][1], p[q[2]][2], p[q[3]][0], p[q[3]][1], p[q[3]][2], fill, fill, fill, fill);
            }
        }
        if ((edge >>> 24) != 0) {
            for (int[] l : CUBE_EDGES) line(p[l[0]][0], p[l[0]][1], p[l[0]][2], p[l[1]][0], p[l[1]][1], p[l[1]][2], half * 0.12, edge);
        }
    }

    // временные буферы для crystal/cube — рисование идёт только в потоке рендера
    private final double[] tmpX = new double[4], tmpZ = new double[4];
    private final double[][] cubePts = new double[8][3];
    private static final int[][] CUBE_FACES = {{0, 1, 3, 2}, {4, 6, 7, 5}, {0, 4, 5, 1}, {2, 3, 7, 6}, {0, 2, 6, 4}, {1, 5, 7, 3}};
    private static final int[][] CUBE_EDGES = {{0, 1}, {2, 3}, {4, 5}, {6, 7}, {0, 2}, {1, 3}, {4, 6}, {5, 7}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};

    /** Треугольник (как вырожденный четырёхугольник). */
    public void tri(double x1, double y1, double z1, double x2, double y2, double z2, double x3, double y3, double z3, int c1, int c2, int c3) {
        quad(x1, y1, z1, x2, y2, z2, x3, y3, z3, x3, y3, z3, c1, c2, c3, c3);
    }

    /** Луч в плоскости экрана из точки под углом ang: от r0 до r1, ширина w у основания, к концу сходится в точку. */
    public void billboardRay(double x, double y, double z, double ang, double r0, double r1, double w, int color) {
        if (skip()) return;
        org.joml.Vector3f right = camRight;
        org.joml.Vector3f up = camUp;
        double ca = Math.cos(ang), sa = Math.sin(ang);
        double dx = right.x() * ca + up.x() * sa, dy = right.y() * ca + up.y() * sa, dz = right.z() * ca + up.z() * sa;
        double nx = -right.x() * sa + up.x() * ca, ny = -right.y() * sa + up.y() * ca, nz = -right.z() * sa + up.z() * ca;
        double hw = w / 2;
        int tip = color & 0x00FFFFFF;
        quad(x + dx * r0 - nx * hw, y + dy * r0 - ny * hw, z + dz * r0 - nz * hw,
                x + dx * r0 + nx * hw, y + dy * r0 + ny * hw, z + dz * r0 + nz * hw,
                x + dx * r1, y + dy * r1, z + dz * r1, x + dx * r1, y + dy * r1, z + dz * r1, color, color, tip, tip);
    }

    /** Таблицы cos/sin для каждого числа сегментов — без тригонометрии в каждом кадре. */
    private static final float[][][] TABLES = new float[SEG_MAX + 1][][];

    private static float[][] table(int n) {
        float[][] t = n <= SEG_MAX ? TABLES[n] : null;
        if (t != null) return t;
        t = new float[2][n];
        for (int i = 0; i < n; i++) {
            t[0][i] = (float) Math.cos(i * Math.PI * 2 / n);
            t[1][i] = (float) Math.sin(i * Math.PI * 2 / n);
        }
        if (n <= SEG_MAX) TABLES[n] = t;
        return t;
    }

    private static float cos(int i, int n) {
        return table(n)[0][i % n];
    }

    private static float sin(int i, int n) {
        return table(n)[1][i % n];
    }
}

package dev.mraz1visual.models;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Отрисовка запечённой модели: матрицы костей из позы анимации, вершины — ручным умножением
 * и одним вызовом addVertex на вершину. Без аллокаций: задания для отложенной отправки
 * ({@link SubmitNodeCollector#submitCustomGeometry}) берутся из кольцевого пула.
 */
public final class ModelDraw {
    /** Проходы: обычный (текстура модели), свечение эмиссивных текстур, свечение слоя *_e. */
    public static final int SOLID = 0, GLOW_SELF = 1, GLOW_LAYER = 2;

    private ModelDraw() {}

    private static final Matrix4f M = new Matrix4f();
    private static final Matrix3f N = new Matrix3f(), BN = new Matrix3f();

    /** Матрицы костей (в блоках) из позы: на кость 9 чисел (rot° xyz, pos px xyz, scale−1 xyz). */
    public static void bones(Mesh m, float[] pose) {
        Mesh.Bone[] bs = m.bones;
        for (int i = 0; i < bs.length; i++) {
            Mesh.Bone b = bs[i];
            Matrix4f out = m.scratch[i];
            if (b.parent >= 0) out.set(m.scratch[b.parent]);
            else out.identity();
            float rx = b.rx, ry = b.ry, rz = b.rz, tx = 0, ty = 0, tz = 0, sx = 1, sy = 1, sz = 1;
            if (pose != null) {
                int o = i * 9;
                // знаки как у Blockbench: поворот по X и Y и сдвиг по X — с минусом
                rx -= (float) Math.toRadians(pose[o]);
                ry -= (float) Math.toRadians(pose[o + 1]);
                rz += (float) Math.toRadians(pose[o + 2]);
                tx = -pose[o + 3] / 16f; ty = pose[o + 4] / 16f; tz = pose[o + 5] / 16f;
                sx = 1 + pose[o + 6]; sy = 1 + pose[o + 7]; sz = 1 + pose[o + 8];
            }
            out.translate(tx + b.px, ty + b.py, tz + b.pz);
            if (rx != 0 || ry != 0 || rz != 0) out.rotateZYX(rz, ry, rx);
            if (sx != 1 || sy != 1 || sz != 1) out.scale(sx, sy, sz);
            out.translate(-b.px, -b.py, -b.pz);
        }
    }

    /**
     * Вершины модели в буфер. pose — текущая поза (в блоках), color — ARGB-множитель, pass — проход.
     * Кости должны быть посчитаны {@link #bones} (или у модели их нет).
     */
    public static void draw(Mesh m, PoseStack.Pose pose, VertexConsumer vc, int color, int overlay, int light, int pass) {
        Matrix4f pm = pose.pose();
        Matrix3f pn = pose.normal();
        float[] d = m.data;
        byte want = pass == GLOW_SELF ? Mesh.GLOW : Mesh.SOLID;
        if (pass != SOLID) light = LightTexture.FULL_BRIGHT;
        int lastBone = Integer.MIN_VALUE;
        Matrix4f mm = pm;
        Matrix3f nn = pn;
        for (int q = 0; q < m.quads; q++) {
            if ((m.quadFlags[q] & want) == 0) continue;
            int b = m.quadBone[q];
            if (b != lastBone) {
                lastBone = b;
                if (b >= 0 && b < m.scratch.length) {
                    mm = M.set(pm).mul(m.scratch[b]);
                    nn = N.set(pn).mul(m.scratch[b].get3x3(BN));
                } else {
                    mm = pm;
                    nn = pn;
                }
            }
            int o = q * 4 * Mesh.STRIDE;
            float nx0 = d[o + 5], ny0 = d[o + 6], nz0 = d[o + 7];
            float nx = nn.m00 * nx0 + nn.m10 * ny0 + nn.m20 * nz0;
            float ny = nn.m01 * nx0 + nn.m11 * ny0 + nn.m21 * nz0;
            float nz = nn.m02 * nx0 + nn.m12 * ny0 + nn.m22 * nz0;
            float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (len > 1e-6f) { nx /= len; ny /= len; nz /= len; }
            for (int i = 0; i < 4; i++, o += Mesh.STRIDE) {
                float x = d[o], y = d[o + 1], z = d[o + 2];
                float tx = mm.m00() * x + mm.m10() * y + mm.m20() * z + mm.m30();
                float ty = mm.m01() * x + mm.m11() * y + mm.m21() * z + mm.m31();
                float tz = mm.m02() * x + mm.m12() * y + mm.m22() * z + mm.m32();
                vc.addVertex(tx, ty, tz, color, d[o + 3], d[o + 4], overlay, light, nx, ny, nz);
            }
        }
    }

    // ── отложенная отправка ──────────────────────────────────

    /** Задание на отрисовку модели в коллектор (переиспользуется, без лямбд с захватом). */
    private static final class Job implements SubmitNodeCollector.CustomGeometryRenderer {
        Mesh mesh;
        int color, overlay, light, pass;
        float[] pose;
        float[] poseCopy = new float[9 * 64];

        @Override
        public void render(PoseStack.Pose p, VertexConsumer vc) {
            Mesh m = mesh;
            if (m == null) return;
            if (m.bones.length > 0) bones(m, pose == null ? null : poseCopy);
            draw(m, p, vc, color, overlay, light, pass);
        }
    }

    private static final Job[] POOL = new Job[256];
    private static int next;

    static {
        for (int i = 0; i < POOL.length; i++) POOL[i] = new Job();
    }

    /** Типы рендера модели: твёрдые части без отсечения граней (плоские детали видны с обеих сторон). */
    public static RenderType solidType(Mesh m) {
        return RenderTypes.entityCutoutNoCull(m.texture());
    }

    /**
     * Отправить модель целиком (обычный проход и свечение) в коллектор в текущей позе ps.
     * bonePose — поза костей (или null — поза покоя); копируется, можно сразу переиспользовать.
     */
    public static void submit(SubmitNodeCollector c, PoseStack ps, Mesh m, float[] bonePose, int color, int overlay, int light, boolean glow) {
        if (m == null || m.texture() == null) return;
        m.atlas.lastUsed = System.currentTimeMillis();
        submitPass(c, ps, solidType(m), m, bonePose, color, overlay, light, SOLID);
        if (!glow || !m.hasGlow) return;
        if (m.selfGlow) submitPass(c, ps, RenderTypes.eyes(m.texture()), m, bonePose, color, overlay, light, GLOW_SELF);
        if (m.atlas.glowTexture != null) submitPass(c, ps, RenderTypes.eyes(m.atlas.glowTexture), m, bonePose, color, overlay, light, GLOW_LAYER);
    }

    private static void submitPass(SubmitNodeCollector c, PoseStack ps, RenderType type, Mesh m, float[] bonePose, int color, int overlay, int light, int pass) {
        Job j = POOL[next];
        next = (next + 1) % POOL.length;
        j.mesh = m;
        j.color = color;
        j.overlay = overlay;
        j.light = light;
        j.pass = pass;
        j.pose = bonePose;
        if (bonePose != null) {
            if (j.poseCopy.length < bonePose.length) j.poseCopy = new float[bonePose.length];
            System.arraycopy(bonePose, 0, j.poseCopy, 0, bonePose.length);
        }
        c.submitCustomGeometry(ps, type, j);
    }
}

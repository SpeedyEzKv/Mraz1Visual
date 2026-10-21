package dev.mraz1visual.models;

import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;

/**
 * Запечённая модель: квады готовыми вершинами (позиция в блоках, UV уже в атласе, нормаль),
 * кость на каждый квад и одна текстура-атлас (плюс эмиссивный атлас). Строится один раз при загрузке,
 * в кадре только умножается на матрицы и пишется в буфер.
 */
public final class Mesh {
    /** На вершину: x, y, z, u, v, nx, ny, nz. Четыре вершины на квад. */
    public static final int STRIDE = 8;
    /** Квад рисуется обычным проходом / проходом свечения. */
    public static final byte SOLID = 1, GLOW = 2;

    public final float[] data;
    public final int quads;
    public final short[] quadBone;
    public final byte[] quadFlags;
    public final Bone[] bones;
    public final Atlas atlas;
    /** Границы в блоках: minX, minY, minZ, maxX, maxY, maxZ (в позе покоя). */
    public final float[] bounds = new float[6];
    public boolean hasGlow;
    /** Есть квады с эмиссивной текстурой модели (светятся сами). */
    public boolean selfGlow;

    /** Матрицы костей (служебные, только поток рендера). */
    final Matrix4f[] scratch;

    Mesh(float[] data, int quads, short[] quadBone, byte[] quadFlags, Bone[] bones, Atlas atlas) {
        this.data = data;
        this.quads = quads;
        this.quadBone = quadBone;
        this.quadFlags = quadFlags;
        this.bones = bones;
        this.atlas = atlas;
        this.scratch = new Matrix4f[bones.length];
        for (int i = 0; i < scratch.length; i++) scratch[i] = new Matrix4f();
        float x0 = Float.MAX_VALUE, y0 = x0, z0 = x0, x1 = -x0, y1 = x1, z1 = x1;
        for (int i = 0; i < quads * 4; i++) {
            int o = i * STRIDE;
            x0 = Math.min(x0, data[o]); y0 = Math.min(y0, data[o + 1]); z0 = Math.min(z0, data[o + 2]);
            x1 = Math.max(x1, data[o]); y1 = Math.max(y1, data[o + 1]); z1 = Math.max(z1, data[o + 2]);
        }
        if (quads == 0) { x0 = y0 = z0 = 0; x1 = y1 = z1 = 1; }
        bounds[0] = x0; bounds[1] = y0; bounds[2] = z0; bounds[3] = x1; bounds[4] = y1; bounds[5] = z1;
        for (byte f : quadFlags) if ((f & GLOW) != 0) { hasGlow = selfGlow = true; break; }
        if (atlas.glow != null) hasGlow = true;
    }

    public Identifier texture() { return atlas.texture; }

    public Identifier glowTexture() { return atlas.glowTexture != null ? atlas.glowTexture : atlas.texture; }

    public float sizeX() { return bounds[3] - bounds[0]; }
    public float sizeY() { return bounds[4] - bounds[1]; }
    public float sizeZ() { return bounds[5] - bounds[2]; }

    /** Кость: родитель, точка вращения (в блоках) и поворот покоя (радианы, порядок Z·Y·X). */
    public static final class Bone {
        public final String name;
        public final int parent;
        public final float px, py, pz, rx, ry, rz;

        public Bone(String name, int parent, float px, float py, float pz, float rx, float ry, float rz) {
            this.name = name;
            this.parent = parent;
            this.px = px; this.py = py; this.pz = pz;
            this.rx = rx; this.ry = ry; this.rz = rz;
        }
    }
}

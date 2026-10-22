package dev.mraz1visual.models;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Сборка модели из кубов (item JSON, bbmodel) и плоских квадов (экструзия): координаты — в пикселях модели
 * (1/16 блока), UV — в пространстве своей текстуры. На выходе — {@link Mesh} с атласом.
 */
final class MeshBuilder {
    final List<TexImage> textures = new ArrayList<>();
    final List<Mesh.Bone> bones = new ArrayList<>();
    private float[] v = new float[1024];
    private int nv;
    private final List<Integer> quadTex = new ArrayList<>();
    private final List<Short> quadBone = new ArrayList<>();
    private final Vector3f tmp = new Vector3f();

    int addTexture(TexImage t) {
        textures.add(t);
        return textures.size() - 1;
    }

    int addBone(Mesh.Bone b) {
        bones.add(b);
        return bones.size() - 1;
    }

    int quadCount() {
        return quadTex.size();
    }

    // Грани куба: ось U и знак (+1 — s=0 у минимума), ось V и знак, ось нормали и сторона.
    // Совпадает с раскладкой UV ванили и Blockbench (вид снаружи, без зеркала).
    static final String[] DIRS = {"down", "up", "north", "south", "west", "east"};
    private static final int[][] FACE = {
            // uAxis, uSign, vAxis, vSign, nAxis, nSide
            {0, 1, 2, -1, 1, 0},  // down: y=min
            {0, 1, 2, 1, 1, 1},   // up
            {0, -1, 1, -1, 2, 0}, // north: z=min
            {0, 1, 1, -1, 2, 1},  // south
            {2, 1, 1, -1, 0, 0},  // west: x=min
            {2, -1, 1, -1, 0, 1}, // east
    };
    private static final int[] CS = {0, 0, 1, 1}, CT = {0, 1, 1, 0};

    /**
     * Куб от from до to (пиксели). faces[d] — {u1, v1, u2, v2} в пикселях UV текстуры или null (грани нет),
     * rot[d] — поворот UV грани (0/90/180/270), tex[d] — индекс текстуры. m — поворот элемента (или null).
     */
    void cube(float[] from, float[] to, float[][] faces, int[] rot, int[] tex, Matrix4f m, int bone) {
        float[] min = {Math.min(from[0], to[0]), Math.min(from[1], to[1]), Math.min(from[2], to[2])};
        float[] max = {Math.max(from[0], to[0]), Math.max(from[1], to[1]), Math.max(from[2], to[2])};
        float[] p = new float[12], uv = new float[8];
        for (int d = 0; d < 6; d++) {
            float[] f = faces[d];
            if (f == null || tex[d] < 0 || tex[d] >= textures.size()) continue;
            int[] fc = FACE[d];
            // плоская грань у куба нулевой толщины по этой оси — рисуем только одну из пары
            if (min[fc[4]] == max[fc[4]] && fc[5] == 1 && faces[d - 1] != null) continue;
            TexImage t = textures.get(tex[d]);
            int r = Math.floorMod(rot[d] / 90, 4);
            for (int i = 0; i < 4; i++) {
                float[] c = new float[3];
                c[fc[4]] = fc[5] == 1 ? max[fc[4]] : min[fc[4]];
                int s = CS[i], tt = CT[i];
                c[fc[0]] = (fc[1] > 0) == (s == 0) ? min[fc[0]] : max[fc[0]];
                c[fc[2]] = (fc[3] > 0) == (tt == 0) ? min[fc[2]] : max[fc[2]];
                p[i * 3] = c[0]; p[i * 3 + 1] = c[1]; p[i * 3 + 2] = c[2];
                int j = (i + r) % 4;
                uv[i * 2] = (f[0] + CS[j] * (f[2] - f[0])) / t.uvW;
                uv[i * 2 + 1] = (f[1] + CT[j] * (f[3] - f[1])) / t.uvH;
            }
            float nx = fc[4] == 0 ? (fc[5] == 1 ? 1 : -1) : 0, ny = fc[4] == 1 ? (fc[5] == 1 ? 1 : -1) : 0, nz = fc[4] == 2 ? (fc[5] == 1 ? 1 : -1) : 0;
            quad(p, uv, nx, ny, nz, m, tex[d], bone);
        }
    }

    /** Квад: 4 точки (пиксели) против часовой снаружи, UV 0..1 в своей текстуре. */
    void quad(float[] p, float[] uv, float nx, float ny, float nz, Matrix4f m, int tex, int bone) {
        if (nv + 32 > v.length) v = java.util.Arrays.copyOf(v, v.length * 2);
        if (m != null) {
            m.transformDirection(tmp.set(nx, ny, nz)).normalize();
            nx = tmp.x; ny = tmp.y; nz = tmp.z;
        }
        for (int i = 0; i < 4; i++) {
            float x = p[i * 3], y = p[i * 3 + 1], z = p[i * 3 + 2];
            if (m != null) {
                m.transformPosition(tmp.set(x, y, z));
                x = tmp.x; y = tmp.y; z = tmp.z;
            }
            v[nv++] = x / 16f; v[nv++] = y / 16f; v[nv++] = z / 16f;
            v[nv++] = uv[i * 2]; v[nv++] = uv[i * 2 + 1];
            v[nv++] = nx; v[nv++] = ny; v[nv++] = nz;
        }
        quadTex.add(tex);
        quadBone.add((short) bone);
    }

    /** Поворот вокруг точки (пиксели): R = Rz·Ry·Rx, углы в градусах (как у Blockbench). */
    static Matrix4f rotation(float ox, float oy, float oz, float rx, float ry, float rz) {
        if (rx == 0 && ry == 0 && rz == 0) return null;
        return new Matrix4f().translate(ox, oy, oz)
                .rotateZYX((float) Math.toRadians(rz), (float) Math.toRadians(ry), (float) Math.toRadians(rx))
                .translate(-ox, -oy, -oz);
    }

    Mesh build() {
        int quads = quadTex.size();
        if (textures.isEmpty()) textures.add(TexImage.solid("white", 0xFFFFFFFF));
        Atlas atlas = Atlas.pack(textures);
        float[] data = java.util.Arrays.copyOf(v, quads * 4 * Mesh.STRIDE);
        short[] qb = new short[quads];
        byte[] flags = new byte[quads];
        for (int q = 0; q < quads; q++) {
            int ti = quadTex.get(q);
            qb[q] = quadBone.get(q);
            TexImage t = textures.get(ti);
            flags[q] = t.emissive ? Mesh.GLOW : Mesh.SOLID;
            for (int i = 0; i < 4; i++) {
                int o = (q * 4 + i) * Mesh.STRIDE;
                // UV за краем текстуры (бывает у моделей из Blockbench) — по кругу, как повтор
                float fu = data[o + 3], fv = data[o + 4];
                data[o + 3] = atlas.u(ti, clamp01(fu));
                data[o + 4] = atlas.v(ti, clamp01(fv));
            }
        }
        return new Mesh(data, quads, qb, flags, bones.toArray(new Mesh.Bone[0]), atlas);
    }

    private static float clamp01(float f) {
        return f < 0 ? 0 : f > 1 ? 1 : f;
    }
}

package dev.mraz1visual.models;

/**
 * Плоская текстура → объёмный предмет, как ванильный item/generated, но толщиной до 1–1.5 px:
 * лицевая и задняя грани на всю картинку (прозрачное отсекается текстурой), по краям непрозрачных
 * пикселей — боковые грани, соседние пиксели одной строки/столбца склеены в одну полосу.
 * Свечение (по желанию): яркие насыщенные пиксели попадают в эмиссивный слой.
 */
final class Extruder {
    private Extruder() {}

    /** thickness — в пикселях модели (1/16 блока), z — сдвиг слоя, чтобы слои не мерцали. */
    static void extrude(MeshBuilder b, TexImage t, float z, float thickness, boolean glow) {
        if (glow && t.glow == null) t.glow = glowLayer(t.frames[0]);
        int tex = b.addTexture(t);
        int w = t.w, h = t.h;
        int[] px = t.frames[0];
        float z0 = 8 - thickness / 2 - z, z1 = 8 + thickness / 2 + z;
        float sx = 16f / w, sy = 16f / h;
        // лицевая (юг, +z) и задняя (север, −z)
        b.quad(new float[]{0, 16, z1, 0, 0, z1, 16, 0, z1, 16, 16, z1}, new float[]{0, 0, 0, 1, 1, 1, 1, 0}, 0, 0, 1, null, tex, -1);
        b.quad(new float[]{16, 16, z0, 16, 0, z0, 0, 0, z0, 0, 16, z0}, new float[]{1, 0, 1, 1, 0, 1, 0, 0}, 0, 0, -1, null, tex, -1);
        // верх/низ пикселей: полосы по строкам
        for (int y = 0; y < h; y++) {
            for (int side = 0; side < 2; side++) {
                int ny = side == 0 ? y - 1 : y + 1;
                int x = 0;
                while (x < w) {
                    if (!edge(px, w, h, x, y, x, ny)) { x++; continue; }
                    int x0 = x;
                    while (x < w && edge(px, w, h, x, y, x, ny)) x++;
                    float u0 = x0 / (float) w, u1 = x / (float) w, v0 = y / (float) h, v1 = (y + 1) / (float) h;
                    float X0 = x0 * sx, X1 = x * sx;
                    if (side == 0) { // верхний край пикселя (+y)
                        float Y = 16 - y * sy;
                        b.quad(new float[]{X0, Y, z0, X0, Y, z1, X1, Y, z1, X1, Y, z0}, new float[]{u0, v0, u0, v1, u1, v1, u1, v0}, 0, 1, 0, null, tex, -1);
                    } else {
                        float Y = 16 - (y + 1) * sy;
                        b.quad(new float[]{X0, Y, z1, X0, Y, z0, X1, Y, z0, X1, Y, z1}, new float[]{u0, v0, u0, v1, u1, v1, u1, v0}, 0, -1, 0, null, tex, -1);
                    }
                }
            }
        }
        // левый/правый края: полосы по столбцам
        for (int x = 0; x < w; x++) {
            for (int side = 0; side < 2; side++) {
                int nx = side == 0 ? x - 1 : x + 1;
                int y = 0;
                while (y < h) {
                    if (!edge(px, w, h, x, y, nx, y)) { y++; continue; }
                    int y0 = y;
                    while (y < h && edge(px, w, h, x, y, nx, y)) y++;
                    float u0 = x / (float) w, u1 = (x + 1) / (float) w, v0 = y0 / (float) h, v1 = y / (float) h;
                    float Y0 = 16 - y0 * sy, Y1 = 16 - y * sy;
                    if (side == 0) { // левый (−x)
                        float X = x * sx;
                        b.quad(new float[]{X, Y0, z0, X, Y1, z0, X, Y1, z1, X, Y0, z1}, new float[]{u0, v0, u0, v1, u1, v1, u1, v0}, -1, 0, 0, null, tex, -1);
                    } else {
                        float X = (x + 1) * sx;
                        b.quad(new float[]{X, Y0, z1, X, Y1, z1, X, Y1, z0, X, Y0, z0}, new float[]{u0, v0, u0, v1, u1, v1, u1, v0}, 1, 0, 0, null, tex, -1);
                    }
                }
            }
        }
    }

    private static boolean opaque(int[] px, int w, int h, int x, int y) {
        return x >= 0 && y >= 0 && x < w && y < h && (px[y * w + x] >>> 24) > 16;
    }

    private static boolean edge(int[] px, int w, int h, int x, int y, int nx, int ny) {
        return opaque(px, w, h, x, y) && !opaque(px, w, h, nx, ny);
    }

    /** Слой свечения: яркие и насыщенные пиксели (клинок, руны), тёмная рукоять не светится. */
    private static int[] glowLayer(int[] px) {
        int[] out = new int[px.length];
        for (int i = 0; i < px.length; i++) {
            int c = px[i];
            int a = c >>> 24, r = c >> 16 & 255, g = c >> 8 & 255, bl = c & 255;
            int max = Math.max(r, Math.max(g, bl)), min = Math.min(r, Math.min(g, bl));
            float sat = max == 0 ? 0 : (max - min) / (float) max;
            float k = Math.max(0, (max / 255f - 0.55f) / 0.45f) * (0.35f + 0.65f * sat);
            if (a > 16 && k > 0.05f) out[i] = Math.min(255, (int) (a * k)) << 24 | (c & 0xFFFFFF);
        }
        return out;
    }
}

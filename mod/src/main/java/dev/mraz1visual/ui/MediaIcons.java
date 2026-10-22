package dev.mraz1visual.ui;

/**
 * Иконки управления музыкой фигурами (в шрифтах клиента нет символов ⏮ ⏯ ⏭): треугольник — строками по 0,5 px,
 * паузы — две скруглённые полоски. Размер s — высота иконки в единицах Draw.
 */
public final class MediaIcons {
    private MediaIcons() {}

    public static final int PREV = 0, PLAY = 1, PAUSE = 2, NEXT = 3;

    public static void draw(Draw d, int kind, float cx, float cy, float s, int color) {
        switch (kind) {
            case PLAY -> tri(d, cx - s * 0.3f, cy, s, true, color);
            case PAUSE -> {
                bar(d, cx - s * 0.32f, cy - s / 2, s * 0.22f, s, color);
                bar(d, cx + s * 0.1f, cy - s / 2, s * 0.22f, s, color);
            }
            case PREV -> {
                bar(d, cx - s * 0.5f, cy - s * 0.4f, s * 0.14f, s * 0.8f, color);
                tri(d, cx + s * 0.35f, cy, s * 0.8f, false, color);
            }
            default -> {
                tri(d, cx - s * 0.35f, cy, s * 0.8f, true, color);
                bar(d, cx + s * 0.36f, cy - s * 0.4f, s * 0.14f, s * 0.8f, color);
            }
        }
    }

    private static void bar(Draw d, float x, float y, float w, float h, int color) {
        Sdf.fill(d.g, x, y, w, h, Math.min(w, h) / 2, color);
    }

    /** Треугольник остриём вправо (right) или влево; x — основание, высота s, ширина 0,8 s. */
    private static void tri(Draw d, float x, float cy, float s, boolean right, int color) {
        float w = s * 0.8f;
        int rows = Math.max(4, Math.round(s * 2));
        float rh = s / rows;
        for (int i = 0; i < rows; i++) {
            float yy = cy - s / 2 + i * rh;
            float mid = Math.abs((i + 0.5f) / rows - 0.5f) * 2; // 0 в центре, 1 у краёв
            float len = w * (1 - mid);
            if (len <= 0.05f) continue;
            d.rectF(right ? x : x - len, yy, len, rh + 0.05f, color);
        }
    }
}

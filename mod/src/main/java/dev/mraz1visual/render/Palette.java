package dev.mraz1visual.render;

import dev.mraz1visual.ui.Colors;

/**
 * Единая палитра визуалов — чтобы косметика сочеталась между собой. У каждого пресета три тона:
 * «горячий» (почти белый центр свечения), основной и «холодный» край. Цвет по t: 0 — центр, 1 — край.
 * Хром — сдержанный перелив тонкой плёнки (плавный сдвиг оттенка), не кислотная радуга.
 */
public final class Palette {
    private Palette() {}

    public static final String[] IDS = {"ether", "amethyst", "sunset", "emerald", "obsidian", "chrome"};
    public static final String[] OPTIONS = {"ether", "Эфир", "amethyst", "Аметист", "sunset", "Закат", "emerald", "Изумруд",
            "obsidian", "Обсидиан", "chrome", "Хром", "custom", "Свой градиент"};

    private static final int[] ETHER = {0xFFF2FAFF, 0xFF8FD8FF, 0xFF3D7BFF};
    private static final int[] AMETHYST = {0xFFF6EEFF, 0xFFB896FF, 0xFF6A3FE0};
    private static final int[] SUNSET = {0xFFFFF4D6, 0xFFFFB067, 0xFFFF5E8E};
    private static final int[] EMERALD = {0xFFEDFFF7, 0xFF62E6B4, 0xFF15A06C};
    private static final int[] OBSIDIAN = {0xFFE8E2FF, 0xFF8C6CFF, 0xFF241A3A};

    private static int[] tones(String id) {
        return switch (id) {
            case "amethyst" -> AMETHYST;
            case "sunset" -> SUNSET;
            case "emerald" -> EMERALD;
            case "obsidian" -> OBSIDIAN;
            default -> ETHER;
        };
    }

    /**
     * Цвет пресета: t 0..1 от центра к краю, phase — для перелива хрома и своих градиентов (обычно время + смещение).
     * custom — от c1 (центр) к c2 (край).
     */
    public static int color(String id, float t, float phase, int c1, int c2) {
        t = Math.max(0f, Math.min(1f, t));
        if (id.equals("custom")) return t < 0.15f ? Colors.lerp(0xFFFFFFFF, c1, t / 0.15f) : Colors.lerp(c1, c2, (t - 0.15f) / 0.85f);
        if (id.equals("chrome")) {
            // перелив: оттенок медленно плывёт, насыщенность невысокая — «плёнка», а не радуга
            float h = frac(0.55f + phase * 0.08f + t * 0.35f);
            int c = hsv(h, 0.18f + 0.32f * t, 1f - 0.18f * t);
            return t < 0.12f ? Colors.lerp(0xFFFFFFFF, c, t / 0.12f) : c;
        }
        int[] p = tones(id);
        return t < 0.3f ? Colors.lerp(p[0], p[1], t / 0.3f) : Colors.lerp(p[1], p[2], (t - 0.3f) / 0.7f);
    }

    private static float frac(float x) { return x - (float) Math.floor(x); }

    private static int hsv(float h, float s, float v) {
        float r = Math.abs(h * 6f - 3f) - 1f, g = 2f - Math.abs(h * 6f - 2f), b = 2f - Math.abs(h * 6f - 4f);
        r = clamp(r); g = clamp(g); b = clamp(b);
        r = ((r - 1f) * s + 1f) * v; g = ((g - 1f) * s + 1f) * v; b = ((b - 1f) * s + 1f) * v;
        return 0xFF000000 | Math.round(r * 255) << 16 | Math.round(g * 255) << 8 | Math.round(b * 255);
    }

    private static float clamp(float x) { return x < 0 ? 0 : Math.min(1, x); }
}

package dev.mraz1visual.ui;

/** Работа с ARGB без объектов. */
public final class Colors {
    private Colors() {}

    public static int withAlpha(int argb, int a) {
        return (Math.max(0, Math.min(255, a)) << 24) | (argb & 0xFFFFFF);
    }

    public static int mulAlpha(int argb, float k) {
        return withAlpha(argb, Math.round((argb >>> 24) * k));
    }

    public static int lerp(int a, int b, float t) {
        if (t <= 0) return a;
        if (t >= 1) return b;
        int aa = a >>> 24, ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int ba = b >>> 24, br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return ((int) (aa + (ba - aa) * t) << 24)
                | ((int) (ar + (br - ar) * t) << 16)
                | ((int) (ag + (bg - ag) * t) << 8)
                | (int) (ab + (bb - ab) * t);
    }

    /** h, s, v в 0..1 → RGB без альфы. */
    public static int hsv(float h, float s, float v) {
        h = (h - (float) Math.floor(h)) * 6f;
        int i = (int) h;
        float f = h - i;
        float p = v * (1 - s), q = v * (1 - s * f), t = v * (1 - s * (1 - f));
        float r, g, b;
        switch (i) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return (Math.round(r * 255) << 16) | (Math.round(g * 255) << 8) | Math.round(b * 255);
    }

    /** RGB → {h, s, v} в переданный массив (без аллокаций). */
    public static void toHsv(int rgb, float[] out) {
        float r = ((rgb >> 16) & 255) / 255f, g = ((rgb >> 8) & 255) / 255f, b = (rgb & 255) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        float d = max - min;
        float h;
        if (d == 0) h = 0;
        else if (max == r) h = ((g - b) / d) % 6f;
        else if (max == g) h = (b - r) / d + 2f;
        else h = (r - g) / d + 4f;
        h /= 6f;
        if (h < 0) h += 1;
        out[0] = h;
        out[1] = max == 0 ? 0 : d / max;
        out[2] = max;
    }

    /** Яркость 0..1. */
    public static float luma(int c) {
        return ((c >> 16 & 0xFF) * 0.299f + (c >> 8 & 0xFF) * 0.587f + (c & 0xFF) * 0.114f) / 255f;
    }

    /** Умножить RGB на k (затемнить/осветлить), альфа как была. */
    public static int shade(int argb, float k) {
        int r = Math.min(255, Math.round(((argb >> 16) & 255) * k));
        int g = Math.min(255, Math.round(((argb >> 8) & 255) * k));
        int b = Math.min(255, Math.round((argb & 255) * k));
        return (argb & 0xFF000000) | (Math.max(0, r) << 16) | (Math.max(0, g) << 8) | Math.max(0, b);
    }
}

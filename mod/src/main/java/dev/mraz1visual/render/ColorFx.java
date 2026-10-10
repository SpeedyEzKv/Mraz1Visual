package dev.mraz1visual.render;

import dev.mraz1visual.ui.Colors;

/** Цвета эффектов: один цвет, градиент между двумя или радуга — с плавной анимацией. */
public final class ColorFx {
    private ColorFx() {}

    /** mode: "solid", "gradient", "rainbow". t — фаза 0..1 (например, позиция по кругу). */
    public static int pick(String mode, int c1, int c2, float t) {
        float time = (System.currentTimeMillis() % 100000) / 1000f;
        return switch (mode) {
            case "gradient" -> {
                float k = 0.5f + 0.5f * (float) Math.sin((t + time * 0.25f) * Math.PI * 2);
                int c = Colors.lerp(c1, c2, k);
                yield c;
            }
            case "rainbow" -> Colors.withAlpha(Colors.hsv(((t + time * 0.15f) % 1f + 1f) % 1f, 0.55f, 1f), c1 >>> 24);
            default -> c1;
        };
    }
}

package dev.mraz1visual.render;

import dev.mraz1visual.module.VisualFx;
import dev.mraz1visual.ui.Colors;

/**
 * Общая база визуалов: качество (сколько частиц и сегментов), яркость эффектов, «Без вспышек» и один пресет
 * палитры на все визуалы. Модули спрашивают отсюда, а не держат свои копии настроек — так всё выглядит одной системой.
 */
public final class Fx {
    private Fx() {}

    /** Множитель качества: 0.35 / 0.6 / 1 / 1.5. */
    public static float quality() {
        VisualFx v = VisualFx.get();
        if (v == null) return 1f;
        return switch (v.quality.getId()) {
            case "low" -> 0.35f;
            case "medium" -> 0.6f;
            case "ultra" -> 1.5f;
            default -> 1f;
        };
    }

    /** Сколько частиц/элементов при текущем качестве (не меньше 1). */
    public static int count(int n) {
        return Math.max(1, Math.round(n * quality()));
    }

    /** Сегменты кругов и лент при текущем качестве (8…96). */
    public static int segments(int n) {
        return Math.max(8, Math.min(96, Math.round(n * (0.5f + 0.5f * quality()))));
    }

    /** Яркость эффектов 0.3…1.3 (множитель прозрачности всего, что рисуется в мире). */
    public static float brightness() {
        VisualFx v = VisualFx.get();
        return v == null ? 1f : v.brightness.getF() / 100f;
    }

    /** «Без вспышек»: резкие вспышки гасятся до мягкого свечения. k — сила вспышки 0..1. */
    public static float flash(float k) {
        VisualFx v = VisualFx.get();
        return v != null && v.noFlash.get() ? Math.min(k, 0.3f) : k;
    }

    public static boolean noFlash() {
        VisualFx v = VisualFx.get();
        return v != null && v.noFlash.get();
    }

    /**
     * Цвет эффекта по пресету палитры: если включено «Один пресет на все визуалы» — пресет из общих настроек.
     * t — 0 центр (почти белый) … 1 край, phase — для перелива.
     */
    public static int color(String preset, float t, float phase, int c1, int c2) {
        VisualFx v = VisualFx.get();
        if (v != null && v.sync.get()) {
            preset = v.preset.getId();
            c1 = v.color1.get();
            c2 = v.color2.get();
        }
        return Palette.color(preset, t, phase, c1, c2);
    }

    /** Цвет пресета с прозрачностью a (0..1). */
    public static int color(String preset, float t, float phase, int c1, int c2, float a) {
        return Colors.mulAlpha(color(preset, t, phase, c1, c2), a);
    }

    /** Секунды с запуска — общее время анимаций (по реальному времени кадра). */
    public static float time() {
        return (System.nanoTime() - START) / 1e9f;
    }

    private static final long START = System.nanoTime();
}

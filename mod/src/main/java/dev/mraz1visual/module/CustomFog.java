package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.setting.ColorSetting;
import dev.mraz1visual.setting.SliderSetting;
import org.joml.Vector4f;

/** Свой цвет тумана и горизонта: атмосфера заката, ночи или неона. */
public final class CustomFog extends Module {
    private static CustomFog instance;
    final dev.mraz1visual.setting.EnumSetting preset = add(new dev.mraz1visual.setting.EnumSetting("preset", "Пресет", 0,
            "custom", "Свой", "sunset", "Закат", "night", "Ночь", "violet", "Фиолет").onChange(this::applyPreset));
    final ColorSetting color = add(new ColorSetting("color", "Цвет тумана (горизонт)", 0xFF8A6FD8));
    final SliderSetting mix = add(new SliderSetting("mix", "Сила", 0.7, 0.1, 1, 0.05, ""));
    final ColorSetting sky = add(new ColorSetting("sky", "Цвет неба (верх)", 0xFF3B2A7A));
    final SliderSetting skyMix = add(new SliderSetting("sky_mix", "Сила для неба", 0.6, 0, 1, 0.05, ""));

    public CustomFog() {
        super("custom_fog", "CustomWorld", "Небо градиентом из двух цветов (верх и горизонт), цвет тумана, пресеты.", Category.SKY);
        instance = this;
        markNew();
    }

    /** Из миксина FogRenderer: смешивает цвет тумана со своим. */
    public static Vector4f fog(Vector4f v) {
        CustomFog f = instance;
        if (f == null || !f.isEnabled() || v == null) return v;
        int c = f.color.get();
        float k = f.mix.getF();
        // на месте: вектор свой у каждого вызова computeFogColor, лишний объект в кадре не нужен
        return v.set(lerp(v.x(), (c >> 16 & 255) / 255f, k), lerp(v.y(), (c >> 8 & 255) / 255f, k), lerp(v.z(), (c & 255) / 255f, k), v.w());
    }

    /** Из миксина неба: цвет купола неба (RGB int). */
    public static int sky(int rgb) {
        CustomFog f = instance;
        if (f == null || !f.isEnabled()) return rgb;
        int c = f.sky.get();
        float k = f.skyMix.getF();
        int r = Math.round(lerp((rgb >> 16 & 255), (c >> 16 & 255), k)), g = Math.round(lerp((rgb >> 8 & 255), (c >> 8 & 255), k)),
                b = Math.round(lerp(rgb & 255, c & 255, k));
        return (rgb & 0xFF000000) | r << 16 | g << 8 | b;
    }

    /** Пресет переписывает цвета; потом их можно подправить вручную. */
    private void applyPreset() {
        switch (preset.getId()) {
            case "sunset" -> { sky.set(0xFF3A2A6E); color.set(0xFFFF8A5B); }
            case "night" -> { sky.set(0xFF070B1E); color.set(0xFF1C2448); }
            case "violet" -> { sky.set(0xFF2B1B5E); color.set(0xFF8B6CFF); }
            default -> { }
        }
    }

    private static float lerp(float a, float b, float k) {
        return a + (b - a) * k;
    }
}

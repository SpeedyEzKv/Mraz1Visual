package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.setting.SliderSetting;

/**
 * Насыщенность картинки пост-шейдером Mraz1 PostFX. Работает и без включённого PostFX —
 * тогда остальные эффекты нейтральны и меняется только насыщенность. С шейдерпаком Iris не действует.
 */
public final class Saturation extends Module {
    private static Saturation instance;
    final SliderSetting strength = add(new SliderSetting("strength", "Сила", 1.3, 0, 2, 0.05, "×"));

    public Saturation() {
        super("saturation", "Saturation", "Насыщенность цветов через пост-шейдер.", Category.SKY);
        instance = this;
        markNew();
    }

    public static boolean active() {
        return instance != null && instance.isEnabled();
    }

    public static float strength() {
        return instance == null ? 1f : instance.strength.getF();
    }
}

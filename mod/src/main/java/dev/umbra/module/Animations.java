package dev.umbra.module;

import dev.umbra.core.Category;
import dev.umbra.core.Module;
import dev.umbra.setting.SliderSetting;

/**
 * Скорость анимации взмаха рукой. Только анимация у тебя на экране:
 * скорость атаки и перезарядка удара не меняются.
 */
public final class Animations extends Module {
    private static Animations instance;

    final SliderSetting speed = add(new SliderSetting("speed", "Скорость взмаха", 1.0, 0.3, 2.0, 0.05, "×")
            .hint("Меньше 1 — медленнее и плавнее, больше 1 — резче."));

    public Animations() {
        super("animations", "Animations", "Скорость анимации взмаха рукой.", Category.VISUALS);
        instance = this;
    }

    /** Длительность взмаха в тиках после множителя. */
    public static int swingDuration(int original) {
        Animations a = instance;
        if (a == null || !a.isEnabled()) return original;
        return Math.max(1, Math.round(original / a.speed.getF()));
    }
}

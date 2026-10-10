package dev.mraz1visual.module;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.setting.SliderSetting;

/** Положение и размер рук от первого лица. Влияет только на картинку у тебя. */
public final class ViewModel extends Module {
    private static ViewModel instance;

    final SliderSetting x = add(new SliderSetting("x", "Влево / вправо", 0, -0.8, 0.8, 0.02, ""));
    final SliderSetting y = add(new SliderSetting("y", "Вниз / вверх", 0, -0.8, 0.8, 0.02, ""));
    final SliderSetting z = add(new SliderSetting("z", "Ближе / дальше", 0, -0.8, 0.8, 0.02, ""));
    final SliderSetting scale = add(new SliderSetting("scale", "Размер", 1, 0.4, 1.6, 0.02, "×"));

    private boolean pushed;

    public ViewModel() {
        super("view_model", "View Model", "Сдвиг и размер рук и предметов в руках.", Category.VISUALS);
        instance = this;
    }

    /** Вызывается миксином в начале отрисовки рук. */
    public static void apply(PoseStack ps) {
        ViewModel v = instance;
        if (v == null || !v.isEnabled()) return;
        ps.pushPose();
        ps.translate(v.x.getF(), v.y.getF(), -v.z.getF());
        float s = v.scale.getF();
        ps.scale(s, s, s);
        v.pushed = true;
    }

    /** Вызывается миксином в конце отрисовки рук. */
    public static void restore(PoseStack ps) {
        ViewModel v = instance;
        if (v == null || !v.pushed) return;
        ps.popPose();
        v.pushed = false;
    }
}

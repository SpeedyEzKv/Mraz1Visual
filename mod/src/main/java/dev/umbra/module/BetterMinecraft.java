package dev.umbra.module;

import dev.umbra.core.Category;
import dev.umbra.core.Module;
import dev.umbra.setting.BoolSetting;

/** Мелкие улучшения картинки: без тряски при уроне и без размытия фона в меню. */
public final class BetterMinecraft extends Module {
    private static BetterMinecraft instance;
    final BoolSetting noHurtCam = add(new BoolSetting("no_hurt_cam", "Без тряски камеры при уроне", true));
    final BoolSetting noMenuBlur = add(new BoolSetting("no_menu_blur", "Без размытия фона в меню", true));

    public BetterMinecraft() {
        super("better_minecraft", "Better Minecraft", "Без тряски при уроне и размытия в меню.", Category.VISUALS);
        instance = this;
    }

    public static boolean noHurtCam() {
        return instance != null && instance.isEnabled() && instance.noHurtCam.get();
    }

    public static boolean noMenuBlur() {
        return instance != null && instance.isEnabled() && instance.noMenuBlur.get();
    }
}

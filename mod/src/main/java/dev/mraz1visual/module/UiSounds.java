package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.setting.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

/** Тихий щелчок при включении (выше тон) и выключении (ниже тон) функций. */
public final class UiSounds extends Module {
    private static UiSounds instance;
    final SliderSetting volume = add(new SliderSetting("volume", "Громкость", 0.5, 0.05, 1, 0.05, ""));

    public UiSounds() {
        super("ui_sounds", "UI Sounds", "Щелчок при включении и выключении функций.", Category.MISC);
        instance = this;
    }

    public static void toggled(Module m, boolean on) {
        UiSounds u = instance;
        if (u == null || !u.isEnabled() || m == u || m.alwaysOn()) return;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), on ? 1.4f : 0.9f, u.volume.getF()));
    }
}

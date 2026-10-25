package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import net.minecraft.client.Minecraft;

/**
 * Переключение приседа: нажал Shift — сидишь, нажал ещё раз — встал. Включает игровую настройку
 * «Присед: переключение», а при выключении модуля возвращает как было. Бег — модуль Auto Sprint.
 */
public final class ToggleSneak extends Module {
    private Boolean before;

    public ToggleSneak() {
        super("toggle_sneak", "Toggle Sneak", "Присед переключается нажатием, а не удержанием.", Category.PLAYER);
    }

    @Override
    protected void onEnable() {
        var o = Minecraft.getInstance().options.toggleCrouch();
        before = o.get();
        o.set(true);
    }

    @Override
    protected void onDisable() {
        if (before != null) Minecraft.getInstance().options.toggleCrouch().set(before);
        before = null;
    }
}

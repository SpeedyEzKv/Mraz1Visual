package dev.mraz1visual.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * Тонкий слой совместимости 1.21.11 ↔ 26.x: то, что нельзя выразить переименованием (VERSIONS.md).
 * Блоки {@code //? if} разворачивает Stonecutter при сборке каждой версии; в git файл лежит в состоянии 1.21.11.
 */
public final class Mc {
    private Mc() {}

    /** Полная яркость в упакованных координатах света (блок 15, небо 15). */
    public static final int FULL_BRIGHT = 0xF000F0;

    /** Сообщение игроку: в чат или над хотбаром. Только локально — на сервер ничего не уходит. */
    public static void message(Player p, Component text, boolean overlay) {
        if (p == null) return;
        //? if >=26.1 {
        /*if (overlay) p.sendOverlayMessage(text);
        else p.sendSystemMessage(text);
        *///?} else {
        p.displayClientMessage(text, overlay);
        //?}
    }

    public static void message(Component text, boolean overlay) {
        message(Minecraft.getInstance().player, text, overlay);
    }

    /** Применить новый масштаб интерфейса. */
    public static void resizeGui(Minecraft mc) {
        //? if >=26.1 {
        /*mc.resizeGui();
        *///?} else {
        mc.resizeDisplay();
        //?}
    }
}

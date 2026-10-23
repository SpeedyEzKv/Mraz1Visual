package dev.mraz1visual.ui.menu;

import dev.mraz1visual.core.Modules;
import dev.mraz1visual.core.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.PauseScreen;
//? if <26.2
import net.minecraft.client.gui.screens.ShareToLanScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.achievement.StatsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;

/** Решает, какой экран показать вместо ванильного. Любая ошибка — показываем ванильный. */
public final class MenuRouter {
    private MenuRouter() {}

    /** Ванильный выбор миров нужен как запасной путь — тогда не перехватываем. */
    static boolean bypassWorlds;

    public static Screen route(Minecraft mc, Screen screen) {
        if (Modules.client == null) return screen;
        try {
            // setScreen(null) без мира ваниль сама превращает в TitleScreen — перехватываем заранее
            if (screen == null) {
                return mc.level == null && Modules.client.customTitle.get() ? new Mraz1VisualTitleScreen() : null;
            }
            if (screen instanceof TitleScreen && Modules.client.customTitle.get()) {
                return new Mraz1VisualTitleScreen();
            }
            if (screen instanceof OptionsScreen) {
                if (Mraz1VisualOptionsScreen.bypass) {
                    Mraz1VisualOptionsScreen.vanillaInstance = new java.lang.ref.WeakReference<>(screen);
                    return screen;
                }
                if (screen == Mraz1VisualOptionsScreen.vanillaInstance.get() || !Modules.client.customOptions.get()) return screen;
                return new Mraz1VisualOptionsScreen(mc.screen);
            }
            if (screen instanceof net.minecraft.client.gui.screens.worldselection.CreateWorldScreen cws
                    && Modules.client.customWorlds.get() && !Mraz1VisualCreateWorldScreen.bypass) {
                return new Mraz1VisualCreateWorldScreen(cws);
            }
            if (screen instanceof SelectWorldScreen && Modules.client.customWorlds.get() && !bypassWorlds) {
                Screen parent = mc.screen instanceof Mraz1VisualTitleScreen ? mc.screen : new Mraz1VisualTitleScreen();
                return new Mraz1VisualWorldsScreen(parent);
            }
            if (Modules.client.customPause.get()) {
                if (screen instanceof PauseScreen p && p.showsPauseMenu() && !Mraz1VisualPauseScreen.bypass) return new Mraz1VisualPauseScreen();
                if (screen instanceof StatsScreen) return new Mraz1VisualStatsScreen(mc.screen);
                //? if <26.2
                if (screen instanceof ShareToLanScreen) return new Mraz1VisualLanScreen(mc.screen);
            }
            if (screen instanceof JoinMultiplayerScreen && Modules.client.customServers.get()) {
                Screen parent = mc.screen instanceof Mraz1VisualTitleScreen ? mc.screen : new Mraz1VisualTitleScreen();
                return new Mraz1VisualServersScreen(parent);
            }
        } catch (Throwable t) {
            ModuleManager.LOG.error("Custom menu failed, falling back to vanilla", t);
        }
        return screen;
    }
}

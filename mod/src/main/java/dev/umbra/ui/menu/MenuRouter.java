package dev.umbra.ui.menu;

import dev.umbra.core.Modules;
import dev.umbra.core.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.PauseScreen;
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
                return mc.level == null && Modules.client.customTitle.get() ? new UmbraTitleScreen() : null;
            }
            if (screen instanceof TitleScreen && Modules.client.customTitle.get()) {
                return new UmbraTitleScreen();
            }
            if (screen instanceof OptionsScreen) {
                if (UmbraOptionsScreen.bypass) {
                    UmbraOptionsScreen.vanillaInstance = new java.lang.ref.WeakReference<>(screen);
                    return screen;
                }
                if (screen == UmbraOptionsScreen.vanillaInstance.get() || !Modules.client.customOptions.get()) return screen;
                return new UmbraOptionsScreen(mc.screen);
            }
            if (screen instanceof SelectWorldScreen && Modules.client.customWorlds.get() && !bypassWorlds) {
                Screen parent = mc.screen instanceof UmbraTitleScreen ? mc.screen : new UmbraTitleScreen();
                return new UmbraWorldsScreen(parent);
            }
            if (Modules.client.customPause.get()) {
                if (screen instanceof PauseScreen p && p.showsPauseMenu() && !UmbraPauseScreen.bypass) return new UmbraPauseScreen();
                if (screen instanceof StatsScreen) return new UmbraStatsScreen(mc.screen);
                if (screen instanceof ShareToLanScreen) return new UmbraLanScreen(mc.screen);
            }
            if (screen instanceof JoinMultiplayerScreen && Modules.client.customServers.get()) {
                Screen parent = mc.screen instanceof UmbraTitleScreen ? mc.screen : new UmbraTitleScreen();
                return new UmbraServersScreen(parent);
            }
        } catch (Throwable t) {
            ModuleManager.LOG.error("Custom menu failed, falling back to vanilla", t);
        }
        return screen;
    }
}

package dev.mraz1visual.browser;

import dev.mraz1visual.overlay.Screenshots;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Открыть встроенный браузер, если MCEF Modern установлен, иначе — системный. */
public final class Browser {
    private Browser() {}

    public static boolean available() {
        return FabricLoader.getInstance().isModLoaded("mcef-modern");
    }

    public static void open(Screen parent) {
        if (available()) {
            Minecraft.getInstance().setScreen(new BrowserScreen(parent));
        } else {
            Screenshots.openExternal("https://www.google.com");
        }
    }
}

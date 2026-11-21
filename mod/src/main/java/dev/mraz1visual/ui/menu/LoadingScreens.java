package dev.mraz1visual.ui.menu;

import dev.mraz1visual.core.Modules;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.Screen;

/** Only the background is replaced. Vanilla draws its live status, progress and controls. */
public final class LoadingScreens {
    private LoadingScreens() {}

    public static boolean themed(Screen screen) {
        return Modules.client != null && Modules.client.customTitle.get()
                && (screen instanceof LevelLoadingScreen || screen instanceof GenericMessageScreen
                || screen instanceof ProgressScreen || screen instanceof ConnectScreen);
    }
}

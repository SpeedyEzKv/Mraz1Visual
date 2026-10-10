package dev.mraz1visual.ui.menu;

import dev.mraz1visual.core.Modules;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.Screen;

/**
 * Экраны ожидания (загрузка мира, сохранение, подключение к серверу) остаются ванильными по логике,
 * но поверх них рисуется наш экран загрузки — ванильной картинки не видно.
 */
public final class LoadingScreens {
    private LoadingScreens() {}

    private static boolean isLoading(Screen s) {
        return s instanceof LevelLoadingScreen || s instanceof GenericMessageScreen || s instanceof ProgressScreen || s instanceof ConnectScreen;
    }

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!isLoading(screen)) return;
            long opened = System.nanoTime();
            ScreenEvents.afterRender(screen).register((s, g, mx, my, delta) -> {
                if (Modules.client == null || !Modules.client.customTitle.get()) return;
                float alpha = Math.min(1f, (System.nanoTime() - opened) / 2.5e8f + 0.6f);
                g.nextStratum();
                LoadingView.render(g, -1f, alpha);
                String title = s.getTitle().getString();
                if (s instanceof ConnectScreen) title = "Подключение к серверу…   Esc — отмена";
                else if (title.isBlank()) title = "Загрузка мира…";
                LoadingView.caption(g, title, alpha);
            });
        });
    }
}

package dev.umbra;

import dev.umbra.config.ConfigManager;
import dev.umbra.core.ModuleManager;
import dev.umbra.core.Modules;
import dev.umbra.account.AccountStore;
import dev.umbra.input.Keys;
import dev.umbra.overlay.OverlayScreen;
import dev.umbra.overlay.PlayStats;
import org.lwjgl.glfw.GLFW;
import dev.umbra.module.ChatHelpers;
import dev.umbra.module.Crosshair;
import dev.umbra.module.NoRender;
import dev.umbra.module.ShulkerPreview;
import dev.umbra.render.CombatHooks;
import dev.umbra.render.HudDraw;
import dev.umbra.render.WorldDraw;
import dev.umbra.ui.UmbraScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

public final class Umbra implements ClientModInitializer {
    public static final ModuleManager MODULES = new ModuleManager();
    public static ConfigManager CONFIG;

    private static UmbraScreen screen;
    private static boolean menuKeyWasDown, overlayWasDown;

    @Override
    public void onInitializeClient() {
        MODULES.init();
        CONFIG = new ConfigManager(MODULES);
        CONFIG.load();
        Modules.client.applyAccent();
        MODULES.afterConfigLoad();

        ClientTickEvents.END_CLIENT_TICK.register(Umbra::tick);
        HudDraw.register();
        WorldDraw.register();
        CombatHooks.register();
        ShulkerPreview.register();
        Crosshair.register();
        NoRender.register();
        ChatHelpers.register();
        dev.umbra.music.MediaClient.start();
        dev.umbra.render.FriendGuard.register();
        PlayStats.load();
        AccountStore.load();
        ClientLifecycleEvents.CLIENT_STARTED.register(mc -> AccountStore.autoLogin());
        ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> {
            CONFIG.saveIfDirty();
            PlayStats.save();
        });
        ModuleManager.LOG.info("Umbra ready: {} modules", MODULES.all().size());
    }

    private static void tick(Minecraft mc) {
        int key = Modules.client.menuKey.get();
        boolean down = Keys.isDown(key);
        if (down && !menuKeyWasDown && mc.screen == null) openMenu(mc);
        menuKeyWasDown = down;

        // оверлей: Shift + клавиша (по умолчанию Tab)
        boolean shift = Keys.isDown(GLFW.GLFW_KEY_LEFT_SHIFT) || Keys.isDown(GLFW.GLFW_KEY_RIGHT_SHIFT);
        boolean ov = shift && Keys.isDown(Modules.client.overlayKey.get());
        if (ov && !overlayWasDown && mc.screen == null && mc.level != null) mc.setScreen(new OverlayScreen());
        overlayWasDown = ov;

        PlayStats.tick(mc);

        MODULES.tick(mc);
    }

    public static void openMenu(Minecraft mc) {
        if (screen == null) screen = new UmbraScreen();
        screen.setParent(mc.screen);
        mc.setScreen(screen);
    }
}

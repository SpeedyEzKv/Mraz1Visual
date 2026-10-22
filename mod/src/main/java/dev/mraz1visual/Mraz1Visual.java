package dev.mraz1visual;

import dev.mraz1visual.config.ConfigManager;
import dev.mraz1visual.core.ModuleManager;
import dev.mraz1visual.core.Modules;
import dev.mraz1visual.account.AccountStore;
import dev.mraz1visual.input.Keys;
import dev.mraz1visual.overlay.OverlayScreen;
import dev.mraz1visual.overlay.PlayStats;
import org.lwjgl.glfw.GLFW;
import dev.mraz1visual.module.AutoAccept;
import dev.mraz1visual.module.AutoCommand;
import dev.mraz1visual.module.Crosshair;
import dev.mraz1visual.module.NoRender;
import dev.mraz1visual.module.ShulkerPreview;
import dev.mraz1visual.render.CombatHooks;
import dev.mraz1visual.render.HudDraw;
import dev.mraz1visual.render.WorldDraw;
import dev.mraz1visual.ui.Mraz1VisualScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

public final class Mraz1Visual implements ClientModInitializer {
    public static final ModuleManager MODULES = new ModuleManager();
    public static ConfigManager CONFIG;

    private static Mraz1VisualScreen screen;
    private static dev.mraz1visual.ui.ClientScreen screenV2;
    private static boolean menuKeyWasDown, overlayWasDown;

    @Override
    public void onInitializeClient() {
        migrateOldConfig();
        MODULES.init();
        CONFIG = new ConfigManager(MODULES);
        CONFIG.load();
        Modules.client.applyAccent();
        MODULES.afterConfigLoad();

        ClientTickEvents.END_CLIENT_TICK.register(Mraz1Visual::tick);
        HudDraw.register();
        WorldDraw.register();
        dev.mraz1visual.render.PlayerLayer.register();
        CombatHooks.register();
        ShulkerPreview.register();
        Crosshair.register();
        NoRender.register();
        dev.mraz1visual.module.CustomScoreboard.register(); // строго после NoRender
        dev.mraz1visual.module.AutoReconnect.register();
        dev.mraz1visual.module.ItemScroller.register();
        dev.mraz1visual.module.CustomSky.register();
        dev.mraz1visual.module.UiAnimations.register();
        AutoAccept.register();
        AutoCommand.register();
        dev.mraz1visual.module.AuctionHelper.register();
        dev.mraz1visual.module.ItemHighlighter.register();
        dev.mraz1visual.ui.menu.LoadingScreens.register();
        dev.mraz1visual.module.Hotbar.register();
        dev.mraz1visual.music.MediaClient.start();
        dev.mraz1visual.render.FriendGuard.register();
        dev.mraz1visual.dev.Harness.init();
        // импортированные 3D-модели: выбор и позы — из своего файла, сами паки читаются в фоне после старта
        dev.mraz1visual.models.ModelPrefs.load();
        dev.mraz1visual.ui.ModelPreview.register();
        dev.mraz1visual.models.ModelRegistry.onLoaded = () -> {
            for (var e : dev.mraz1visual.models.ModelRegistry.all()) dev.mraz1visual.models.ModelPrefs.applyPose(e);
            dev.mraz1visual.module.WeaponSkins.applyMaterial();
        };
        PlayStats.load();
        AccountStore.load();
        ClientLifecycleEvents.CLIENT_STARTED.register(mc -> {
            AccountStore.autoLogin();
            WindowBranding.applyIcon();
            mc.updateTitle();
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> {
            CONFIG.saveIfDirty();
            PlayStats.save();
        });
        ModuleManager.LOG.info("Mraz1Visual ready: {} modules", MODULES.all().size());
    }

    private static int modelTick;

    private static boolean on(String id) {
        var m = MODULES.find(id);
        return m != null && m.isEnabled();
    }

    private static Object lastLevel;

    private static void tick(Minecraft mc) {
        // смена мира / измерения — частицы старого мира не «висят» на чужих координатах
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            dev.mraz1visual.render.FxParticles.clear();
        }
        dev.mraz1visual.models.ModelRegistry.tick(modelTick++);
        if (modelTick % 20 == 1 && (on("weapon_skins") || on("model_pets") || on("model_cosmetics"))) dev.mraz1visual.models.ModelRegistry.ensureLoaded();
        dev.mraz1visual.wallpaper.WallpaperPlayer.tick();
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
        if (!Modules.client.menuStyle.getId().equals("classic")) {
            if (screenV2 == null) screenV2 = new dev.mraz1visual.ui.ClientScreen();
            screenV2.setParent(mc.screen);
            mc.setScreen(screenV2);
            return;
        }
        if (screen == null) screen = new Mraz1VisualScreen();
        screen.setParent(mc.screen);
        mc.setScreen(screen);
    }

    /** Проект раньше назывался Umbra: переносим config/umbra → config/mraz1visual один раз. */
    private static void migrateOldConfig() {
        try {
            java.nio.file.Path dir = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir();
            java.nio.file.Path old = dir.resolve("umb" + "ra"), now = dir.resolve("mraz1visual");
            // главный файл настроек лежит рядом с папкой: config/umbra.json → config/mraz1visual.json
            java.nio.file.Path oldMain = dir.resolve("umb" + "ra.json"), newMain = dir.resolve("mraz1visual.json");
            if (java.nio.file.Files.exists(oldMain) && !java.nio.file.Files.exists(newMain)) java.nio.file.Files.copy(oldMain, newMain);
            if (!java.nio.file.Files.isDirectory(old) || java.nio.file.Files.exists(now)) return;
            try (var walk = java.nio.file.Files.walk(old)) {
                for (java.nio.file.Path src : (Iterable<java.nio.file.Path>) walk::iterator) {
                    java.nio.file.Path dst = now.resolve(old.relativize(src).toString());
                    if (java.nio.file.Files.isDirectory(src)) java.nio.file.Files.createDirectories(dst);
                    else java.nio.file.Files.copy(src, dst);
                }
            }
        } catch (Exception e) {
            ModuleManager.LOG.warn("Config migration failed", e);
        }
    }
}

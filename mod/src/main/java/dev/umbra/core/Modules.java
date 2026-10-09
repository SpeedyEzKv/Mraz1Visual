package dev.umbra.core;

import dev.umbra.module.ClientSettings;
import dev.umbra.module.Ambience;
import dev.umbra.module.Animations;
import dev.umbra.module.Crosshair;
import dev.umbra.module.Keystrokes;
import dev.umbra.module.NoRender;
import dev.umbra.module.TargetHud;
import dev.umbra.module.WorldEffects;
import dev.umbra.module.Zoom;
import dev.umbra.module.BlockOutline;
import dev.umbra.module.ChinaHat;
import dev.umbra.module.CritIndicator;
import dev.umbra.module.ItemCooldowns;
import dev.umbra.module.ShulkerPreview;
import dev.umbra.module.ViewModel;
import dev.umbra.module.DemoModule;
import dev.umbra.module.HitEffects;
import dev.umbra.module.HitSounds;
import dev.umbra.module.Hud;
import dev.umbra.module.JumpCircle;
import dev.umbra.module.ChatHelpers;
import dev.umbra.module.Hitboxes;
import dev.umbra.module.InventoryKeys;
import dev.umbra.module.Music;
import dev.umbra.module.TargetEsp;
import dev.umbra.module.Trails;
import dev.umbra.module.Trajectories;
import dev.umbra.module.Waypoints;
import net.fabricmc.loader.api.FabricLoader;

/** Реестр. Новый визуал — одна строка здесь. */
public final class Modules {
    private Modules() {}

    public static ClientSettings client;

    static void registerAll(ModuleManager m) {
        client = m.register(new ClientSettings());

        // ── визуалы ──────────────────────────────────────────
        m.register(new Hud());
        m.register(new HitEffects());
        m.register(new HitSounds());
        m.register(new JumpCircle());
        m.register(new ChinaHat());
        m.register(new Trails());
        m.register(new BlockOutline());
        m.register(new ViewModel());
        m.register(new Animations());
        m.register(new ShulkerPreview());
        m.register(new ItemCooldowns());
        m.register(new CritIndicator());
        m.register(new Crosshair());
        m.register(new Zoom());
        m.register(new Ambience());
        m.register(new WorldEffects());
        m.register(new TargetHud());
        m.register(new Keystrokes());
        m.register(new NoRender());
        m.register(new Waypoints());
        m.register(new TargetEsp());
        m.register(new Hitboxes());
        m.register(new Trajectories());
        m.register(new InventoryKeys());
        m.register(new ChatHelpers());
        m.register(new Music());
        m.register(new dev.umbra.module.NoDelay());
        m.register(new dev.umbra.module.Cape());

        // Демо всех компонентов — только в dev-клиенте, в jar его нет в списке.
        if (FabricLoader.getInstance().isDevelopmentEnvironment()) m.register(new DemoModule());
    }
}

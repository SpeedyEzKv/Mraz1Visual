package dev.umbra.core;

import dev.umbra.module.*;
import net.fabricmc.loader.api.FabricLoader;

/** Реестр. Порядок регистрации = порядок в списке вкладки. Новый визуал — одна строка здесь. */
public final class Modules {
    private Modules() {}

    public static ClientSettings client;

    static void registerAll(ModuleManager m) {
        client = m.register(new ClientSettings());

        // ── Player ───────────────────────────────────────────
        m.register(new AutoSprint());
        m.register(new HotbarSwap());
        m.register(new OffhandTotem());
        m.register(new ClickPearl());
        m.register(new NoFriendDamage());
        m.register(new FakePlayer());
        m.register(new AutoRespawn());
        m.register(new NoDelay());

        // ── Visuals ──────────────────────────────────────────
        m.register(new Hud());
        m.register(new NameTags());
        m.register(new TargetEsp());
        m.register(new Esp());
        m.register(new Trajectories());
        m.register(new HitEffects());
        m.register(new HitSounds());
        m.register(new MotionBlur());
        m.register(new AspectRatio());
        m.register(new FullBright());
        m.register(new JumpCircle());
        m.register(new ChinaHat());
        m.register(new Trails());
        m.register(new Hitboxes());
        m.register(new SkeletonEsp());
        m.register(new BlockOutline());
        m.register(new Animations());
        m.register(new SwingAnimations());
        m.register(new WorldMusic());
        m.register(new ViewModel());
        m.register(new FreeLook());
        m.register(new Cape());
        m.register(new Hands());
        m.register(new LocalSkin());
        m.register(new WorldEffects());
        m.register(new CustomPet());
        m.register(new BetterMinecraft());
        m.register(new ItemPhysic());
        m.register(new NoRender());
        m.register(new Ambience());
        m.register(new ShulkerPreview());
        m.register(new ItemCooldowns());
        m.register(new TargetHud());
        m.register(new Keystrokes());
        m.register(new Crosshair());
        m.register(new CritIndicator());
        m.register(new Zoom());

        // ── Misc ─────────────────────────────────────────────
        m.register(new AuctionHelper());
        m.register(new Optimization());
        m.register(new DeathCoords());
        m.register(new Waypoints());
        m.register(new Emotes());
        m.register(new FriendsModule());
        m.register(new KillMessage());
        m.register(new AutoAccept());
        m.register(new AutoCommand());
        m.register(new StreamerMode());

        // ── Music ────────────────────────────────────────────
        m.register(new Music());

        // Демо всех компонентов — только в dev-клиенте.
        if (FabricLoader.getInstance().isDevelopmentEnvironment()) m.register(new DemoModule());
    }
}

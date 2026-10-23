package dev.mraz1visual.core;

import dev.mraz1visual.module.*;
import net.fabricmc.loader.api.FabricLoader;

/** Реестр. Порядок регистрации = порядок в списке вкладки. Новый визуал — одна строка здесь. */
public final class Modules {
    private Modules() {}

    public static ClientSettings client;

    static void registerAll(ModuleManager m) {
        client = m.register(new ClientSettings());

        // ── Игрок ────────────────────────────────────────────
        m.register(new AutoSprint());
        m.register(new HotbarSwap());
        m.register(new NoFriendDamage());
        m.register(new FakePlayer());
        m.register(new AutoRespawn());
        m.register(new NoDelay());
        m.register(new LockSlot());
        m.register(new AutoEat());
        m.register(new AutoPotion());
        m.register(new AutoLeave());
        m.register(new ElytraSwap());

        // ── Visuals ──────────────────────────────────────────
        m.register(new Hud());
        m.register(new NameTags());
        m.register(new TargetEsp());
        m.register(new HitEffects());
        m.register(new HitSounds());
        m.register(new MotionBlur());
        m.register(new PostFxModule());
        m.register(new AspectRatio());
        m.register(new FullBright());
        m.register(new JumpCircle());
        m.register(new ChinaHat());
        m.register(new Trails());
        m.register(new Hitboxes());
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
        m.register(new SpiritPet());
        m.register(new Emblem());
        m.register(new BetterMinecraft());
        m.register(new ItemPhysic());
        m.register(new NoRender());
        m.register(new Ambience());
        m.register(new ShulkerPreview());
        m.register(new ItemCooldowns());
        m.register(new TargetHud());
        m.register(new Keystrokes());
        m.register(new CustomScoreboard());
        m.register(new CustomSky());
        m.register(new Crosshair());
        m.register(new CritIndicator());
        m.register(new Zoom());
        m.register(new DeathAnimation());
        m.register(new KillEffect());
        m.register(new HitBubbles());
        m.register(new DamageNumbers());
        m.register(new TotemPops());
        m.register(new Halo());
        m.register(new PlayerAura());
        m.register(new LightSteps());
        m.register(new Sonar());
        m.register(new TntTimer());
        m.register(new ArmorHud());
        m.register(new PotionsHud());
        m.register(new InventoryHud());
        m.register(new ItemHighlighter());
        m.register(new DashTrail());
        m.register(new JumpParticles());
        m.register(new HitColor());
        m.register(new CustomFog());
        m.register(new ShieldIndicator());
        m.register(new Hotbar());
        m.register(new Notifications());
        m.register(new UiAnimations());
        m.register(new Wings());
        m.register(new Wings3D());
        m.register(new FireHand());
        m.register(new CustomSwords());
        m.register(new Pets());
        m.register(new dev.mraz1visual.module.VisualFx());
        m.register(new dev.mraz1visual.module.TotemFx());
        m.register(new dev.mraz1visual.module.SpeedLines());
        m.register(new dev.mraz1visual.module.HurtVignette());
        m.register(new dev.mraz1visual.module.FrostBreath());
        m.register(new dev.mraz1visual.module.Cinematic());
        m.register(new dev.mraz1visual.module.EnchantGlint());
        m.register(new dev.mraz1visual.module.MusicPlayerModule());
        m.register(new dev.mraz1visual.module.LyricsHud());
        m.register(new dev.mraz1visual.module.SlotPulse());
        m.register(new dev.mraz1visual.module.LandingDust());
        m.register(new dev.mraz1visual.module.PickupSparkle());
        m.register(new dev.mraz1visual.module.ModelPets());
        m.register(new dev.mraz1visual.module.WeaponSkins());
        m.register(new dev.mraz1visual.module.ModelCosmetics());
        m.register(new Accessories());
        m.register(new AmbientFx());
        m.register(new HitMarker());
        m.register(new ChunkBorders());
        m.register(new Watermark());
        m.register(new KeybindsHud());
        m.register(new TotemCounter());
        m.register(new ThirdPersonCam());
        m.register(new AttackEffects());
        m.register(new ItemGlow());

        // ── Misc ─────────────────────────────────────────────
        m.register(new AuctionHelper());
        m.register(new FramePacing());
        m.register(new FrameStats());
        m.register(new Optimization());
        m.register(new DeathCoords());
        m.register(new Waypoints());
        m.register(new Emotes());
        m.register(new FriendsModule());
        m.register(new KillMessage());
        m.register(new AutoAccept());
        m.register(new AutoReconnect());
        m.register(new ItemScroller());
        m.register(new Wheels.Staff());
        m.register(new Wheels.Phrase());
        m.register(new Desktop());
        m.register(new AutoCommand());
        m.register(new StreamerMode());
        m.register(new ChatTweaks());
        m.register(new NoSounds());
        m.register(new UiSounds());
        m.register(new DiscordRpc());

        // ── Music ────────────────────────────────────────────
        m.register(new Music());

        // Демо всех компонентов — только в dev-клиенте.
        if (FabricLoader.getInstance().isDevelopmentEnvironment()) m.register(new DemoModule());
    }
}

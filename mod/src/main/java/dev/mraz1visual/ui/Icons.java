package dev.mraz1visual.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.util.Map;

/** Атлас линейных иконок 8×8 по 64 px (assets/mraz1visual/textures/gui/icons.png). */
public final class Icons {
    private Icons() {}

    public static final Identifier ATLAS = Identifier.fromNamespaceAndPath("mraz1visual", "textures/gui/icons.png");

    public static final int PLAYER = 0, EYE = 1, GLOBE = 2, HUD = 3, GRID = 4, SPARKLE = 5, NOTE = 6, SLIDERS = 7,
            GEAR = 8, FRIENDS = 9, BELL = 10, SEARCH = 11, SHIELD = 12, SWORD = 13, TARGET = 14, KEYBOARD = 15,
            STAR = 16, SPRINT = 17, SWAP = 18, PEARL = 19, HEART = 20, CUBE = 21, WAVE = 22, HAT = 23,
            EYE_OFF = 24, ZOOM = 25, CLOCK = 26, PIN = 27, ARC = 28, CHAT = 29, CAPE = 30, BOLT = 31,
            TAG = 32, ESPBOX = 33, BLUR = 34, RATIO = 35, SUN = 36, SKELETON = 37, SWING = 38, CHUNK = 39,
            HAND = 40, SKIN = 41, PAW = 42, GEM = 43, DROP = 44, CLOUD = 45, SHULKER = 46, FAKE = 47,
            RESPAWN = 48, COIN = 49, GAUGE = 50, SKULL = 51, MIC = 52, SMILE = 53, CHECK = 54, TERMINAL = 55,
            CAMOFF = 56, TOTEM = 57, REPLAY = 58, KILL = 59, GHOST = 60, EAR = 61, LAYERS = 62, ROCKET = 63;

    private static final Map<String, Integer> MODULES = Map.ofEntries(
            Map.entry("hud", HUD),
            Map.entry("name_tags", TAG),
            Map.entry("target_esp", TARGET),
            Map.entry("esp", ESPBOX),
            Map.entry("trajectories", ARC),
            Map.entry("hit_effects", STAR),
            Map.entry("hit_sounds", NOTE),
            Map.entry("motion_blur", BLUR),
            Map.entry("aspect_ratio", RATIO),
            Map.entry("full_bright", SUN),
            Map.entry("jump_circle", PEARL),
            Map.entry("china_hat", HAT),
            Map.entry("trails", WAVE),
            Map.entry("hitboxes", CUBE),
            Map.entry("skeleton_esp", SKELETON),
            Map.entry("block_outline", CUBE),
            Map.entry("animations", BOLT),
            Map.entry("swing_animations", SWING),
            Map.entry("world_music", NOTE),
            Map.entry("view_model", SWORD),
            Map.entry("free_look", EYE),
            Map.entry("cape", CAPE),
            Map.entry("hands", HAND),
            Map.entry("local_skin", SKIN),
            Map.entry("world_effects", SPARKLE),
            Map.entry("custom_pet", PAW),
            Map.entry("better_minecraft", GEM),
            Map.entry("item_physic", DROP),
            Map.entry("no_render", EYE_OFF),
            Map.entry("ambience", CLOUD),
            Map.entry("shulker_preview", SHULKER),
            Map.entry("item_cooldowns", CLOCK),
            Map.entry("target_hud", HEART),
            Map.entry("keystrokes", KEYBOARD),
            Map.entry("crosshair", TARGET),
            Map.entry("crit_indicator", STAR),
            Map.entry("zoom", ZOOM),
            Map.entry("auto_sprint", SPRINT),
            Map.entry("hotbar_swap", SWAP),
            Map.entry("offhand_totem", TOTEM),
            Map.entry("click_pearl", PEARL),
            Map.entry("no_friend_damage", SHIELD),
            Map.entry("fake_player", FAKE),
            Map.entry("auto_respawn", RESPAWN),
            Map.entry("no_delay", ROCKET),
            Map.entry("auction_helper", COIN),
            Map.entry("optimization", GAUGE),
            Map.entry("death_coords", SKULL),
            Map.entry("waypoints", PIN),
            Map.entry("emotes", SMILE),
            Map.entry("friends", FRIENDS),
            Map.entry("kill_message", KILL),
            Map.entry("auto_accept", CHECK),
            Map.entry("auto_command", TERMINAL),
            Map.entry("streamer_mode", CAMOFF),
            Map.entry("music", NOTE),
            Map.entry("client", GEAR),
            Map.entry("kill_effect", KILL), Map.entry("hit_bubbles", PEARL), Map.entry("damage_numbers", HEART), Map.entry("totem_pops", TOTEM),
            Map.entry("halo", SUN), Map.entry("sonar", EAR), Map.entry("tnt_timer", CLOCK), Map.entry("armor_hud", SHIELD),
            Map.entry("potions_hud", SPARKLE), Map.entry("inventory_hud", GRID), Map.entry("item_highlighter", STAR),
            Map.entry("dash_trail", SPRINT), Map.entry("jump_particles", SPARKLE), Map.entry("hit_color", HEART), Map.entry("custom_fog", CLOUD),
            Map.entry("shield_indicator", SHIELD), Map.entry("hotbar", LAYERS), Map.entry("notifications", BELL), Map.entry("lock_slot", KEYBOARD), Map.entry("death_animation", GHOST),
            Map.entry("wings", CAPE), Map.entry("accessories", HAT), Map.entry("ambient_fx", SPARKLE), Map.entry("hit_marker", TARGET),
            Map.entry("chunk_borders", CHUNK), Map.entry("watermark", TAG), Map.entry("keybinds_hud", KEYBOARD), Map.entry("totem_counter", TOTEM),
            Map.entry("third_person", EYE),
            Map.entry("attack_effects", BOLT), Map.entry("item_glow", DROP), Map.entry("chat_tweaks", CHAT), Map.entry("no_sounds", EAR), Map.entry("ui_sounds", NOTE), Map.entry("discord_rpc", CHAT));

    public static int forModule(String id, int fallback) {
        return MODULES.getOrDefault(id, fallback);
    }

    public static int forCategory(dev.mraz1visual.core.Category c) {
        return switch (c) {
            case PLAYER -> PLAYER;
            case VISUALS -> EYE;
            case MISC -> GRID;
            case MUSIC -> NOTE;
            case CONFIGS -> SLIDERS;
            case CLIENT -> GEAR;
        };
    }

    private static final int[] CELLS = {16, 24, 32, 64};
    private static final Identifier[] ATLASES = {
            Identifier.fromNamespaceAndPath("mraz1visual", "textures/gui/icons_16.png"),
            Identifier.fromNamespaceAndPath("mraz1visual", "textures/gui/icons_24.png"),
            Identifier.fromNamespaceAndPath("mraz1visual", "textures/gui/icons_32.png"),
            ATLAS};

    /**
     * Иконка. Берём атлас, где клетка ближе всего к размеру на экране (в физических пикселях)
     * и не меньше его, — уменьшение небольшое и сглаженное, без «пикселей».
     */
    public static void draw(GuiGraphics g, int index, int x, int y, int size, int color) {
        float ps = 1f;
        try {
            ps = (float) (Math.hypot(g.pose().m00(), g.pose().m01())
                    * net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScale());
        } catch (Throwable ignored) {
        }
        float phys = size * ps;
        int a = CELLS.length - 1;
        for (int i = 0; i < CELLS.length; i++) {
            if (CELLS[i] >= phys * 0.95f) {
                a = i;
                break;
            }
        }
        int cell = CELLS[a];
        int u = (index % 8) * cell, v = (index / 8) * cell;
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(size / (float) cell, size / (float) cell);
        g.blit(RenderPipelines.GUI_TEXTURED, ATLASES[a], 0, 0, (float) u, (float) v, cell, cell, cell, cell, cell * 8, cell * 8, color);
        g.pose().popMatrix();
    }
}

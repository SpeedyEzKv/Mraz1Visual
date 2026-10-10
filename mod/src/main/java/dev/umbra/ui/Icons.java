package dev.umbra.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.util.Map;

/** Атлас линейных иконок 8×4 по 64 px (assets/umbra/textures/gui/icons.png). */
public final class Icons {
    private Icons() {}

    public static final Identifier ATLAS = Identifier.fromNamespaceAndPath("umbra", "textures/gui/icons.png");

    public static final int PLAYER = 0, EYE = 1, GLOBE = 2, HUD = 3, GRID = 4, SPARKLE = 5, NOTE = 6, SLIDERS = 7,
            GEAR = 8, FRIENDS = 9, BELL = 10, SEARCH = 11, SHIELD = 12, SWORD = 13, TARGET = 14, KEYBOARD = 15,
            STAR = 16, SPRINT = 17, SWAP = 18, PEARL = 19, HEART = 20, CUBE = 21, WAVE = 22, HAT = 23,
            EYE_OFF = 24, ZOOM = 25, CLOCK = 26, PIN = 27, ARC = 28, CHAT = 29, CAPE = 30, BOLT = 31;

    private static final Map<String, Integer> MODULES = Map.ofEntries(
            Map.entry("hud", HUD), Map.entry("hit_effects", STAR), Map.entry("hit_sounds", NOTE),
            Map.entry("jump_circle", PEARL), Map.entry("china_hat", HAT), Map.entry("trails", WAVE),
            Map.entry("block_outline", CUBE), Map.entry("view_model", SWORD), Map.entry("animations", BOLT),
            Map.entry("shulker_preview", CUBE), Map.entry("item_cooldowns", CLOCK), Map.entry("crit_indicator", STAR),
            Map.entry("crosshair", TARGET), Map.entry("zoom", ZOOM), Map.entry("ambience", GLOBE),
            Map.entry("world_effects", SPARKLE), Map.entry("target_hud", HEART), Map.entry("keystrokes", KEYBOARD),
            Map.entry("no_render", EYE_OFF), Map.entry("waypoints", PIN), Map.entry("target_esp", TARGET),
            Map.entry("hitboxes", CUBE), Map.entry("trajectories", ARC), Map.entry("inventory_keys", SWAP),
            Map.entry("chat_helpers", CHAT), Map.entry("music", NOTE), Map.entry("cape", CAPE),
            Map.entry("no_delay", BOLT), Map.entry("client", GEAR));

    public static int forModule(String id, int fallback) {
        return MODULES.getOrDefault(id, fallback);
    }

    public static int forCategory(dev.umbra.core.Category c) {
        return switch (c) {
            case PLAYER -> PLAYER;
            case VISUALS -> EYE;
            case WORLD -> GLOBE;
            case HUD -> HUD;
            case MISC -> GRID;
            case COSMETICS -> SPARKLE;
            case MUSIC -> NOTE;
            case CONFIGS -> SLIDERS;
            case CLIENT -> GEAR;
        };
    }

    public static void draw(GuiGraphics g, int index, int x, int y, int size, int color) {
        int u = (index % 8) * 64, v = (index / 8) * 64;
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(size / 64f, size / 64f);
        g.blit(RenderPipelines.GUI_TEXTURED, ATLAS, 0, 0, (float) u, (float) v, 64, 64, 64, 64, 512, 256, color);
        g.pose().popMatrix();
    }
}

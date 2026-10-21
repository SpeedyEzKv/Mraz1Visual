package dev.mraz1visual.module;

import com.google.gson.JsonPrimitive;
import dev.mraz1visual.Mraz1Visual;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.setting.Setting;

import java.util.Map;
import java.util.Set;

/**
 * Секции вкладки «Визуалы» и пресеты одним кликом. Секция — только для раскладки меню (Бой / Игрок / Мир / HUD);
 * пресет включает набор функций и выставляет им стиль и общий цвет. Остальные визуалы пресет выключает,
 * косметику (крылья, питомцы, шляпы) не трогает.
 */
public final class VisualPresets {
    private VisualPresets() {}

    public static final String[][] SECTIONS = {{"all", "Все"}, {"combat", "Бой"}, {"player", "Игрок"}, {"world", "Мир"}, {"hud", "HUD"}};

    private static final Map<String, String> SECTION = Map.ofEntries(
            Map.entry("target_esp", "combat"), Map.entry("hit_effects", "combat"), Map.entry("attack_effects", "combat"),
            Map.entry("hit_bubbles", "combat"), Map.entry("hit_color", "combat"), Map.entry("kill_effect", "combat"),
            Map.entry("death_animation", "combat"), Map.entry("hit_marker", "combat"), Map.entry("crosshair", "combat"),
            Map.entry("crit_indicator", "combat"), Map.entry("damage_numbers", "combat"), Map.entry("hit_sounds", "combat"),
            Map.entry("totem_fx", "combat"), Map.entry("hurt_vignette", "combat"), Map.entry("esp", "combat"),
            Map.entry("skeleton_esp", "combat"), Map.entry("hitboxes", "combat"), Map.entry("totem_pops", "combat"),
            Map.entry("trajectories", "combat"),
            Map.entry("jump_circle", "player"), Map.entry("jump_particles", "player"), Map.entry("trails", "player"),
            Map.entry("dash_trail", "player"), Map.entry("speed_lines", "player"), Map.entry("frost_breath", "player"),
            Map.entry("free_look", "player"), Map.entry("third_person", "player"), Map.entry("zoom", "player"),
            Map.entry("motion_blur", "player"), Map.entry("aspect_ratio", "player"), Map.entry("sonar", "player"),
            Map.entry("cinematic", "player"), Map.entry("landing_dust", "player"), Map.entry("pickup_sparkle", "player"),
            Map.entry("block_outline", "world"), Map.entry("item_physic", "world"), Map.entry("enchant_glint", "world"),
            Map.entry("full_bright", "world"), Map.entry("chunk_borders", "world"), Map.entry("tnt_timer", "world"),
            Map.entry("name_tags", "world"), Map.entry("no_render", "world"), Map.entry("better_minecraft", "world"),
            Map.entry("world_music", "world"), Map.entry("item_highlighter", "world"), Map.entry("shulker_preview", "world"));

    /** Секция модуля вкладки «Визуалы»: всё, чего нет в списке и что рисует на экране, — HUD. */
    public static String section(Module m) {
        if (m.id.equals("visual_fx")) return "all";
        return SECTION.getOrDefault(m.id, "hud");
    }

    public static final String[][] PRESETS = {{"minimal", "Минимал"}, {"premium", "Премиум"}, {"neon", "Неон"}, {"cinema", "Кино"}};

    /** Визуалы, которыми управляют пресеты (остальное — по-прежнему на усмотрение игрока). */
    private static final Set<String> MANAGED = Set.of("target_esp", "hit_effects", "attack_effects", "hit_bubbles", "kill_effect",
            "hit_marker", "crosshair", "crit_indicator", "jump_circle", "jump_particles", "trails", "dash_trail", "speed_lines",
            "frost_breath", "block_outline", "totem_fx", "hurt_vignette", "target_hud", "notifications", "enchant_glint",
            "cinematic", "motion_blur", "slot_pulse", "landing_dust", "pickup_sparkle");

    public static void apply(String id) {
        // id → включённые модули и их настройки (setting=value)
        String[][] on = switch (id) {
            case "minimal" -> new String[][]{
                    {"crosshair"}, {"hit_marker"}, {"target_esp", "style=ring"}, {"block_outline", "style=edges"},
                    {"notifications", "style=island"}, {"target_hud"}};
            case "neon" -> new String[][]{
                    {"crosshair"}, {"hit_marker"}, {"target_esp", "style=orbit"}, {"hit_effects", "type=wave"},
                    {"kill_effect", "style=ascend"}, {"jump_circle", "style=waves"}, {"trails", "style=stardust"},
                    {"block_outline", "style=glass"}, {"totem_fx"}, {"target_hud"}, {"notifications", "style=island"},
                    {"speed_lines"}, {"enchant_glint"}, {"slot_pulse"}, {"pickup_sparkle"}};
            case "cinema" -> new String[][]{
                    {"crosshair"}, {"target_esp", "style=soul"}, {"hit_effects", "type=wave"}, {"kill_effect", "style=ascend"},
                    {"trails", "style=glass"}, {"block_outline", "style=edges"}, {"totem_fx"}, {"hurt_vignette"},
                    {"speed_lines"}, {"frost_breath"}, {"cinematic"}, {"notifications", "style=island"}};
            default -> new String[][]{ // premium
                    {"crosshair"}, {"hit_marker"}, {"target_esp", "style=ring"}, {"hit_effects", "type=wave"},
                    {"kill_effect", "style=ascend"}, {"jump_circle", "style=ring"}, {"trails", "style=light"},
                    {"block_outline", "style=glass"}, {"totem_fx"}, {"target_hud"}, {"notifications", "style=island"},
                    {"speed_lines"}, {"hurt_vignette"}, {"enchant_glint"}, {"slot_pulse"}, {"landing_dust"}, {"pickup_sparkle"}};
        };
        Set<String> enable = new java.util.HashSet<>();
        for (String[] e : on) enable.add(e[0]);
        for (String m : MANAGED) {
            Module mod = Mraz1Visual.MODULES.find(m);
            if (mod != null) mod.setEnabled(enable.contains(m));
        }
        for (String[] e : on) {
            Module mod = Mraz1Visual.MODULES.find(e[0]);
            if (mod == null) continue;
            for (int i = 1; i < e.length; i++) {
                String[] kv = e[i].split("=", 2);
                set(mod, kv[0], kv[1]);
            }
        }
        // общий цвет и яркость
        VisualFx fx = VisualFx.get();
        if (fx != null) {
            String preset = switch (id) {
                case "neon" -> "obsidian";
                case "cinema" -> "sunset";
                case "minimal" -> "accent";
                default -> "ether";
            };
            set(fx, "sync", "true");
            set(fx, "preset", preset);
            set(fx, "brightness", id.equals("neon") ? "115" : id.equals("minimal") ? "85" : "100");
            set(fx, "no_flash", id.equals("minimal") ? "true" : "false");
        }
    }

    private static void set(Module m, String id, String value) {
        for (Setting<?> s : m.settings()) {
            if (!s.id.equals(id)) continue;
            JsonPrimitive v;
            if (value.equals("true") || value.equals("false")) v = new JsonPrimitive(Boolean.parseBoolean(value));
            else {
                try {
                    v = new JsonPrimitive(Double.parseDouble(value));
                } catch (NumberFormatException e) {
                    v = new JsonPrimitive(value);
                }
            }
            if (s.fromJson(v)) Setting.globalChangeHook.run();
        }
    }
}

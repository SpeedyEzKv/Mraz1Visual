package dev.mraz1visual.cosmetics;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import dev.mraz1visual.Mraz1Visual;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.setting.Setting;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Гардероб: своя косметика клиента одним списком — крылья, плащи, головные уборы, ауры, питомцы.
 * Вещь = модуль + значения его настроек. «Надеть» — включить модуль с этими значениями; «примерить» — то же,
 * но с запоминанием прежнего состояния, чтобы вернуть его, когда мышь ушла с карточки (ничего не теряется).
 */
public final class Wardrobe {
    private Wardrobe() {}

    public record Item(String category, String name, String module, String values, int color) {}

    public static final String[] CATEGORIES = {"Крылья", "Плащи", "Головные уборы", "Ауры", "Питомцы"};

    public static final List<Item> ITEMS = List.of(
            // крылья
            item("Крылья", "Перьевые", "wings_3d", "style=angel|color_mode=preset", "#F2F0FF"),
            item("Крылья", "Драконьи", "wings_3d", "style=dragon|color_mode=preset", "#7A3B3B"),
            item("Крылья", "Стеклянные", "wings_3d", "style=energy|color_mode=preset", "#8FD8FF"),
            item("Крылья", "Механические", "wings_3d", "style=mech|color_mode=preset", "#9AA0AA"),
            item("Крылья", "Полярные", "wings_3d", "style=angel|color_mode=solid|color=#FFDDF4FF", "#DDF4FF"),
            item("Крылья", "Угольные", "wings_3d", "style=dragon|color_mode=solid|color=#FF2A2A30", "#2A2A30"),
            item("Крылья", "Неоновые", "wings_3d", "style=energy|color_mode=gradient|color=#FFFF5AD8|color2=#FF5AC8FA", "#FF5AD8"),
            item("Крылья", "Золотые", "wings_3d", "style=mech|color_mode=solid|color=#FFF2C14E", "#F2C14E"),
            // плащи
            item("Плащи", "Затмение", "cape", "style=eclipse", "#1E1B2E"),
            item("Плащи", "Градиент", "cape", "style=gradient", "#B9A6FF"),
            item("Плащи", "Графит", "cape", "style=plain|color=#FF2C2C34", "#2C2C34"),
            item("Плащи", "Аметист", "cape", "style=plain|color=#FF8C6CFF", "#8C6CFF"),
            item("Плащи", "Изумруд", "cape", "style=plain|color=#FF2FA37A", "#2FA37A"),
            item("Плащи", "Закат", "cape", "style=gradient|color=#FFFF9A6B", "#FF9A6B"),
            item("Плащи", "Алый", "cape", "style=plain|color=#FFD7263D", "#D7263D"),
            item("Плащи", "Лёд", "cape", "style=gradient|color=#FF9FE6FF", "#9FE6FF"),
            // головные уборы
            item("Головные уборы", "Нимб", "halo", "style=ring", "#FFE7A8"),
            item("Головные уборы", "Корона из света", "halo", "style=crown", "#FFD36B"),
            item("Головные уборы", "Орбита огоньков", "halo", "style=orbit", "#8FD8FF"),
            item("Головные уборы", "Аметистовый нимб", "halo", "style=ring|preset=amethyst", "#B98CFF"),
            item("Головные уборы", "Китайская шляпа", "china_hat", "", "#B8E6FF"),
            item("Головные уборы", "Шляпа «Закат»", "china_hat", "preset=sunset", "#FF9A6B"),
            // ауры
            item("Ауры", "Эфир", "player_aura", "preset=ether", "#8FD8FF"),
            item("Ауры", "Аметист", "player_aura", "preset=amethyst", "#B98CFF"),
            item("Ауры", "Изумруд", "player_aura", "preset=emerald", "#2FA37A"),
            item("Ауры", "Хром", "player_aura", "preset=chrome", "#D8DCE6"),
            // питомцы
            item("Питомцы", "Дракончик", "pets", "kind=dragon|color_mode=preset", "#5FD49A"),
            item("Питомцы", "Котик-призрак", "pets", "kind=cat|color_mode=preset", "#B8D8FF"),
            item("Питомцы", "Стеклянный лис", "pets", "kind=fox|color_mode=preset", "#9FE6FF"),
            item("Питомцы", "Совёнок", "pets", "kind=owl|color_mode=preset", "#F6D9A8"),
            item("Питомцы", "Дрон", "pets", "kind=drone|color_mode=preset", "#4FD8FF"),
            item("Питомцы", "Медуза", "pets", "kind=jelly|color_mode=preset", "#FF8AD8"),
            item("Питомцы", "Огненный дракончик", "pets", "kind=dragon|color_mode=gradient|color=#FFFF6A3D|color2=#FFFFD36B", "#FF6A3D"),
            item("Питомцы", "Радужная медуза", "pets", "kind=jelly|color_mode=rainbow", "#C08CFF"));

    private static Item item(String cat, String name, String module, String values, String hex) {
        return new Item(cat, name, module, values, 0xFF000000 | Integer.parseInt(hex.substring(1), 16));
    }

    public static int count(String category) {
        int n = 0;
        for (Item i : ITEMS) if (i.category.equals(category)) n++;
        return n;
    }

    /** Вещь надета: модуль включён и все её значения совпадают. */
    public static boolean worn(Item it) {
        Module m = Mraz1Visual.MODULES.find(it.module);
        if (m == null || !m.isEnabled()) return false;
        if (it.values.isEmpty()) return true;
        for (String kv : it.values.split("\\|")) {
            String[] p = kv.split("=", 2);
            Setting<?> s = setting(m, p[0]);
            if (s != null && !s.toJson().getAsString().equalsIgnoreCase(p[1])) return false;
        }
        return true;
    }

    /** Надеть (или снять, если уже надета). */
    public static void toggle(Item it) {
        endTryOn();
        Module m = Mraz1Visual.MODULES.find(it.module);
        if (m == null) return;
        if (worn(it)) {
            m.setEnabled(false);
            return;
        }
        apply(m, it);
        m.setEnabled(true);
    }

    // ── примерка при наведении ───────────────────────────────

    private static Item trying;
    private static Module tryModule;
    private static boolean tryWasOn;
    private static final Map<String, JsonElement> TRY_SAVED = new LinkedHashMap<>();

    /** Примерить: запомнить, как было, и показать вещь. null — снять примерку. */
    public static void tryOn(Item it) {
        if (it == trying) return;
        endTryOn();
        if (it == null) return;
        Module m = Mraz1Visual.MODULES.find(it.module);
        if (m == null) return;
        trying = it;
        tryModule = m;
        tryWasOn = m.isEnabled();
        TRY_SAVED.clear();
        for (Setting<?> s : m.settings()) TRY_SAVED.put(s.id, s.toJson());
        apply(m, it);
        if (!tryWasOn) m.setEnabled(true);
    }

    /** Вернуть всё как было до примерки. */
    public static void endTryOn() {
        if (trying == null) return;
        for (Setting<?> s : tryModule.settings()) {
            JsonElement v = TRY_SAVED.get(s.id);
            if (v != null) s.fromJson(v);
        }
        if (!tryWasOn) tryModule.setEnabled(false);
        trying = null;
        tryModule = null;
        TRY_SAVED.clear();
    }

    public static Item trying() {
        return trying;
    }

    private static void apply(Module m, Item it) {
        if (it.values.isEmpty()) return;
        for (String kv : it.values.split("\\|")) {
            String[] p = kv.split("=", 2);
            Setting<?> s = setting(m, p[0]);
            if (s != null) s.fromJson(new JsonPrimitive(p[1]));
        }
    }

    private static Setting<?> setting(Module m, String id) {
        for (Setting<?> s : m.settings()) if (s.id.equals(id)) return s;
        return null;
    }

    /** Цветовая группа для фильтра: 0 тёплые, 1 холодные, 2 светлые, 3 тёмные. */
    public static int tone(int argb) {
        float[] hsb = java.awt.Color.RGBtoHSB(argb >> 16 & 255, argb >> 8 & 255, argb & 255, null);
        if (hsb[2] < 0.3f) return 3;
        if (hsb[1] < 0.18f) return 2;
        float h = hsb[0] * 360;
        return h < 70 || h > 300 ? 0 : 1;
    }

    public static List<Item> filter(String category, int tone) {
        List<Item> out = new ArrayList<>();
        for (Item i : ITEMS) if ((category == null || i.category.equals(category)) && (tone < 0 || tone(i.color) == tone)) out.add(i);
        return out;
    }
}

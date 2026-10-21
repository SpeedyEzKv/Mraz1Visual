package dev.mraz1visual.models;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.mraz1visual.core.ModuleManager;
import dev.mraz1visual.util.TextFiles;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Выбор импортированных моделей: какой скин на какой тип оружия, избранное, ручные позы,
 * активный питомец, шляпа и крылья. Отдельный файл config/mraz1visual/models.json — id моделей
 * зависят от содержимого папок, в профили настроек их не тащим.
 */
public final class ModelPrefs {
    private static final Path FILE = TextFiles.DIR.resolve("models.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Слот привязки → id скина. */
    public static final Map<String, String> weapons = new LinkedHashMap<>();
    public static final Set<String> favorites = new LinkedHashSet<>();
    /** id скина → [группа FP/TP/ICON][rx, ry, rz, tx, ty, tz, масштаб]. */
    public static final Map<String, float[][]> poses = new HashMap<>();
    public static String pet = "", hat = "", wing = "";
    /** Меняется при любой правке позы — часть ключа кэша иконок предметов. */
    public static int poseVersion;

    private ModelPrefs() {}

    public static void load() {
        try {
            if (!Files.exists(FILE)) return;
            JsonObject o = Json.parse(Files.readAllBytes(FILE));
            JsonObject w = Json.obj(o, "weapons");
            if (w != null) for (var en : w.entrySet()) weapons.put(en.getKey(), en.getValue().getAsString());
            JsonArray f = Json.arr(o, "favorites");
            if (f != null) for (JsonElement e : f) favorites.add(e.getAsString());
            JsonObject p = Json.obj(o, "poses");
            if (p != null) for (var en : p.entrySet()) {
                JsonArray groups = en.getValue().getAsJsonArray();
                float[][] g = new float[3][];
                for (int i = 0; i < Math.min(3, groups.size()); i++) {
                    if (!groups.get(i).isJsonArray()) continue;
                    JsonArray a = groups.get(i).getAsJsonArray();
                    float[] v = defaultAdjust();
                    for (int k = 0; k < Math.min(7, a.size()); k++) v[k] = a.get(k).getAsFloat();
                    g[i] = v;
                }
                poses.put(en.getKey(), g);
            }
            pet = Json.s(o, "pet", "");
            hat = Json.s(o, "hat", "");
            wing = Json.s(o, "wing", "");
        } catch (Exception e) {
            ModuleManager.LOG.warn("models.json не прочитан, выбор моделей сброшен", e);
        }
    }

    public static void save() {
        JsonObject o = new JsonObject();
        JsonObject w = new JsonObject();
        weapons.forEach(w::addProperty);
        o.add("weapons", w);
        JsonArray f = new JsonArray();
        favorites.forEach(f::add);
        o.add("favorites", f);
        JsonObject p = new JsonObject();
        poses.forEach((id, g) -> {
            JsonArray groups = new JsonArray();
            for (float[] v : g) {
                JsonArray a = new JsonArray();
                if (v != null) for (float x : v) a.add(x);
                groups.add(a);
            }
            p.add(id, groups);
        });
        o.add("poses", p);
        o.addProperty("pet", pet);
        o.addProperty("hat", hat);
        o.addProperty("wing", wing);
        try {
            Files.createDirectories(FILE.getParent());
            Path tmp = FILE.resolveSibling("models.json.tmp");
            Files.writeString(tmp, GSON.toJson(o), StandardCharsets.UTF_8);
            Files.move(tmp, FILE, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            ModuleManager.LOG.warn("models.json не сохранён", e);
        }
    }

    public static float[] defaultAdjust() {
        return new float[]{0, 0, 0, 0, 0, 0, 1};
    }

    /** Подстройка позы скина для группы (создаётся при первом обращении). */
    public static float[] adjust(String id, int group) {
        float[][] g = poses.computeIfAbsent(id, k -> new float[3][]);
        if (g[group] == null) g[group] = defaultAdjust();
        return g[group];
    }

    /** Применить подстройки к готовым позам скина. */
    public static void applyPose(ModelEntry e) {
        e.rebuildDisplay(poses.get(e.id));
    }

    public static void poseChanged(ModelEntry e) {
        applyPose(e);
        poseVersion++;
        save();
    }

    public static void resetPose(ModelEntry e) {
        poses.remove(e.id);
        poseChanged(e);
    }
}

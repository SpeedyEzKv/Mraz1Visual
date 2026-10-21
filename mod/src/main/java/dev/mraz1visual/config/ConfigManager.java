package dev.mraz1visual.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.core.ModuleManager;
import dev.mraz1visual.setting.KeySetting;
import dev.mraz1visual.setting.Setting;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Конфиг и профили.
 *
 *   config/mraz1visual.json                — настройки клиента (клавиша меню, окно, цвет), активный профиль, состояние окна
 *   config/mraz1visual/profiles/*.json     — профили: какие визуалы включены и как настроены
 *   config/mraz1visual/exports/*.json      — выгруженные «В файл» профили, чтобы отдать другу
 *
 * Правила: битый или старый файл никогда не роняет игру. Не прочиталось — значения по умолчанию
 * и строка в лог. Битый файл сохраняется рядом как .broken. Запись атомарная.
 */
public final class ConfigManager {
    public static final int VERSION = 2;
    public static final int MAX_PROFILES = 20;
    public static final String CLIENT_ID = "client";
    private static final Logger LOG = ModuleManager.LOG;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final ModuleManager modules;
    private final Path rootFile, profilesDir, exportsDir;
    private boolean dirty;

    /** Файл активного профиля (имя файла без пути). */
    private String active = "";

    /** Состояние окна: последняя вкладка и модуль. */
    public String uiCategory = "";
    public String uiModule = "";

    /** Сведения о профиле для списка. */
    public record ProfileInfo(String file, String name, long updated, long size) {}

    public ConfigManager(ModuleManager modules) {
        this.modules = modules;
        Path dir = FabricLoader.getInstance().getConfigDir();
        this.rootFile = dir.resolve("mraz1visual.json");
        this.profilesDir = dir.resolve("mraz1visual").resolve("profiles");
        this.exportsDir = dir.resolve("mraz1visual").resolve("exports");
        Setting.globalChangeHook = this::markDirty;
    }

    public void markDirty() { dirty = true; }

    public String activeFile() { return active; }

    public Path exportsDir() { return exportsDir; }

    // ── загрузка ─────────────────────────────────────────────

    public void load() {
        try {
            Files.createDirectories(profilesDir);
        } catch (IOException e) {
            LOG.error("Cannot create {}", profilesDir, e);
        }

        JsonObject root = readJson(rootFile);
        JsonObject migratedModules = null;
        if (root != null) {
            int version = intOr(root.get("version"), 0);
            Module client = modules.find(CLIENT_ID);
            if (version < 2) {
                // v1: всё лежало в mraz1visual.json → клиентские настройки берём оттуда, остальное станет профилем
                LOG.info("Config v{} -> v{}: moving modules into profile", version, VERSION);
                migratedModules = objOr(root.get("modules"));
                JsonObject cm = migratedModules == null ? null : objOr(migratedModules.get(CLIENT_ID));
                if (client != null && cm != null) readSettings(client, objOr(cm.get("settings")));
                dirty = true;
            } else {
                if (client != null) readSettings(client, objOr(root.get("client")));
                active = strOr(root.get("active"), "");
            }
            JsonObject ui = objOr(root.get("ui"));
            if (ui != null) {
                uiCategory = strOr(ui.get("category"), "");
                uiModule = strOr(ui.get("module"), "");
            }
        } else {
            dirty = true;
        }

        if (migratedModules != null) {
            active = newFileName();
            JsonObject p = new JsonObject();
            p.addProperty("version", VERSION);
            p.addProperty("name", "Основной");
            p.addProperty("updated", System.currentTimeMillis());
            p.add("modules", migratedModules);
            writeJson(profilesDir.resolve(active), p);
        }

        if (active.isEmpty() || !Files.exists(profilesDir.resolve(active))) {
            List<ProfileInfo> list = list();
            if (!list.isEmpty()) {
                active = list.get(0).file();
            } else {
                active = newFileName();
                writeJson(profilesDir.resolve(active), profileJson("Основной", null));
            }
            dirty = true;
        }

        JsonObject profile = readJson(profilesDir.resolve(active));
        applyModules(profile == null ? null : objOr(profile.get("modules")), false);
        if (dirty) save();
    }

    /** Применить модули профиля. null — всё по умолчанию. callHooks — включать/выключать «вживую». */
    private void applyModules(JsonObject mods, boolean callHooks) {
        for (Module m : modules.all()) {
            if (m.alwaysOn()) continue;
            JsonObject mj = mods == null ? null : objOr(mods.get(m.id));
            boolean enabled = false;
            try {
                if (mj != null) {
                    JsonElement en = mj.get("enabled");
                    enabled = en != null && en.isJsonPrimitive() && en.getAsJsonPrimitive().isBoolean() && en.getAsBoolean();
                    if (!mj.has("bind") || !m.bind.fromJson(mj.get("bind"))) m.bind.set(KeySetting.NONE);
                    readSettings(m, objOr(mj.get("settings")));
                } else {
                    m.bind.set(KeySetting.NONE);
                    for (Setting<?> s : m.settings()) s.reset();
                }
            } catch (Throwable t) {
                LOG.warn("Profile: module {} unreadable, defaults ({})", m.id, t.toString());
            }
            if (callHooks) m.setEnabled(enabled);
            else m.loadEnabled(enabled);
        }
        modules.markBindsDirty();
    }

    private void readSettings(Module m, JsonObject sj) {
        // миграция: раньше акцент задавался только цветом. Был свой (не белый по умолчанию) — оставляем его как «Свой»
        if (m == dev.mraz1visual.core.Modules.client && sj != null && !sj.has("accent_preset") && sj.has("accent")) {
            dev.mraz1visual.module.ClientSettings cs = dev.mraz1visual.core.Modules.client;
            if (cs.accent.fromJson(sj.get("accent")) && (cs.accent.get() & 0xFFFFFF) != (dev.mraz1visual.ui.Theme.DEFAULT_ACCENT & 0xFFFFFF)) {
                sj = sj.deepCopy();
                sj.addProperty("accent_preset", "custom");
            }
        }
        for (Setting<?> s : m.settings()) {
            JsonElement v = sj == null ? null : sj.get(s.id);
            if (v == null) {
                s.reset();
            } else if (!s.fromJson(v)) {
                LOG.warn("Config: {}.{} invalid, default", m.id, s.id);
                s.reset();
            }
        }
    }

    // ── сохранение ───────────────────────────────────────────

    public void saveIfDirty() {
        if (dirty) save();
    }

    public void save() {
        JsonObject root = new JsonObject();
        root.addProperty("version", VERSION);
        root.addProperty("active", active);
        Module client = modules.find(CLIENT_ID);
        if (client != null) root.add("client", settingsJson(client));
        JsonObject ui = new JsonObject();
        ui.addProperty("category", uiCategory);
        ui.addProperty("module", uiModule);
        root.add("ui", ui);
        boolean ok = writeJson(rootFile, root);

        if (!active.isEmpty()) {
            JsonObject old = readJsonQuiet(profilesDir.resolve(active));
            String name = old == null ? "Основной" : strOr(old.get("name"), "Основной");
            ok &= writeJson(profilesDir.resolve(active), profileJson(name, currentModules()));
        }
        if (ok) dirty = false;
    }

    private JsonObject currentModules() {
        JsonObject mods = new JsonObject();
        for (Module m : modules.all()) {
            if (m.alwaysOn()) continue;
            JsonObject mj = new JsonObject();
            mj.addProperty("enabled", m.isEnabled());
            mj.add("bind", m.bind.toJson());
            mj.add("settings", settingsJson(m));
            mods.add(m.id, mj);
        }
        return mods;
    }

    private static JsonObject settingsJson(Module m) {
        JsonObject sj = new JsonObject();
        for (Setting<?> s : m.settings()) sj.add(s.id, s.toJson());
        return sj;
    }

    private static JsonObject profileJson(String name, JsonObject mods) {
        JsonObject p = new JsonObject();
        p.addProperty("version", VERSION);
        p.addProperty("name", name);
        p.addProperty("updated", System.currentTimeMillis());
        p.add("modules", mods == null ? new JsonObject() : mods);
        return p;
    }

    // ── профили ──────────────────────────────────────────────

    public List<ProfileInfo> list() {
        List<ProfileInfo> out = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(profilesDir, "*.json")) {
            for (Path p : ds) {
                JsonObject j = readJsonQuiet(p);
                if (j == null) continue;
                String file = p.getFileName().toString();
                long updated = longOr(j.get("updated"), Files.getLastModifiedTime(p).toMillis());
                out.add(new ProfileInfo(file, strOr(j.get("name"), file.replace(".json", "")), updated, Files.size(p)));
            }
        } catch (IOException e) {
            LOG.warn("Cannot list profiles", e);
        }
        out.sort(Comparator.comparing(ProfileInfo::file));
        return out;
    }

    /** id включённых модулей профиля. Для активного — живое состояние. */
    public List<String> enabledIn(String file) {
        List<String> ids = new ArrayList<>();
        if (file.equals(active)) {
            for (Module m : modules.all()) if (!m.alwaysOn() && m.isEnabled()) ids.add(m.id);
            return ids;
        }
        JsonObject j = readJsonQuiet(profilesDir.resolve(file));
        JsonObject mods = j == null ? null : objOr(j.get("modules"));
        if (mods == null) return ids;
        for (String id : mods.keySet()) {
            JsonObject mj = objOr(mods.get(id));
            if (mj == null) continue;
            JsonElement en = mj.get("enabled");
            if (en != null && en.isJsonPrimitive() && en.getAsJsonPrimitive().isBoolean() && en.getAsBoolean()) ids.add(id);
        }
        return ids;
    }

    /** @return имя файла нового профиля или null, если лимит. */
    public String create(String name) {
        if (list().size() >= MAX_PROFILES) return null;
        String file = newFileName();
        return writeJson(profilesDir.resolve(file), profileJson(uniqueName(name), null)) ? file : null;
    }

    public String duplicate(String file) {
        if (list().size() >= MAX_PROFILES) return null;
        if (file.equals(active)) save();
        JsonObject j = readJsonQuiet(profilesDir.resolve(file));
        if (j == null) return null;
        String copy = newFileName();
        j.addProperty("name", uniqueName(strOr(j.get("name"), "Профиль") + " копия"));
        j.addProperty("updated", System.currentTimeMillis());
        return writeJson(profilesDir.resolve(copy), j) ? copy : null;
    }

    public void rename(String file, String name) {
        name = name.trim();
        if (name.isEmpty()) return;
        if (file.equals(active)) save();
        JsonObject j = readJsonQuiet(profilesDir.resolve(file));
        if (j == null) return;
        j.addProperty("name", name.length() > 32 ? name.substring(0, 32) : name);
        writeJson(profilesDir.resolve(file), j);
    }

    /** Нельзя удалить активный и последний. */
    public boolean delete(String file) {
        if (file.equals(active) || list().size() <= 1) return false;
        try {
            Files.deleteIfExists(profilesDir.resolve(file));
            return true;
        } catch (IOException e) {
            LOG.warn("Cannot delete profile {}", file, e);
            return false;
        }
    }

    /** Переключиться: сохранить текущий, применить новый вживую. */
    public void switchTo(String file) {
        if (file.equals(active)) return;
        JsonObject j = readJson(profilesDir.resolve(file));
        if (j == null) return;
        save();
        active = file;
        applyModules(objOr(j.get("modules")), true);
        dirty = true;
        save();
    }

    /** Сбросить активный профиль к умолчанию: всё выключено, настройки по умолчанию. */
    public void resetActive() {
        applyModules(null, true);
        dirty = true;
        save();
    }

    /** @return путь к файлу или null. */
    public Path export(String file) {
        if (file.equals(active)) save();
        JsonObject j = readJsonQuiet(profilesDir.resolve(file));
        if (j == null) return null;
        String name = strOr(j.get("name"), "profile").replaceAll("[\\\\/:*?\"<>|]", "_");
        try {
            Files.createDirectories(exportsDir);
        } catch (IOException e) {
            return null;
        }
        Path out = exportsDir.resolve(name + ".json");
        return writeJson(out, j) ? out : null;
    }

    /** Импорт из файла (перетащили в окно). @return имя файла профиля или null. */
    public String importFile(Path path) {
        if (list().size() >= MAX_PROFILES) return null;
        JsonObject j = readJson(path);
        if (j == null || objOr(j.get("modules")) == null) return null;
        String base = strOr(j.get("name"), path.getFileName().toString().replace(".json", ""));
        j.addProperty("name", uniqueName(base));
        j.addProperty("version", VERSION);
        j.addProperty("updated", System.currentTimeMillis());
        String file = newFileName();
        return writeJson(profilesDir.resolve(file), j) ? file : null;
    }

    private String uniqueName(String base) {
        String name = base.length() > 32 ? base.substring(0, 32) : base;
        List<ProfileInfo> list = list();
        int n = 2;
        String candidate = name;
        while (true) {
            boolean taken = false;
            for (ProfileInfo p : list) if (p.name().equalsIgnoreCase(candidate)) taken = true;
            if (!taken) return candidate;
            candidate = name + " " + n++;
        }
    }

    private String newFileName() {
        long t = System.currentTimeMillis();
        String f;
        do {
            f = "p" + Long.toString(t++, 36) + ".json";
        } while (Files.exists(profilesDir.resolve(f)));
        return f;
    }

    // ── файлы ────────────────────────────────────────────────

    /** Читает JSON. Битый файл копируется в .broken, в лог — предупреждение. */
    private JsonObject readJson(Path file) {
        if (!Files.exists(file)) return null;
        try {
            JsonElement e = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            if (!e.isJsonObject()) throw new IllegalStateException("root is not an object");
            return e.getAsJsonObject();
        } catch (Throwable t) {
            LOG.warn("{} is broken ({}), using defaults", file.getFileName(), t.toString());
            try {
                Files.copy(file, file.resolveSibling(file.getFileName() + ".broken"), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
            }
            return null;
        }
    }

    private static JsonObject readJsonQuiet(Path file) {
        try {
            JsonElement e = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            return e.isJsonObject() ? e.getAsJsonObject() : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean writeJson(Path file, JsonObject json) {
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(json), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException e) {
            LOG.error("Could not save {}", file, e);
            return false;
        }
    }

    private static JsonObject objOr(JsonElement e) {
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
    }

    private static int intOr(JsonElement e, int def) {
        try { return e != null && e.isJsonPrimitive() ? e.getAsInt() : def; } catch (Exception x) { return def; }
    }

    private static long longOr(JsonElement e, long def) {
        try { return e != null && e.isJsonPrimitive() ? e.getAsLong() : def; } catch (Exception x) { return def; }
    }

    private static String strOr(JsonElement e, String def) {
        return e != null && e.isJsonPrimitive() ? e.getAsString() : def;
    }
}

package dev.mraz1visual.models;

import com.google.gson.JsonObject;
import dev.mraz1visual.core.ModuleManager;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Импортированные модели из &lt;gameDir&gt;/mraz1visual/weapon_skins и /pets. Читает паки во время работы
 * (zip — как есть, со вложенными zip), в фоне, по одному потоку; в кадре — никакого I/O.
 * Текстуры попадают на видеокарту в потоке рендера. «Обновить папку» — перезагрузка без перезапуска игры.
 * Битая модель не валит загрузку: пропускается, ошибка — в лог и значком в меню.
 */
public final class ModelRegistry {
    public static final Path ROOT = FabricLoader.getInstance().getGameDir().resolve("mraz1visual");
    public static final Path WEAPONS = ROOT.resolve("weapon_skins"), PETS = ROOT.resolve("pets");

    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Mraz1Visual model loader");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    private static volatile List<ModelEntry> entries = List.of();
    private static volatile Map<String, ModelEntry> byId = Map.of();
    private static volatile boolean loading;
    private static volatile String status = "";
    private static volatile int done, total;
    private static int generation;
    /** Вызывается в потоке рендера после каждой загрузки (модули обновляют выбор). */
    public static Runnable onLoaded = () -> {};

    private ModelRegistry() {}

    public static List<ModelEntry> all() { return entries; }

    public static ModelEntry get(String id) { return id == null ? null : byId.get(id); }

    public static boolean loading() { return loading; }

    public static String status() { return status; }

    /** 0..1 — прогресс текущей загрузки. */
    public static float progress() { return total == 0 ? 0 : done / (float) total; }

    public static List<ModelEntry> of(ModelEntry.Kind kind) {
        List<ModelEntry> out = new ArrayList<>();
        for (ModelEntry e : entries) if (e.kind == kind) out.add(e);
        return out;
    }

    private static volatile boolean requested;

    /**
     * Модели грузятся по требованию — когда включены скины, питомцы или косметика либо открыта галерея:
     * пока они не нужны, клиент не держит в памяти сотни атласов и вершин.
     */
    public static void ensureLoaded() {
        if (requested) return;
        requested = true;
        reload();
    }

    /** Перечитать обе папки (в фоне). */
    public static synchronized void reload() {
        requested = true;
        int gen = ++generation;
        loading = true;
        done = 0;
        total = 0;
        status = "Загрузка моделей…";
        IO.execute(() -> {
            List<ModelEntry> loaded = new ArrayList<>();
            try {
                Files.createDirectories(WEAPONS);
                Files.createDirectories(PETS);
                List<Path> w = list(WEAPONS), p = list(PETS);
                total = w.size() + p.size() + 1;
                scanBuiltin(loaded);
                done++;
                for (Path f : w) { scanWeapons(f, loaded); done++; }
                for (Path f : p) { scanPets(f, loaded); done++; }
            } catch (Throwable t) {
                ModuleManager.LOG.error("Model import failed", t);
            }
            Minecraft.getInstance().execute(() -> install(gen, loaded));
        });
    }

    private static List<Path> list(Path dir) throws java.io.IOException {
        try (var s = Files.list(dir)) {
            List<Path> out = new ArrayList<>(s.filter(f -> !f.getFileName().toString().startsWith(".")).toList());
            out.sort(Path::compareTo);
            return out;
        }
    }

    private static void install(int gen, List<ModelEntry> loaded) {
        if (gen != generation) return;
        List<ModelEntry> old = entries;
        Map<String, ModelEntry> map = new LinkedHashMap<>();
        int ok = 0, bad = 0;
        for (ModelEntry e : loaded) {
            if (map.containsKey(e.id)) continue;
            try {
                for (Mesh m : e.states.values()) { m.atlas.upload(e.name); m.atlas.dropCpuCopy(); }
                for (ModelEntry fx : e.effects) for (Mesh m : fx.states.values()) { m.atlas.upload(fx.name); m.atlas.dropCpuCopy(); }
            } catch (Throwable t) {
                e.error = "текстура не загрузилась: " + t.getMessage();
            }
            map.put(e.id, e);
            if (e.broken()) bad++; else ok++;
        }
        entries = Collections.unmodifiableList(new ArrayList<>(map.values()));
        byId = map;
        for (ModelEntry e : old) {
            for (Mesh m : e.states.values()) m.atlas.release();
            for (ModelEntry fx : e.effects) for (Mesh m : fx.states.values()) m.atlas.release();
        }
        loading = false;
        status = ok + " " + plural(ok, "модель", "модели", "моделей") + (bad > 0 ? ", с ошибками: " + bad : "");
        ModuleManager.LOG.info("Mraz1Visual models: {}", status);
        try {
            onLoaded.run();
        } catch (Throwable t) {
            ModuleManager.LOG.error("Model listeners failed", t);
        }
    }

    private static String plural(int n, String one, String few, String many) {
        int m10 = n % 10, m100 = n % 100;
        if (m10 == 1 && m100 != 11) return one;
        if (m10 >= 2 && m10 <= 4 && (m100 < 12 || m100 > 14)) return few;
        return many;
    }

    /** Анимированные текстуры видимых моделей — раз в тик (вызывается из тика клиента). */
    public static void tick(int gameTick) {
        long now = System.currentTimeMillis();
        for (ModelEntry e : entries) {
            for (Mesh m : e.states.values()) if (!m.atlas.anims.isEmpty() && now - m.atlas.lastUsed < 1500) m.atlas.tick(gameTick);
        }
    }

    // ── оружие ───────────────────────────────────────────────

    private static void scanWeapons(Path file, List<ModelEntry> out) {
        String packName = file.getFileName().toString();
        PackFs fs;
        try {
            fs = PackFs.open(file);
        } catch (Throwable t) {
            out.add(broken("weapon:" + packName, PackFs.stripExt(packName), packName, ModelEntry.Kind.WEAPON, t));
            return;
        }
        scanWeapons(fs, PackFs.stripExt(packName), out);
    }

    private static void scanWeapons(PackFs fs, String source, List<ModelEntry> out) {
        String packName = source;
        ItemModelLoader.TexCache cache = new ItemModelLoader.TexCache();
        Set<String> used = new java.util.HashSet<>();
        int before = out.size();

        // 1) OptiFine CIT: *.properties → модель или текстура, имя и привязка
        for (String props : fs.paths(".properties")) {
            if (!props.contains("/cit/")) continue;
            Map<String, String> p = properties(fs.get(props));
            String model = p.get("model"), texture = p.get("texture");
            if (model == null && texture == null) {
                texture = PackFs.baseName(props);
            }
            String dir = PackFs.dir(props);
            String key = PackFs.baseName(props);
            String name = citName(p, key);
            String id = "weapon:" + source + "/" + key;
            ModelEntry e = new ModelEntry(id, name, source, kindFor(key));
            e.format = "OptiFine CIT";
            e.bindHint = bindFromItems(p.getOrDefault("matchItems", p.getOrDefault("items", "")));
            try {
                if (model != null) {
                    String mp = relative(dir, model.endsWith(".json") ? model : model + ".json");
                    if (fs.get(mp) == null) throw new IllegalStateException("нет файла " + mp);
                    used.add(mp);
                    loadJsonInto(e, fs, mp, ModelEntry.DEFAULT, cache);
                } else {
                    String tp = relative(dir, texture.endsWith(".png") ? texture : texture + ".png");
                    if (fs.get(tp) == null) tp = fs.findTexture(texture, dir);
                    if (tp == null) throw new IllegalStateException("нет текстуры " + texture);
                    extrudeInto(e, fs, tp);
                }
            } catch (Throwable t) {
                fail(e, t);
            }
            out.add(e);
        }
        Map<String, String> names = new HashMap<>();
        byte[] nj = fs.get("names.json");
        if (nj != null) {
            try {
                for (var en : Json.parse(nj).entrySet()) names.put(en.getKey().toLowerCase(Locale.ROOT), en.getValue().getAsString());
            } catch (Exception ignored) {
            }
        }
        // 2) модели предметов (CraftEngine, ItemsAdder, обычные ресурспаки) — с группировкой состояний
        Map<String, Map<String, String>> groups = new LinkedHashMap<>();
        for (String path : fs.paths(".json")) {
            if (used.contains(path)) continue;
            boolean cit = path.contains("/cit/");
            if (!cit && !path.contains("/models/")) continue;
            if (path.contains("/armor/") || path.contains("/block/") || path.endsWith("pack.mcmeta")) continue;
            if (!cit && !path.contains("/models/item/") && !path.matches(".*/assets/[^/]+/models/.*")) continue;
            if (!hasElements(fs, path)) continue;
            String base = PackFs.baseName(path), state = ModelEntry.DEFAULT;
            String low = base.toLowerCase(Locale.ROOT);
            if (low.matches(".*bow_[0-2]")) { state = "pull" + low.charAt(low.length() - 1); base = base.substring(0, base.length() - 2); }
            else if (low.endsWith("_cast")) { state = ModelEntry.CAST; base = base.substring(0, base.length() - 5); }
            else if (low.endsWith("_blocking")) { state = ModelEntry.BLOCKING; base = base.substring(0, base.length() - 9); }
            groups.computeIfAbsent(PackFs.dir(path) + "/" + base, k -> new LinkedHashMap<>()).putIfAbsent(state, path);
        }
        for (var g : groups.entrySet()) {
            String key = PackFs.baseName(g.getKey());
            Map<String, String> states = g.getValue();
            String id = "weapon:" + source + "/" + key;
            ModelEntry e = new ModelEntry(id, names.getOrDefault(key.toLowerCase(Locale.ROOT), pretty(key)), source, kindFor(key));
            e.format = states.values().iterator().next().contains("/cit/") ? "OptiFine CIT" : "Item JSON";
            e.bindHint = bindFromName(key);
            try {
                if (!states.containsKey(ModelEntry.DEFAULT)) states.put(ModelEntry.DEFAULT, states.values().iterator().next());
                loadJsonInto(e, fs, states.get(ModelEntry.DEFAULT), ModelEntry.DEFAULT, cache);
                for (var s : states.entrySet()) {
                    if (s.getKey().equals(ModelEntry.DEFAULT)) continue;
                    try {
                        loadJsonInto(e, fs, s.getValue(), s.getKey(), cache);
                    } catch (Throwable t) {
                        e.warnings.add(s.getKey() + ": " + t.getMessage());
                    }
                }
            } catch (Throwable t) {
                fail(e, t);
            }
            out.add(e);
        }
        // 3) модели Blockbench
        for (String path : fs.paths(".bbmodel")) {
            String key = PackFs.baseName(path);
            ModelEntry e = new ModelEntry("weapon:" + source + "/" + key, pretty(key), source, kindFor(key));
            e.format = "Blockbench";
            e.bindHint = bindFromName(key);
            try {
                BbModelLoader.Result r = BbModelLoader.load(fs.get(path), key, fs, path, warn(e));
                e.states.put(ModelEntry.DEFAULT, r.mesh);
                e.anims.addAll(r.anims);
                e.setDisplay(r.display, null);
            } catch (Throwable t) {
                fail(e, t);
            }
            out.add(e);
        }
        // 4) в паке нет моделей — только плоские текстуры: каждая png — объёмный скин (повторы — один раз)
        if (out.size() == before) {
            Set<String> seen = new java.util.HashSet<>();
            for (String path : fs.paths(".png")) {
                String key = PackFs.baseName(path).toLowerCase(Locale.ROOT);
                if (key.equals("pack") || key.endsWith("_e") || !itemSized(fs.get(path)) || !seen.add(key)) continue;
                ModelEntry e = new ModelEntry("weapon:" + source + "/" + key, pretty(key), source, ModelEntry.Kind.WEAPON);
                e.format = "PNG → 3D";
                e.bindHint = bindFromName(key);
                try {
                    extrudeInto(e, fs, path);
                } catch (Throwable t) {
                    fail(e, t);
                }
                out.add(e);
            }
        }
        for (int i = before; i < out.size(); i++) out.get(i).rebuildDisplay(null);
        if (out.size() == before) ModuleManager.LOG.warn("Mraz1Visual models: в «{}» не найдено ни одной модели", packName);
    }

    private static void loadJsonInto(ModelEntry e, PackFs fs, String path, String state, ItemModelLoader.TexCache cache) throws Exception {
        ItemModelLoader.Result r = ItemModelLoader.load(fs, path, cache, true, warn(e));
        e.states.put(state, r.mesh);
        if (state.equals(ModelEntry.DEFAULT)) e.setDisplay(r.display, r.builtin);
    }

    private static void extrudeInto(ModelEntry e, PackFs fs, String png) throws Exception {
        byte[] meta = fs.get(png + ".mcmeta");
        TexImage t = TexImage.decode(PackFs.baseName(png), fs.get(png), meta == null ? null : Json.parse(meta), -1);
        byte[] ep = fs.get(png.substring(0, png.length() - 4) + "_e.png");
        if (ep != null) t.setGlow(TexImage.decode("e", ep, null, t.h));
        MeshBuilder b = new MeshBuilder();
        Extruder.extrude(b, t, 0, 1.25f, true);
        e.states.put(ModelEntry.DEFAULT, b.build());
        e.setDisplay(null, "handheld");
    }

    /** Похоже на текстуру предмета (не превью пака): ширина до 128 px по заголовку PNG. */
    private static boolean itemSized(byte[] png) {
        if (png == null || png.length < 24) return false;
        int w = (png[16] & 255) << 24 | (png[17] & 255) << 16 | (png[18] & 255) << 8 | (png[19] & 255);
        int h = (png[20] & 255) << 24 | (png[21] & 255) << 16 | (png[22] & 255) << 8 | (png[23] & 255);
        return w > 0 && w <= 128 && h >= w && h % w == 0;
    }

    private static boolean hasElements(PackFs fs, String path) {
        try {
            JsonObject o = Json.parse(fs.get(path));
            if (o.has("elements")) return true;
            // модель-наследник с текстурами поверх родителя с elements из того же пака
            String parent = Json.s(o, "parent", null);
            if (parent == null) return false;
            String p = fs.findModel(parent);
            return p != null && !p.equals(path) && hasElements(fs, p);
        } catch (Exception ex) {
            return false;
        }
    }

    // ── питомцы ──────────────────────────────────────────────

    private static void scanPets(Path file, List<ModelEntry> out) {
        String packName = file.getFileName().toString();
        PackFs fs;
        try {
            fs = PackFs.open(file);
        } catch (Throwable t) {
            out.add(broken("pet:" + packName, PackFs.stripExt(packName), packName, ModelEntry.Kind.PET, t));
            return;
        }
        scanPets(fs, PackFs.stripExt(packName), out);
    }

    // ── встроенная косметика (ресурсы мода) ───────────────────

    /** Коллекция встроенной косметики: пак в .jar, его автор и ссылка. */
    public record Collection(String id, String title, String author, String url, String kind, String note, String stem) {}

    private static volatile List<Collection> collections = List.of();

    public static List<Collection> collections() { return collections; }

    public static Collection collection(String id) {
        for (Collection c : collections) if (c.id().equals(id)) return c;
        return null;
    }

    /** Встроенные паки из ресурсов мода: сразу доступны, ничего не нужно скачивать. */
    private static void scanBuiltin(List<ModelEntry> out) {
        var mod = FabricLoader.getInstance().getModContainer("mraz1visual").orElse(null);
        if (mod == null) return;
        Path root = mod.findPath("assets/mraz1visual/cosmetics").orElse(null);
        if (root == null) return;
        List<Collection> list = new ArrayList<>();
        try {
            com.google.gson.JsonObject j = Json.parse(Files.readAllBytes(root.resolve("collections.json")));
            for (var el : j.getAsJsonArray("collections")) {
                com.google.gson.JsonObject o = el.getAsJsonObject();
                list.add(new Collection(Json.s(o, "id", ""), Json.s(o, "title", ""), Json.s(o, "author", ""), Json.s(o, "url", ""),
                        Json.s(o, "kind", "weapons"), Json.s(o, "note", ""), Json.s(o, "stem", Json.s(o, "id", ""))));
            }
        } catch (Exception e) {
            ModuleManager.LOG.warn("Mraz1Visual models: нет collections.json встроенной косметики", e);
        }
        collections = List.copyOf(list);
        for (Collection c : list) {
            Path dir = root.resolve(c.id());
            if (!Files.isDirectory(dir)) continue; // пак ещё не встроен (ждёт разрешения) — только локальный импорт
            int before = out.size();
            try {
                PackFs fs = PackFs.open(dir);
                if (c.kind().equals("pets")) scanPets(fs, c.stem(), out);
                else scanWeapons(fs, c.stem(), out);
            } catch (Throwable t) {
                ModuleManager.LOG.warn("Mraz1Visual models: встроенный пак {} не прочитан", c.id(), t);
            }
            for (int i = before; i < out.size(); i++) {
                ModelEntry e = out.get(i);
                e.builtin = true;
                e.collection = c.id();
                e.sourceTitle = c.title();
                e.author = c.author();
            }
        }
    }

    private static void scanPets(PackFs fs, String source, List<ModelEntry> out) {
        String packName = source;
        Map<String, ModelEntry> pets = new LinkedHashMap<>();
        List<ModelEntry> fx = new ArrayList<>();
        for (String path : fs.paths(".bbmodel")) {
            String key = PackFs.baseName(path).toLowerCase(Locale.ROOT);
            boolean effect = key.startsWith("vfx") || key.endsWith("_effect");
            // один питомец — один раз (в паках CUBEES одна модель лежит в 6 папках)
            String id = (effect ? "fx:" : "pet:") + key;
            if (pets.containsKey(id) || containsId(out, id) || containsId(fx, id)) continue;
            ModelEntry e = new ModelEntry(id, pretty(key.replaceFirst("^cubee-", "")), source, effect ? ModelEntry.Kind.EFFECT : ModelEntry.Kind.PET);
            e.format = "Blockbench";
            try {
                BbModelLoader.Result r = BbModelLoader.load(fs.get(path), key, fs, path, warn(e));
                e.states.put(ModelEntry.DEFAULT, r.mesh);
                e.anims.addAll(r.anims);
                e.flying = guessFlying(key, e);
                // рамка — по позе idle: спрятанные анимацией части (эффекты) не в счёт
                Anim idle = e.anim("idle") != null ? e.anim("idle") : e.anims.isEmpty() ? null : e.anims.get(0);
                if (idle != null) {
                    AnimPlayer ap = new AnimPlayer();
                    ap.play(idle, 0, true);
                    r.mesh.fitBounds(ap.pose(r.mesh));
                }
            } catch (Throwable t) {
                fail(e, t);
            }
            if (effect) fx.add(e); else pets.put(id, e);
        }
        // эффекты — к питомцу, чьё имя есть в имени эффекта (vfx_cubee_llama_bullet → llama), общие — ко всем из пака
        for (ModelEntry f : fx) {
            if (f.broken()) continue;
            String fk = f.id.substring(3);
            boolean attached = false;
            for (ModelEntry p : pets.values()) {
                String pk = p.id.substring(4).replaceFirst("^cubee-", "");
                if (fk.contains(pk)) { p.effects.add(f); attached = true; }
            }
            if (!attached && fk.contains("dizzy")) for (ModelEntry p : pets.values()) p.effects.add(f);
        }
        out.addAll(pets.values());
        out.addAll(fx);
    }

    private static boolean containsId(List<ModelEntry> list, String id) {
        for (ModelEntry e : list) if (e.id.equals(id)) return true;
        return false;
    }

    /** Летает: в имени — ghast/bat/bee/fly/wing/ghost/drone, или нет анимации ходьбы. */
    private static boolean guessFlying(String key, ModelEntry e) {
        key = key.replaceFirst("^cubees?[-_]", ""); // «cubee» содержит «bee»
        for (String w : new String[]{"ghast", "bat", "bee", "fly", "wing", "ghost", "drone", "phantom", "vex", "allay", "jelly"}) {
            if (key.contains(w)) return true;
        }
        return e.anim("walk") == null && (e.anim("fly") != null || e.anim("idle") == null);
    }

    // ── общее ────────────────────────────────────────────────

    private static ModelEntry broken(String id, String name, String source, ModelEntry.Kind kind, Throwable t) {
        ModelEntry e = new ModelEntry(id, name, source, kind);
        fail(e, t);
        return e;
    }

    private static void fail(ModelEntry e, Throwable t) {
        e.error = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
        ModuleManager.LOG.warn("Mraz1Visual models: «{}» ({}) пропущена: {}", e.name, e.source, e.error);
    }

    private static Consumer<String> warn(ModelEntry e) {
        Set<String> once = new java.util.HashSet<>();
        return msg -> {
            if (!once.add(msg) || e.warnings.size() > 50) return;
            e.warnings.add(msg);
            ModuleManager.LOG.warn("Mraz1Visual models: {} ({}): {}", e.name, e.source, msg);
        };
    }

    private static Map<String, String> properties(byte[] data) {
        Map<String, String> out = new HashMap<>();
        for (String line : new String(data, java.nio.charset.StandardCharsets.UTF_8).split("\\R")) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int eq = line.indexOf('=');
            if (eq > 0) out.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
        }
        return out;
    }

    private static String relative(String dir, String ref) {
        ref = ref.replace('\\', '/');
        if (ref.startsWith("./")) ref = ref.substring(2);
        if (ref.startsWith("assets/") || ref.contains(":")) {
            int c = ref.indexOf(':');
            return c >= 0 ? "assets/" + ref.substring(0, c) + "/" + ref.substring(c + 1) : ref;
        }
        return (dir.isEmpty() ? "" : dir + "/") + ref;
    }

    /** Имя из nbt.display.Name=ipattern:*molten sword* → «Molten Sword»; иначе — из имени файла. */
    private static String citName(Map<String, String> p, String key) {
        for (var en : p.entrySet()) {
            if (!en.getKey().startsWith("nbt.display.Name") && !en.getKey().startsWith("components.custom_name")) continue;
            String v = en.getValue().replaceFirst("^(i?pattern|i?regex):", "").replace("*", " ").replace("\\", "");
            v = v.replaceAll("[^\\p{L}\\p{N}' -]", " ").trim().replaceAll("\\s+", " ");
            if (!v.isEmpty() && v.length() < 40) return pretty(v);
        }
        return pretty(key);
    }

    static String pretty(String key) {
        String s = key.replace('_', ' ').replace('-', ' ').trim();
        // camelCase и слитные имена файлов оставляем, только первые буквы слов — заглавные
        StringBuilder b = new StringBuilder();
        boolean up = true;
        for (char c : s.toCharArray()) {
            if (c == ' ') { up = true; b.append(c); continue; }
            b.append(up ? Character.toUpperCase(c) : c);
            up = false;
        }
        return b.toString().replaceAll("\\s+", " ");
    }

    private static ModelEntry.Kind kindFor(String key) {
        String k = key.toLowerCase(Locale.ROOT);
        if (k.equals("hat") || k.endsWith("_hat") || k.startsWith("hat_")) return ModelEntry.Kind.HAT;
        if (k.equals("wing") || k.equals("wings") || k.endsWith("_wing") || k.endsWith("_wings")) return ModelEntry.Kind.WING;
        return ModelEntry.Kind.WEAPON;
    }

    /** Привязка по списку предметов CIT: только топоры → axe, есть мечи → sword и т. п. */
    private static String bindFromItems(String items) {
        String s = items.toLowerCase(Locale.ROOT);
        if (s.contains("sword")) return "sword";
        if (s.contains("_axe")) return "axe";
        if (s.contains("trident")) return "trident";
        if (s.contains("crossbow")) return "crossbow";
        if (s.contains("bow")) return "bow";
        if (s.contains("shield")) return "shield";
        if (s.contains("mace")) return "mace";
        if (s.contains("fishing_rod")) return "fishing_rod";
        return null;
    }

    private static String bindFromName(String key) {
        String k = key.toLowerCase(Locale.ROOT);
        if (k.contains("crossbow")) return "crossbow";
        if (k.contains("bow")) return "bow";
        if (k.contains("shield")) return "shield";
        if (k.contains("fishing") || k.contains("rod")) return "fishing_rod";
        if (k.contains("trident") || k.contains("spear")) return "trident";
        if (k.contains("mace") || k.contains("hammer")) return "mace";
        if (k.contains("axe") && !k.contains("pickaxe")) return "axe";
        return "sword";
    }
}

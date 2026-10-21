package dev.mraz1visual.models;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Один «пак» из папки моделей — zip, папка или отдельный файл — как плоская файловая система в памяти.
 * Zip читается как есть, вложенные zip раскрываются в подпапку с их именем. Обратные слеши в путях
 * (архивы из Windows) приводятся к прямым, поиск — без учёта регистра. Живёт только на время загрузки.
 */
public final class PackFs {
    private static final long MAX_TOTAL = 768L << 20, MAX_ENTRY = 96L << 20;
    private static final int MAX_FILES = 60_000, MAX_DEPTH = 4;

    public final String name;
    private final Map<String, byte[]> files = new LinkedHashMap<>();
    private final Map<String, String> real = new LinkedHashMap<>();
    private long total;

    private PackFs(String name) {
        this.name = name;
    }

    /** Папка, zip или одиночный файл модели. */
    public static PackFs open(Path p) throws IOException {
        String fn = p.getFileName().toString();
        if (Files.isDirectory(p)) {
            PackFs fs = new PackFs(fn);
            try (Stream<Path> s = Files.walk(p)) {
                for (Path f : (Iterable<Path>) s::iterator) {
                    if (!Files.isRegularFile(f)) continue;
                    String rel = p.relativize(f).toString();
                    fs.putFile(rel, Files.readAllBytes(f), 0);
                }
            }
            return fs;
        }
        PackFs fs = new PackFs(stripExt(fn));
        fs.putFile(fn, Files.readAllBytes(p), 0);
        return fs;
    }

    private void putFile(String path, byte[] data, int depth) throws IOException {
        path = normalize(path);
        if (path.isEmpty() || path.startsWith("__macosx/") || path.contains("/__macosx/")) return;
        if (path.toLowerCase(Locale.ROOT).endsWith(".zip") && depth < MAX_DEPTH) {
            String prefix = depth == 0 && files.isEmpty() && real.isEmpty() && path.indexOf('/') < 0 ? "" : stripExt(path) + "/";
            readZip(data, prefix, depth + 1);
            return;
        }
        put(path, data);
    }

    private void put(String path, byte[] data) throws IOException {
        if (files.size() >= MAX_FILES) throw new IOException("слишком много файлов в паке");
        total += data.length;
        if (total > MAX_TOTAL) throw new IOException("пак больше " + (MAX_TOTAL >> 20) + " МБ");
        String key = path.toLowerCase(Locale.ROOT);
        files.putIfAbsent(key, data);
        real.putIfAbsent(key, path);
    }

    private void readZip(byte[] zip, String prefix, int depth) throws IOException {
        try {
            readZip(zip, prefix, depth, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException malformed) {
            // имена в архиве не в UTF-8 (старые архиваторы Windows) — читаем однобайтовой кодировкой
            readZip(zip, prefix, depth, Charset.forName("IBM437"));
        }
    }

    private void readZip(byte[] zip, String prefix, int depth, Charset cs) throws IOException {
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip), cs)) {
            ZipEntry e;
            while ((e = in.getNextEntry()) != null) {
                if (e.isDirectory()) continue;
                byte[] data = readLimited(in);
                putFile(prefix + e.getName(), data, depth);
            }
        }
    }

    private static byte[] readLimited(InputStream in) throws IOException {
        byte[] buf = in.readNBytes((int) MAX_ENTRY + 1);
        if (buf.length > MAX_ENTRY) throw new IOException("файл в архиве больше " + (MAX_ENTRY >> 20) + " МБ");
        return buf;
    }

    public static String normalize(String p) {
        p = p.replace('\\', '/');
        while (p.startsWith("/") || p.startsWith("./")) p = p.substring(p.startsWith("/") ? 1 : 2);
        return p;
    }

    public static String stripExt(String n) {
        int slash = n.lastIndexOf('/');
        int dot = n.lastIndexOf('.');
        return dot > slash + 0 && dot > 0 ? n.substring(0, dot) : n;
    }

    public static String baseName(String path) {
        int slash = path.lastIndexOf('/');
        return stripExt(path.substring(slash + 1));
    }

    // ── поиск ────────────────────────────────────────────────

    public byte[] get(String path) {
        return files.get(normalize(path).toLowerCase(Locale.ROOT));
    }

    /** Исходное написание пути (с регистром) — для подписей. */
    public String realName(String path) {
        return real.getOrDefault(normalize(path).toLowerCase(Locale.ROOT), path);
    }

    /** Все пути (в нижнем регистре). */
    public List<String> paths() {
        return new ArrayList<>(files.keySet());
    }

    public List<String> paths(String suffix) {
        List<String> out = new ArrayList<>();
        for (String k : files.keySet()) if (k.endsWith(suffix)) out.add(k);
        return out;
    }

    /**
     * Текстура по ссылке модели: "item/x", "ns:item/x". Сначала точный путь в assets (в любом месте пака —
     * паки бывают вложены в папки плагинов), потом — тот же файл без подпапки item/, потом — по имени файла.
     */
    public String findTexture(String ref, String hintDir) {
        String ns = "minecraft", p = ref;
        int c = ref.indexOf(':');
        if (c >= 0) { ns = ref.substring(0, c); p = ref.substring(c + 1); }
        p = normalize(p).toLowerCase(Locale.ROOT);
        ns = ns.toLowerCase(Locale.ROOT);
        if (p.endsWith(".png")) p = p.substring(0, p.length() - 4);
        String exact = "assets/" + ns + "/textures/" + p + ".png";
        String noItem = p.startsWith("item/") ? "assets/" + ns + "/textures/" + p.substring(5) + ".png" : null;
        String rel = hintDir == null ? null : (hintDir.isEmpty() ? "" : hintDir + "/") + p + ".png";
        String base = "/" + p.substring(p.lastIndexOf('/') + 1) + ".png";
        String best = null;
        int bestScore = 0;
        for (String k : files.keySet()) {
            int score = 0;
            if (k.equals(exact) || k.endsWith("/" + exact)) score = 5;
            else if (rel != null && k.equals(rel)) score = 4;
            else if (noItem != null && (k.equals(noItem) || k.endsWith("/" + noItem))) score = 3;
            else if (k.endsWith(base) || k.equals(base.substring(1))) score = k.contains("/" + ns + "/") ? 2 : 1;
            if (score > bestScore) { bestScore = score; best = k; if (score == 5) break; }
        }
        return best;
    }

    /** Модель по ссылке parent: "item/x" → assets/ns/models/item/x.json (где угодно в паке). */
    public String findModel(String ref) {
        String ns = "minecraft", p = ref;
        int c = ref.indexOf(':');
        if (c >= 0) { ns = ref.substring(0, c); p = ref.substring(c + 1); }
        String want = ("assets/" + ns + "/models/" + p + ".json").toLowerCase(Locale.ROOT);
        for (String k : files.keySet()) if (k.equals(want) || k.endsWith("/" + want)) return k;
        return null;
    }

    public static String dir(String path) {
        int s = path.lastIndexOf('/');
        return s < 0 ? "" : path.substring(0, s);
    }
}

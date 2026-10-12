package dev.mraz1visual.wallpaper;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Ищет обои Wallpaper Engine: подписки из Мастерской Steam (appid 431960), свои проекты и встроенные,
 * а также любые файлы в config/mraz1visual/wallpapers. Только чтение файлов на диске.
 */
public final class WallpaperScanner {
    public static final Path OWN = FabricLoader.getInstance().getConfigDir().resolve("mraz1visual").resolve("wallpapers");
    private static final Pattern VDF_PATH = Pattern.compile("\"path\"\\s+\"([^\"]+)\"");
    private static final Set<String> VIDEO = Set.of("mp4", "webm", "mkv", "mov", "avi", "m4v", "wmv");
    private static final Set<String> IMAGE = Set.of("jpg", "jpeg", "png", "bmp", "gif", "webp");

    private WallpaperScanner() {}

    public static List<Wallpaper> scan() {
        List<Wallpaper> out = new ArrayList<>();
        Set<String> seen = new java.util.HashSet<>();
        for (Path lib : steamLibraries()) {
            scanProjects(lib.resolve("steamapps/workshop/content/431960"), "Мастерская", out, seen);
            Path we = lib.resolve("steamapps/common/wallpaper_engine/projects");
            scanProjects(we.resolve("myprojects"), "Мои проекты", out, seen);
            scanProjects(we.resolve("defaultprojects"), "Встроенные", out, seen);
        }
        scanOwn(out, seen);
        out.sort(Comparator.comparing((Wallpaper w) -> w.title().toLowerCase(Locale.ROOT)));
        return out;
    }

    /** Папки библиотек Steam: путь из реестра + libraryfolders.vdf + стандартные места. */
    public static List<Path> steamLibraries() {
        Set<Path> roots = new LinkedHashSet<>();
        String reg = registrySteamPath();
        if (reg != null) roots.add(Path.of(reg));
        for (String p : new String[]{"C:/Program Files (x86)/Steam", "C:/Program Files/Steam", "D:/Steam", "D:/SteamLibrary", "E:/SteamLibrary"}) {
            roots.add(Path.of(p));
        }
        String home = System.getProperty("user.home", "");
        roots.add(Path.of(home, ".steam/steam"));
        roots.add(Path.of(home, ".local/share/Steam"));

        Set<Path> libs = new LinkedHashSet<>();
        for (Path root : roots) {
            if (!Files.isDirectory(root)) continue;
            libs.add(norm(root));
            for (Path vdf : new Path[]{root.resolve("steamapps/libraryfolders.vdf"), root.resolve("config/libraryfolders.vdf")}) {
                try {
                    if (!Files.exists(vdf)) continue;
                    Matcher m = VDF_PATH.matcher(Files.readString(vdf, StandardCharsets.UTF_8));
                    while (m.find()) {
                        Path p = Path.of(m.group(1).replace("\\\\", "\\"));
                        if (Files.isDirectory(p)) libs.add(norm(p));
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        return new ArrayList<>(libs);
    }

    private static Path norm(Path p) {
        try {
            return p.toRealPath();
        } catch (Throwable t) {
            return p.toAbsolutePath().normalize();
        }
    }

    private static String registrySteamPath() {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) return null;
        try {
            Process p = new ProcessBuilder("reg", "query", "HKCU\\Software\\Valve\\Steam", "/v", "SteamPath")
                    .redirectErrorStream(true).start();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    int i = line.indexOf("REG_SZ");
                    if (i >= 0) return line.substring(i + 6).trim();
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static void scanProjects(Path dir, String source, List<Wallpaper> out, Set<String> seen) {
        if (!Files.isDirectory(dir)) return;
        try (Stream<Path> s = Files.list(dir)) {
            for (Path folder : (Iterable<Path>) s::iterator) {
                Wallpaper w = project(folder, source);
                if (w != null && seen.add(w.key())) out.add(w);
            }
        } catch (Throwable ignored) {
        }
    }

    /** Разобрать папку с project.json. */
    static Wallpaper project(Path folder, String source) {
        Path pj = folder.resolve("project.json");
        if (!Files.exists(pj)) return null;
        try {
            JsonObject j = JsonParser.parseString(Files.readString(pj, StandardCharsets.UTF_8)).getAsJsonObject();
            String title = str(j, "title");
            if (title.isBlank()) title = folder.getFileName().toString();
            String type = str(j, "type").toLowerCase(Locale.ROOT);
            String file = str(j, "file");
            String prev = str(j, "preview");
            Path preview = prev.isEmpty() ? null : folder.resolve(prev);
            if (preview != null && !Files.exists(preview)) preview = null;
            if (preview == null) preview = firstExisting(folder, "preview.gif", "preview.jpg", "preview.png", "preview.webp");
            String key = folder.toAbsolutePath().normalize().toString();
            Path main = file.isEmpty() ? null : folder.resolve(file);
            boolean hasMain = main != null && Files.exists(main);
            if (type.equals("video") && hasMain) {
                return new Wallpaper(key, title, Wallpaper.Kind.VIDEO, source, main, preview, folder);
            }
            if (type.equals("web") && hasMain) {
                return new Wallpaper(key, title, Wallpaper.Kind.WEB, source, main, preview, folder);
            }
            if (type.equals("scene") || Files.exists(folder.resolve("scene.pkg")) || Files.exists(folder.resolve("scene.json"))) {
                Path m = preview != null ? preview : folder.resolve("scene.pkg");
                return new Wallpaper(key, title, Wallpaper.Kind.SCENE, source, m, preview, folder);
            }
            // программы и прочее в игре не запустить — их превью
            if (preview != null) return new Wallpaper(key, title, Wallpaper.Kind.PREVIEW, source, preview, preview, folder);
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Path firstExisting(Path folder, String... names) {
        for (String n : names) {
            Path p = folder.resolve(n);
            if (Files.exists(p)) return p;
        }
        return null;
    }

    private static String str(JsonObject j, String k) {
        try {
            return j.has(k) && j.get(k).isJsonPrimitive() ? j.get(k).getAsString() : "";
        } catch (Throwable t) {
            return "";
        }
    }

    private static void scanOwn(List<Wallpaper> out, Set<String> seen) {
        try {
            Files.createDirectories(OWN);
        } catch (Throwable ignored) {
        }
        try (Stream<Path> s = Files.list(OWN)) {
            for (Path p : (Iterable<Path>) s::iterator) {
                Wallpaper w;
                if (Files.isDirectory(p)) {
                    w = project(p, "Свои");
                } else {
                    w = fromFile(p, "Свои");
                }
                if (w != null && seen.add(w.key())) out.add(w);
            }
        } catch (Throwable ignored) {
        }
    }

    /** Обои из одного файла (видео, gif или картинка) или из project.json. */
    public static Wallpaper fromFile(Path p, String source) {
        if (p.getFileName().toString().equalsIgnoreCase("project.json")) return project(p.getParent(), source);
        String ext = ext(p);
        String title = p.getFileName().toString();
        int dot = title.lastIndexOf('.');
        if (dot > 0) title = title.substring(0, dot);
        String key = p.toAbsolutePath().normalize().toString();
        if (VIDEO.contains(ext)) return new Wallpaper(key, title, Wallpaper.Kind.VIDEO, source, p, null, null);
        if (IMAGE.contains(ext)) return new Wallpaper(key, title, Wallpaper.Kind.IMAGE, source, p, p, null);
        return null;
    }

    static String ext(Path p) {
        String n = p.getFileName().toString();
        int i = n.lastIndexOf('.');
        return i < 0 ? "" : n.substring(i + 1).toLowerCase(Locale.ROOT);
    }

    /** Wallpaper из сохранённого (после перезапуска игры). */
    public static Wallpaper fromSaved(String key, String title, String kind, String media, String preview, String folder) {
        try {
            Path m = Path.of(media);
            Path f = folder == null || folder.isEmpty() ? null : Path.of(folder);
            if (!Files.exists(m) && (f == null || !Files.exists(f))) return null;
            Path pv = preview == null || preview.isEmpty() ? null : Path.of(preview);
            return new Wallpaper(key, title, Wallpaper.Kind.valueOf(kind), "", m, pv, f);
        } catch (Throwable t) {
            return null;
        }
    }
}

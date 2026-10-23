package dev.mraz1visual.music.player;

import dev.mraz1visual.core.ModuleManager;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Библиотека локальных треков: папка &lt;gameDir&gt;/mraz1visual/music и папки, добавленные в настройках
 * (например, «Музыка» Windows). Сканируется в фоне; исполнители и альбомы — группировкой по тегам.
 */
public final class Library {
    public static final Path DEFAULT = FabricLoader.getInstance().getGameDir().resolve("mraz1visual").resolve("music");

    private static volatile List<Track> tracks = List.of();
    private static volatile boolean scanning;

    private Library() {}

    public static List<Track> tracks() { return tracks; }

    public static boolean scanning() { return scanning; }

    public static Track find(Path p) {
        for (Track t : tracks) if (t.path.equals(p)) return t;
        return null;
    }

    /** Пересканировать все папки (в фоне). */
    public static synchronized void rescan(List<Path> folders) {
        if (scanning) return;
        scanning = true;
        Thread th = new Thread(() -> {
            List<Track> out = new ArrayList<>();
            try {
                Files.createDirectories(DEFAULT);
                List<Path> roots = new ArrayList<>();
                roots.add(DEFAULT);
                for (Path f : folders) if (!roots.contains(f)) roots.add(f);
                java.util.Set<Path> seen = new java.util.HashSet<>();
                for (Path root : roots) {
                    if (!Files.isDirectory(root)) continue;
                    try (Stream<Path> s = Files.walk(root, 6)) {
                        for (Path p : (Iterable<Path>) s::iterator) {
                            if (out.size() >= 20000) break;
                            if (!Files.isRegularFile(p) || !Decoder.supported(p) || !seen.add(p.toAbsolutePath().normalize())) continue;
                            Tags t = Tags.read(p, false);
                            out.add(new Track(p, t.title, t.artist, t.album));
                        }
                    } catch (Exception e) {
                        ModuleManager.LOG.warn("Музыка: папка {} не прочитана", root, e);
                    }
                }
                out.sort(Comparator.comparing((Track t) -> t.artist.toLowerCase()).thenComparing(t -> t.album.toLowerCase()).thenComparing(t -> t.path.toString()));
            } catch (Exception e) {
                ModuleManager.LOG.warn("Музыка: сканирование не удалось", e);
            }
            tracks = List.copyOf(out);
            scanning = false;
        }, "Mraz1Visual music scan");
        th.setDaemon(true);
        th.setPriority(Thread.MIN_PRIORITY);
        th.start();
    }

    public static Map<String, List<Track>> byArtist() {
        Map<String, List<Track>> m = new LinkedHashMap<>();
        for (Track t : tracks) m.computeIfAbsent(t.artist, k -> new ArrayList<>()).add(t);
        return m;
    }

    public static Map<String, List<Track>> byAlbum() {
        Map<String, List<Track>> m = new LinkedHashMap<>();
        for (Track t : tracks) m.computeIfAbsent(t.album + " — " + t.artist, k -> new ArrayList<>()).add(t);
        return m;
    }
}

package dev.mraz1visual.music;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.mraz1visual.core.ModuleManager;
import dev.mraz1visual.music.player.MusicPlayer;
import dev.mraz1visual.music.player.Track;
import dev.mraz1visual.util.TextFiles;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Тексты песен для «Сейчас играет»: свой .lrc рядом с файлом → кэш на диске → LRCLIB (открытый API синхронных текстов).
 * Синхронный текст — строки с таймкодами; нет его — обычный текст; нет и его — «Текст не найден».
 * Всё в фоне с таймаутом, в кадре только чтение готового списка.
 */
public final class Lyrics {
    public record Line(long ms, String text) {}

    private static final Path CACHE = TextFiles.DIR.resolve("lyrics");
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).followRedirects(HttpClient.Redirect.NORMAL).build();
    private static final Pattern TAG = Pattern.compile("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]");
    private static final String UA = "Mraz1Visual (https://github.com/SpeedyEzKv/Mraz1Visual)";

    public static volatile List<Line> lines = List.of();
    public static volatile boolean synced;
    public static volatile String status = "";
    private static volatile String key = "";
    private static volatile int generation;

    private Lyrics() {}

    /** Раз в тик: сменился трек — искать текст. */
    public static void tick() {
        if (!NowPlaying.available()) {
            if (!key.isEmpty()) { key = ""; lines = List.of(); status = ""; }
            return;
        }
        String k = NowPlaying.key();
        if (k.equals(key) || NowPlaying.title().isBlank()) return;
        key = k;
        lines = List.of();
        synced = false;
        status = "Ищу текст…";
        int gen = ++generation;
        String title = NowPlaying.title(), artist = NowPlaying.artist(), album = NowPlaying.album();
        long dur = NowPlaying.duration();
        Track local = NowPlaying.local() ? MusicPlayer.current() : null;
        Thread th = new Thread(() -> load(gen, title, artist, album, dur, local), "Mraz1Visual lyrics");
        th.setDaemon(true);
        th.start();
    }

    /** Индекс текущей строки по позиции (с учётом сдвига, мс) или −1. */
    public static int index(long posMs) {
        List<Line> l = lines;
        if (!synced || l.isEmpty()) return -1;
        int lo = 0, hi = l.size() - 1, ans = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (l.get(mid).ms() <= posMs) { ans = mid; lo = mid + 1; } else hi = mid - 1;
        }
        return ans;
    }

    private static void load(int gen, String title, String artist, String album, long durMs, Track local) {
        String text = null;
        try {
            // 1) свой .lrc рядом с аудиофайлом
            if (local != null) {
                String n = local.path.getFileName().toString();
                Path lrc = local.path.resolveSibling(n.substring(0, n.lastIndexOf('.') < 0 ? n.length() : n.lastIndexOf('.')) + ".lrc");
                if (Files.isRegularFile(lrc)) text = Files.readString(lrc, StandardCharsets.UTF_8);
            }
            // 2) кэш
            Path cache = CACHE.resolve(hash(artist + "|" + title) + ".lrc");
            if (text == null && Files.isRegularFile(cache)) text = Files.readString(cache, StandardCharsets.UTF_8);
            // 3) LRCLIB
            if (text == null) {
                text = fetch(title, artist, album, durMs);
                Files.createDirectories(CACHE);
                Files.writeString(cache, text == null ? "" : text, StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            ModuleManager.LOG.debug("Тексты: {}", e.toString());
        }
        if (gen != generation) return;
        apply(text);
    }

    private static void apply(String text) {
        if (text == null || text.isBlank()) {
            lines = List.of();
            synced = false;
            status = "Текст не найден";
            return;
        }
        List<Line> out = new ArrayList<>();
        boolean timed = false;
        for (String raw : text.split("\\R")) {
            Matcher m = TAG.matcher(raw);
            List<Long> times = new ArrayList<>();
            int end = 0;
            while (m.find() && m.start() == end) {
                long ms = Long.parseLong(m.group(1)) * 60000 + Long.parseLong(m.group(2)) * 1000;
                if (m.group(3) != null) {
                    String f = m.group(3);
                    ms += f.length() == 1 ? Long.parseLong(f) * 100 : f.length() == 2 ? Long.parseLong(f) * 10 : Long.parseLong(f);
                }
                times.add(ms);
                end = m.end();
            }
            // пословные таймкоды <мм:сс.хх> — убираем, показываем строку целиком
            String line = raw.substring(end).replaceAll("<\\d{1,3}:\\d{1,2}(?:[.:]\\d{1,3})?>", "").trim();
            if (raw.matches("^\\[(ar|ti|al|by|offset|length|re|ve):.*")) continue;
            if (!times.isEmpty()) {
                timed = true;
                for (long t : times) out.add(new Line(t, line));
            } else if (!line.isEmpty()) out.add(new Line(-1, line));
        }
        if (timed) {
            out.removeIf(l -> l.ms() < 0);
            out.sort((a, b) -> Long.compare(a.ms(), b.ms()));
        }
        lines = List.copyOf(out);
        synced = timed;
        status = out.isEmpty() ? "Текст не найден" : "";
    }

    /** LRCLIB: точный /api/get, иначе /api/search — первый результат с синхронным текстом. */
    private static String fetch(String title, String artist, String album, long durMs) throws Exception {
        StringBuilder q = new StringBuilder("https://lrclib.net/api/get?track_name=").append(enc(title)).append("&artist_name=").append(enc(artist));
        if (!album.isBlank()) q.append("&album_name=").append(enc(album));
        if (durMs > 0) q.append("&duration=").append(Math.round(durMs / 1000.0));
        HttpResponse<String> r = HTTP.send(HttpRequest.newBuilder(URI.create(q.toString())).header("User-Agent", UA).timeout(Duration.ofSeconds(8)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() == 200) {
            String t = pick(JsonParser.parseString(r.body()).getAsJsonObject());
            if (t != null) return t;
        }
        String s = "https://lrclib.net/api/search?track_name=" + enc(title) + "&artist_name=" + enc(artist);
        r = HTTP.send(HttpRequest.newBuilder(URI.create(s)).header("User-Agent", UA).timeout(Duration.ofSeconds(8)).GET().build(), HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() != 200) return null;
        JsonArray arr = JsonParser.parseString(r.body()).getAsJsonArray();
        String plain = null;
        for (JsonElement e : arr) {
            JsonObject o = e.getAsJsonObject();
            if (o.has("syncedLyrics") && !o.get("syncedLyrics").isJsonNull()) return o.get("syncedLyrics").getAsString();
            if (plain == null && o.has("plainLyrics") && !o.get("plainLyrics").isJsonNull()) plain = o.get("plainLyrics").getAsString();
        }
        return plain;
    }

    private static String pick(JsonObject o) {
        if (o.has("syncedLyrics") && !o.get("syncedLyrics").isJsonNull()) return o.get("syncedLyrics").getAsString();
        if (o.has("plainLyrics") && !o.get("plainLyrics").isJsonNull()) return o.get("plainLyrics").getAsString();
        return null;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String hash(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(s.toLowerCase().getBytes(StandardCharsets.UTF_8))).substring(0, 20);
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }
}

package dev.mraz1visual.market;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.mraz1visual.core.ModuleManager;
import net.fabricmc.loader.api.FabricLoader;

import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Modrinth API для вкладки «Маркет»: поиск модов, ресурспаков и шейдеров, выбор файла под текущую версию игры
 * и установка в папку игры с проверкой sha1. Всё сетевое — в своём фоновом потоке, игра не ждёт.
 * Список установленного — config/mraz1visual/market.json (чтобы показывать «Установлено» и удалять своё).
 */
public final class Modrinth {
    private Modrinth() {}

    private static final String API = "https://api.modrinth.com/v2";
    private static final String UA = "SpeedyEzKv/Mraz1Visual (github.com/SpeedyEzKv/Mraz1Visual)";
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).followRedirects(HttpClient.Redirect.NORMAL).build();
    private static final ExecutorService POOL = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Mraz1Visual Market");
        t.setDaemon(true);
        return t;
    });

    public enum Kind {
        MOD("mod", "mods"), RESOURCEPACK("resourcepack", "resourcepacks"), SHADER("shader", "shaderpacks");

        public final String type, folder;

        Kind(String type, String folder) {
            this.type = type;
            this.folder = folder;
        }
    }

    public record Hit(String id, String slug, String title, String author, String description, long downloads, Kind kind) {}

    public static String gameVersion() {
        return net.minecraft.SharedConstants.getCurrentVersion().id();
    }

    /**
     * Поиск. sort: relevance / downloads / newest / updated. resolution: "" или 16x / 32x / 64x / 128x (ресурспаки).
     */
    public static CompletableFuture<List<Hit>> search(Kind kind, String query, String sort, boolean currentOnly, String resolution, int offset) {
        return CompletableFuture.supplyAsync(() -> {
            StringBuilder facets = new StringBuilder("[[\"project_type:").append(kind.type).append("\"]");
            if (kind == Kind.MOD) facets.append(",[\"categories:fabric\"]");
            if (kind == Kind.SHADER) facets.append(",[\"categories:iris\"]");
            if (currentOnly && kind != Kind.SHADER) facets.append(",[\"versions:").append(gameVersion()).append("\"]");
            if (kind == Kind.RESOURCEPACK && !resolution.isEmpty()) facets.append(",[\"categories:").append(resolution).append("\"]");
            facets.append("]");
            String url = API + "/search?limit=24&offset=" + offset + "&index=" + sort
                    + "&query=" + enc(query) + "&facets=" + enc(facets.toString());
            JsonObject o = get(url).getAsJsonObject();
            List<Hit> out = new ArrayList<>();
            for (JsonElement e : o.getAsJsonArray("hits")) {
                JsonObject h = e.getAsJsonObject();
                out.add(new Hit(str(h, "project_id"), str(h, "slug"), str(h, "title"), str(h, "author"), str(h, "description"),
                        h.has("downloads") ? h.get("downloads").getAsLong() : 0, kind));
            }
            return out;
        }, POOL);
    }

    /** Проект по slug (для подборки Mraz1). */
    public static CompletableFuture<Hit> project(String slug, Kind kind) {
        return CompletableFuture.supplyAsync(() -> {
            JsonObject p = get(API + "/project/" + enc(slug)).getAsJsonObject();
            return new Hit(str(p, "id"), str(p, "slug"), str(p, "title"), "", str(p, "description"),
                    p.has("downloads") ? p.get("downloads").getAsLong() : 0, kind);
        }, POOL);
    }

    /** Поставить последнюю подходящую версию: для модов — Fabric и текущая версия игры. @return имя файла */
    public static CompletableFuture<String> install(Hit hit) {
        return CompletableFuture.supplyAsync(() -> {
            String q = "?game_versions=" + enc("[\"" + gameVersion() + "\"]");
            if (hit.kind() == Kind.MOD) q += "&loaders=" + enc("[\"fabric\"]");
            if (hit.kind() == Kind.SHADER) q = "?loaders=" + enc("[\"iris\"]");
            JsonArray vs = get(API + "/project/" + hit.id() + "/version" + q).getAsJsonArray();
            if (vs.isEmpty()) throw new IllegalStateException("нет версии под " + gameVersion());
            JsonObject v = vs.get(0).getAsJsonObject();
            JsonObject file = null;
            for (JsonElement f : v.getAsJsonArray("files")) {
                if (file == null || f.getAsJsonObject().get("primary").getAsBoolean()) file = f.getAsJsonObject();
            }
            if (file == null) throw new IllegalStateException("у версии нет файлов");
            String name = str(file, "filename").replaceAll("[\\\\/:*?\"<>|]", "_");
            String sha1 = file.getAsJsonObject("hashes").get("sha1").getAsString();
            Path dir = FabricLoader.getInstance().getGameDir().resolve(hit.kind().folder);
            Path tmp = dir.resolve(name + ".part"), dst = dir.resolve(name);
            try {
                Files.createDirectories(dir);
                HttpResponse<InputStream> r = HTTP.send(req(str(file, "url")), HttpResponse.BodyHandlers.ofInputStream());
                if (r.statusCode() != 200) throw new IllegalStateException("HTTP " + r.statusCode());
                MessageDigest md = MessageDigest.getInstance("SHA-1");
                try (InputStream in = r.body(); var out = Files.newOutputStream(tmp)) {
                    byte[] buf = new byte[65536];
                    for (int n; (n = in.read(buf)) > 0; ) {
                        md.update(buf, 0, n);
                        out.write(buf, 0, n);
                    }
                }
                if (!HexFormat.of().formatHex(md.digest()).equalsIgnoreCase(sha1)) {
                    Files.deleteIfExists(tmp);
                    throw new IllegalStateException("файл повреждён (sha1)");
                }
                Files.move(tmp, dst, StandardCopyOption.REPLACE_EXISTING);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException(e.getMessage(), e);
            }
            MarketIndex.put(hit.id(), hit.kind(), name);
            return name;
        }, POOL);
    }

    /** Удалить установленное через Маркет (чужие файлы не трогаем). */
    public static void uninstall(Hit hit) {
        String file = MarketIndex.file(hit.id());
        if (file == null) return;
        try {
            Files.deleteIfExists(FabricLoader.getInstance().getGameDir().resolve(hit.kind().folder).resolve(file));
        } catch (Exception e) {
            ModuleManager.LOG.warn("Market: can't delete {}", file, e);
        }
        MarketIndex.remove(hit.id());
    }

    public static boolean installed(String id) {
        return MarketIndex.installed(id);
    }

    private static HttpRequest req(String url) {
        return HttpRequest.newBuilder(URI.create(url)).header("User-Agent", UA).timeout(Duration.ofSeconds(20)).GET().build();
    }

    private static JsonElement get(String url) {
        try {
            HttpResponse<String> r = HTTP.send(req(url), HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() != 200) throw new IllegalStateException("Modrinth: HTTP " + r.statusCode());
            return JsonParser.parseString(r.body());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("нет связи с Modrinth", e);
        }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String str(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e == null || e.isJsonNull() ? "" : e.getAsString();
    }

    public static String formatDownloads(long n) {
        if (n >= 1_000_000) return String.format(Locale.ROOT, "%.1f млн", n / 1e6).replace('.', ',');
        if (n >= 1_000) return (n / 1000) + " тыс";
        return Long.toString(n);
    }
}

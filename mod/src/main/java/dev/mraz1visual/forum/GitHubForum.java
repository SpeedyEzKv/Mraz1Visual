package dev.mraz1visual.forum;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.mraz1visual.util.TextFiles;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

/**
 * Форум клиента — GitHub Discussions репозитория Mraz1Visual. Вход через GitHub Device Flow:
 * игрок вводит короткий код на github.com/login/device, токен приходит сюда и хранится только на его компьютере
 * (config/mraz1visual/github_token). Ничего не вшито: client_id OAuth-приложения — в config/mraz1visual/github.json.
 */
public final class GitHubForum {
    private GitHubForum() {}

    public static final String REPO_OWNER = "SpeedyEzKv", REPO_NAME = "Mraz1Visual";
    private static final Path CONFIG = TextFiles.DIR.resolve("github.json"), TOKEN = TextFiles.DIR.resolve("github_token");
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();
    private static final ExecutorService POOL = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Mraz1Visual Forum");
        t.setDaemon(true);
        return t;
    });

    public record Topic(String title, String url, String author, String category, int comments, String updated) {}

    public record DeviceCode(String userCode, String verificationUri, String deviceCode, int interval, long expiresAt) {}

    /** client_id OAuth-приложения с включённым Device Flow; пусто — форум недоступен. */
    public static String clientId() {
        // лаунчер передаёт client_id из manifest.json релиза (поле github_client_id)
        String prop = System.getProperty("mraz1visual.github", "").trim();
        if (!prop.isEmpty()) return prop;
        try {
            if (!Files.exists(CONFIG)) {
                Files.createDirectories(CONFIG.getParent());
                Files.writeString(CONFIG, "{\n  \"client_id\": \"\"\n}\n", StandardCharsets.UTF_8);
            }
            JsonObject o = JsonParser.parseString(Files.readString(CONFIG, StandardCharsets.UTF_8)).getAsJsonObject();
            return o.has("client_id") ? o.get("client_id").getAsString().trim() : "";
        } catch (Exception e) {
            return "";
        }
    }

    public static boolean loggedIn() {
        return !token().isEmpty();
    }

    private static String token() {
        try {
            return Files.exists(TOKEN) ? Files.readString(TOKEN, StandardCharsets.UTF_8).trim() : "";
        } catch (Exception e) {
            return "";
        }
    }

    public static void logout() {
        try {
            Files.deleteIfExists(TOKEN);
        } catch (Exception ignored) {
            // нет файла — уже вышли
        }
    }

    /** Шаг 1 Device Flow: получить код для ввода на github.com/login/device. */
    public static CompletableFuture<DeviceCode> start() {
        return CompletableFuture.supplyAsync(() -> {
            JsonObject o = post("https://github.com/login/device/code", "client_id=" + enc(clientId()) + "&scope=" + enc("read:discussion"));
            if (o.has("error")) throw new IllegalStateException(o.get("error_description").getAsString());
            return new DeviceCode(o.get("user_code").getAsString(), o.get("verification_uri").getAsString(), o.get("device_code").getAsString(),
                    o.get("interval").getAsInt(), System.currentTimeMillis() + o.get("expires_in").getAsLong() * 1000);
        }, POOL);
    }

    /** Шаг 2: ждать, пока игрок подтвердит код в браузере; токен — в локальный файл. */
    public static CompletableFuture<Boolean> poll(DeviceCode code) {
        return CompletableFuture.supplyAsync(() -> {
            int interval = Math.max(5, code.interval());
            while (System.currentTimeMillis() < code.expiresAt()) {
                try {
                    Thread.sleep(interval * 1000L);
                } catch (InterruptedException e) {
                    return false;
                }
                JsonObject o = post("https://github.com/login/oauth/access_token", "client_id=" + enc(clientId())
                        + "&device_code=" + enc(code.deviceCode()) + "&grant_type=" + enc("urn:ietf:params:oauth:grant-type:device_code"));
                if (o.has("access_token")) {
                    try {
                        Files.createDirectories(TOKEN.getParent());
                        Files.writeString(TOKEN, o.get("access_token").getAsString(), StandardCharsets.UTF_8);
                    } catch (Exception e) {
                        throw new IllegalStateException("не удалось сохранить вход", e);
                    }
                    return true;
                }
                String err = o.has("error") ? o.get("error").getAsString() : "";
                if (err.equals("slow_down")) interval += 5;
                else if (!err.equals("authorization_pending")) return false;
            }
            return false;
        }, POOL);
    }

    /** Последние обсуждения (GraphQL, нужен вход). */
    public static CompletableFuture<List<Topic>> topics() {
        return CompletableFuture.supplyAsync(() -> {
            String q = "{\"query\":\"query{repository(owner:\\\"" + REPO_OWNER + "\\\",name:\\\"" + REPO_NAME + "\\\"){discussions(first:25,orderBy:{field:UPDATED_AT,direction:DESC}){nodes{title url updatedAt author{login} category{name} comments{totalCount}}}}}\"}";
            try {
                HttpResponse<String> r = HTTP.send(HttpRequest.newBuilder(URI.create("https://api.github.com/graphql"))
                        .header("Authorization", "bearer " + token()).header("User-Agent", "Mraz1Visual")
                        .timeout(Duration.ofSeconds(10)).POST(HttpRequest.BodyPublishers.ofString(q)).build(), HttpResponse.BodyHandlers.ofString());
                if (r.statusCode() == 401) {
                    logout();
                    throw new IllegalStateException("вход устарел — войдите снова");
                }
                JsonObject o = JsonParser.parseString(r.body()).getAsJsonObject();
                List<Topic> out = new ArrayList<>();
                JsonObject repo = o.getAsJsonObject("data").getAsJsonObject("repository");
                if (repo == null) return out;
                JsonArray nodes = repo.getAsJsonObject("discussions").getAsJsonArray("nodes");
                for (JsonElement e : nodes) {
                    JsonObject n = e.getAsJsonObject();
                    String author = n.get("author").isJsonNull() ? "?" : n.getAsJsonObject("author").get("login").getAsString();
                    out.add(new Topic(n.get("title").getAsString(), n.get("url").getAsString(), author,
                            n.getAsJsonObject("category").get("name").getAsString(), n.getAsJsonObject("comments").get("totalCount").getAsInt(),
                            n.get("updatedAt").getAsString().substring(0, 10)));
                }
                return out;
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException("нет связи с GitHub", e);
            }
        }, POOL);
    }

    public static String discussionsUrl() {
        return "https://github.com/" + REPO_OWNER + "/" + REPO_NAME + "/discussions";
    }

    private static JsonObject post(String url, String form) {
        try {
            HttpResponse<String> r = HTTP.send(HttpRequest.newBuilder(URI.create(url)).header("Accept", "application/json")
                    .header("Content-Type", "application/x-www-form-urlencoded").timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(form)).build(), HttpResponse.BodyHandlers.ofString());
            return JsonParser.parseString(r.body()).getAsJsonObject();
        } catch (Exception e) {
            throw new IllegalStateException("нет связи с GitHub", e);
        }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}

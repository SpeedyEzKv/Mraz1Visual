package dev.mraz1visual.account;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Вход через Microsoft по коду устройства (как на телевизорах):
 * Microsoft → Xbox Live → XSTS → Minecraft → профиль.
 * Пароль игрок вводит только на сайте Microsoft, мод его никогда не видит.
 */
public final class MicrosoftAuth {
    private static final String MS = "https://login.microsoftonline.com/consumers/oauth2/v2.0/";
    private static final String SCOPE = "XboxLive.signin offline_access";
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    private MicrosoftAuth() {}

    public record DeviceCode(String deviceCode, String userCode, String verificationUri, int interval, long expiresAt) {}

    public record Result(String name, String uuid, String accessToken, long expiresAt, String refreshToken, String xuid) {}

    public static final class AuthException extends Exception {
        public AuthException(String message) { super(message); }
    }

    public static DeviceCode requestCode(String clientId) throws IOException, InterruptedException, AuthException {
        JsonObject j = postForm(MS + "devicecode", Map.of("client_id", clientId, "scope", SCOPE));
        if (j.has("error")) throw new AuthException(describe(j));
        return new DeviceCode(j.get("device_code").getAsString(), j.get("user_code").getAsString(),
                j.get("verification_uri").getAsString(), j.has("interval") ? j.get("interval").getAsInt() : 5,
                System.currentTimeMillis() + j.get("expires_in").getAsLong() * 1000);
    }

    /** Ждём, пока игрок введёт код на сайте. Проверяет флаг отмены между запросами. */
    public static Result poll(String clientId, DeviceCode code, java.util.function.BooleanSupplier cancelled)
            throws IOException, InterruptedException, AuthException {
        int interval = Math.max(2, code.interval());
        while (System.currentTimeMillis() < code.expiresAt()) {
            Thread.sleep(interval * 1000L);
            if (cancelled.getAsBoolean()) throw new AuthException("Отменено");
            JsonObject j = postForm(MS + "token", Map.of(
                    "grant_type", "urn:ietf:params:oauth:grant-type:device_code",
                    "client_id", clientId,
                    "device_code", code.deviceCode()));
            if (j.has("error")) {
                String err = j.get("error").getAsString();
                if (err.equals("authorization_pending")) continue;
                if (err.equals("slow_down")) {
                    interval += 5;
                    continue;
                }
                throw new AuthException(describe(j));
            }
            return minecraft(j.get("access_token").getAsString(), j.get("refresh_token").getAsString());
        }
        throw new AuthException("Код истёк — попробуй ещё раз");
    }

    /** Тихий повторный вход по сохранённому refresh token. */
    public static Result refresh(String clientId, String refreshToken) throws IOException, InterruptedException, AuthException {
        JsonObject j = postForm(MS + "token", Map.of(
                "grant_type", "refresh_token",
                "client_id", clientId,
                "refresh_token", refreshToken,
                "scope", SCOPE));
        if (j.has("error")) throw new AuthException(describe(j));
        return minecraft(j.get("access_token").getAsString(), j.get("refresh_token").getAsString());
    }

    private static Result minecraft(String msToken, String refresh) throws IOException, InterruptedException, AuthException {
        // Xbox Live
        JsonObject xblReq = new JsonObject();
        JsonObject p = new JsonObject();
        p.addProperty("AuthMethod", "RPS");
        p.addProperty("SiteName", "user.auth.xboxlive.com");
        p.addProperty("RpsTicket", "d=" + msToken);
        xblReq.add("Properties", p);
        xblReq.addProperty("RelyingParty", "http://auth.xboxlive.com");
        xblReq.addProperty("TokenType", "JWT");
        JsonObject xbl = postJson("https://user.auth.xboxlive.com/user/authenticate", xblReq, null);
        String xblToken = xbl.get("Token").getAsString();

        // XSTS
        JsonObject xstsReq = new JsonObject();
        JsonObject p2 = new JsonObject();
        p2.addProperty("SandboxId", "RETAIL");
        JsonArray tokens = new JsonArray();
        tokens.add(xblToken);
        p2.add("UserTokens", tokens);
        xstsReq.add("Properties", p2);
        xstsReq.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
        xstsReq.addProperty("TokenType", "JWT");
        JsonObject xsts = postJson("https://xsts.auth.xboxlive.com/xsts/authorize", xstsReq, null);
        if (xsts.has("XErr")) {
            long code = xsts.get("XErr").getAsLong();
            if (code == 2148916233L) throw new AuthException("У этого аккаунта нет профиля Xbox — создай его на xbox.com");
            if (code == 2148916238L) throw new AuthException("Детский аккаунт — нужно разрешение в семейной группе");
            throw new AuthException("Xbox отказал: " + code);
        }
        String xstsToken = xsts.get("Token").getAsString();
        String uhs = xsts.getAsJsonObject("DisplayClaims").getAsJsonArray("xui").get(0).getAsJsonObject().get("uhs").getAsString();
        String xuid = "";
        var xui = xsts.getAsJsonObject("DisplayClaims").getAsJsonArray("xui").get(0).getAsJsonObject();
        if (xui.has("xid")) xuid = xui.get("xid").getAsString();

        // Minecraft
        JsonObject mcReq = new JsonObject();
        mcReq.addProperty("identityToken", "XBL3.0 x=" + uhs + ";" + xstsToken);
        JsonObject mc = postJson("https://api.minecraftservices.com/authentication/login_with_xbox", mcReq, null);
        if (!mc.has("access_token")) {
            throw new AuthException("Minecraft не принял вход. Если приложение в Azure новое — нужна заявка на доступ (см. README).");
        }
        String mcToken = mc.get("access_token").getAsString();
        long expires = System.currentTimeMillis() + mc.get("expires_in").getAsLong() * 1000;

        // профиль
        HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.minecraftservices.com/minecraft/profile"))
                .header("Authorization", "Bearer " + mcToken).timeout(Duration.ofSeconds(20)).GET().build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() == 404) throw new AuthException("На этом аккаунте не куплен Minecraft: Java Edition");
        JsonObject prof = JsonParser.parseString(resp.body()).getAsJsonObject();
        String id = prof.get("id").getAsString();
        String uuid = id.replaceFirst("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5");
        return new Result(prof.get("name").getAsString(), uuid, mcToken, expires, refresh, xuid);
    }

    // ── http ─────────────────────────────────────────────────

    private static JsonObject postForm(String url, Map<String, String> form) throws IOException, InterruptedException {
        StringJoiner body = new StringJoiner("&");
        form.forEach((k, v) -> body.add(URLEncoder.encode(k, StandardCharsets.UTF_8) + "=" + URLEncoder.encode(v, StandardCharsets.UTF_8)));
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(Duration.ofSeconds(20))
                .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
        return parse(HTTP.send(req, HttpResponse.BodyHandlers.ofString()).body());
    }

    private static JsonObject postJson(String url, JsonObject json, String bearer) throws IOException, InterruptedException {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(20))
                .POST(HttpRequest.BodyPublishers.ofString(json.toString()));
        if (bearer != null) b.header("Authorization", "Bearer " + bearer);
        return parse(HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString()).body());
    }

    private static JsonObject parse(String body) {
        try {
            return JsonParser.parseString(body).getAsJsonObject();
        } catch (Exception e) {
            JsonObject o = new JsonObject();
            o.addProperty("error", "bad_response");
            return o;
        }
    }

    private static String describe(JsonObject j) {
        String err = j.has("error") ? j.get("error").getAsString() : "";
        return switch (err) {
            case "invalid_client", "unauthorized_client" -> "Неверный Client ID или в Azure не включены public client flows";
            case "expired_token" -> "Код истёк — попробуй ещё раз";
            case "authorization_declined" -> "Вход отклонён";
            case "invalid_grant" -> "Сессия устарела — войди заново";
            default -> j.has("error_description") ? j.get("error_description").getAsString() : err;
        };
    }
}

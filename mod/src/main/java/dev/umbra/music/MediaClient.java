package dev.umbra.music;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Связь с лаунчером: «что сейчас играет» на компьютере (Spotify, Яндекс Музыка, браузер…).
 * Лаунчер передаёт порт и токен через -Dumbra.media.port / -Dumbra.media.token.
 * Опрос раз в секунду в фоновом потоке; в кадре — только чтение готовых полей.
 */
public final class MediaClient {
    private static final String PORT = System.getProperty("umbra.media.port");
    private static final String TOKEN = System.getProperty("umbra.media.token", "");
    private static final HttpClient HTTP = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofMillis(500)).build();
    private static final Identifier COVER = Identifier.fromNamespaceAndPath("umbra", "music/cover");

    public static volatile String title = "", artist = "", app = "";
    public static volatile boolean playing;
    public static volatile long position, duration, polledAt;
    public static volatile boolean hasCover;
    private static volatile long coverId = -1;
    private static Thread thread;

    private MediaClient() {}

    public static boolean available() {
        return PORT != null;
    }

    public static Identifier cover() {
        return COVER;
    }

    /** Текущая позиция с учётом времени с последнего опроса. */
    public static long positionNow() {
        if (!playing) return position;
        return Math.min(duration, position + (System.currentTimeMillis() - polledAt));
    }

    public static synchronized void start() {
        if (!available() || thread != null) return;
        thread = new Thread(MediaClient::loop, "Umbra media");
        thread.setDaemon(true);
        thread.start();
    }

    private static void loop() {
        while (true) {
            try {
                poll();
            } catch (Throwable ignored) {
                title = "";
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    private static HttpRequest.Builder req(String path) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + PORT + path))
                .header("X-Umbra-Token", TOKEN).timeout(Duration.ofSeconds(2));
    }

    private static void poll() throws Exception {
        HttpResponse<String> r = HTTP.send(req("/now").GET().build(), HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() != 200) return;
        JsonObject j = JsonParser.parseString(r.body()).getAsJsonObject();
        title = j.get("title").getAsString();
        artist = j.get("artist").getAsString();
        app = j.get("app").getAsString();
        playing = j.get("playing").getAsBoolean();
        position = j.get("position").getAsLong();
        duration = j.get("duration").getAsLong();
        polledAt = System.currentTimeMillis();
        long cid = j.get("cover_id").getAsLong();
        if (cid != coverId) {
            coverId = cid;
            loadCover();
        }
    }

    private static void loadCover() throws Exception {
        HttpResponse<byte[]> r = HTTP.send(req("/cover").GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        if (r.statusCode() != 200 || r.body().length == 0) {
            hasCover = false;
            return;
        }
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(r.body()));
        if (img == null) {
            hasCover = false;
            return;
        }
        int s = 64;
        BufferedImage scaled = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        var g = scaled.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        // квадрат из центра
        int side = Math.min(img.getWidth(), img.getHeight());
        g.drawImage(img, 0, 0, s, s, (img.getWidth() - side) / 2, (img.getHeight() - side) / 2,
                (img.getWidth() + side) / 2, (img.getHeight() + side) / 2, null);
        g.dispose();
        int[] argb = scaled.getRGB(0, 0, s, s, null, 0, s);
        Minecraft.getInstance().execute(() -> {
            NativeImage ni = new NativeImage(s, s, false);
            for (int y = 0; y < s; y++) for (int x = 0; x < s; x++) ni.setPixel(x, y, argb[y * s + x] | 0xFF000000);
            Minecraft.getInstance().getTextureManager().register(COVER, new DynamicTexture(() -> "umbra music cover", ni));
            hasCover = true;
        });
    }

    public static void command(String c) {
        if (!available()) return;
        HTTP.sendAsync(req("/cmd/" + c).GET().build(), HttpResponse.BodyHandlers.discarding());
        if (c.equals("toggle")) playing = !playing;
    }

    /** «Spotify», «Яндекс Музыка», «Браузер» — из идентификатора приложения Windows. */
    public static String appName() {
        String a = app.toLowerCase();
        if (a.contains("spotify")) return "Spotify";
        if (a.contains("yandex")) return a.contains("music") ? "Яндекс Музыка" : "Яндекс Браузер";
        if (a.contains("chrome")) return "Chrome";
        if (a.contains("msedge")) return "Edge";
        if (a.contains("firefox") || a.contains("zen")) return "Браузер";
        if (a.contains("vk")) return "VK";
        return app.isEmpty() ? "" : "Плеер";
    }
}

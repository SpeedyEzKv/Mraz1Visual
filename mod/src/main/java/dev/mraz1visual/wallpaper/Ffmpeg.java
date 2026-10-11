package dev.mraz1visual.wallpaper;

import dev.mraz1visual.core.ModuleManager;
import net.fabricmc.loader.api.FabricLoader;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Locale;
import java.util.zip.GZIPInputStream;

/**
 * ffmpeg для проигрывания видео-обоев. Ищется в папке клиента и в PATH;
 * если его нет — один раз скачивается готовая сборка (~30 МБ) в config/mraz1visual/ffmpeg.
 */
public final class Ffmpeg {
    private static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("mraz1visual").resolve("ffmpeg");
    private static final String BASE = "https://github.com/eugeneware/ffmpeg-static/releases/download/";
    private static final String[] TAGS = {"b6.1.1", "b6.0"};

    private static volatile String exe;
    private static volatile boolean downloading;
    private static volatile float progress;
    private static volatile String error = "";

    private Ffmpeg() {}

    private static boolean windows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static Path local() {
        return DIR.resolve(windows() ? "ffmpeg.exe" : "ffmpeg");
    }

    /** Путь к ffmpeg, если он уже есть (без скачивания). */
    public static String find() {
        if (exe != null) return exe;
        if (Files.isRegularFile(local())) return exe = local().toString();
        String name = windows() ? "ffmpeg.exe" : "ffmpeg";
        String path = System.getenv("PATH");
        if (path != null) {
            for (String dir : path.split(java.io.File.pathSeparator)) {
                try {
                    Path p = Path.of(dir.trim(), name);
                    if (Files.isRegularFile(p)) return exe = p.toString();
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }

    public static boolean downloading() { return downloading; }

    public static float progress() { return progress; }

    public static String error() { return error; }

    /** Скачать в фоне, если ffmpeg не найден. */
    public static synchronized void ensureAsync() {
        if (find() != null || downloading) return;
        downloading = true;
        error = "";
        progress = 0;
        Thread t = new Thread(Ffmpeg::download, "Mraz1Visual ffmpeg download");
        t.setDaemon(true);
        t.start();
    }

    private static String asset() {
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        boolean arm = arch.contains("aarch64") || arch.contains("arm");
        if (windows()) return "ffmpeg-win32-x64.gz";
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac")) return arm ? "ffmpeg-darwin-arm64.gz" : "ffmpeg-darwin-x64.gz";
        return arm ? "ffmpeg-linux-arm64.gz" : "ffmpeg-linux-x64.gz";
    }

    private static void download() {
        try {
            Files.createDirectories(DIR);
            HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS)
                    .connectTimeout(Duration.ofSeconds(20)).build();
            Throwable last = null;
            for (String tag : TAGS) {
                try {
                    HttpResponse<InputStream> r = http.send(HttpRequest.newBuilder(URI.create(BASE + tag + "/" + asset()))
                            .header("User-Agent", "Mraz1Visual").build(), HttpResponse.BodyHandlers.ofInputStream());
                    if (r.statusCode() != 200) throw new IllegalStateException("HTTP " + r.statusCode());
                    long total = r.headers().firstValueAsLong("content-length").orElse(-1);
                    Path tmp = DIR.resolve("ffmpeg.download");
                    try (InputStream raw = r.body();
                         InputStream counted = new java.io.FilterInputStream(raw) {
                             long n;
                             @Override public int read(byte[] b, int off, int len) throws java.io.IOException {
                                 int k = super.read(b, off, len);
                                 if (k > 0 && total > 0) progress = (n += k) / (float) total;
                                 return k;
                             }
                         };
                         InputStream gz = new GZIPInputStream(counted, 1 << 16)) {
                        Files.copy(gz, tmp, StandardCopyOption.REPLACE_EXISTING);
                    }
                    Files.move(tmp, local(), StandardCopyOption.REPLACE_EXISTING);
                    local().toFile().setExecutable(true);
                    exe = local().toString();
                    progress = 1;
                    return;
                } catch (Throwable t) {
                    last = t;
                }
            }
            throw last == null ? new IllegalStateException("no source") : last;
        } catch (Throwable t) {
            error = "Не удалось скачать ffmpeg: " + t.getMessage();
            ModuleManager.LOG.warn("ffmpeg download failed", t);
        } finally {
            downloading = false;
        }
    }
}

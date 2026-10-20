package dev.mraz1visual.wallpaper;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import dev.mraz1visual.core.ModuleManager;
import dev.mraz1visual.core.Modules;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.DataInputStream;
import java.io.EOFException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Проигрывает выбранные обои на фоне меню и GUI.
 *
 * Видео и анимации декодирует ffmpeg (с аппаратным ускорением, если есть) и отдаёт готовые кадры RGBA
 * в pipe; поток-читатель кладёт их в маленький пул буферов, а кадр игры только копирует последний
 * кадр в текстуру. Если обои никто не показывает дольше 3 секунд — ffmpeg останавливается и не ест CPU.
 */
public final class WallpaperPlayer {
    private static final Path SAVE = FabricLoader.getInstance().getConfigDir().resolve("mraz1visual").resolve("wallpaper.json");
    private static final Path LOG = FabricLoader.getInstance().getConfigDir().resolve("mraz1visual").resolve("ffmpeg").resolve("last-error.log");

    private static Wallpaper current;
    private static boolean loaded;

    // видео
    private static int W, H;
    private static DynamicTexture video;
    private static Identifier videoId;
    private static volatile Process proc;
    private static volatile int generation;
    private static volatile long lastShown;
    private static volatile long nextStart;
    private static volatile boolean gotFrame;
    private static volatile String streamError = "";
    private static final AtomicReference<byte[]> latest = new AtomicReference<>();
    private static ArrayBlockingQueue<byte[]> free;

    // картинка / заставка, пока видео стартует
    private static DynamicTexture still;
    private static Identifier stillId;
    private static int stillW, stillH;
    private static volatile BufferedImage pendingStill;
    private static volatile int stillGen;

    // размытая копия для «стекла»: маленькая текстура, растянутая с линейной фильтрацией
    private static final AtomicReference<int[]> blurLatest = new AtomicReference<>();
    private static volatile int[] pendingStillBlur;
    private static volatile int pendingBW, pendingBH;
    private static DynamicTexture videoBlur, stillBlur;
    private static Identifier videoBlurId, stillBlurId;
    private static int vbw, vbh, sbw, sbh, stillBlurW, stillBlurH;
    /** Что было нарисовано последним: видео (true) или картинка (false). */
    private static boolean lastVideo;
    private static int lastSrcW, lastSrcH;

    private static Field pixelPtr;
    private static boolean ptrProbed;
    private static int texCounter;

    private WallpaperPlayer() {}

    // ── выбор и сохранение ───────────────────────────────────

    public static Wallpaper current() {
        load();
        return current;
    }

    public static boolean active() {
        return current() != null;
    }

    public static void select(Wallpaper w) {
        load();
        stop();
        if (webOpen) WebWallpaper.close();
        webOpen = false;
        releaseTextures();
        current = w;
        gotFrame = false;
        streamError = "";
        nextStart = 0;
        save();
        if (w != null) {
            loadStillAsync(w);
            if (needsStream(w)) Ffmpeg.ensureAsync();
        }
    }

    private static void load() {
        if (loaded) return;
        loaded = true;
        try {
            if (!Files.exists(SAVE)) return;
            JsonObject j = JsonParser.parseString(Files.readString(SAVE, StandardCharsets.UTF_8)).getAsJsonObject();
            Wallpaper w = WallpaperScanner.fromSaved(j.get("key").getAsString(), j.get("title").getAsString(),
                    j.get("kind").getAsString(), j.get("media").getAsString(), j.has("preview") ? j.get("preview").getAsString() : "",
                    j.has("folder") ? j.get("folder").getAsString() : "");
            if (w != null) {
                current = w;
                loadStillAsync(w);
            }
        } catch (Throwable t) {
            ModuleManager.LOG.warn("Wallpaper settings broken", t);
        }
    }

    private static void save() {
        try {
            Files.createDirectories(SAVE.getParent());
            if (current == null) {
                Files.deleteIfExists(SAVE);
                return;
            }
            JsonObject j = new JsonObject();
            j.addProperty("key", current.key());
            j.addProperty("title", current.title());
            j.addProperty("kind", current.kind().name());
            j.addProperty("media", current.media().toString());
            j.addProperty("preview", current.preview() == null ? "" : current.preview().toString());
            j.addProperty("folder", current.folder() == null ? "" : current.folder().toString());
            Files.writeString(SAVE, j.toString(), StandardCharsets.UTF_8);
        } catch (Throwable t) {
            ModuleManager.LOG.warn("Can't save wallpaper", t);
        }
    }

    private static boolean sharpScenes() {
        return !Modules.client.wallScene.getId().equals("anim");
    }

    private static boolean webLive(Wallpaper w) {
        return w.kind() == Wallpaper.Kind.WEB && dev.mraz1visual.browser.Browser.available() && !WebWallpaper.failed();
    }

    /** Что идёт через ffmpeg: видео и анимированные превью/картинки. */
    private static boolean needsStream(Wallpaper w) {
        return switch (w.kind()) {
            case VIDEO -> true;
            case SCENE -> !sharpScenes() && Wallpaper.isAnimated(w.media());
            case WEB -> !webLive(w) && Wallpaper.isAnimated(w.media());
            default -> Wallpaper.isAnimated(w.media());
        };
    }

    /** Вызывается каждый тик: закрыть Chromium веб-обоев, если их давно не видно. */
    public static void tick() {
        if (webOpen && System.currentTimeMillis() - lastShown > 5000) {
            WebWallpaper.close();
            webOpen = false;
        }
    }

    private static boolean webOpen;

    /** Строка состояния для экрана выбора. */
    public static String status() {
        Wallpaper w = current();
        if (w == null) return "Сейчас: затмение (стандартный фон)";
        if (webLive(w)) {
            String st = WebWallpaper.stage();
            if (!st.isEmpty()) return st;
        }
        if (w.kind() == Wallpaper.Kind.SCENE && sharpScenes() && sceneLoading) return "Достаю чёткую картинку сцены…";
        if (needsStream(w)) {
            if (Ffmpeg.downloading()) return "Скачиваю ffmpeg для видео… " + Math.round(Ffmpeg.progress() * 100) + "%";
            if (!Ffmpeg.error().isEmpty()) return Ffmpeg.error();
            if (!streamError.isEmpty()) return streamError;
            if (!gotFrame) return "Запускаю: " + w.title() + "…";
        }
        return "Сейчас: " + w.title();
    }

    // ── поток видео ──────────────────────────────────────────

    /** Размер кадра: как у исходника, но не больше выбранного предела (без лишнего растягивания). */
    private static int[] size(Wallpaper w) {
        int cap = switch (Modules.client.wallQuality.getId()) {
            case "1080" -> 1080;
            case "2160" -> 2160;
            default -> 1440;
        };
        int[] src = Ffmpeg.probe(w.kind() == Wallpaper.Kind.VIDEO ? w.media() : w.media());
        int sw = src == null ? 1920 : src[0], sh = src == null ? 1080 : src[1];
        float k = Math.min(1f, Math.min(cap / (float) sh, cap * 16f / 9f / sw));
        int tw = Math.max(2, Math.round(sw * k) & ~1), th = Math.max(2, Math.round(sh * k) & ~1);
        return new int[]{tw, th};
    }

    private static void start(Wallpaper w) {
        String ff = Ffmpeg.find();
        if (ff == null) {
            Ffmpeg.ensureAsync();
            return;
        }
        int[] s = size(w);
        if (video == null || W != s[0] || H != s[1]) {
            releaseVideo();
            W = s[0];
            H = s[1];
            video = new DynamicTexture(() -> "mraz1visual wallpaper", W, H, false);
            videoId = Identifier.fromNamespaceAndPath("mraz1visual", "wallpaper/video_" + (texCounter++));
            Minecraft.getInstance().getTextureManager().register(videoId, video);
        }
        free = new ArrayBlockingQueue<>(3);
        for (int i = 0; i < 3; i++) free.add(new byte[W * H * 4]);
        latest.set(null);
        int gen = ++generation;
        String fpsId = Modules.client.wallFps.getId();
        String vf = "scale=" + W + ":" + H + ":flags=lanczos";
        java.util.List<String> cmd = new java.util.ArrayList<>(java.util.List.of(ff, "-hide_banner", "-loglevel", "error", "-nostdin"));
        if (w.kind() == Wallpaper.Kind.VIDEO) cmd.addAll(java.util.List.of("-hwaccel", "auto"));
        cmd.addAll(java.util.List.of("-stream_loop", "-1", "-re", "-i", w.media().toString(),
                "-an", "-sn", "-vf", vf));
        if (!fpsId.equals("orig")) cmd.addAll(java.util.List.of("-fpsmax", fpsId));
        cmd.addAll(java.util.List.of("-pix_fmt", "rgba", "-f", "rawvideo", "pipe:1"));
        try {
            Files.createDirectories(LOG.getParent());
            ProcessBuilder pb = new ProcessBuilder(cmd).redirectError(LOG.toFile());
            Process p = pb.start();
            proc = p;
            Thread t = new Thread(() -> read(p, gen, W * H * 4), "Mraz1Visual wallpaper");
            t.setDaemon(true);
            t.start();
        } catch (Throwable t) {
            streamError = "Не удалось запустить ffmpeg: " + t.getMessage();
        }
    }

    private static int blurCounter;

    /** Уменьшить кадр в 8 раз (среднее по блокам) и размыть — получится «матовое стекло». */
    static int[] blurFromRgba(byte[] rgba, int w, int h) {
        int f = 8, bw = Math.max(1, w / f), bh = Math.max(1, h / f);
        int[] out = new int[bw * bh];
        for (int by = 0; by < bh; by++) {
            for (int bx = 0; bx < bw; bx++) {
                int r = 0, g = 0, b = 0, n = 0;
                for (int yy = by * f; yy < by * f + f && yy < h; yy += 2) {
                    int o = (yy * w + bx * f) * 4;
                    for (int xx = 0; xx < f && bx * f + xx < w; xx += 2, o += 8) {
                        r += rgba[o] & 0xFF;
                        g += rgba[o + 1] & 0xFF;
                        b += rgba[o + 2] & 0xFF;
                        n++;
                    }
                }
                out[by * bw + bx] = 0xFF000000 | (r / n) << 16 | (g / n) << 8 | (b / n);
            }
        }
        boxBlur(out, bw, bh, 3);
        boxBlur(out, bw, bh, 3);
        return out;
    }

    /** Размытие «коробкой» по горизонтали и вертикали (ARGB, на месте). */
    static void boxBlur(int[] px, int w, int h, int r) {
        int[] tmp = new int[px.length];
        for (int pass = 0; pass < 2; pass++) {
            int[] src = pass == 0 ? px : tmp, dst = pass == 0 ? tmp : px;
            int len = pass == 0 ? w : h, lines = pass == 0 ? h : w;
            for (int l = 0; l < lines; l++) {
                int sr = 0, sg = 0, sb = 0;
                for (int i = -r; i <= r; i++) {
                    int c = src[idx(pass, l, Math.max(0, Math.min(len - 1, i)), w)];
                    sr += c >> 16 & 0xFF;
                    sg += c >> 8 & 0xFF;
                    sb += c & 0xFF;
                }
                int n = 2 * r + 1;
                for (int i = 0; i < len; i++) {
                    dst[idx(pass, l, i, w)] = 0xFF000000 | (sr / n) << 16 | (sg / n) << 8 | (sb / n);
                    int cOut = src[idx(pass, l, Math.max(0, i - r), w)], cIn = src[idx(pass, l, Math.min(len - 1, i + r + 1), w)];
                    sr += (cIn >> 16 & 0xFF) - (cOut >> 16 & 0xFF);
                    sg += (cIn >> 8 & 0xFF) - (cOut >> 8 & 0xFF);
                    sb += (cIn & 0xFF) - (cOut & 0xFF);
                }
            }
        }
    }

    private static int idx(int pass, int line, int i, int w) {
        return pass == 0 ? line * w + i : i * w + line;
    }

    private static void read(Process p, int gen, int frameBytes) {
        try (DataInputStream in = new DataInputStream(new java.io.BufferedInputStream(p.getInputStream(), 1 << 20))) {
            while (generation == gen) {
                if (System.currentTimeMillis() - lastShown > 3000) { // фон не виден — экономим CPU
                    nextStart = 0;
                    break;
                }
                byte[] buf = free.poll(250, java.util.concurrent.TimeUnit.MILLISECONDS);
                if (buf == null) continue;
                in.readFully(buf, 0, frameBytes);
                if (++blurCounter % 2 == 0) blurLatest.set(blurFromRgba(buf, W, H));
                byte[] old = latest.getAndSet(buf);
                if (old != null) free.offer(old);
                gotFrame = true;
            }
        } catch (EOFException e) {
            if (generation == gen) streamError = errorText();
        } catch (Throwable ignored) {
        } finally {
            p.destroy();
            if (proc == p) proc = null;
        }
    }

    private static String errorText() {
        try {
            String s = Files.readString(LOG, StandardCharsets.UTF_8).trim();
            if (!s.isEmpty()) {
                String line = s.lines().reduce((a, b) -> b).orElse(s);
                return "ffmpeg: " + (line.length() > 90 ? line.substring(0, 90) + "…" : line);
            }
        } catch (Throwable ignored) {
        }
        return "Видео не проигрывается";
    }

    public static void stop() {
        generation++;
        Process p = proc;
        if (p != null) p.destroy();
        proc = null;
    }

    // ── картинка ─────────────────────────────────────────────

    private static volatile boolean sceneLoading;

    private static void loadStillAsync(Wallpaper w) {
        boolean scene = w.kind() == Wallpaper.Kind.SCENE && sharpScenes() && w.folder() != null;
        Path src = !Wallpaper.isAnimated(w.media()) ? w.media() : w.preview() != null && !Wallpaper.isAnimated(w.preview()) ? w.preview() : w.preview();
        if (w.kind() == Wallpaper.Kind.SCENE && src != null && src.getFileName().toString().endsWith(".pkg")) src = null;
        Path fsrc = src;
        int gen = ++stillGen;
        sceneLoading = scene;
        Thread t = new Thread(() -> {
            try {
                BufferedImage img = null;
                if (scene) {
                    img = SceneImage.load(w.folder());
                    sceneLoading = false;
                }
                if (img == null && fsrc != null) img = ImageIO.read(fsrc.toFile());
                if (img == null || gen != stillGen) return;
                int max = 3840;
                if (img.getWidth() > max) {
                    int nh = Math.round(img.getHeight() * (max / (float) img.getWidth()));
                    BufferedImage s = new BufferedImage(max, nh, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D g = s.createGraphics();
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    g.drawImage(img, 0, 0, max, nh, null);
                    g.dispose();
                    img = s;
                }
                if (gen == stillGen) {
                    int bw = Math.max(16, img.getWidth() / 10), bh = Math.max(9, img.getHeight() / 10);
                    BufferedImage small = new BufferedImage(bw, bh, BufferedImage.TYPE_INT_RGB);
                    Graphics2D g2 = small.createGraphics();
                    g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                    g2.drawImage(img, 0, 0, bw, bh, null);
                    g2.dispose();
                    int[] px = small.getRGB(0, 0, bw, bh, null, 0, bw);
                    boxBlur(px, bw, bh, 2);
                    boxBlur(px, bw, bh, 2);
                    pendingBW = bw;
                    pendingBH = bh;
                    pendingStillBlur = px;
                    pendingStill = img;
                }
            } catch (Throwable ignored) {
            } finally {
                if (gen == stillGen) sceneLoading = false;
            }
        }, "Mraz1Visual wallpaper image");
        t.setDaemon(true);
        t.start();
    }

    /** Перечитать картинку (сменили режим сцен). */
    public static void reload() {
        Wallpaper w = current();
        stop();
        if (w != null) {
            gotFrame = false;
            streamError = "";
            loadStillAsync(w);
        }
    }

    private static void uploadStill() {
        BufferedImage img = pendingStill;
        if (img == null) return;
        pendingStill = null;
        int[] blur = pendingStillBlur;
        if (blur != null) {
            Identifier[] id = {stillBlurId};
            stillBlur = uploadSmall(stillBlur, id, blur, pendingBW, pendingBH, sbw, sbh);
            stillBlurId = id[0];
            sbw = pendingBW;
            sbh = pendingBH;
            pendingStillBlur = null;
        }
        if (still != null) Minecraft.getInstance().getTextureManager().release(stillId);
        stillW = img.getWidth();
        stillH = img.getHeight();
        NativeImage ni = new NativeImage(stillW, stillH, false);
        int[] row = new int[stillW];
        for (int y = 0; y < stillH; y++) {
            img.getRGB(0, y, stillW, 1, row, 0, stillW);
            for (int x = 0; x < stillW; x++) ni.setPixel(x, y, row[x] | 0xFF000000);
        }
        still = new DynamicTexture(() -> "mraz1visual wallpaper image", ni);
        stillId = Identifier.fromNamespaceAndPath("mraz1visual", "wallpaper/still_" + (texCounter++));
        Minecraft.getInstance().getTextureManager().register(stillId, still);
    }

    private static DynamicTexture uploadSmall(DynamicTexture tex, Identifier[] id, int[] px, int w, int h, int curW, int curH) {
        if (tex == null || curW != w || curH != h) {
            if (tex != null) Minecraft.getInstance().getTextureManager().release(id[0]);
            tex = new DynamicTexture(() -> "mraz1visual glass", w, h, false);
            id[0] = Identifier.fromNamespaceAndPath("mraz1visual", "wallpaper/glass_" + (texCounter++));
            Minecraft.getInstance().getTextureManager().register(id[0], tex);
        }
        NativeImage img = tex.getPixels();
        if (img == null) return tex;
        for (int y = 0, i = 0; y < h; y++) for (int x = 0; x < w; x++, i++) img.setPixel(x, y, px[i]);
        tex.upload();
        return tex;
    }

    private static void releaseVideo() {
        if (video != null) Minecraft.getInstance().getTextureManager().release(videoId);
        video = null;
    }

    private static void releaseTextures() {
        releaseVideo();
        if (videoBlur != null) Minecraft.getInstance().getTextureManager().release(videoBlurId);
        if (stillBlur != null) Minecraft.getInstance().getTextureManager().release(stillBlurId);
        videoBlur = stillBlur = null;
        vbw = vbh = sbw = sbh = 0;
        blurLatest.set(null);
        if (still != null) Minecraft.getInstance().getTextureManager().release(stillId);
        still = null;
        pendingStill = null;
        stillGen++;
    }

    // ── загрузка кадра в текстуру ────────────────────────────

    private static long pointer(NativeImage img) {
        if (!ptrProbed) {
            ptrProbed = true;
            try {
                long size = (long) img.getWidth() * img.getHeight() * 4;
                for (Field f : NativeImage.class.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers()) || f.getType() != long.class) continue;
                    f.setAccessible(true);
                    long v = f.getLong(img);
                    if (v == size || v == 0) continue;
                    // проверка: пиксель, записанный через API, должен читаться по этому адресу
                    img.setPixel(0, 0, 0xFF123456);
                    if (org.lwjgl.system.MemoryUtil.memGetInt(v) == 0xFF563412) {
                        pixelPtr = f;
                        break;
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        try {
            return pixelPtr == null ? 0 : pixelPtr.getLong(img);
        } catch (Throwable t) {
            return 0;
        }
    }

    private static void uploadFrame(byte[] rgba) {
        NativeImage img = video.getPixels();
        if (img == null) return;
        long ptr = pointer(img);
        if (ptr != 0) {
            org.lwjgl.system.MemoryUtil.memByteBuffer(ptr, W * H * 4).put(rgba, 0, W * H * 4);
        } else {
            for (int y = 0, i = 0; y < H; y++) {
                for (int x = 0; x < W; x++, i += 4) {
                    img.setPixel(x, y, 0xFF000000 | (rgba[i] & 0xFF) << 16 | (rgba[i + 1] & 0xFF) << 8 | (rgba[i + 2] & 0xFF));
                }
            }
        }
        video.upload();
    }

    // ── рисование ────────────────────────────────────────────

    /**
     * Нарисовать обои на весь прямоугольник (обрезка по краям, без полос).
     * @return false — обоев нет (тогда рисуйте свой фон)
     */
    public static boolean render(GuiGraphics g, int x, int y, int w, int h) {
        return render(g, x, y, w, h, 0, Modules.client.wallDim.get());
    }

    /**
     * То же, но со скруглёнными углами радиуса r и своим затемнением (в процентах).
     */
    public static boolean render(GuiGraphics g, int x, int y, int w, int h, int r, double dimPercent) {
        Wallpaper wp = current();
        if (wp == null) return false;
        lastShown = System.currentTimeMillis();
        try {
            uploadStill();
            boolean drew = false;
            if (webLive(wp)) {
                int[] s = webSize();
                com.mojang.blaze3d.textures.GpuTextureView view = WebWallpaper.view(wp.media().toUri().toString(), s[0], s[1]);
                webOpen = true;
                if (view != null) {
                    float[] uv = cover(w, h, s[0], s[1]);
                    blitRounded(g, view, x, y, w, h, r, uv);
                    drew = true;
                }
            } else if (needsStream(wp)) {
                if (proc == null && System.currentTimeMillis() >= nextStart && streamError.isEmpty()) {
                    nextStart = System.currentTimeMillis() + 2000;
                    start(wp);
                }
                byte[] f = latest.getAndSet(null);
                if (f != null && video != null) {
                    if (f.length == W * H * 4) uploadFrame(f);
                    free.offer(f);
                }
                int[] bl = blurLatest.getAndSet(null);
                if (bl != null) {
                    int bw = Math.max(1, W / 8), bh = Math.max(1, H / 8);
                    if (bl.length == bw * bh) {
                        Identifier[] id = {videoBlurId};
                        videoBlur = uploadSmall(videoBlur, id, bl, bw, bh, vbw, vbh);
                        videoBlurId = id[0];
                        vbw = bw;
                        vbh = bh;
                    }
                }
                if (gotFrame && video != null) {
                    float[] uv = cover(w, h, W, H);
                    blitRounded(g, video.getTextureView(), x, y, w, h, r, uv);
                    drew = true;
                    lastVideo = true;
                    lastSrcW = W;
                    lastSrcH = H;
                }
            }
            if (!drew && still != null) {
                float[] uv = cover(w, h, stillW, stillH);
                blitRounded(g, still.getTextureView(), x, y, w, h, r, uv);
                drew = true;
                lastVideo = false;
                lastSrcW = stillW;
                lastSrcH = stillH;
            }
            if (drew) {
                glassWin[0] = x;
                glassWin[1] = y;
                glassWin[2] = w;
                glassWin[3] = h;
            }
            int dim = (int) Math.round(dimPercent * 2.55);
            if (!drew) dim = 255;
            if (dim > 0) {
                int c = Math.min(255, dim) << 24 | 0x050508;
                if (r <= 0) g.fill(x, y, x + w, y + h, c);
                else roundedFill(g, x, y, w, h, r, c);
            }
            return true;
        } catch (Throwable t) {
            ModuleManager.LOG.warn("Wallpaper render failed", t);
            streamError = "Ошибка показа обоев";
            stop();
            return false;
        }
    }

    /** Кадр обоев для неба: текстура (зарегистрирована в TextureManager) и её размер. */
    public record SkyFrame(Identifier id, int w, int h) {}

    /**
     * Подготовить текущий кадр для Custom Sky, ничего не рисуя: запускает ffmpeg, забирает свежий кадр,
     * грузит картинку. Веб-обои в небе показываются картинкой-превью.
     * @return null — обоев нет или кадр ещё не готов
     */
    public static SkyFrame skyFrame() {
        Wallpaper wp = current();
        if (wp == null) return null;
        lastShown = System.currentTimeMillis();
        try {
            uploadStill();
            if (needsStream(wp)) {
                if (proc == null && System.currentTimeMillis() >= nextStart && streamError.isEmpty()) {
                    nextStart = System.currentTimeMillis() + 2000;
                    start(wp);
                }
                byte[] f = latest.getAndSet(null);
                if (f != null && video != null) {
                    if (f.length == W * H * 4) uploadFrame(f);
                    free.offer(f);
                }
                if (gotFrame && video != null && videoId != null) {
                    smooth(video);
                    return new SkyFrame(videoId, W, H);
                }
            }
            if (still != null && stillId != null) {
                smooth(still);
                return new SkyFrame(stillId, stillW, stillH);
            }
        } catch (Throwable t) {
            ModuleManager.LOG.warn("Sky wallpaper failed", t);
        }
        return null;
    }

    /** Линейная фильтрация у самой текстуры — иначе в мире (через RenderType) она рисуется «пикселями». */
    private static void smooth(DynamicTexture t) {
        try {
            ((dev.mraz1visual.mixin.AbstractTextureAccessor) t).mraz1visual$setSampler(
                    com.mojang.blaze3d.systems.RenderSystem.getSamplerCache().getClampToEdge(com.mojang.blaze3d.textures.FilterMode.LINEAR));
        } catch (Throwable ignored) {
            // миксин не применился — останется стандартная фильтрация
        }
    }

    private static final int[] glassWin = new int[4];

    /**
     * Размытые обои под стеклянной панелью: тот же кусок картинки, что лежит под прямоугольником,
     * но размытый. Координаты окна берутся из последнего render(). mapDy — сдвиг прокрутки
     * (рисуем в (x, y), а картинку берём как для (x, y + mapDy)).
     * @return false — размывать нечего (обоев нет или веб-обои)
     */
    public static boolean glass(GuiGraphics g, int x, int y, int w, int h, int r, int mapDy) {
        DynamicTexture t = lastVideo ? videoBlur : stillBlur;
        if (current() == null || t == null || glassWin[2] <= 0 || lastSrcW <= 0) return false;
        float[] uv = cover(glassWin[2], glassWin[3], lastSrcW, lastSrcH);
        float du = (uv[1] - uv[0]) / glassWin[2], dv = (uv[3] - uv[2]) / glassWin[3];
        int sy = y + mapDy;
        float[] sub = {uv[0] + (x - glassWin[0]) * du, uv[0] + (x + w - glassWin[0]) * du,
                uv[2] + (sy - glassWin[1]) * dv, uv[2] + (sy + h - glassWin[1]) * dv};
        blitRounded(g, t.getTextureView(), x, y, w, h, r, sub);
        return true;
    }

    private static int[] webSize() {
        int cap = switch (Modules.client.wallQuality.getId()) {
            case "1080" -> 1080;
            case "2160" -> 2160;
            default -> 1440;
        };
        var win = Minecraft.getInstance().getWindow();
        int sh = Math.min(cap, Math.max(360, win.getHeight()));
        int sw = Math.round(sh * Math.max(1f, win.getWidth() / (float) Math.max(1, win.getHeight())));
        return new int[]{sw, sh};
    }

    /** Полоски по высоте у скруглённых краёв — так картинка не вылезает за углы окна. */
    private static int inset(int r, int row) {
        double dy = r - row - 0.5;
        return (int) Math.ceil(r - Math.sqrt(Math.max(0, (double) r * r - dy * dy)));
    }

    private static void blitRounded(GuiGraphics g, com.mojang.blaze3d.textures.GpuTextureView view, int x, int y, int w, int h, int r, float[] uv) {
        if (view == null) return;
        if (r <= 0 || h < 2 * r) {
            blitView(g, view, x, y, w, h, uv[0], uv[1], uv[2], uv[3]);
            return;
        }
        float du = (uv[1] - uv[0]) / w, dv = (uv[3] - uv[2]) / h;
        for (int i = 0; i < r; i++) {
            int in = inset(r, i);
            blitView(g, view, x + in, y + i, w - 2 * in, 1, uv[0] + in * du, uv[1] - in * du, uv[2] + i * dv, uv[2] + (i + 1) * dv);
            int yb = h - 1 - i;
            blitView(g, view, x + in, y + yb, w - 2 * in, 1, uv[0] + in * du, uv[1] - in * du, uv[2] + yb * dv, uv[2] + (yb + 1) * dv);
        }
        blitView(g, view, x, y + r, w, h - 2 * r, uv[0], uv[1], uv[2] + r * dv, uv[2] + (h - r) * dv);
    }

    private static void roundedFill(GuiGraphics g, int x, int y, int w, int h, int r, int c) {
        if (h < 2 * r) {
            g.fill(x, y, x + w, y + h, c);
            return;
        }
        for (int i = 0; i < r; i++) {
            int in = inset(r, i);
            g.fill(x + in, y + i, x + w - in, y + i + 1, c);
            g.fill(x + in, y + h - 1 - i, x + w - in, y + h - i, c);
        }
        g.fill(x, y + r, x + w, y + h - r, c);
    }

    private static float[] cover(int w, int h, int tw, int th) {
        float scale = Math.max(w / (float) tw, h / (float) th);
        float sw = w / scale, sh = h / scale;
        float u0 = (tw - sw) / 2f / tw, v0 = (th - sh) / 2f / th;
        return new float[]{u0, 1f - u0, v0, 1f - v0};
    }

    /** Текстура с линейной фильтрацией — без «пикселей» при растяжении. */
    public static void blitLinear(GuiGraphics g, DynamicTexture t, int x, int y, int w, int h, float u0, float u1, float v0, float v1) {
        blitView(g, t.getTextureView(), x, y, w, h, u0, u1, v0, v1);
    }

    private static void blitView(GuiGraphics g, com.mojang.blaze3d.textures.GpuTextureView view, int x, int y, int w, int h, float u0, float u1, float v0, float v1) {
        if (view == null || w <= 0 || h <= 0) return;
        g.guiRenderState.submitGuiElement(new net.minecraft.client.gui.render.state.BlitRenderState(
                RenderPipelines.GUI_TEXTURED,
                net.minecraft.client.gui.render.TextureSetup.singleTexture(view,
                        com.mojang.blaze3d.systems.RenderSystem.getSamplerCache().getClampToEdge(com.mojang.blaze3d.textures.FilterMode.LINEAR)),
                new org.joml.Matrix3x2f(g.pose()),
                x, y, x + w, y + h, u0, u1, v0, v1, 0xFFFFFFFF, g.scissorStack.peek()));
    }
}

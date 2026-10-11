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
                    j.get("kind").getAsString(), j.get("media").getAsString(), j.has("preview") ? j.get("preview").getAsString() : "");
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
            Files.writeString(SAVE, j.toString(), StandardCharsets.UTF_8);
        } catch (Throwable t) {
            ModuleManager.LOG.warn("Can't save wallpaper", t);
        }
    }

    private static boolean needsStream(Wallpaper w) {
        return w.kind() == Wallpaper.Kind.VIDEO || Wallpaper.isAnimated(w.media());
    }

    /** Строка состояния для экрана выбора. */
    public static String status() {
        Wallpaper w = current();
        if (w == null) return "Сейчас: затмение (стандартный фон)";
        if (needsStream(w)) {
            if (Ffmpeg.downloading()) return "Скачиваю ffmpeg для видео… " + Math.round(Ffmpeg.progress() * 100) + "%";
            if (!Ffmpeg.error().isEmpty()) return Ffmpeg.error();
            if (!streamError.isEmpty()) return streamError;
            if (!gotFrame) return "Запускаю: " + w.title() + "…";
        }
        return "Сейчас: " + w.title();
    }

    // ── поток видео ──────────────────────────────────────────

    private static int[] size() {
        String q = Modules.client.wallQuality.getId();
        return switch (q) {
            case "720" -> new int[]{1280, 720};
            case "1440" -> new int[]{2560, 1440};
            default -> new int[]{1920, 1080};
        };
    }

    private static void start(Wallpaper w) {
        String ff = Ffmpeg.find();
        if (ff == null) {
            Ffmpeg.ensureAsync();
            return;
        }
        int[] s = size();
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
        int fps = Modules.client.wallFps.getId().equals("60") ? 60 : 30;
        String vf = "scale=" + W + ":" + H + ":force_original_aspect_ratio=increase:flags=bicubic,crop=" + W + ":" + H;
        java.util.List<String> cmd = new java.util.ArrayList<>(java.util.List.of(ff, "-hide_banner", "-loglevel", "error", "-nostdin"));
        if (w.kind() == Wallpaper.Kind.VIDEO) cmd.addAll(java.util.List.of("-hwaccel", "auto"));
        cmd.addAll(java.util.List.of("-stream_loop", "-1", "-re", "-i", w.media().toString(),
                "-an", "-sn", "-vf", vf, "-fpsmax", Integer.toString(fps), "-pix_fmt", "rgba", "-f", "rawvideo", "pipe:1"));
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

    private static void loadStillAsync(Wallpaper w) {
        Path src = !Wallpaper.isAnimated(w.media()) ? w.media() : w.preview() != null ? w.preview() : null;
        if (src == null) return;
        int gen = ++stillGen;
        Thread t = new Thread(() -> {
            try {
                BufferedImage img = ImageIO.read(src.toFile());
                if (img == null || gen != stillGen) return;
                int max = 2560;
                if (img.getWidth() > max) {
                    int nh = Math.round(img.getHeight() * (max / (float) img.getWidth()));
                    BufferedImage s = new BufferedImage(max, nh, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D g = s.createGraphics();
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    g.drawImage(img, 0, 0, max, nh, null);
                    g.dispose();
                    img = s;
                }
                if (gen == stillGen) pendingStill = img;
            } catch (Throwable ignored) {
            }
        }, "Mraz1Visual wallpaper image");
        t.setDaemon(true);
        t.start();
    }

    private static void uploadStill() {
        BufferedImage img = pendingStill;
        if (img == null) return;
        pendingStill = null;
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

    private static void releaseVideo() {
        if (video != null) Minecraft.getInstance().getTextureManager().release(videoId);
        video = null;
    }

    private static void releaseTextures() {
        releaseVideo();
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
     * @return false — обоев нет или они ещё не готовы (тогда рисуйте свой фон)
     */
    public static boolean render(GuiGraphics g, int x, int y, int w, int h) {
        Wallpaper wp = current();
        if (wp == null) return false;
        lastShown = System.currentTimeMillis();
        try {
            uploadStill();
            boolean drew = false;
            if (needsStream(wp)) {
                if (proc == null && System.currentTimeMillis() >= nextStart && streamError.isEmpty()) {
                    nextStart = System.currentTimeMillis() + 2000;
                    start(wp);
                }
                byte[] f = latest.getAndSet(null);
                if (f != null && video != null) {
                    uploadFrame(f);
                    free.offer(f);
                }
                if (gotFrame && video != null) {
                    float[] uv = cover(w, h, W, H);
                    blitLinear(g, video, x, y, w, h, uv[0], uv[1], uv[2], uv[3]);
                    drew = true;
                }
            }
            if (!drew && still != null) {
                float[] uv = cover(w, h, stillW, stillH);
                blitLinear(g, still, x, y, w, h, uv[0], uv[1], uv[2], uv[3]);
                drew = true;
            }
            if (!drew) g.fill(x, y, x + w, y + h, 0xFF050508);
            int dim = (int) Math.round(Modules.client.wallDim.get() * 2.55);
            if (dim > 0) g.fill(x, y, x + w, y + h, dim << 24);
            return true;
        } catch (Throwable t) {
            ModuleManager.LOG.warn("Wallpaper render failed", t);
            streamError = "Ошибка показа обоев";
            stop();
            return false;
        }
    }

    private static float[] cover(int w, int h, int tw, int th) {
        float scale = Math.max(w / (float) tw, h / (float) th);
        float sw = w / scale, sh = h / scale;
        float u0 = (tw - sw) / 2f / tw, v0 = (th - sh) / 2f / th;
        return new float[]{u0, 1f - u0, v0, 1f - v0};
    }

    /** Текстура с линейной фильтрацией — без «пикселей» при растяжении. */
    public static void blitLinear(GuiGraphics g, DynamicTexture t, int x, int y, int w, int h, float u0, float u1, float v0, float v1) {
        com.mojang.blaze3d.textures.GpuTextureView view = t.getTextureView();
        if (view == null) return;
        g.guiRenderState.submitGuiElement(new net.minecraft.client.gui.render.state.BlitRenderState(
                RenderPipelines.GUI_TEXTURED,
                net.minecraft.client.gui.render.TextureSetup.singleTexture(view,
                        com.mojang.blaze3d.systems.RenderSystem.getSamplerCache().getClampToEdge(com.mojang.blaze3d.textures.FilterMode.LINEAR)),
                new org.joml.Matrix3x2f(g.pose()),
                x, y, x + w, y + h, u0, u1, v0, v1, 0xFFFFFFFF, g.scissorStack.peek()));
    }
}

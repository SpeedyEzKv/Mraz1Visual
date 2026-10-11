package dev.mraz1visual.ui.menu;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import dev.mraz1visual.core.ModuleManager;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Видео-фон меню. Кадры (JPEG) лежат в папке игры: game/mraz1visual/video — их один раз скачивает лаунчер
 * (60 кадров/с, 720p или 1080p). Пока кадров нет — показывается заставка-кадр из jar.
 *
 * Скорость: кадры декодируются заранее в нескольких потоках (буфер на ~0,3 с вперёд),
 * в кадре игры — одно копирование памяти в текстуру и плавное перетекание к следующему кадру,
 * поэтому видео плавное на любом FPS монитора.
 */
public final class VideoBackground {
    private static final Path DIR = FabricLoader.getInstance().getGameDir().resolve("mraz1visual").resolve("video");
    private static final Identifier[] TEX = {
            Identifier.fromNamespaceAndPath("mraz1visual", "video/bg_a"),
            Identifier.fromNamespaceAndPath("mraz1visual", "video/bg_b")};
    private static final Identifier POSTER = Identifier.fromNamespaceAndPath("mraz1visual", "video/poster");
    private static final int AHEAD = 12;
    private static final ExecutorService DECODER = Executors.newFixedThreadPool(
            Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors() - 2)), r -> {
                Thread t = new Thread(r, "Mraz1Visual video");
                t.setDaemon(true);
                return t;
            });
    private static final long START = System.nanoTime();

    private static int frames, fps = 30, W, H;
    private static boolean probed, haveVideo, failed;
    private static final DynamicTexture[] tex = new DynamicTexture[2];
    private static final int[] slotFrame = {-1, -1};
    private static final ConcurrentHashMap<Integer, int[]> decoded = new ConcurrentHashMap<>();
    private static final java.util.Set<Integer> inFlight = ConcurrentHashMap.newKeySet();
    private static DynamicTexture poster;
    private static int posterW = 1280, posterH = 720;
    private static boolean posterFailed;
    private static Field pixelPtr;
    private static boolean ptrProbed;

    private VideoBackground() {}

    /** Найти видео в папке игры: video.json (fps, frames) и размеры по первому кадру. */
    private static void probe() {
        probed = true;
        try {
            Path meta = DIR.resolve("video.json");
            if (!Files.exists(meta)) return;
            JsonObject j = JsonParser.parseString(Files.readString(meta)).getAsJsonObject();
            frames = j.get("frames").getAsInt();
            fps = j.get("fps").getAsInt();
            BufferedImage first = ImageIO.read(DIR.resolve(name(0)).toFile());
            W = first.getWidth();
            H = first.getHeight();
            haveVideo = frames > 1 && W > 0;
        } catch (Throwable t) {
            ModuleManager.LOG.warn("Menu video not found or broken", t);
        }
    }

    private static String name(int i) {
        return String.format("f%04d.jpg", i + 1);
    }

    private static void requestDecode(int index) {
        if (decoded.containsKey(index) || !inFlight.add(index)) return;
        DECODER.execute(() -> {
            try {
                BufferedImage img = ImageIO.read(DIR.resolve(name(index)).toFile());
                int[] px = new int[W * H];
                // в порядок байт текстуры: RGBA в памяти = 0xAABBGGRR как int
                if (img.getType() == BufferedImage.TYPE_3BYTE_BGR && img.getWidth() == W && img.getHeight() == H) {
                    byte[] b = ((java.awt.image.DataBufferByte) img.getRaster().getDataBuffer()).getData();
                    for (int i = 0, j = 0; i < px.length; i++, j += 3) {
                        px[i] = 0xFF000000 | (b[j] & 0xFF) << 16 | (b[j + 1] & 0xFF) << 8 | (b[j + 2] & 0xFF);
                    }
                } else {
                    img.getRGB(0, 0, Math.min(W, img.getWidth()), Math.min(H, img.getHeight()), px, 0, W);
                    for (int i = 0; i < px.length; i++) {
                        int c = px[i];
                        px[i] = 0xFF000000 | (c & 0xFF) << 16 | (c & 0xFF00) | (c >> 16 & 0xFF);
                    }
                }
                decoded.put(index, px);
            } catch (Throwable t) {
                ModuleManager.LOG.warn("Video frame {} failed", index, t);
            } finally {
                inFlight.remove(index);
            }
        });
    }

    /** Адрес пикселей NativeImage (поле long, которое не равно размеру в байтах). */
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
                    int got = org.lwjgl.system.MemoryUtil.memGetInt(v);
                    if (got == 0xFF563412) { // ARGB 0x123456 → в памяти R,G,B,A
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

    private static void upload(int slot, int[] abgr, int frame) {
        NativeImage img = tex[slot].getPixels();
        if (img == null) return;
        long ptr = pointer(img);
        if (ptr != 0) {
            org.lwjgl.system.MemoryUtil.memIntBuffer(ptr, W * H).put(abgr, 0, W * H);
        } else {
            for (int y = 0, i = 0; y < H; y++) {
                for (int x = 0; x < W; x++, i++) {
                    int c = abgr[i]; // обратно в ARGB для setPixel
                    img.setPixel(x, y, 0xFF000000 | (c & 0xFF) << 16 | (c & 0xFF00) | (c >> 16 & 0xFF));
                }
            }
        }
        tex[slot].upload();
        slotFrame[slot] = frame;
    }

    private static int slotOf(int frame) {
        return slotFrame[0] == frame ? 0 : slotFrame[1] == frame ? 1 : -1;
    }

    private static int drawA = -1, drawB = -1;
    private static float mix;

    private static boolean update() {
        if (!probed) probe();
        if (!haveVideo || failed) return false;
        if (tex[0] == null) {
            for (int i = 0; i < 2; i++) {
                tex[i] = new DynamicTexture(() -> "mraz1visual video", W, H, false);
                Minecraft.getInstance().getTextureManager().register(TEX[i], tex[i]);
            }
        }
        double f = (System.nanoTime() - START) / 1e9 * fps;
        int cur = (int) (Math.floor(f) % frames), next = (cur + 1) % frames;
        mix = (float) (f - Math.floor(f));
        // заранее декодируем кадры вперёд, старые выбрасываем
        for (int i = 0; i < AHEAD; i++) requestDecode((cur + i) % frames);
        decoded.keySet().removeIf(k -> {
            int d = Math.floorMod(k - cur, frames);
            return d >= AHEAD + 2;
        });
        // загрузить в текстуры текущий и следующий кадр
        if (slotOf(cur) < 0) {
            int[] px = decoded.get(cur);
            if (px != null) upload(slotOf(next) == 0 ? 1 : 0, px, cur);
        }
        if (slotOf(next) < 0 && slotOf(cur) >= 0) {
            int[] px = decoded.get(next);
            if (px != null) upload(slotOf(cur) == 0 ? 1 : 0, px, next);
        }
        int a = slotOf(cur), b = slotOf(next);
        if (a >= 0) {
            drawA = a;
            drawB = b;
        } else if (slotFrame[0] >= 0 || slotFrame[1] >= 0) {
            drawA = slotFrame[0] >= 0 ? 0 : 1;
            drawB = -1;
        } else {
            return false;
        }
        return true;
    }

    private static boolean poster() {
        if (poster != null) return true;
        if (posterFailed) return false;
        try (InputStream in = VideoBackground.class.getResourceAsStream("/assets/mraz1visual/video/poster.jpg")) {
            BufferedImage img = ImageIO.read(in);
            posterW = img.getWidth();
            posterH = img.getHeight();
            NativeImage ni = new NativeImage(posterW, posterH, false);
            for (int y = 0; y < posterH; y++) for (int x = 0; x < posterW; x++) ni.setPixel(x, y, img.getRGB(x, y) | 0xFF000000);
            poster = new DynamicTexture(() -> "mraz1visual poster", ni);
            Minecraft.getInstance().getTextureManager().register(POSTER, poster);
            return true;
        } catch (Throwable t) {
            posterFailed = true;
            return false;
        }
    }

    /** Заполнить прямоугольник видео по принципу «cover» (без полос). */
    public static boolean render(GuiGraphics g, int x, int y, int w, int h) {
        boolean ok;
        try {
            ok = update();
        } catch (Throwable t) {
            failed = true;
            ok = false;
            ModuleManager.LOG.warn("Video background disabled", t);
        }
        if (ok) {
            float[] uv = cover(w, h, W, H);
            blitLinear(g, tex[drawA], x, y, w, h, uv[0], uv[1], uv[2], uv[3], 0xFFFFFFFF);
            if (drawB >= 0 && mix > 0.01f) {
                blitLinear(g, tex[drawB], x, y, w, h, uv[0], uv[1], uv[2], uv[3], Math.round(255 * mix) << 24 | 0xFFFFFF);
            }
            return true;
        }
        if (poster()) {
            float[] uv = cover(w, h, posterW, posterH);
            blitLinear(g, poster, x, y, w, h, uv[0], uv[1], uv[2], uv[3], 0xFFFFFFFF);
            return true;
        }
        g.fill(x, y, x + w, y + h, 0xFF050508);
        return false;
    }

    private static float[] cover(int w, int h, int tw, int th) {
        float scale = Math.max(w / (float) tw, h / (float) th);
        float sw = w / scale, sh = h / scale;
        float u0 = (tw - sw) / 2f / tw, v0 = (th - sh) / 2f / th;
        return new float[]{u0, 1f - u0, v0, 1f - v0};
    }

    /** Текстура с плавной (линейной) фильтрацией: видео растягивается без «пикселей». */
    private static void blitLinear(GuiGraphics g, DynamicTexture t, int x, int y, int w, int h, float u0, float u1, float v0, float v1, int color) {
        com.mojang.blaze3d.textures.GpuTextureView view = t.getTextureView();
        if (view == null) return;
        g.guiRenderState.submitGuiElement(new net.minecraft.client.gui.render.state.BlitRenderState(
                RenderPipelines.GUI_TEXTURED,
                net.minecraft.client.gui.render.TextureSetup.singleTexture(view,
                        com.mojang.blaze3d.systems.RenderSystem.getSamplerCache().getClampToEdge(com.mojang.blaze3d.textures.FilterMode.LINEAR)),
                new org.joml.Matrix3x2f(g.pose()),
                x, y, x + w, y + h, u0, u1, v0, v1, color, g.scissorStack.peek()));
    }

    /** Фон меню: видео + затемнение слева (там пункты меню) + лёгкая виньетка. */
    public static void renderMenu(GuiGraphics g, int width, int height) {
        render(g, 0, 0, width, height);
        int steps = 24;
        for (int i = 0; i < steps; i++) {
            float t = i / (float) steps;
            int a = Math.round(215 * (float) Math.pow(1 - t, 1.6));
            int x0 = Math.round(width * 0.62f * t), x1 = Math.round(width * 0.62f * (t + 1f / steps));
            g.fill(x0, 0, x1, height, a << 24 | 0x030308);
        }
        g.fillGradient(0, 0, width, height / 5, 0x70000000, 0x00000000);
        g.fillGradient(0, height - height / 4, width, height, 0x00000000, 0x90000000);
    }
}

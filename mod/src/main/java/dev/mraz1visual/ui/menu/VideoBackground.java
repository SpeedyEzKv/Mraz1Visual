package dev.mraz1visual.ui.menu;

import com.mojang.blaze3d.platform.NativeImage;
import dev.mraz1visual.core.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Видео-фон меню (то же видео, что в лаунчере). Кадры — JPEG в jar (assets/mraz1visual/video/fNNN.jpg),
 * читаются напрямую из jar, без ресурс-паков, поэтому работают и во время загрузки игры.
 * Декодирование — в отдельном потоке; в кадре только копия пикселей в одну текстуру.
 */
public final class VideoBackground {
    public static final int FRAMES = 138, FPS = 15, W = 768, H = 432;
    private static final Identifier TEX = Identifier.fromNamespaceAndPath("mraz1visual", "video/bg");
    private static final ExecutorService DECODER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Mraz1Visual video");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });
    private static final long START = System.nanoTime();

    private static DynamicTexture texture;
    private static volatile int[] ready;      // декодированный кадр, ждёт загрузки в текстуру
    private static volatile int readyIndex = -1;
    private static volatile boolean decoding;
    private static int shownIndex = -1;
    private static boolean failed;

    private VideoBackground() {}

    /** Номер кадра по времени (видео зациклено без стыка). */
    private static int frameNow() {
        return (int) (((System.nanoTime() - START) / (1_000_000_000L / FPS)) % FRAMES);
    }

    private static void requestDecode(int index) {
        if (decoding || failed) return;
        decoding = true;
        DECODER.execute(() -> {
            try (InputStream in = VideoBackground.class.getResourceAsStream(String.format("/assets/mraz1visual/video/f%03d.jpg", index + 1))) {
                if (in == null) {
                    failed = true;
                    return;
                }
                BufferedImage img = ImageIO.read(in);
                int[] px = new int[W * H];
                img.getRGB(0, 0, Math.min(W, img.getWidth()), Math.min(H, img.getHeight()), px, 0, W);
                ready = px;
                readyIndex = index;
            } catch (Throwable t) {
                failed = true;
                ModuleManager.LOG.warn("Video frame {} failed", index, t);
            } finally {
                decoding = false;
            }
        });
    }

    /** Подготовить текстуру и обновить кадр. Вызывать раз в кадр на потоке рендера. */
    private static boolean update() {
        if (failed) return false;
        if (texture == null) {
            texture = new DynamicTexture(() -> "mraz1visual video", W, H, false);
            Minecraft.getInstance().getTextureManager().register(TEX, texture);
        }
        int want = frameNow();
        int[] px = ready;
        if (px != null && readyIndex != shownIndex) {
            NativeImage img = texture.getPixels();
            if (img != null) {
                for (int y = 0, i = 0; y < H; y++) for (int x = 0; x < W; x++, i++) img.setPixel(x, y, px[i] | 0xFF000000);
                texture.upload();
            }
            shownIndex = readyIndex;
            ready = null;
        }
        if (want != shownIndex && readyIndex != want) requestDecode(want);
        return shownIndex >= 0;
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
        if (!ok) {
            g.fill(x, y, x + w, y + h, 0xFF050508);
            return false;
        }
        float scale = Math.max(w / (float) W, h / (float) H);
        float sw = w / scale, sh = h / scale; // видимая часть кадра в пикселях текстуры
        float u = (W - sw) / 2f, v = (H - sh) / 2f;
        g.blit(RenderPipelines.GUI_TEXTURED, TEX, x, y, u, v, w, h, Math.round(sw), Math.round(sh), W, H, 0xFFFFFFFF);
        return true;
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

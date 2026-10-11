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
    public static final int FRAMES = 221, FPS = 24, W = 768, H = 432;
    private static final Identifier[] TEX = {
            Identifier.fromNamespaceAndPath("mraz1visual", "video/bg_a"),
            Identifier.fromNamespaceAndPath("mraz1visual", "video/bg_b")};
    private static final ExecutorService DECODER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Mraz1Visual video");
        t.setDaemon(true);
        return t;
    });
    private static final long START = System.nanoTime();

    /** Две текстуры: в одной текущий кадр, в другой следующий — между ними плавный переход. */
    private static final DynamicTexture[] tex = new DynamicTexture[2];
    private static final int[] slotFrame = {-1, -1};
    private static volatile int[] ready;
    private static volatile int readyIndex = -1;
    private static volatile boolean decoding;
    private static boolean failed;

    private VideoBackground() {}

    private static void requestDecode(int index) {
        if (decoding || failed || readyIndex == index) return;
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
                readyIndex = index;
                ready = px;
            } catch (Throwable t) {
                failed = true;
                ModuleManager.LOG.warn("Video frame {} failed", index, t);
            } finally {
                decoding = false;
            }
        });
    }

    private static int slotOf(int frame) {
        return slotFrame[0] == frame ? 0 : slotFrame[1] == frame ? 1 : -1;
    }

    private static void upload(int slot, int[] px, int frame) {
        NativeImage img = tex[slot].getPixels();
        if (img == null) return;
        for (int y = 0, i = 0; y < H; y++) for (int x = 0; x < W; x++, i++) img.setPixel(x, y, px[i] | 0xFF000000);
        tex[slot].upload();
        slotFrame[slot] = frame;
    }

    // результат кадра: какие слоты рисовать и с какой прозрачностью второй
    private static int drawA = -1, drawB = -1;
    private static float mix;

    /** Обновить текстуры. Вызывать раз в кадр на потоке рендера. */
    private static boolean update() {
        if (failed) return false;
        if (tex[0] == null) {
            for (int i = 0; i < 2; i++) {
                tex[i] = new DynamicTexture(() -> "mraz1visual video", W, H, false);
                Minecraft.getInstance().getTextureManager().register(TEX[i], tex[i]);
            }
        }
        double f = (System.nanoTime() - START) / 1e9 * FPS;
        int cur = (int) (Math.floor(f) % FRAMES), next = (cur + 1) % FRAMES;
        mix = (float) (f - Math.floor(f));

        // готовый кадр — в слот, где сейчас не лежит текущий
        int[] px = ready;
        if (px != null) {
            int idx = readyIndex;
            ready = null;
            readyIndex = -1;
            if (slotOf(idx) < 0) {
                int curSlot = slotOf(cur);
                int target = curSlot == 0 ? 1 : curSlot == 1 ? 0 : (slotFrame[0] == -1 ? 0 : 1);
                upload(target, px, idx);
            }
        }
        // что декодировать дальше: сначала текущий, потом следующий
        if (slotOf(cur) < 0) requestDecode(cur);
        else if (slotOf(next) < 0) requestDecode(next);

        int a = slotOf(cur), b = slotOf(next);
        if (a >= 0) {
            drawA = a;
            drawB = b;
        } else if (slotFrame[0] >= 0 || slotFrame[1] >= 0) {
            // декодер не успел: показываем последний готовый кадр без перехода
            drawA = slotFrame[0] >= 0 ? 0 : 1;
            drawB = -1;
        } else {
            return false;
        }
        return true;
    }

    /** Заполнить прямоугольник видео по принципу «cover» (без полос), с плавным переходом между кадрами. */
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
        float sw = w / scale, sh = h / scale;
        float u = (W - sw) / 2f, v = (H - sh) / 2f;
        g.blit(RenderPipelines.GUI_TEXTURED, TEX[drawA], x, y, u, v, w, h, Math.round(sw), Math.round(sh), W, H, 0xFFFFFFFF);
        if (drawB >= 0 && mix > 0.01f) {
            int a = Math.round(255 * mix);
            g.blit(RenderPipelines.GUI_TEXTURED, TEX[drawB], x, y, u, v, w, h, Math.round(sw), Math.round(sh), W, H, a << 24 | 0xFFFFFF);
        }
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

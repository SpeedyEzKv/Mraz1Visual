package dev.mraz1visual;

import dev.mraz1visual.core.ModuleManager;
import net.fabricmc.loader.api.FabricLoader;
//? if <26.3 {
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWImage;
//?}
import org.lwjgl.system.MemoryUtil;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/** Название и иконка окна игры: «Mraz1Visual» вместо «Minecraft 1.21.11». */
public final class WindowBranding {
    private WindowBranding() {}

    public static String title() {
        String v = FabricLoader.getInstance().getModContainer("mraz1visual")
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("");
        return v.isEmpty() ? "Mraz1Visual" : "Mraz1Visual " + v;
    }

    /** Ставит иконку окна. Вызывать в потоке отрисовки после создания окна. */
    public static void applyIcon() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("mac")) return; // macOS берёт иконку из приложения
        // 26.3: окно на SDL3 — иконку ставит сама игра из ресурсов, GLFW нет
        //? if <26.3 {
        long window = GLFW.glfwGetCurrentContext();
        if (window == 0) return;
        int[] sizes = {16, 32, 48, 128, 256};
        List<ByteBuffer> buffers = new ArrayList<>();
        try (GLFWImage.Buffer images = GLFWImage.malloc(sizes.length)) {
            int n = 0;
            for (int s : sizes) {
                try (InputStream in = WindowBranding.class.getResourceAsStream("/assets/mraz1visual/icon/icon_" + s + ".png")) {
                    if (in == null) continue;
                    BufferedImage img = ImageIO.read(in);
                    int w = img.getWidth(), h = img.getHeight();
                    ByteBuffer buf = MemoryUtil.memAlloc(w * h * 4);
                    buffers.add(buf);
                    for (int y = 0; y < h; y++) {
                        for (int x = 0; x < w; x++) {
                            int argb = img.getRGB(x, y);
                            buf.put((byte) (argb >> 16)).put((byte) (argb >> 8)).put((byte) argb).put((byte) (argb >>> 24));
                        }
                    }
                    buf.flip();
                    images.position(n).width(w).height(h).pixels(buf);
                    n++;
                }
            }
            images.position(0);
            images.limit(n);
            if (n > 0) GLFW.glfwSetWindowIcon(window, images);
        } catch (Throwable t) {
            ModuleManager.LOG.warn("Mraz1Visual: window icon failed", t);
        } finally {
            for (ByteBuffer b : buffers) MemoryUtil.memFree(b);
        }
        //?}
    }
}

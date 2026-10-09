package dev.umbra.overlay;

import com.mojang.blaze3d.platform.NativeImage;
import dev.umbra.core.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Миниатюры последних скриншотов. Картинки уменьшаются в фоне (ImageIO),
 * на видеокарту попадают только маленькие копии. Всё освобождается при закрытии оверлея.
 * Единственное место, где мод создаёт текстуры.
 */
public final class Screenshots {
    public static final int THUMB_W = 192, THUMB_H = 108;
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Umbra screenshots");
        t.setDaemon(true);
        return t;
    });

    public static final class Shot {
        public final Path path;
        public final String name;
        public volatile Identifier texture;
        public int texW, texH;

        Shot(Path path) {
            this.path = path;
            this.name = path.getFileName().toString().replace(".png", "");
        }
    }

    private final List<Shot> shots = new ArrayList<>();
    private final List<Identifier> registered = new ArrayList<>();
    private Shot full;
    private int seq;
    /** Растёт при каждом закрытии: опоздавшие загрузки после закрытия выбрасываются. */
    private int gen;

    public static Path dir() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("screenshots");
    }

    public List<Shot> shots() { return shots; }

    /** Найти последние N и начать грузить миниатюры. */
    public void open(int count) {
        close();
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir(), "*.png")) {
            for (Path p : ds) files.add(p);
        } catch (Exception ignored) {
            return;
        }
        files.sort(Comparator.comparingLong(Screenshots::mtime).reversed());
        for (int i = 0; i < Math.min(count, files.size()); i++) {
            Shot s = new Shot(files.get(i));
            shots.add(s);
            load(s, THUMB_W, THUMB_H);
        }
    }

    /** Полноразмерная картинка для просмотра (только одна одновременно). */
    public Shot openFull(Shot s) {
        if (full != null && full.texture != null) release(full.texture);
        full = new Shot(s.path);
        load(full, 1280, 720);
        return full;
    }

    private void load(Shot s, int maxW, int maxH) {
        int id = seq++;
        int myGen = gen;
        IO.submit(() -> {
            try {
                BufferedImage src = ImageIO.read(s.path.toFile());
                if (src == null) return;
                double k = Math.min(maxW / (double) src.getWidth(), maxH / (double) src.getHeight());
                int w = Math.max(1, (int) (src.getWidth() * Math.min(1, k))), h = Math.max(1, (int) (src.getHeight() * Math.min(1, k)));
                BufferedImage scaled = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                var g = scaled.createGraphics();
                g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(src, 0, 0, w, h, null);
                g.dispose();
                int[] argb = scaled.getRGB(0, 0, w, h, null, 0, w);
                Minecraft.getInstance().execute(() -> {
                    if (myGen == gen) upload(s, argb, w, h, id);
                });
            } catch (Throwable t) {
                ModuleManager.LOG.debug("Cannot read screenshot {}", s.path, t);
            }
        });
    }

    private void upload(Shot s, int[] argb, int w, int h, int id) {
        NativeImage img = new NativeImage(w, h, false);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) img.setPixel(x, y, argb[y * w + x] | 0xFF000000);
        }
        Identifier tid = Identifier.fromNamespaceAndPath("umbra", "overlay/shot_" + id);
        Minecraft.getInstance().getTextureManager().register(tid, new DynamicTexture(() -> "umbra screenshot", img));
        registered.add(tid);
        s.texW = w;
        s.texH = h;
        s.texture = tid;
    }

    private void release(Identifier id) {
        Minecraft.getInstance().getTextureManager().release(id);
        registered.remove(id);
    }

    /** Освободить всё — вызывается при закрытии оверлея. */
    public void close() {
        gen++;
        for (Identifier id : new ArrayList<>(registered)) Minecraft.getInstance().getTextureManager().release(id);
        registered.clear();
        shots.clear();
        full = null;
    }

    private static long mtime(Path p) {
        try {
            return Files.getLastModifiedTime(p).toMillis();
        } catch (Exception e) {
            return 0;
        }
    }

    /** Открыть папку или ссылку в системе. */
    public static void openExternal(String target) {
        try {
            String os = System.getProperty("os.name", "").toLowerCase();
            if (os.contains("win")) new ProcessBuilder("explorer", target).start();
            else if (os.contains("mac")) new ProcessBuilder("open", target).start();
            else new ProcessBuilder("xdg-open", target).start();
        } catch (Exception e) {
            ModuleManager.LOG.warn("Cannot open {}", target, e);
        }
    }
}

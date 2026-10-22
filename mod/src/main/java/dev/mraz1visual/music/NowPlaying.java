package dev.mraz1visual.music;

import com.mojang.blaze3d.platform.NativeImage;
import dev.mraz1visual.music.player.MusicPlayer;
import dev.mraz1visual.music.player.Tags;
import dev.mraz1visual.music.player.Track;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

/**
 * «Сейчас играет» — один источник для виджета, текстов песен и Discord: встроенный плеер, если в нём есть трек,
 * иначе то, что играет на компьютере (SMTC через лаунчер: Spotify, Яндекс Музыка, браузер…).
 */
public final class NowPlaying {
    private static final Identifier LOCAL_COVER = Identifier.fromNamespaceAndPath("mraz1visual", "music/local_cover");
    private static int coverSerial = -1;
    private static volatile boolean localCover;
    /** Средний цвет обложки — фон карточки («размытый цвет обложки»). */
    public static volatile int coverTint = 0xFF2A2A3C;

    private NowPlaying() {}

    public static boolean local() {
        return MusicPlayer.active();
    }

    public static boolean available() {
        return local() || (MediaClient.available() && !MediaClient.title.isEmpty());
    }

    public static String title() {
        Track t = MusicPlayer.current();
        return t != null ? t.title : MediaClient.title;
    }

    public static String artist() {
        Track t = MusicPlayer.current();
        return t != null ? t.artist : MediaClient.artist;
    }

    public static String album() {
        Track t = MusicPlayer.current();
        return t != null ? t.album : "";
    }

    public static String source() {
        return local() ? "Плеер Mraz1Visual" : MediaClient.appName();
    }

    public static boolean playing() {
        return local() ? MusicPlayer.playing() : MediaClient.playing;
    }

    public static long position() {
        return local() ? MusicPlayer.position() : MediaClient.positionNow();
    }

    public static long duration() {
        return local() ? MusicPlayer.length() : MediaClient.duration;
    }

    /** Ключ трека (смена → перезапрос текста, анимация смены). */
    public static String key() {
        return artist() + "\u0001" + title();
    }

    public static boolean hasCover() {
        if (local()) {
            syncLocalCover();
            return localCover;
        }
        return MediaClient.hasCover;
    }

    public static Identifier cover() {
        return local() ? LOCAL_COVER : MediaClient.cover();
    }

    public static void command(String c) {
        if (local()) {
            switch (c) {
                case "toggle" -> MusicPlayer.toggle();
                case "next" -> MusicPlayer.next();
                case "prev" -> MusicPlayer.prev();
                default -> { }
            }
        } else MediaClient.command(c);
    }

    /** Обложка трека плеера: из тегов или cover.jpg рядом — в фоне, на видеокарту — в потоке рендера. */
    private static void syncLocalCover() {
        int s = MusicPlayer.trackSerial;
        if (s == coverSerial) return;
        coverSerial = s;
        localCover = false;
        Track t = MusicPlayer.current();
        if (t == null) return;
        Thread th = new Thread(() -> {
            try {
                byte[] img = Tags.read(t.path, true).cover;
                if (img == null) return;
                upload(ImageIO.read(new ByteArrayInputStream(img)), LOCAL_COVER, () -> localCover = true);
            } catch (Exception ignored) {
            }
        }, "Mraz1Visual cover");
        th.setDaemon(true);
        th.start();
    }

    /** Картинка → квадрат 96×96 по центру → текстура; заодно средний цвет для фона карточки. */
    static void upload(BufferedImage img, Identifier id, Runnable done) {
        if (img == null) return;
        int s = 96;
        BufferedImage scaled = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        var g = scaled.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        int side = Math.min(img.getWidth(), img.getHeight());
        g.drawImage(img, 0, 0, s, s, (img.getWidth() - side) / 2, (img.getHeight() - side) / 2,
                (img.getWidth() + side) / 2, (img.getHeight() + side) / 2, null);
        g.dispose();
        int[] argb = scaled.getRGB(0, 0, s, s, null, 0, s);
        long r = 0, gg = 0, b = 0;
        for (int c : argb) { r += c >> 16 & 255; gg += c >> 8 & 255; b += c & 255; }
        int n = argb.length;
        // приглушённый средний цвет: темнее и чуть менее насыщенный — спокойный фон под текстом
        int tr = (int) (r / n * 0.55), tg = (int) (gg / n * 0.55), tb = (int) (b / n * 0.55);
        coverTint = 0xFF000000 | tr << 16 | tg << 8 | tb;
        Minecraft.getInstance().execute(() -> {
            NativeImage ni = new NativeImage(s, s, false);
            for (int y = 0; y < s; y++) for (int x = 0; x < s; x++) ni.setPixel(x, y, argb[y * s + x] | 0xFF000000);
            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "mraz1visual cover", ni));
            done.run();
        });
    }
}

package dev.mraz1visual.wallpaper;

import dev.mraz1visual.core.ModuleManager;
import net.fabricmc.loader.api.FabricLoader;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Достаёт из сцены Wallpaper Engine её главную картинку в полном разрешении (обычно 1080p–4K),
 * чтобы вместо мыльного gif-превью был чёткий фон.
 *
 * Сцена — это scene.pkg (архив) с текстурами .tex. Форматы описаны в открытом проекте RePKG (MIT):
 * TEXV0005 / TEXI0001 / TEXB000x, мипмапы в RGBA8888, DXT1/3/5, R8, RG88 или готовой картинкой
 * (JPEG/PNG), иногда сжатые LZ4. Берём самую большую не анимированную текстуру — это фон сцены.
 * Результат кэшируется в PNG, второй раз открывается мгновенно.
 */
public final class SceneImage {
    private static final Path CACHE = FabricLoader.getInstance().getConfigDir().resolve("mraz1visual").resolve("wallpaper-cache");
    private static final int FLAG_GIF = 4, FLAG_VIDEO = 32;

    private SceneImage() {}

    /** Чёткая картинка сцены (из кэша или заново), либо null. */
    public static BufferedImage load(Path folder) {
        try {
            Path cache = CACHE.resolve(Integer.toHexString(folder.toAbsolutePath().normalize().toString().hashCode()) + ".png");
            Path pkg = folder.resolve("scene.pkg");
            long stamp = Files.exists(pkg) ? Files.getLastModifiedTime(pkg).toMillis() : 0;
            if (Files.exists(cache) && Files.getLastModifiedTime(cache).toMillis() >= stamp) {
                BufferedImage img = ImageIO.read(cache.toFile());
                if (img != null) return img;
            }
            BufferedImage img = extract(folder);
            if (img != null) {
                Files.createDirectories(CACHE);
                ImageIO.write(img, "png", cache.toFile());
            }
            return img;
        } catch (Throwable t) {
            ModuleManager.LOG.warn("Scene image failed for {}", folder, t);
            return null;
        }
    }

    static BufferedImage extract(Path folder) throws Exception {
        Map<String, byte[]> files = new LinkedHashMap<>();
        Path pkg = folder.resolve("scene.pkg");
        if (Files.exists(pkg)) files.putAll(readPkg(Files.readAllBytes(pkg)));
        // распакованные сцены: materials/**.tex рядом с project.json
        Path mats = folder.resolve("materials");
        if (Files.isDirectory(mats)) {
            try (Stream<Path> s = Files.walk(mats, 6)) {
                for (Path p : (Iterable<Path>) s::iterator) {
                    if (p.toString().toLowerCase(Locale.ROOT).endsWith(".tex") && Files.size(p) < 256L << 20) {
                        files.put(folder.relativize(p).toString().replace('\\', '/'), Files.readAllBytes(p));
                    }
                }
            }
        }
        // кандидаты по размеру из заголовка; эффекты/частицы/маски пропускаем
        List<Object[]> cand = new ArrayList<>();
        for (Map.Entry<String, byte[]> e : files.entrySet()) {
            String n = e.getKey().toLowerCase(Locale.ROOT);
            if (!n.endsWith(".tex")) continue;
            if (n.contains("effects/") || n.contains("particle") || n.contains("mask") || n.contains("noise")
                    || n.contains("gradient") || n.contains("util")) continue;
            try {
                Tex t = Tex.header(e.getValue());
                if ((t.flags & (FLAG_GIF | FLAG_VIDEO)) != 0) continue;
                if (t.format == 9 || t.format == 8) continue; // R8 / RG88 — маски
                cand.add(new Object[]{(long) t.imageW * t.imageH, e.getValue()});
            } catch (Throwable ignored) {
            }
        }
        cand.sort((a, b) -> Long.compare((long) b[0], (long) a[0]));
        for (Object[] c : cand) {
            if ((long) c[0] < 512L * 288) break; // мелочь — это не фон
            try {
                BufferedImage img = Tex.decode((byte[]) c[1]);
                if (img != null) return img;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    // ── PKG ──────────────────────────────────────────────────

    static Map<String, byte[]> readPkg(byte[] data) {
        ByteBuffer b = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        int magicLen = b.getInt();
        if (magicLen < 0 || magicLen > 32) throw new IllegalArgumentException("not a pkg");
        b.position(b.position() + magicLen);
        int count = b.getInt();
        if (count < 0 || count > 100000) throw new IllegalArgumentException("bad pkg");
        String[] names = new String[count];
        int[] off = new int[count], len = new int[count];
        for (int i = 0; i < count; i++) {
            int nl = b.getInt();
            if (nl < 0 || nl > 4096) throw new IllegalArgumentException("bad name");
            byte[] nb = new byte[nl];
            b.get(nb);
            names[i] = new String(nb, StandardCharsets.UTF_8);
            off[i] = b.getInt();
            len[i] = b.getInt();
        }
        int dataStart = b.position();
        Map<String, byte[]> out = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            long s = (long) dataStart + off[i];
            if (len[i] < 0 || s + len[i] > data.length) continue;
            if (!names[i].toLowerCase(Locale.ROOT).endsWith(".tex")) continue; // нужны только текстуры
            byte[] e = new byte[len[i]];
            System.arraycopy(data, (int) s, e, 0, len[i]);
            out.put(names[i], e);
        }
        return out;
    }

    // ── TEX ──────────────────────────────────────────────────

    static final class Tex {
        int format, flags, texW, texH, imageW, imageH;

        private static String nstr(ByteBuffer b) {
            StringBuilder sb = new StringBuilder();
            while (b.hasRemaining()) {
                byte c = b.get();
                if (c == 0) break;
                if (sb.length() < 64) sb.append((char) c);
            }
            return sb.toString();
        }

        private static Tex readHeader(ByteBuffer b) {
            if (!nstr(b).equals("TEXV0005") || !nstr(b).equals("TEXI0001")) throw new IllegalArgumentException("not a tex");
            Tex t = new Tex();
            t.format = b.getInt();
            t.flags = b.getInt();
            t.texW = b.getInt();
            t.texH = b.getInt();
            t.imageW = b.getInt();
            t.imageH = b.getInt();
            b.getInt();
            return t;
        }

        static Tex header(byte[] data) {
            return readHeader(ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN));
        }

        static BufferedImage decode(byte[] data) throws Exception {
            ByteBuffer b = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
            Tex t = readHeader(b);
            String container = nstr(b);
            int images = b.getInt();
            if (images < 1) return null;
            int version = Integer.parseInt(container.substring(4));
            int imageFormat = -1;
            if (version == 3) imageFormat = b.getInt();
            if (version == 4) {
                imageFormat = b.getInt();
                boolean mp4 = b.getInt() == 1;
                if (mp4 && imageFormat == -1) return null; // видео-текстура
                version = 3;
            }
            int mips = b.getInt();
            if (mips < 1) return null;
            // первый мипмап — самый большой
            int w = b.getInt(), h = b.getInt();
            boolean lz4 = false;
            int raw = -1;
            if (version >= 2) {
                lz4 = b.getInt() == 1;
                raw = b.getInt();
            }
            int count = b.getInt();
            if (count < 0 || count > b.remaining()) throw new IllegalArgumentException("bad mip");
            byte[] bytes = new byte[count];
            b.get(bytes);
            if (lz4) bytes = lz4(bytes, raw);

            BufferedImage img;
            if (imageFormat != -1) {
                img = ImageIO.read(new ByteArrayInputStream(bytes));
                if (img == null) return null;
            } else {
                byte[] rgba = switch (t.format) {
                    case 0 -> bytes;
                    case 4 -> dxt(w, h, bytes, 5);
                    case 6 -> dxt(w, h, bytes, 3);
                    case 7 -> dxt(w, h, bytes, 1);
                    default -> null;
                };
                if (rgba == null || rgba.length < w * h * 4) return null;
                img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
                int[] px = new int[w * h];
                for (int i = 0, j = 0; i < px.length; i++, j += 4) {
                    px[i] = (rgba[j] & 0xFF) << 16 | (rgba[j + 1] & 0xFF) << 8 | (rgba[j + 2] & 0xFF);
                }
                img.setRGB(0, 0, w, h, px, 0, w);
            }
            // текстура бывает дополнена до степени двойки — обрезаем до настоящего размера
            int cw = t.imageW > 0 ? Math.min(t.imageW, img.getWidth()) : img.getWidth();
            int ch = t.imageH > 0 ? Math.min(t.imageH, img.getHeight()) : img.getHeight();
            if (cw != img.getWidth() || ch != img.getHeight()) img = img.getSubimage(0, 0, cw, ch);
            return img;
        }
    }

    // ── LZ4 (блочный формат) ─────────────────────────────────

    static byte[] lz4(byte[] src, int outLen) {
        byte[] dst = new byte[outLen];
        int s = 0, d = 0;
        while (s < src.length) {
            int token = src[s++] & 0xFF;
            int lit = token >>> 4;
            if (lit == 15) {
                int x;
                do {
                    x = src[s++] & 0xFF;
                    lit += x;
                } while (x == 255);
            }
            System.arraycopy(src, s, dst, d, lit);
            s += lit;
            d += lit;
            if (s >= src.length) break;
            int offset = (src[s] & 0xFF) | (src[s + 1] & 0xFF) << 8;
            s += 2;
            int ml = token & 15;
            if (ml == 15) {
                int x;
                do {
                    x = src[s++] & 0xFF;
                    ml += x;
                } while (x == 255);
            }
            ml += 4;
            int from = d - offset;
            for (int i = 0; i < ml; i++) dst[d++] = dst[from + i];
        }
        return dst;
    }

    // ── DXT1/3/5 ─────────────────────────────────────────────

    static byte[] dxt(int w, int h, byte[] src, int kind) {
        byte[] out = new byte[w * h * 4];
        int blockSize = kind == 1 ? 8 : 16;
        int bw = Math.max(1, (w + 3) / 4), bh = Math.max(1, (h + 3) / 4);
        int[] r = new int[4], g = new int[4], bl = new int[4], a = new int[4];
        int[] alpha = new int[16];
        int pos = 0;
        for (int by = 0; by < bh; by++) {
            for (int bx = 0; bx < bw; bx++, pos += blockSize) {
                if (pos + blockSize > src.length) return out;
                int cpos = pos;
                java.util.Arrays.fill(alpha, 255);
                if (kind == 3) {
                    for (int i = 0; i < 16; i++) {
                        int v = (src[pos + i / 2] >> ((i & 1) * 4)) & 0xF;
                        alpha[i] = v * 17;
                    }
                    cpos = pos + 8;
                } else if (kind == 5) {
                    int a0 = src[pos] & 0xFF, a1 = src[pos + 1] & 0xFF;
                    long bits = 0;
                    for (int i = 0; i < 6; i++) bits |= (long) (src[pos + 2 + i] & 0xFF) << (8 * i);
                    int[] pal = new int[8];
                    pal[0] = a0;
                    pal[1] = a1;
                    if (a0 > a1) {
                        for (int i = 1; i < 7; i++) pal[i + 1] = ((7 - i) * a0 + i * a1) / 7;
                    } else {
                        for (int i = 1; i < 5; i++) pal[i + 1] = ((5 - i) * a0 + i * a1) / 5;
                        pal[6] = 0;
                        pal[7] = 255;
                    }
                    for (int i = 0; i < 16; i++) alpha[i] = pal[(int) (bits >>> (3 * i)) & 7];
                    cpos = pos + 8;
                }
                int c0 = (src[cpos] & 0xFF) | (src[cpos + 1] & 0xFF) << 8;
                int c1 = (src[cpos + 2] & 0xFF) | (src[cpos + 3] & 0xFF) << 8;
                r[0] = (c0 >> 11 & 31) * 255 / 31; g[0] = (c0 >> 5 & 63) * 255 / 63; bl[0] = (c0 & 31) * 255 / 31;
                r[1] = (c1 >> 11 & 31) * 255 / 31; g[1] = (c1 >> 5 & 63) * 255 / 63; bl[1] = (c1 & 31) * 255 / 31;
                a[0] = a[1] = a[2] = a[3] = 255;
                if (kind != 1 || c0 > c1) {
                    r[2] = (2 * r[0] + r[1]) / 3; g[2] = (2 * g[0] + g[1]) / 3; bl[2] = (2 * bl[0] + bl[1]) / 3;
                    r[3] = (r[0] + 2 * r[1]) / 3; g[3] = (g[0] + 2 * g[1]) / 3; bl[3] = (bl[0] + 2 * bl[1]) / 3;
                } else {
                    r[2] = (r[0] + r[1]) / 2; g[2] = (g[0] + g[1]) / 2; bl[2] = (bl[0] + bl[1]) / 2;
                    r[3] = g[3] = bl[3] = 0;
                    a[3] = 0;
                }
                int idx = (src[cpos + 4] & 0xFF) | (src[cpos + 5] & 0xFF) << 8 | (src[cpos + 6] & 0xFF) << 16 | (src[cpos + 7] & 0xFF) << 24;
                for (int i = 0; i < 16; i++) {
                    int px = bx * 4 + (i & 3), py = by * 4 + (i >> 2);
                    if (px >= w || py >= h) continue;
                    int c = idx >>> (2 * i) & 3;
                    int o = (py * w + px) * 4;
                    out[o] = (byte) r[c];
                    out[o + 1] = (byte) g[c];
                    out[o + 2] = (byte) bl[c];
                    out[o + 3] = (byte) (kind == 1 ? a[c] : alpha[i]);
                }
            }
        }
        return out;
    }
}

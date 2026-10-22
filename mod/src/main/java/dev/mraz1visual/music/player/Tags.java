package dev.mraz1visual.music.player;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Теги трека без сторонних библиотек: ID3v2 (MP3), Vorbis comments (OGG, FLAC), FLAC PICTURE, ID3 APIC.
 * Нет тегов — название и исполнитель из имени файла «Исполнитель - Название», обложка — cover/folder.jpg рядом.
 */
public final class Tags {
    public String title = "", artist = "", album = "";
    public byte[] cover;

    private Tags() {}

    public static Tags read(Path p, boolean withCover) {
        Tags t = new Tags();
        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
        try {
            if (n.endsWith(".mp3")) t.id3(p, withCover);
            else if (n.endsWith(".flac")) t.flac(p, withCover);
            else if (n.endsWith(".ogg") || n.endsWith(".oga")) t.ogg(p);
        } catch (Exception ignored) {
        }
        if (t.title.isBlank()) {
            String base = p.getFileName().toString();
            int dot = base.lastIndexOf('.');
            if (dot > 0) base = base.substring(0, dot);
            int dash = base.indexOf(" - ");
            if (dash > 0) {
                if (t.artist.isBlank()) t.artist = base.substring(0, dash).trim();
                t.title = base.substring(dash + 3).trim();
            } else t.title = base;
        }
        if (withCover && t.cover == null) {
            for (String c : new String[]{"cover.jpg", "cover.png", "folder.jpg", "folder.png", "front.jpg", "front.png", "album.jpg"}) {
                Path f = p.resolveSibling(c);
                try {
                    if (Files.isRegularFile(f) && Files.size(f) < 8 << 20) { t.cover = Files.readAllBytes(f); break; }
                } catch (IOException ignored) {
                }
            }
        }
        return t;
    }

    // ── ID3v2 ──

    private void id3(Path p, boolean withCover) throws IOException {
        try (InputStream in = Files.newInputStream(p)) {
            byte[] h = in.readNBytes(10);
            if (h.length < 10 || h[0] != 'I' || h[1] != 'D' || h[2] != '3') return;
            int ver = h[3];
            int size = syncsafe(h, 6);
            if (size > 16 << 20) return;
            byte[] tag = in.readNBytes(size);
            int i = 0;
            if ((h[5] & 0x40) != 0 && ver >= 3) i += ver == 4 ? syncsafe(tag, 0) : be32(tag, 0) + 4; // расширенный заголовок
            while (i + (ver == 2 ? 6 : 10) <= tag.length) {
                String id;
                int len;
                if (ver == 2) {
                    id = new String(tag, i, 3, StandardCharsets.ISO_8859_1);
                    len = (tag[i + 3] & 255) << 16 | (tag[i + 4] & 255) << 8 | tag[i + 5] & 255;
                    i += 6;
                } else {
                    id = new String(tag, i, 4, StandardCharsets.ISO_8859_1);
                    len = ver == 4 ? syncsafe(tag, i + 4) : be32(tag, i + 4);
                    i += 10;
                }
                if (id.charAt(0) == 0 || len <= 0 || i + len > tag.length) break;
                switch (id) {
                    case "TIT2", "TT2" -> title = text(tag, i, len);
                    case "TPE1", "TP1" -> artist = text(tag, i, len);
                    case "TALB", "TAL" -> album = text(tag, i, len);
                    case "APIC", "PIC" -> { if (withCover && cover == null) cover = apic(tag, i, len, ver == 2); }
                    default -> { }
                }
                i += len;
            }
        }
    }

    private static int syncsafe(byte[] b, int o) {
        return (b[o] & 127) << 21 | (b[o + 1] & 127) << 14 | (b[o + 2] & 127) << 7 | b[o + 3] & 127;
    }

    private static int be32(byte[] b, int o) {
        return (b[o] & 255) << 24 | (b[o + 1] & 255) << 16 | (b[o + 2] & 255) << 8 | b[o + 3] & 255;
    }

    private static Charset cs(int enc) {
        return switch (enc) {
            case 1 -> StandardCharsets.UTF_16;
            case 2 -> StandardCharsets.UTF_16BE;
            case 3 -> StandardCharsets.UTF_8;
            default -> Charset.forName("windows-1251"); // старые теги русских треков — почти всегда cp1251
        };
    }

    private static String text(byte[] b, int o, int len) {
        String s = new String(b, o + 1, len - 1, cs(b[o]));
        int z = s.indexOf('\0');
        return (z >= 0 ? s.substring(0, z) : s).trim();
    }

    private static byte[] apic(byte[] b, int o, int len, boolean v2) {
        int end = o + len, enc = b[o];
        int i = o + 1;
        if (v2) i += 3; // формат «JPG»
        else { while (i < end && b[i] != 0) i++; i++; } // MIME
        i++; // тип картинки
        // описание: в UTF-16 конец — два нулевых байта
        if (enc == 1 || enc == 2) { while (i + 1 < end && (b[i] != 0 || b[i + 1] != 0)) i += 2; i += 2; }
        else { while (i < end && b[i] != 0) i++; i++; }
        if (i >= end) return null;
        byte[] out = new byte[end - i];
        System.arraycopy(b, i, out, 0, out.length);
        return out;
    }

    // ── FLAC ──

    private void flac(Path p, boolean withCover) throws IOException {
        try (InputStream in = Files.newInputStream(p)) {
            byte[] m = in.readNBytes(4);
            if (m.length < 4 || m[0] != 'f' || m[1] != 'L' || m[2] != 'a' || m[3] != 'C') return;
            boolean last = false;
            while (!last) {
                byte[] h = in.readNBytes(4);
                if (h.length < 4) return;
                last = (h[0] & 0x80) != 0;
                int type = h[0] & 0x7F, len = (h[1] & 255) << 16 | (h[2] & 255) << 8 | h[3] & 255;
                if (type == 4 || (type == 6 && withCover && cover == null)) {
                    if (len > 16 << 20) return;
                    byte[] b = in.readNBytes(len);
                    if (type == 4) vorbisComments(b, 0);
                    else picture(b);
                } else in.skipNBytes(len);
            }
        }
    }

    private void picture(byte[] b) {
        int i = 4;
        int mime = be32(b, i); i += 4 + mime;
        int desc = be32(b, i); i += 4 + desc;
        i += 16;
        int len = be32(b, i); i += 4;
        if (i + len <= b.length) {
            cover = new byte[len];
            System.arraycopy(b, i, cover, 0, len);
        }
    }

    // ── OGG ──

    private void ogg(Path p) throws IOException {
        try (InputStream in = Files.newInputStream(p)) {
            byte[] b = in.readNBytes(256 * 1024);
            for (int i = 0; i + 7 < b.length; i++) {
                if (b[i] == 3 && b[i + 1] == 'v' && b[i + 2] == 'o' && b[i + 3] == 'r' && b[i + 4] == 'b' && b[i + 5] == 'i' && b[i + 6] == 's') {
                    vorbisComments(b, i + 7);
                    return;
                }
            }
        }
    }

    private static int le32(byte[] b, int o) {
        return b[o] & 255 | (b[o + 1] & 255) << 8 | (b[o + 2] & 255) << 16 | (b[o + 3] & 255) << 24;
    }

    private void vorbisComments(byte[] b, int i) {
        int vendor = le32(b, i); i += 4 + vendor;
        int n = le32(b, i); i += 4;
        for (int k = 0; k < n && i + 4 <= b.length; k++) {
            int len = le32(b, i); i += 4;
            if (len < 0 || i + len > b.length) return;
            String c = new String(b, i, len, StandardCharsets.UTF_8);
            i += len;
            int eq = c.indexOf('=');
            if (eq <= 0) continue;
            String key = c.substring(0, eq).toUpperCase(Locale.ROOT), val = c.substring(eq + 1).trim();
            switch (key) {
                case "TITLE" -> title = val;
                case "ARTIST" -> { if (artist.isEmpty()) artist = val; }
                case "ALBUM" -> album = val;
                default -> { }
            }
        }
    }
}

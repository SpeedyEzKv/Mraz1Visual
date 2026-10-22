package dev.mraz1visual.models;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;

import java.io.IOException;

/**
 * Текстура модели до сборки атласа: кадры (у анимированных — полоса кадров сверху вниз, как в ванили),
 * порядок и длительность кадров из .mcmeta, своя эмиссивная пара (*_e.png) и масштаб UV.
 */
public final class TexImage {
    public final String name;
    public final int w, h;          // размер одного кадра, px
    public final int[][] frames;    // ARGB
    public int[] order = {0};       // индексы кадров по порядку
    public int[] ticks = {1};       // длительность каждого шага, тики
    public boolean interpolate;
    public int[] glow;              // эмиссивный слой (кадр 0), может быть null
    /** Вся текстура светится (эмиссивная текстура модели: *_e, emissive, glow). */
    public boolean emissive;
    /** Размер пространства UV, в котором заданы координаты граней (16 у item-моделей). */
    public float uvW = 16, uvH = 16;

    private TexImage(String name, int w, int h, int[][] frames) {
        this.name = name;
        this.w = w;
        this.h = h;
        this.frames = frames;
    }

    public static TexImage solid(String name, int argb) {
        int[] px = new int[16];
        java.util.Arrays.fill(px, argb);
        return new TexImage(name, 4, 4, new int[][]{px});
    }

    public boolean animated() {
        return frames.length > 1 && order.length > 1;
    }

    /** PNG → кадры. frameH ≤ 0 — квадратные кадры, если картинка — вертикальная полоса (как у ванили). */
    public static TexImage decode(String name, byte[] png, JsonObject mcmeta, int frameH) throws IOException {
        int iw, ih;
        int[] px;
        try (NativeImage img = NativeImage.read(png)) {
            iw = img.getWidth();
            ih = img.getHeight();
            if ((long) iw * ih > 4096L * 4096L) throw new IOException("текстура слишком большая: " + iw + "×" + ih);
            px = new int[iw * ih];
            for (int y = 0; y < ih; y++) for (int x = 0; x < iw; x++) px[y * iw + x] = img.getPixel(x, y);
        }
        JsonObject anim = mcmeta != null && mcmeta.has("animation") && mcmeta.get("animation").isJsonObject()
                ? mcmeta.getAsJsonObject("animation") : null;
        int fw = iw, fh = ih;
        if (anim != null) {
            fw = Json.i(anim, "width", iw);
            fh = Json.i(anim, "height", anim.has("width") ? fw : Math.min(iw, ih));
        } else if (frameH > 0 && frameH < ih && ih % frameH == 0) {
            fh = frameH;
        }
        fw = Math.max(1, Math.min(fw, iw));
        fh = Math.max(1, Math.min(fh, ih));
        int cols = iw / fw, rows = ih / fh, n = Math.max(1, cols * rows);
        int[][] frames = new int[n][];
        for (int f = 0; f < n; f++) {
            int ox = (f % cols) * fw, oy = (f / cols) * fh;
            int[] fr = new int[fw * fh];
            for (int y = 0; y < fh; y++) System.arraycopy(px, (oy + y) * iw + ox, fr, y * fw, fw);
            frames[f] = fr;
        }
        TexImage t = new TexImage(name, fw, fh, frames);
        if (n > 1) {
            int def = anim != null ? Math.max(1, Json.i(anim, "frametime", 1)) : 2;
            t.interpolate = anim != null && Json.b(anim, "interpolate", false);
            if (anim != null && anim.has("frames") && anim.get("frames").isJsonArray()) {
                var arr = anim.getAsJsonArray("frames");
                t.order = new int[arr.size()];
                t.ticks = new int[arr.size()];
                for (int i = 0; i < arr.size(); i++) {
                    var e = arr.get(i);
                    if (e.isJsonObject()) {
                        t.order[i] = Math.floorMod(Json.i(e.getAsJsonObject(), "index", 0), n);
                        t.ticks[i] = Math.max(1, Json.i(e.getAsJsonObject(), "time", def));
                    } else {
                        t.order[i] = Math.floorMod(e.getAsInt(), n);
                        t.ticks[i] = def;
                    }
                }
            } else {
                t.order = new int[n];
                t.ticks = new int[n];
                for (int i = 0; i < n; i++) { t.order[i] = i; t.ticks[i] = def; }
            }
        }
        return t;
    }

    /** Эмиссивная пара того же размера (кадр 0); другого размера — растягивается ближайшим пикселем. */
    public void setGlow(TexImage g) {
        int[] src = g.frames[0];
        int[] out = new int[w * h];
        for (int y = 0; y < h; y++) {
            int sy = y * g.h / h;
            for (int x = 0; x < w; x++) out[y * w + x] = src[sy * g.w + x * g.w / w];
        }
        glow = out;
    }
}

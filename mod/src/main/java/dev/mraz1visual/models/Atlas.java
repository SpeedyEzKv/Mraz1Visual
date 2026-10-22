package dev.mraz1visual.models;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Атлас текстур одной модели: все её текстуры на одном листе (одна текстура — один тип рендера — один вызов).
 * Собирается в фоне, на видеокарту попадает в потоке рендера. Анимированные текстуры (.mcmeta, полосы кадров
 * у bbmodel) обновляются по тикам игры — только у моделей, которые сейчас на экране.
 */
public final class Atlas {
    public final int w, h;
    final int[] px;
    final int[] glow;           // null — эмиссивного слоя нет
    final List<Slot> anims = new ArrayList<>();
    public Identifier texture, glowTexture;
    private DynamicTexture tex, glowTex;
    private int lastTick = Integer.MIN_VALUE;
    /** Кадр, когда модель последний раз рисовалась (анимации текстур идут только у видимых). */
    public volatile long lastUsed;

    /** Место текстуры в атласе. */
    static final class Slot {
        final TexImage img;
        final int x, y;

        Slot(TexImage img, int x, int y) {
            this.img = img;
            this.x = x;
            this.y = y;
        }
    }

    final Slot[] slots;

    private Atlas(int w, int h, Slot[] slots) {
        this.w = w;
        this.h = h;
        this.slots = slots;
        this.px = new int[w * h];
        boolean anyGlow = false;
        for (Slot s : slots) if (s != null && s.img.glow != null) anyGlow = true;
        this.glow = anyGlow ? new int[w * h] : null;
        for (Slot s : slots) {
            if (s == null) continue;
            blit(px, s, s.img.frames[s.img.order[0]]);
            if (glow != null && s.img.glow != null) blit(glow, s, s.img.glow);
            if (s.img.animated()) anims.add(s);
        }
    }

    private void blit(int[] dst, Slot s, int[] src) {
        int iw = s.img.w;
        for (int y = 0; y < s.img.h; y++) System.arraycopy(src, y * iw, dst, (s.y + y) * w + s.x, iw);
    }

    /** Полочная упаковка с отступом 1 px (без «протечек» соседей при фильтрации). */
    static Atlas pack(List<TexImage> images) {
        int n = images.size();
        Integer[] idx = new Integer[n];
        long area = 0;
        int maxW = 1;
        for (int i = 0; i < n; i++) {
            idx[i] = i;
            area += (long) (images.get(i).w + 2) * (images.get(i).h + 2);
            maxW = Math.max(maxW, images.get(i).w + 2);
        }
        java.util.Arrays.sort(idx, (a, b) -> images.get(b).h - images.get(a).h);
        int width = Integer.highestOneBit(Math.max(maxW, (int) Math.ceil(Math.sqrt(area))) - 1) << 1;
        width = Math.max(16, Math.min(4096, width));
        Slot[] slots = new Slot[n];
        int x = 0, y = 0, rowH = 0;
        for (int k : idx) {
            TexImage t = images.get(k);
            if (x + t.w + 2 > width) { x = 0; y += rowH; rowH = 0; }
            slots[k] = new Slot(t, x + 1, y + 1);
            x += t.w + 2;
            rowH = Math.max(rowH, t.h + 2);
        }
        int height = Math.max(16, Integer.highestOneBit(Math.max(1, y + rowH) - 1) << 1);
        if (height > 4096) throw new IllegalStateException("атлас не помещается в 4096 px");
        Atlas a = new Atlas(width, height, slots);
        a.pad();
        return a;
    }

    /** Отступ вокруг каждой текстуры — копия края. */
    private void pad() {
        for (Slot s : slots) {
            if (s == null) continue;
            padSlot(px, s);
            if (glow != null) padSlot(glow, s);
        }
    }

    private void padSlot(int[] a, Slot s) {
        int x0 = s.x, y0 = s.y, x1 = s.x + s.img.w - 1, y1 = s.y + s.img.h - 1;
        for (int y = y0; y <= y1; y++) {
            a[y * w + x0 - 1] = a[y * w + x0];
            if (x1 + 1 < w) a[y * w + x1 + 1] = a[y * w + x1];
        }
        for (int x = x0 - 1; x <= Math.min(w - 1, x1 + 1); x++) {
            a[(y0 - 1) * w + x] = a[y0 * w + x];
            if (y1 + 1 < h) a[(y1 + 1) * w + x] = a[y1 * w + x];
        }
    }

    float u(int slot, float fu) {
        Slot s = slots[slot];
        return (s.x + fu * s.img.w) / w;
    }

    float v(int slot, float fv) {
        Slot s = slots[slot];
        return (s.y + fv * s.img.h) / h;
    }

    // ── видеокарта (только поток рендера) ────────────────────

    private static int counter;

    void upload(String name) {
        if (texture != null) return;
        String safe = name.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
        int id = counter++;
        tex = make(px, "models/" + safe + "_" + id);
        texture = Identifier.fromNamespaceAndPath("mraz1visual", "models/" + safe + "_" + id);
        Minecraft.getInstance().getTextureManager().register(texture, tex);
        if (glow != null) {
            glowTex = make(glow, "models/" + safe + "_e_" + id);
            glowTexture = Identifier.fromNamespaceAndPath("mraz1visual", "models/" + safe + "_e_" + id);
            Minecraft.getInstance().getTextureManager().register(glowTexture, glowTex);
        }
    }

    private DynamicTexture make(int[] src, String label) {
        NativeImage ni = new NativeImage(w, h, false);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) ni.setPixel(x, y, src[y * w + x]);
        return new DynamicTexture(() -> "mraz1visual " + label, ni);
    }

    void release() {
        var tm = Minecraft.getInstance().getTextureManager();
        if (texture != null) tm.release(texture);
        if (glowTexture != null) tm.release(glowTexture);
        texture = glowTexture = null;
        tex = glowTex = null;
    }

    /** Следующий кадр анимированных текстур (раз в тик; с интерполяцией — смешивание соседних кадров). */
    void tick(int gameTick) {
        if (tex == null || anims.isEmpty() || gameTick == lastTick) return;
        lastTick = gameTick;
        NativeImage img = tex.getPixels();
        if (img == null) return;
        for (Slot s : anims) {
            TexImage t = s.img;
            int total = 0;
            for (int k : t.ticks) total += k;
            int p = Math.floorMod(gameTick, Math.max(1, total)), step = 0;
            while (p >= t.ticks[step]) { p -= t.ticks[step]; step++; }
            int[] a = t.frames[t.order[step]];
            int[] b = t.interpolate ? t.frames[t.order[(step + 1) % t.order.length]] : null;
            float k = b == null ? 0 : p / (float) t.ticks[step];
            for (int y = 0; y < t.h; y++) {
                for (int x = 0; x < t.w; x++) {
                    int c = a[y * t.w + x];
                    if (b != null) c = mix(c, b[y * t.w + x], k);
                    img.setPixel(s.x + x, s.y + y, c);
                }
            }
        }
        tex.upload();
    }

    private static int mix(int a, int b, float k) {
        int r = 0;
        for (int sh = 0; sh < 32; sh += 8) {
            int ca = a >>> sh & 255, cb = b >>> sh & 255;
            r |= (int) (ca + (cb - ca) * k) << sh;
        }
        return r;
    }
}

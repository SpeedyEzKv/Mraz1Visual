package dev.mraz1visual.render;

import com.mojang.blaze3d.platform.NativeImage;
import dev.mraz1visual.core.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Процедурные текстуры неба (развёртка «равнопромежуточная», 2:1): дневные пресеты с облаками,
 * ночные — туманность, звёзды, млечный путь. Считаются в фоне один раз, в кадре — только отрисовка.
 * Шов по долготе отсутствует: шум периодический по X.
 */
public final class SkyTextures {
    public static final int W = 1024, H = 512, CW = 512, CH = 256;

    private SkyTextures() {}

    // ── шум ──────────────────────────────────────────────────

    private static float hash(int x, int y, int seed) {
        int h = x * 374761393 + y * 668265263 + seed * 1442695041;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / (float) 0xFFFFFF;
    }

    private static float ease(float t) { return t * t * (3 - 2 * t); }

    /** Значение шума в точке; period — число клеток по X (для бесшовности по кругу). */
    private static float value(float x, float y, int period, int seed) {
        int xi = (int) Math.floor(x), yi = (int) Math.floor(y);
        float fx = ease(x - xi), fy = ease(y - yi);
        int x0 = Math.floorMod(xi, period), x1 = Math.floorMod(xi + 1, period);
        float a = hash(x0, yi, seed), b = hash(x1, yi, seed), c = hash(x0, yi + 1, seed), d = hash(x1, yi + 1, seed);
        return (a + (b - a) * fx) + ((c + (d - c) * fx) - (a + (b - a) * fx)) * fy;
    }

    /** Фрактальный шум 0..1: u — доля долготы 0..1, v — 0..1 по высоте. */
    public static float fbm(float u, float v, int baseCells, int octaves, int seed) {
        float sum = 0, amp = 0.5f, norm = 0;
        int cells = baseCells;
        for (int o = 0; o < octaves; o++) {
            sum += value(u * cells, v * cells * 0.5f, cells, seed + o * 31) * amp;
            norm += amp;
            amp *= 0.5f;
            cells *= 2;
        }
        return sum / norm;
    }

    private static int lerp(int a, int b, float t) {
        t = Math.max(0, Math.min(1, t));
        int ar = a >> 16 & 255, ag = a >> 8 & 255, ab = a & 255;
        int br = b >> 16 & 255, bg = b >> 8 & 255, bb = b & 255;
        return 0xFF000000 | (int) (ar + (br - ar) * t) << 16 | (int) (ag + (bg - ag) * t) << 8 | (int) (ab + (bb - ab) * t);
    }

    private static int add(int c, int r, int g, int b) {
        int cr = Math.min(255, (c >> 16 & 255) + r), cg = Math.min(255, (c >> 8 & 255) + g), cb = Math.min(255, (c & 255) + b);
        return 0xFF000000 | cr << 16 | cg << 8 | cb;
    }

    // ── пресеты ──────────────────────────────────────────────

    /** Основной слой неба. v = 0 — зенит, 0.5 — горизонт, 1 — надир. */
    public static int[] base(String preset) {
        int[] px = new int[W * H];
        boolean night = preset.startsWith("night");
        int zen, mid, hor;
        switch (preset) {
            case "day_sunset" -> { zen = 0xFF27336E; mid = 0xFFC8678C; hor = 0xFFFFB778; }
            case "day_pastel" -> { zen = 0xFF7F95EA; mid = 0xFFB9B4F0; hor = 0xFFF5CBE4; }
            case "night_aurora" -> { zen = 0xFF040812; mid = 0xFF0A1628; hor = 0xFF14263A; }
            case "night_stars" -> { zen = 0xFF020309; mid = 0xFF060914; hor = 0xFF101626; }
            case "night_nebula" -> { zen = 0xFF05060F; mid = 0xFF0C0F24; hor = 0xFF181A36; }
            default -> { zen = 0xFF4F86D6; mid = 0xFF86B4EA; hor = 0xFFC8DEF4; } // day_soft
        }
        java.util.Random rnd = new java.util.Random(preset.hashCode());
        for (int y = 0; y < H; y++) {
            float v = y / (float) (H - 1);
            float h = Math.min(1f, v / 0.5f); // 0 зенит → 1 горизонт
            int grad = h < 0.6f ? lerp(zen, mid, h / 0.6f) : lerp(mid, hor, (h - 0.6f) / 0.4f);
            if (v > 0.5f) grad = lerp(hor, mid, (v - 0.5f) * 1.2f);
            for (int x = 0; x < W; x++) {
                float u = x / (float) W;
                int c = grad;
                if (night) {
                    if (preset.equals("night_nebula")) {
                        float n = fbm(u, v, 4, 6, 7);
                        float m = fbm(u + 0.37f, v, 3, 5, 19);
                        float k = Math.max(0, n - 0.48f) * 2.6f;
                        int neb = lerp(0xFF4A2A8A, 0xFFE05AA8, m);
                        neb = lerp(neb, 0xFF3A8AD8, Math.max(0, m - 0.6f) * 2f);
                        c = lerp(c, neb, Math.min(0.75f, k) * (1 - Math.abs(v - 0.35f)));
                    }
                    if (!preset.equals("night_aurora")) {
                        // млечный путь — светлая полоса под наклоном
                        float band = (float) Math.exp(-Math.pow((v - 0.3f - 0.12f * Math.sin(u * Math.PI * 2)) / 0.07f, 2));
                        float grain = fbm(u, v, 16, 4, 3);
                        c = add(c, (int) (40 * band * grain), (int) (36 * band * grain), (int) (52 * band * grain));
                    }
                } else {
                    // мягкие облака у горизонта и выше
                    float n = fbm(u, v, 6, 6, preset.hashCode());
                    float cover = Math.max(0, n - 0.5f) * 2.4f * (1 - Math.abs(v - 0.38f) * 1.8f);
                    int cloud = preset.equals("day_sunset") ? lerp(0xFFFFD0B8, 0xFFFF9AA8, v * 1.4f) : 0xFFFFFFFF;
                    c = lerp(c, cloud, Math.max(0, Math.min(0.85f, cover)));
                }
                px[y * W + x] = c;
            }
        }
        if (night) {
            // звёзды: мягкие точки, ярче к зениту
            int stars = preset.equals("night_stars") ? 2600 : 1600;
            for (int i = 0; i < stars; i++) {
                int sx = rnd.nextInt(W), sy = rnd.nextInt((int) (H * 0.55f));
                float b = (float) Math.pow(rnd.nextFloat(), 3);
                int r = b > 0.6f ? 2 : 1;
                for (int dy = -r; dy <= r; dy++) for (int dx = -r; dx <= r; dx++) {
                    int xx = Math.floorMod(sx + dx, W), yy = Math.max(0, Math.min(H - 1, sy + dy));
                    float fall = 1f - (float) Math.sqrt(dx * dx + dy * dy) / (r + 0.8f);
                    if (fall <= 0) continue;
                    int a = (int) (255 * b * fall);
                    px[yy * W + xx] = add(px[yy * W + xx], a, a, Math.min(255, a + 20));
                }
            }
        }
        return px;
    }

    /** Слой облаков с прозрачностью (белые, мягкие), бесшовный по кругу. */
    public static int[] clouds(int seed) {
        int[] px = new int[CW * CH];
        for (int y = 0; y < CH; y++) {
            float v = y / (float) (CH - 1);
            float fade = (float) Math.sin(v * Math.PI); // к краям полосы прозрачнее
            for (int x = 0; x < CW; x++) {
                float u = x / (float) CW;
                float n = fbm(u, v, 5, 6, seed);
                float a = Math.max(0, n - 0.47f) * 2.8f * fade;
                int alpha = (int) (Math.min(1f, a) * 230);
                int shade = 235 + (int) (20 * n);
                px[y * CW + x] = alpha << 24 | Math.min(255, shade) << 16 | Math.min(255, shade) << 8 | 255;
            }
        }
        return px;
    }

    // ── загрузка в текстуру (только поток рендера) ───────────

    private static int counter;

    /** Пиксели ARGB → зарегистрированная текстура с линейной фильтрацией. */
    public static Identifier upload(int[] argb, int w, int h, String name) {
        try {
            NativeImage ni = new NativeImage(w, h, false);
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) ni.setPixel(x, y, argb[y * w + x]);
            DynamicTexture tex = new DynamicTexture(() -> "mraz1visual sky " + name, ni);
            Identifier id = Identifier.fromNamespaceAndPath("mraz1visual", "sky/" + name + "_" + (counter++));
            Minecraft.getInstance().getTextureManager().register(id, tex);
            smooth(tex);
            return id;
        } catch (Throwable t) {
            ModuleManager.LOG.warn("Sky texture upload failed", t);
            return null;
        }
    }

    public static void release(Identifier id) {
        if (id != null) Minecraft.getInstance().getTextureManager().release(id);
    }

    /** Белая текстура 4×4 — для цветной геометрии (сияние, мерцающие звёзды) тем же типом рендера. */
    public static Identifier white() {
        if (white == null) {
            int[] px = new int[16];
            java.util.Arrays.fill(px, 0xFFFFFFFF);
            white = upload(px, 4, 4, "white");
        }
        return white;
    }

    private static Identifier white;

    private static void smooth(DynamicTexture t) {
        try {
            ((dev.mraz1visual.mixin.AbstractTextureAccessor) t).mraz1visual$setSampler(
                    com.mojang.blaze3d.systems.RenderSystem.getSamplerCache().getClampToEdge(com.mojang.blaze3d.textures.FilterMode.LINEAR));
        } catch (Throwable ignored) {
        }
    }
}

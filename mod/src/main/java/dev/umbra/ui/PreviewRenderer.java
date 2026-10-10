package dev.umbra.ui;

import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Анимированное превью модуля в шапке окна.
 *
 * Свои превью: положи кадры в config/umbra/previews/&lt;id модуля&gt;/0.png, 1.png, … (20 кадров в секунду)
 * или одну картинку config/umbra/previews/&lt;id&gt;.png. Иначе рисуется встроенная сцена:
 * ночной пейзаж, персонаж и сам эффект модуля.
 */
public final class PreviewRenderer {
    private static final Map<String, List<Frame>> CUSTOM = new HashMap<>();

    private record Frame(Identifier id, int w, int h) {}

    private PreviewRenderer() {}

    // ── палитра сцены ────────────────────────────────────────
    private static final int SKY_TOP = 0xFF0E0D1A, SKY_BOTTOM = 0xFF2B2546;
    private static final int HILL_FAR = 0xFF1B1830, HILL_NEAR = 0xFF15131F;
    private static final int GRASS = 0xFF2F3F2C, DIRT = 0xFF1E1A17;
    private static final int SKIN = 0xFFC99B7A, HAIR = 0xFF3B2A20, SHIRT = 0xFF6E63B5, PANTS = 0xFF2A2A44, SHOES = 0xFF1A1A22;
    private static final int TARGET_SKIN = 0xFF7FAE6C, TARGET_SHIRT = 0xFF3D6E3A;

    public static void render(Draw d, String id, int x, int y, int w, int h, float t, int accent) {
        List<Frame> frames = custom(id);
        if (!frames.isEmpty()) {
            Frame f = frames.get((int) (t * 20) % frames.size());
            // заполняем по ширине/высоте с обрезкой, как object-fit: cover
            float s = Math.max(w / (float) f.w, h / (float) f.h);
            int dw = Math.round(f.w * s), dh = Math.round(f.h * s);
            d.clip(x, y, w, h);
            d.g.blit(RenderPipelines.GUI_TEXTURED, f.id, x + (w - dw) / 2, y + (h - dh) / 2, 0f, 0f, dw, dh, f.w, f.h, f.w, f.h, 0xFFFFFFFF);
            d.unclip();
            return;
        }
        if (ScenePreviews.render(d, id, x, y, w, h, t, accent)) return;
        d.clip(x, y, w, h);
        scene(d, id, x, y, w, h, t, accent);
        d.unclip();
    }

    // ── встроенная сцена ─────────────────────────────────────

    private static void scene(Draw d, String id, int x, int y, int w, int h, float t, int accent) {
        d.gradientV(x, y, w, h, SKY_TOP, SKY_BOTTOM);
        // звёзды
        for (int i = 0; i < 40; i++) {
            float sx = x + (hash(i * 3 + 1) * w);
            float sy = y + (hash(i * 7 + 2) * h * 0.55f);
            float tw = 0.5f + 0.5f * (float) Math.sin(t * (1.2 + hash(i) * 2) + i);
            d.rectF(sx, sy, 0.75f, 0.75f, Colors.withAlpha(0xFFFFFF, (int) (60 + 120 * tw * hash(i * 5))));
        }
        // луна-затмение
        float mx = x + w * 0.80f, my = y + h * 0.40f, mr = h * 0.12f;
        for (int i = 6; i > 0; i--) d.disc(mx, my, mr + i * 1.6f, Colors.withAlpha(accent, 10 + (6 - i) * 6));
        d.disc(mx, my, mr + 0.8f, 0xFFE8E4FF);
        d.disc(mx, my, mr, 0xFF050508);

        float ground = y + h * 0.74f;
        float scroll = moves(id) ? t * 14f : 0f;
        hills(d, x, w, ground, h * 0.20f, 9f, HILL_FAR, scroll * 0.3f, 11);
        hills(d, x, w, ground, h * 0.10f, 6f, HILL_NEAR, scroll * 0.6f, 23);
        // земля из блоков
        float bs = h * 0.09f;
        float off = -(scroll % bs);
        for (float bx = x + off - bs; bx < x + w + bs; bx += bs) {
            d.rectF(bx, ground, bs - 0.25f, bs * 0.25f, GRASS);
            d.rectF(bx, ground + bs * 0.25f, bs - 0.25f, y + h - ground, DIRT);
        }

        float s = h / 64f;               // масштаб персонажа
        float px = x + w * 0.42f, feet = ground;
        float walk = moves(id) ? t * 7f : 0f;
        switch (id) {
            case "jump_circle" -> jumpCircle(d, px, feet, s, t, accent);
            case "china_hat" -> { figure(d, px, feet, s, 0, SHIRT, SKIN); chinaHat(d, px, feet, s, t, accent); }
            case "trails" -> { trail(d, px, feet, s, t, accent); figure(d, px, feet, s, walk, SHIRT, SKIN); }
            case "hit_effects", "crit_indicator", "hit_sounds" -> hit(d, px, feet, s, t, accent, id);
            case "target_esp", "target_hud", "hitboxes" -> target(d, px, feet, s, t, accent, id, x, y, w, h);
            case "trajectories" -> trajectory(d, px, feet, s, t, accent);
            case "cape" -> { figureBack(d, px, feet, s); cape(d, px, feet, s, t, accent); }
            case "keystrokes" -> { figure(d, px, feet, s, walk, SHIRT, SKIN); keystrokes(d, x, y, w, h, t, accent); }
            case "music" -> { figure(d, px, feet, s, 0, SHIRT, SKIN); equalizer(d, x, y, w, h, t, accent); }
            case "crosshair", "zoom" -> crosshair(d, x, y, w, h, t, accent, id);
            case "block_outline", "no_delay", "shulker_preview" -> blocks(d, px, feet, s, t, accent, id);
            case "waypoints" -> { figure(d, px, feet, s, walk, SHIRT, SKIN); waypoint(d, x, y, w, h, t, accent); }
            case "world_effects", "ambience" -> { figure(d, px, feet, s, 0, SHIRT, SKIN); particles(d, x, y, w, h, t, accent); }
            default -> { figure(d, px, feet, s, walk, SHIRT, SKIN); glow(d, px, feet, s, t, accent); }
        }
        // нижнее затемнение под заголовок
        d.gradientV(x, y + h / 2, w, h - h / 2, 0x00000000, 0xE00A0A0E);
    }

    private static boolean moves(String id) {
        return switch (id) {
            case "trails", "keystrokes", "waypoints", "animations", "view_model", "inventory_keys", "no_render" -> true;
            default -> false;
        };
    }

    private static void hills(Draw d, int x, int w, float ground, float amp, float step, int color, float scroll, int seed) {
        float off = -(scroll % step);
        for (float bx = x + off - step; bx < x + w + step; bx += step) {
            int col = (int) Math.floor((bx - off + scroll) / step);
            float hh = amp * (0.35f + 0.65f * hash(col * 31 + seed));
            d.rectF(bx, ground - hh, step, hh, color);
        }
    }

    /** Персонаж «как в майнкрафте», вид спереди. feet — уровень земли. */
    private static void figure(Draw d, float cx, float feet, float s, float walk, int shirt, int skin) {
        float sw = (float) Math.sin(walk) * 3f * s;
        float leg = 12 * s, body = 12 * s, head = 8 * s, limb = 4 * s;
        float top = feet - leg - body - head;
        // ноги
        d.rectF(cx - limb, feet - leg - Math.max(0, sw) * 0.3f, limb, leg, PANTS);
        d.rectF(cx, feet - leg - Math.max(0, -sw) * 0.3f, limb, leg, PANTS);
        d.rectF(cx - limb, feet - 2 * s, limb, 2 * s, SHOES);
        d.rectF(cx, feet - 2 * s, limb, 2 * s, SHOES);
        // тело и руки
        d.rectF(cx - limb, feet - leg - body, limb * 2, body, shirt);
        d.rectF(cx - limb * 2, feet - leg - body + sw * 0.4f, limb, body, Colors.lerp(shirt, 0xFF000000, 0.15f));
        d.rectF(cx + limb, feet - leg - body - sw * 0.4f, limb, body, Colors.lerp(shirt, 0xFF000000, 0.15f));
        d.rectF(cx - limb * 2, feet - leg - 3 * s + sw * 0.4f, limb, 3 * s, skin);
        d.rectF(cx + limb, feet - leg - 3 * s - sw * 0.4f, limb, 3 * s, skin);
        // голова
        d.rectF(cx - head / 2, top, head, head, skin);
        d.rectF(cx - head / 2, top, head, 2.5f * s, HAIR);
        d.rectF(cx - 2.5f * s, top + 4 * s, s, s, 0xFF2B2B44);
        d.rectF(cx + 1.5f * s, top + 4 * s, s, s, 0xFF2B2B44);
    }

    private static void jumpCircle(Draw d, float px, float feet, float s, float t, int accent) {
        float cycle = t % 1.6f;
        float jump = cycle < 0.6f ? (float) Math.sin(cycle / 0.6f * Math.PI) * 10 * s : 0;
        for (int i = 0; i < 3; i++) {
            float p = ((t + i * 0.53f) % 1.6f) / 1.6f;
            float r = 4 * s + p * 22 * s;
            d.ellipseRing(px, feet - s, r, r * 0.28f, 1.1f, Colors.withAlpha(accent, (int) (230 * (1 - p))));
        }
        figure(d, px, feet - jump, s, 0, SHIRT, SKIN);
    }

    private static void chinaHat(Draw d, float px, float feet, float s, float t, int accent) {
        float top = feet - 32 * s;
        float spin = t * 1.5f;
        float rx = 9 * s, ry = 2.2f * s, tip = top - 7 * s;
        for (int i = 0; i < 28; i++) {
            double a = spin + i * Math.PI * 2 / 28;
            float ex = px + (float) Math.cos(a) * rx, ey = top + (float) Math.sin(a) * ry;
            d.line(px, tip, ex, ey, 0.6f, Colors.withAlpha(accent, 70 + (int) (60 * (0.5 + 0.5 * Math.sin(a)))));
        }
        d.ellipseRing(px, top, rx, ry, 0.9f, Colors.withAlpha(accent, 220));
    }

    private static void trail(Draw d, float px, float feet, float s, float t, int accent) {
        float baseY = feet - 14 * s;
        float prevX = px, prevY = baseY;
        for (int i = 1; i < 40; i++) {
            float x = px - i * 1.6f * s;
            float y = baseY + (float) Math.sin(t * 7 - i * 0.35f) * 1.6f * s;
            int a = (int) (220 * (1 - i / 40f));
            d.line(prevX, prevY, x, y, 2.2f * s * (1 - i / 50f), Colors.withAlpha(accent, a));
            prevX = x;
            prevY = y;
        }
    }

    private static void hit(Draw d, float px, float feet, float s, float t, int accent, String id) {
        float cycle = t % 1.4f;
        float tx = px + 26 * s;
        float knock = cycle < 0.25f ? (float) Math.sin(cycle / 0.25f * Math.PI) * 3 * s : 0;
        float swing = cycle < 0.2f ? 1 - cycle / 0.2f : 0;
        figure(d, px, feet, s, 0, SHIRT, SKIN);
        if (swing > 0) d.line(px + 8 * s, feet - 22 * s, px + 18 * s, feet - 26 * s + swing * 6 * s, 1.5f * s, 0xFFDADAE8);
        int red = cycle < 0.3f ? 0xFFB84A4A : TARGET_SHIRT;
        figure(d, tx + knock, feet, s, 0, red, TARGET_SKIN);
        if (cycle < 0.7f) {
            float p = cycle / 0.7f;
            for (int i = 0; i < 14; i++) {
                double a = hash(i * 13) * Math.PI * 2;
                float r = p * (8 + 10 * hash(i * 17)) * s;
                float ex = tx + (float) Math.cos(a) * r, ey = feet - 24 * s + (float) Math.sin(a) * r;
                int c = id.equals("crit_indicator") ? 0xFFFFE08A : accent;
                d.rectF(ex, ey, 1.4f * s, 1.4f * s, Colors.withAlpha(c, (int) (255 * (1 - p))));
            }
        }
        if (id.equals("crit_indicator")) {
            float pulse = 0.5f + 0.5f * (float) Math.sin(t * 6);
            d.ring(tx, feet - 24 * s, 6 * s + pulse * s, 0.8f, Colors.withAlpha(0xFFE08A, 200));
        }
        if (id.equals("hit_sounds") && cycle < 0.6f) {
            float p = cycle / 0.6f;
            for (int i = 0; i < 3; i++) d.ring(tx, feet - 24 * s, (6 + i * 5 + p * 10) * s, 0.7f, Colors.withAlpha(accent, (int) (180 * (1 - p))));
        }
    }

    private static void target(Draw d, float px, float feet, float s, float t, int accent, String id, int x, int y, int w, int h) {
        figure(d, px - 14 * s, feet, s, 0, SHIRT, SKIN);
        float tx = px + 22 * s;
        float bob = (float) Math.sin(t * 2) * s;
        figure(d, tx, feet + bob * 0.2f, s, t * 4, TARGET_SHIRT, TARGET_SKIN);
        switch (id) {
            case "hitboxes" -> {
                float bw = 9 * s, top = feet - 33 * s;
                int c = Colors.withAlpha(accent, 220);
                d.line(tx - bw, top, tx + bw, top, 0.8f, c);
                d.line(tx - bw, feet, tx + bw, feet, 0.8f, c);
                d.line(tx - bw, top, tx - bw, feet, 0.8f, c);
                d.line(tx + bw, top, tx + bw, feet, 0.8f, c);
                d.line(tx, top + 4 * s, tx + 14 * s, top + 4 * s, 0.6f, Colors.withAlpha(0x6FA0FF, 220));
            }
            case "target_hud" -> {
                int cx = Math.round(tx + 16 * s), cy = Math.round(feet - 40 * s);
                d.card(cx, cy, 86, 26, 4, 0xE00C0C12, 0x20FFFFFF);
                d.rect(cx + 4, cy + 4, 18, 18, TARGET_SKIN);
                d.text("Steve", cx + 27, cy + 5, 0xFFE9E9F1);
                float hp = 0.35f + 0.6f * (0.5f + 0.5f * (float) Math.sin(t * 1.3f));
                d.rect(cx + 27, cy + 17, 54, 3, 0x30FFFFFF);
                d.rect(cx + 27, cy + 17, Math.round(54 * hp), 3, accent);
            }
            default -> {
                float r = 10 * s;
                for (int i = 0; i < 3; i++) {
                    double a = t * 2.4 + i * Math.PI * 2 / 3;
                    float ox = (float) Math.cos(a) * r, oy = (float) Math.sin(a) * r * 0.35f;
                    float yy = feet - 16 * s + (float) Math.sin(t * 3 + i) * 10 * s;
                    for (int k = 0; k < 10; k++) {
                        double b = a - k * 0.12;
                        float qx = tx + (float) Math.cos(b) * r, qy = yy + (float) Math.sin(b) * r * 0.35f;
                        d.disc(qx, qy, 1.2f * s * (1 - k / 12f), Colors.withAlpha(accent, (int) (230 * (1 - k / 10f))));
                    }
                    d.disc(tx + ox, yy + oy, 1.4f * s, 0xFFFFFFFF);
                }
            }
        }
    }

    private static void trajectory(Draw d, float px, float feet, float s, float t, int accent) {
        figure(d, px - 20 * s, feet, s, 0, SHIRT, SKIN);
        float x0 = px - 12 * s, y0 = feet - 24 * s;
        float vx = 3.2f * s, vy = -3.6f * s, g = 0.16f * s;
        float prog = (t % 2f) / 2f;
        float lx = x0, ly = y0;
        for (int i = 1; i < 44; i++) {
            float nx = x0 + vx * i, ny = y0 + vy * i + g * i * i;
            if (ny > feet) break;
            if (i % 2 == 0) d.line(lx, ly, nx, ny, 0.9f, Colors.withAlpha(accent, 210));
            lx = nx;
            ly = ny;
        }
        int k = (int) (prog * 40);
        float bx = x0 + vx * k, by = y0 + vy * k + g * k * k;
        if (by <= feet) d.disc(bx, by, 1.6f * s, 0xFF2E7D6B);
        d.ellipseRing(lx, feet - s, 5 * s, 1.4f * s, 0.9f, Colors.withAlpha(accent, 200));
    }

    /** Персонаж со спины (для плаща). */
    private static void figureBack(Draw d, float cx, float feet, float s) {
        float leg = 12 * s, body = 12 * s, head = 8 * s, limb = 4 * s;
        d.rectF(cx - limb, feet - leg, limb, leg, PANTS);
        d.rectF(cx, feet - leg, limb, leg, PANTS);
        d.rectF(cx - limb, feet - leg - body, limb * 2, body, SHIRT);
        d.rectF(cx - limb * 2, feet - leg - body, limb, body, Colors.lerp(SHIRT, 0xFF000000, 0.15f));
        d.rectF(cx + limb, feet - leg - body, limb, body, Colors.lerp(SHIRT, 0xFF000000, 0.15f));
        d.rectF(cx - head / 2, feet - leg - body - head, head, head, HAIR);
    }

    private static void cape(Draw d, float px, float feet, float s, float t, int accent) {
        float top = feet - 23.5f * s;
        int rows = 17;
        int c1 = Colors.lerp(accent, 0xFF000000, 0.45f), c2 = Colors.lerp(accent, 0xFF000000, 0.62f);
        for (int i = 0; i < rows; i++) {
            float k = i / (float) rows;
            float sway = (float) Math.sin(t * 3 - i * 0.35f) * 1.4f * s * k;
            float half = (4.6f + k * 1.6f) * s;
            d.rectF(px - half + sway, top + i * s, half * 2, s + 0.25f, i % 5 == 4 ? c2 : c1);
        }
        float ex = px + (float) Math.sin(t * 3 - 3) * 0.6f * s, ey = top + 7 * s;
        d.disc(ex, ey, 2.6f * s, 0xFFE8E4FF);
        d.disc(ex, ey, 2.0f * s, 0xFF050508);
    }

    private static void keystrokes(Draw d, int x, int y, int w, int h, float t, int accent) {
        int k = 14, gap = 2;
        int bx = x + 16, by = y + 12;
        String[] keys = {"W", "A", "S", "D"};
        int[][] pos = {{1, 0}, {0, 1}, {1, 1}, {2, 1}};
        int pressed = (int) (t * 3) % 4;
        for (int i = 0; i < 4; i++) {
            int kx = bx + pos[i][0] * (k + gap), ky = by + pos[i][1] * (k + gap);
            boolean on = i == 0 || i == pressed;
            d.round(kx, ky, k, k, 3, on ? Colors.withAlpha(accent, 0xD0) : 0xA0101018);
            d.textCenter(keys[i], kx + k / 2, ky + 3, on ? 0xFF0A0A0E : 0xFFE9E9F1);
        }
    }

    private static void equalizer(Draw d, int x, int y, int w, int h, float t, int accent) {
        int bars = 18;
        float bx = x + w * 0.55f, base = y + h * 0.66f, bw = 3.2f;
        for (int i = 0; i < bars; i++) {
            float v = 0.25f + 0.75f * Math.abs((float) Math.sin(t * (2.2f + hash(i) * 3) + i * 0.7f));
            float bh = v * h * 0.35f;
            d.rectF(bx + i * (bw + 1.4f), base - bh, bw, bh, Colors.withAlpha(accent, 120 + (int) (120 * v)));
        }
    }

    private static void crosshair(Draw d, int x, int y, int w, int h, float t, int accent, String id) {
        float z = id.equals("zoom") ? 1 + 0.6f * (0.5f + 0.5f * (float) Math.sin(t * 1.2f)) : 1f;
        float cx = x + w * 0.5f, cy = y + h * 0.42f;
        // дальний персонаж
        float s = h / 64f * 0.55f * z;
        figure(d, cx + 18 * z, y + h * 0.74f, s, 0, TARGET_SHIRT, TARGET_SKIN);
        float gap = 3 + (float) Math.sin(t * 3) * 0.8f, len = 6;
        int c = Colors.withAlpha(accent, 240);
        d.line(cx - gap - len, cy, cx - gap, cy, 1.2f, c);
        d.line(cx + gap, cy, cx + gap + len, cy, 1.2f, c);
        d.line(cx, cy - gap - len, cx, cy - gap, 1.2f, c);
        d.line(cx, cy + gap, cx, cy + gap + len, 1.2f, c);
        d.disc(cx, cy, 0.8f, c);
    }

    private static void blocks(Draw d, float px, float feet, float s, float t, int accent, String id) {
        figure(d, px - 16 * s, feet, s, 0, SHIRT, SKIN);
        float bs = 10 * s;
        int placed = id.equals("no_delay") ? (int) ((t * 6) % 6) + 1 : 3;
        for (int i = 0; i < placed; i++) {
            float bx = px + 2 * s + i * bs, by = feet - bs;
            d.rectF(bx, by, bs - 0.3f, bs - 0.3f, 0xFF4A3B6E);
            d.rectF(bx, by, bs - 0.3f, 1.5f * s, 0xFF5E4C8C);
        }
        if (!id.equals("no_delay")) {
            float bx = px + 2 * s + bs, by = feet - bs;
            float pulse = 0.6f + 0.4f * (float) Math.sin(t * 4);
            int c = Colors.withAlpha(accent, (int) (255 * pulse));
            d.line(bx, by, bx + bs, by, 0.8f, c);
            d.line(bx, by + bs, bx + bs, by + bs, 0.8f, c);
            d.line(bx, by, bx, by + bs, 0.8f, c);
            d.line(bx + bs, by, bx + bs, by + bs, 0.8f, c);
            d.rectF(bx, by, bs, bs, Colors.withAlpha(accent, 0x28));
        }
    }

    private static void waypoint(Draw d, int x, int y, int w, int h, float t, int accent) {
        float wx = x + w * 0.7f, wy = y + h * 0.35f;
        float bob = (float) Math.sin(t * 2) * 2;
        d.line(wx, wy + bob, wx, y + h * 0.74f, 0.8f, Colors.withAlpha(accent, 120));
        d.disc(wx, wy + bob, 4, Colors.withAlpha(accent, 230));
        d.disc(wx, wy + bob, 1.6f, 0xFF0A0A0E);
        d.textCenter("Дом · 128 м", Math.round(wx), Math.round(wy + bob - 14), 0xFFE9E9F1);
    }

    private static void particles(Draw d, int x, int y, int w, int h, float t, int accent) {
        for (int i = 0; i < 50; i++) {
            float fx = x + hash(i * 11) * w;
            float fy = y + ((hash(i * 19) * h + t * (8 + 10 * hash(i))) % h);
            d.rectF(fx + (float) Math.sin(t + i) * 2, fy, 1, 1, Colors.withAlpha(0xFFFFFF, 90 + (int) (120 * hash(i * 3))));
        }
        d.gradientV(x, y, w, h / 2, Colors.withAlpha(accent, 0x30), 0x00000000);
    }

    private static void glow(Draw d, float px, float feet, float s, float t, int accent) {
        float pulse = 0.5f + 0.5f * (float) Math.sin(t * 2);
        d.ellipseRing(px, feet - s, 10 * s + pulse * 2 * s, 2.6f * s, 0.9f, Colors.withAlpha(accent, 140 + (int) (80 * pulse)));
    }

    private static float hash(int n) {
        n = (n << 13) ^ n;
        int v = (n * (n * n * 15731 + 789221) + 1376312589) & 0x7fffffff;
        return v / (float) 0x7fffffff;
    }

    // ── свои превью из папки ─────────────────────────────────

    private static List<Frame> custom(String id) {
        return CUSTOM.computeIfAbsent(id, PreviewRenderer::load);
    }

    /** Сбросить кэш (например, после добавления файлов). */
    public static void reload() {
        CUSTOM.clear();
    }

    private static List<Frame> load(String id) {
        List<Frame> out = new ArrayList<>();
        try {
            Path dir = FabricLoader.getInstance().getConfigDir().resolve("umbra/previews");
            Path folder = dir.resolve(id);
            List<Path> files = new ArrayList<>();
            if (Files.isDirectory(folder)) {
                for (int i = 0; i < 600; i++) {
                    Path p = folder.resolve(i + ".png");
                    if (!Files.exists(p)) break;
                    files.add(p);
                }
            } else if (Files.exists(dir.resolve(id + ".png"))) {
                files.add(dir.resolve(id + ".png"));
            }
            for (int i = 0; i < files.size(); i++) {
                try (InputStream in = Files.newInputStream(files.get(i))) {
                    NativeImage img = NativeImage.read(in);
                    Identifier tex = Identifier.fromNamespaceAndPath("umbra", "preview/" + id + "/" + i);
                    int fw = img.getWidth(), fh = img.getHeight();
                    Minecraft.getInstance().getTextureManager().register(tex, new DynamicTexture(() -> "umbra preview", img));
                    out.add(new Frame(tex, fw, fh));
                }
            }
        } catch (Exception ignored) {
            out.clear();
        }
        return out;
    }
}

package dev.umbra.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Превью-сцены «как в игре»: заранее отрисованные кадры (лес, персонаж, противник, вид от первого лица)
 * из assets/umbra/textures/preview/*.png и точки привязки из assets/umbra/previews/scenes.json.
 * Поверх картинки анимируется сам эффект модуля, привязанный к персонажу на кадре.
 */
final class ScenePreviews {
    private static JsonObject scenes;
    private static boolean loaded;
    /** Текущий кадр анимации сцены и фаза цикла 0..1 (рисуем в одном потоке). */
    private static int frame;
    private static float loop, loopSec = 1f;
    private static final float FPS = 12f;

    private ScenePreviews() {}

    private static JsonObject scenes() {
        if (!loaded) {
            loaded = true;
            try (InputStream in = ScenePreviews.class.getResourceAsStream("/assets/umbra/previews/scenes.json")) {
                if (in != null) scenes = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (Exception ignored) {
                scenes = null;
            }
        }
        return scenes;
    }

    /** Сцена для модуля; null — у модуля своя (старая) отрисовка. */
    private static String sceneFor(String id) {
        return switch (id) {
            case "jump_circle", "china_hat", "hud", "keystrokes", "music", "client", "demo" -> "front";
            case "chat_helpers", "waypoints_night" -> "night";
            case "cape", "trails" -> "back";
            case "hit_effects", "hit_sounds", "crit_indicator", "target_esp", "target_hud", "hitboxes",
                 "inventory_keys" -> "duel";
            case "crosshair", "zoom", "trajectories", "waypoints", "no_render", "item_cooldowns", "view_model", "animations" -> "fp";
            case "block_outline", "no_delay", "shulker_preview" -> "blocks";
            case "world_effects", "ambience" -> "night";
            default -> "front";
        };
    }

    /** Перевод координат кадра (px картинки) в единицы окна. */
    private static final class Map {
        float ox, oy, k;
        float x(float ix) { return ox + ix * k; }
        float y(float iy) { return oy + iy * k; }
    }

    static boolean render(Draw d, String id, int x, int y, int w, int h, float t, int accent) {
        JsonObject all = scenes();
        if (all == null) return false;
        String name = sceneFor(id);
        JsonObject sc = all.getAsJsonObject(name);
        if (sc == null) return false;
        int iw = sc.get("w").getAsInt(), ih = sc.get("h").getAsInt();

        // медленный «наезд камеры», у зума — заметный
        float zoom = 1.03f + 0.02f * (float) Math.sin(t * 0.35f);
        if (id.equals("zoom")) zoom = 1.0f + 0.9f * (0.5f + 0.5f * (float) Math.sin(t * 0.9f));
        Map m = new Map();
        m.k = Math.max(w / (float) iw, h / (float) ih) * zoom;
        m.ox = x + (w - iw * m.k) / 2f;
        m.oy = y + (h - ih * m.k) / 2f;

        d.clip(x, y, w, h);
        Identifier tex = Identifier.fromNamespaceAndPath("umbra", "textures/preview/" + name + ".png");
        d.g.pose().pushMatrix();
        d.g.pose().translate(m.ox, m.oy);
        d.g.pose().scale(m.k, m.k);
        d.g.blit(RenderPipelines.GUI_TEXTURED, tex, 0, 0, 0f, 0f, iw, ih, iw, ih, iw, ih, 0xFFFFFFFF);
        int n = sc.has("frames") ? Math.max(1, sc.get("frames").getAsInt()) : 1;
        float ft = (t * FPS) % n;
        frame = (int) ft;
        loop = ft / n;
        loopSec = n / FPS;
        if (sc.has("anim")) {
            JsonArray a = sc.getAsJsonArray("anim");
            int ax = a.get(0).getAsInt(), ay = a.get(1).getAsInt(), aw = a.get(2).getAsInt(), ah = a.get(3).getAsInt();
            Identifier strip = Identifier.fromNamespaceAndPath("umbra", "textures/preview/" + name + "_anim.png");
            d.g.blit(RenderPipelines.GUI_TEXTURED, strip, ax, ay, 0f, (float) (frame * ah), aw, ah, aw, ah, aw, ah * n, 0xFFFFFFFF);
        }
        d.g.pose().popMatrix();
        if (name.equals("duel") || name.equals("night")) eclipse(d, x + w - 74, y + 44, h * 0.10f, t, accent);

        effect(d, id, sc, m, x, y, w, h, t, accent);
        // нижнее затемнение под заголовок
        int gy = y + Math.round(h * 0.58f);
        d.gradientV(x, gy, w, y + h - gy, 0x00000000, 0xE80A0A0E);
        d.unclip();
        return true;
    }

    // ── точки привязки ───────────────────────────────────────

    private static float[] pt(JsonElement e, Map m) {
        JsonArray a = e.getAsJsonArray();
        return new float[]{m.x(a.get(0).getAsFloat()), m.y(a.get(1).getAsFloat())};
    }

    private static float[][] pts(JsonElement e, Map m) {
        JsonArray a = e.getAsJsonArray();
        float[][] out = new float[a.size()][];
        for (int i = 0; i < a.size(); i++) out[i] = pt(a.get(i), m);
        return out;
    }

    private record Ent(float[] feet, float[] chest, float[] head, float[] tip, float[][] box, float[][] ring, float[][] headring) {
        float height() { return feet[1] - head[1]; }
    }

    /** Секунды с момента события (кадр key) в текущем цикле анимации. */
    private static float since(JsonObject sc, String key) {
        if (!sc.has(key)) return loop * loopSec;
        float ev = sc.get(key).getAsInt() / FPS;
        float now = loop * loopSec;
        return ((now - ev) % loopSec + loopSec) % loopSec;
    }

    private static Ent ent(JsonObject sc, String key, Map m) {
        JsonObject o = sc.has("per_frame")
                ? sc.getAsJsonArray("per_frame").get(Math.min(frame, sc.getAsJsonArray("per_frame").size() - 1)).getAsJsonObject().getAsJsonObject(key)
                : sc.getAsJsonObject(key);
        return new Ent(pt(o.get("feet"), m), pt(o.get("chest"), m), pt(o.get("head"), m), pt(o.get("tip"), m),
                pts(o.get("box"), m), pts(o.get("ring"), m), pts(o.get("headring"), m));
    }

    // ── эффекты ──────────────────────────────────────────────

    private static void effect(Draw d, String id, JsonObject sc, Map m, int x, int y, int w, int h, float t, int accent) {
        switch (id) {
            case "jump_circle" -> jumpCircle(d, ent(sc, "player", m), since(sc, "land_frame"), accent);
            case "china_hat" -> chinaHat(d, ent(sc, "player", m), t, accent);
            case "cape" -> cape(d, sc, m, t, accent);
            case "trails" -> trail(d, ent(sc, "player", m), t, accent);
            case "hit_effects" -> hitParticles(d, ent(sc, "target", m), since(sc, "hit_frame"), accent, false);
            case "crit_indicator" -> hitParticles(d, ent(sc, "target", m), since(sc, "hit_frame"), 0xFFFFE08A, true);
            case "hit_sounds" -> soundRings(d, ent(sc, "target", m), since(sc, "hit_frame"), accent);
            case "target_esp" -> targetEsp(d, ent(sc, "target", m), t, accent);
            case "hitboxes" -> { hitbox(d, ent(sc, "target", m), accent); hitbox(d, ent(sc, "player", m), 0xFF9AD0FF); }
            case "target_hud" -> targetHud(d, ent(sc, "target", m), t, accent);
            case "trajectories" -> trajectory(d, pts(sc.get("path"), m), pts(sc.get("land"), m), t, accent);
            case "crosshair", "zoom" -> crosshair(d, x + w / 2f, y + h / 2f, t, accent);
            case "waypoints" -> waypoint(d, pts(sc.get("waypoint"), m), t, accent);
            case "block_outline" -> {
                JsonArray bl = sc.getAsJsonArray("blocks");
                int cur = Math.min(bl.size() - 1, (int) (loop * 6.5f));
                float[][] c = pts(bl.get(cur), m);
                fill(d, new float[][]{c[4], c[5], c[7], c[6]}, Colors.withAlpha(accent, 50));
                outline(d, c, accent, 1f);
            }
            case "no_delay" -> placing(d, sc.getAsJsonArray("blocks"), m, t, accent);
            case "shulker_preview" -> shulker(d, x, y, w, h, accent);
            case "item_cooldowns", "inventory_keys" -> hotbar(d, x, y, w, h, t, accent, id.equals("item_cooldowns"));
            case "chat_helpers" -> chat(d, x, y, h);
            case "no_render" -> noRender(d, x, y, w, h, t);
            case "keystrokes" -> keystrokes(d, x + 14, y + 12, t, accent);
            case "hud" -> hud(d, x, y, accent);
            case "music" -> music(d, x, y, w, t, accent);
            case "world_effects", "ambience" -> particles(d, x, y, w, h, t, accent);
            case "animations", "view_model" -> swing(d, x, y, w, h, t, accent);
            default -> glow(d, ent(sc, "player", m), t, accent);
        }
    }

    private static void poly(Draw d, float[][] p, float scale, float[] c, float thick, int color) {
        for (int i = 1; i < p.length; i++) {
            float x0 = c[0] + (p[i - 1][0] - c[0]) * scale, y0 = c[1] + (p[i - 1][1] - c[1]) * scale;
            float x1 = c[0] + (p[i][0] - c[0]) * scale, y1 = c[1] + (p[i][1] - c[1]) * scale;
            d.line(x0, y0, x1, y1, thick, color);
        }
    }

    /** Заливка выпуклого многоугольника горизонтальными полосками. */
    private static void fill(Draw d, float[][] p, int color) {
        float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (float[] q : p) { minY = Math.min(minY, q[1]); maxY = Math.max(maxY, q[1]); }
        for (float yy = minY; yy < maxY; yy += 0.5f) {
            float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
            for (int i = 0; i < p.length; i++) {
                float[] a = p[i], b = p[(i + 1) % p.length];
                if ((a[1] <= yy && b[1] > yy) || (b[1] <= yy && a[1] > yy)) {
                    float xx = a[0] + (yy - a[1]) / (b[1] - a[1]) * (b[0] - a[0]);
                    lo = Math.min(lo, xx);
                    hi = Math.max(hi, xx);
                }
            }
            if (hi > lo) d.rectF(lo, yy, hi - lo, 0.5f, color);
        }
    }

    private static void jumpCircle(Draw d, Ent p, float t, int accent) {
        for (int i = 0; i < 3; i++) {
            float k = (t - i * 0.12f) / 0.75f;
            if (k < 0 || k > 1) continue;
            int a = (int) (240 * (1 - k));
            poly(d, p.ring, 0.25f + k * 1.5f, p.feet, 1.4f, Colors.withAlpha(accent, a));
            poly(d, p.ring, 0.25f + k * 1.5f, p.feet, 3.5f, Colors.withAlpha(accent, a / 5));
        }
    }

    private static void chinaHat(Draw d, Ent p, float t, int accent) {
        float[][] r = p.headring;
        float lift = p.height() * 0.04f;
        float[] tip = {p.tip[0], p.tip[1] - lift};
        float[][] cone = new float[r.length][];
        for (int i = 0; i < r.length; i++) cone[i] = new float[]{r[i][0], r[i][1] - lift};
        fill(d, cone, Colors.withAlpha(accent, 40));
        int spin = (int) (t * 8) % r.length;
        for (int i = 0; i < r.length; i++) {
            boolean hi = Math.abs(i - spin) < 3;
            d.line(tip[0], tip[1], cone[i][0], cone[i][1], 0.6f, Colors.withAlpha(accent, hi ? 220 : 90));
        }
        poly(d, cone, 1f, cone[0], 1.0f, Colors.withAlpha(accent, 230));
    }

    private static void cape(Draw d, JsonObject sc, Map m, float t, int accent) {
        float[][] top = pts(sc.get("cape_top"), m), a = pts(sc.get("cape_bot_a"), m), b = pts(sc.get("cape_bot_b"), m);
        float k = 0.5f + 0.5f * (float) Math.sin(t * 2.2f);
        float[] bl = {a[0][0] + (b[0][0] - a[0][0]) * k, a[0][1] + (b[0][1] - a[0][1]) * k};
        float[] br = {a[1][0] + (b[1][0] - a[1][0]) * k, a[1][1] + (b[1][1] - a[1][1]) * k};
        float[][] quad = {top[0], top[1], br, bl};
        fill(d, quad, Colors.lerp(accent, 0xFF000000, 0.55f));
        // светлая кромка и затмение по центру
        d.line(top[0][0], top[0][1], top[1][0], top[1][1], 1f, Colors.lerp(accent, 0xFFFFFFFF, 0.2f));
        d.line(bl[0], bl[1], br[0], br[1], 1f, Colors.lerp(accent, 0xFF000000, 0.75f));
        float cx = (top[0][0] + top[1][0] + bl[0] + br[0]) / 4f, cy = (top[0][1] + top[1][1]) / 2f * 0.6f + (bl[1] + br[1]) / 2f * 0.4f;
        float r = Math.abs(top[0][0] - top[1][0]) * 0.18f;
        d.disc(cx, cy, r * 1.3f, 0xFFE8E4FF);
        d.disc(cx, cy, r, 0xFF050508);
    }

    private static void trail(Draw d, Ent p, float t, int accent) {
        float sx = p.chest[0], sy = (p.chest[1] + p.feet[1]) / 2f;
        float len = p.height() * 3.2f;
        float px = sx, py = sy;
        for (int i = 1; i <= 50; i++) {
            float k = i / 50f;
            float nx = sx - len * k * 0.9f;
            float ny = sy + len * k * 0.45f + (float) Math.sin(t * 6 - k * 9) * p.height() * 0.08f;
            int a = (int) (230 * (1 - k));
            d.line(px, py, nx, ny, Math.max(0.6f, p.height() * 0.09f * (1 - k)), Colors.withAlpha(accent, a));
            px = nx;
            py = ny;
        }
    }

    private static void hitParticles(Draw d, Ent e, float t, int color, boolean crit) {
        float k = t / 0.7f;
        if (k > 1) return;
        float s = e.height();
        for (int i = 0; i < 22; i++) {
            double a = hash(i * 13) * Math.PI * 2;
            float r = k * (0.25f + 0.55f * hash(i * 17)) * s;
            float px = e.chest[0] + (float) Math.cos(a) * r, py = e.chest[1] + (float) Math.sin(a) * r * 0.8f - k * k * s * 0.15f;
            float size = (crit ? 1.6f : 1.3f) * (1 - k * 0.5f);
            int alpha = (int) (255 * (1 - k));
            if (crit) {
                d.line(px - size, py, px + size, py, 0.5f, Colors.withAlpha(color, alpha));
                d.line(px, py - size, px, py + size, 0.5f, Colors.withAlpha(color, alpha));
            } else {
                d.rectF(px - size / 2, py - size / 2, size, size, Colors.withAlpha(color, alpha));
            }
        }
    }

    private static void soundRings(Draw d, Ent e, float t, int accent) {
        float k = t / 0.8f;
        if (k > 1) return;
        for (int i = 0; i < 3; i++) {
            float kk = Math.min(1, k + i * 0.18f);
            d.ring(e.chest[0], e.chest[1], e.height() * (0.15f + kk * 0.5f), 0.7f, Colors.withAlpha(accent, (int) (200 * (1 - kk))));
        }
    }

    private static void targetEsp(Draw d, Ent e, float t, int accent) {
        float[][] r = e.ring;
        int n = r.length - 1;
        for (int s = 0; s < 3; s++) {
            float phase = t * 1.6f + s * n / 3f * 0.25f;
            float lift = (0.5f + 0.5f * (float) Math.sin(t * 2.2f + s * 2.1f)) * (e.feet[1] - e.head[1]) * 0.9f;
            for (int k = 0; k < 12; k++) {
                float idx = ((phase * 4 - k * 0.35f) % n + n) % n;
                int i0 = (int) idx;
                float f = idx - i0;
                float[] a = r[i0], b = r[(i0 + 1) % n];
                float px = e.feet[0] + ((a[0] + (b[0] - a[0]) * f) - e.feet[0]) * 0.75f;
                float py = e.feet[1] + ((a[1] + (b[1] - a[1]) * f) - e.feet[1]) * 0.75f - lift;
                float sz = e.height() * 0.05f * (1 - k / 13f);
                if (k == 0) {
                    d.disc(px, py, sz * 3.2f, Colors.withAlpha(0xFFFFFF, 26));
                    d.disc(px, py, sz * 2.0f, Colors.withAlpha(0xFFFFFF, 50));
                }
                d.disc(px, py, sz, Colors.withAlpha(k == 0 ? 0xFFFFFF : accent, (int) (230 * (1 - k / 12f))));
            }
        }
    }

    private static final int[][] EDGES = {{0, 1}, {0, 2}, {1, 3}, {2, 3}, {4, 5}, {4, 6}, {5, 7}, {6, 7}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};

    private static void hitbox(Draw d, Ent e, int color) {
        for (int[] ed : EDGES) d.line(e.box[ed[0]][0], e.box[ed[0]][1], e.box[ed[1]][0], e.box[ed[1]][1], 0.8f, Colors.withAlpha(color, 230));
    }

    private static void outline(Draw d, float[][] c, int accent, float a) {
        for (int[] ed : EDGES) d.line(c[ed[0]][0], c[ed[0]][1], c[ed[1]][0], c[ed[1]][1], 1.0f, Colors.withAlpha(accent, (int) (240 * a)));
    }

    private static void placing(Draw d, JsonArray blocks, Map m, float t, int accent) {
        int n = blocks.size();
        float rate = 6.5f;
        int cur = Math.min(n - 1, (int) (loop * rate));
        float k = (loop * rate) % 1f;
        outline(d, pts(blocks.get(cur), m), 0xFFFFFFFF, 1f - k * 0.7f);
        float[][] c = pts(blocks.get(cur), m);
        float[][] top = {c[4], c[5], c[7], c[6]};
        fill(d, top, Colors.withAlpha(accent, (int) (90 * (1 - k))));
    }

    private static void targetHud(Draw d, Ent e, float t, int accent) {
        int cx = Math.round(e.head[0] - 100), cy = Math.round(e.head[1] - 8);
        d.card(cx, cy, 86, 28, 4, 0xE00C0C12, 0x20FFFFFF);
        d.rect(cx + 5, cy + 5, 18, 18, 0xFF3D6E3A);
        d.rect(cx + 9, cy + 11, 3, 2, 0xFFFFFFFF);
        d.rect(cx + 16, cy + 11, 3, 2, 0xFFFFFFFF);
        d.text("Rival", cx + 28, cy + 6, 0xFFE9E9F1);
        float hp = 0.25f + 0.7f * (0.5f + 0.5f * (float) Math.sin(t * 1.3f));
        d.rect(cx + 28, cy + 18, 52, 3, 0x30FFFFFF);
        d.rect(cx + 28, cy + 18, Math.round(52 * hp), 3, accent);
        d.text(Math.round(hp * 20) + "", cx + 28 + 40, cy + 6, 0xFF9B9BAB);
    }

    private static void trajectory(Draw d, float[][] path, float[][] land, float t, int accent) {
        for (int i = 1; i < path.length; i++) {
            d.line(path[i - 1][0], path[i - 1][1], path[i][0], path[i][1], 3.0f, Colors.withAlpha(accent, 45));
            d.line(path[i - 1][0], path[i - 1][1], path[i][0], path[i][1], 1.2f, Colors.withAlpha(accent, 235));
        }
        float[] c = land[0].clone();
        float cxs = 0, cys = 0;
        for (float[] q : land) { cxs += q[0]; cys += q[1]; }
        c[0] = cxs / land.length;
        c[1] = cys / land.length;
        poly(d, land, 1f, c, 1.1f, Colors.withAlpha(accent, 230));
        int k = (int) ((t % 1.8f) / 1.8f * (path.length - 1));
        d.disc(path[k][0], path[k][1], 1.8f, 0xFF2E7D6B);
        d.disc(path[k][0], path[k][1], 1.0f, 0xFF7FE0C0);
    }

    private static void crosshair(Draw d, float cx, float cy, float t, int accent) {
        float gap = 3 + (float) Math.sin(t * 3) * 0.8f, len = 6;
        int c = Colors.withAlpha(accent, 245);
        d.line(cx - gap - len, cy, cx - gap, cy, 1.2f, c);
        d.line(cx + gap, cy, cx + gap + len, cy, 1.2f, c);
        d.line(cx, cy - gap - len, cx, cy - gap, 1.2f, c);
        d.line(cx, cy + gap, cx, cy + gap + len, 1.2f, c);
        d.disc(cx, cy, 0.8f, c);
    }

    private static void waypoint(Draw d, float[][] p, float t, int accent) {
        float x0 = p[0][0], y0 = p[0][1];
        d.line(x0, y0, p[1][0], p[1][1], 2.4f, Colors.withAlpha(accent, 50));
        d.line(x0, y0, p[1][0], p[1][1], 0.8f, Colors.withAlpha(accent, 170));
        float bob = (float) Math.sin(t * 2) * 1.5f;
        float my = y0 - 22 + bob;
        d.line(x0, my - 5, x0 + 5, my, 1f, accent);
        d.line(x0 + 5, my, x0, my + 5, 1f, accent);
        d.line(x0, my + 5, x0 - 5, my, 1f, accent);
        d.line(x0 - 5, my, x0, my - 5, 1f, accent);
        d.textCenter("Дом · 64 м", Math.round(x0), Math.round(my - 16), 0xFFE9E9F1);
    }

    private static void shulker(Draw d, int x, int y, int w, int h, int accent) {
        int cw = 9 * 14 + 10, ch = 3 * 14 + 22;
        int cx = x + w - cw - 24, cy = y + 14;
        d.card(cx, cy, cw, ch, 4, 0xF0141020, Colors.withAlpha(accent, 0x80));
        d.text("Шалкер: ресурсы", cx + 6, cy + 5, 0xFFE9E9F1);
        int[] cols = {0xFF6A6A72, 0xFFB08850, 0xFF4FB0C6, 0xFFE0C050, 0xFF7A4FB0};
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                int sx = cx + 5 + c * 14, sy = cy + 17 + r * 14;
                d.rect(sx, sy, 13, 13, 0x40FFFFFF);
                if (hashI(r * 9 + c) % 3 != 0) d.rect(sx + 3, sy + 3, 7, 7, cols[hashI(r * 9 + c + 5) % cols.length]);
            }
        }
    }

    private static void hotbar(Draw d, int x, int y, int w, int h, float t, int accent, boolean cooldowns) {
        int slot = 15, n = 9, bw = n * slot + 2;
        int bx = x + Math.round(w * 0.62f) - bw / 2, by = y + h - slot - 30;
        d.round(bx - 1, by - 1, bw + 2, slot + 2, 2, 0xA0000000);
        int[] cols = {0xFF7FE0C0, 0xFFE0C050, 0xFFB0B0C0, 0xFFE0E0E0, 0xFF6FA0FF, 0xFFC08040, 0xFF9AD070, 0xFFE07070, 0xFF8070E0};
        for (int i = 0; i < n; i++) {
            int sx = bx + 1 + i * slot;
            d.rect(sx, by, slot - 1, slot - 1, 0x30FFFFFF);
            d.rect(sx + 4, by + 4, slot - 9, slot - 9, cols[i]);
        }
        int sel = cooldowns ? 0 : (int) (t * 1.2f) % n;
        d.line(bx + 1 + sel * slot - 0.5f, by - 0.5f, bx + 1 + sel * slot + slot - 0.5f, by - 0.5f, 1f, 0xFFFFFFFF);
        if (cooldowns) {
            float k = (t % 2.5f) / 2.5f;
            int s1 = 0, s2 = 4;
            for (int s : new int[]{s1, s2}) {
                int sx = bx + 1 + s * slot;
                int hh = Math.round((slot - 1) * (1 - k));
                d.rect(sx, by + (slot - 1 - hh), slot - 1, hh, 0xA0FFFFFF);
            }
            d.text(String.format(java.util.Locale.ROOT, "%.1f", (1 - k) * 2.5f), bx + 2, by - 11, accent);
        } else {
            // тотем во второй руке
            int ox = bx - slot - 8;
            d.rect(ox, by, slot - 1, slot - 1, 0x30FFFFFF);
            float pulse = 0.5f + 0.5f * (float) Math.sin(t * 4);
            d.rect(ox + 4, by + 3, slot - 9, slot - 7, Colors.lerp(0xFFE0C050, 0xFFFFF0A0, pulse));
            d.text("R", ox + 4, by - 11, accent);
        }
    }

    private static void chat(Draw d, int x, int y, int h) {
        String[] lines = {"<Rival> gg", "Ты убил Rival", "[Umbra] gg wp, Rival"};
        int by = y + Math.round(h * 0.22f);
        for (int i = 0; i < lines.length; i++) {
            d.rect(x + 8, by + i * 10, 120, 10, 0x70000000);
            d.text(lines[i], x + 10, by + i * 10 + 1, i == 2 ? 0xFFB8B4F2 : 0xFFE9E9F1);
        }
    }

    private static void noRender(Draw d, int x, int y, int w, int h, float t) {
        int half = w / 2;
        for (int i = 0; i < 26; i++) {
            float fx = x + hash(i * 7) * half;
            float fh = h * (0.25f + 0.25f * hash(i * 3)) * (0.8f + 0.2f * (float) Math.sin(t * 6 + i));
            d.gradientV(Math.round(fx), Math.round(y + h - fh), 10, Math.round(fh), 0x00FF8030, 0xB0FF5010);
        }
        d.rect(x + half, y, 1, h, 0xC0FFFFFF);
        d.text("ВАНИЛЬ", x + half - 46, y + 10, 0xFFFFFFFF);
        d.text("UMBRA", x + half + 8, y + 10, 0xFFFFFFFF);
    }

    private static void keystrokes(Draw d, int bx, int by, float t, int accent) {
        int k = 15, gap = 2;
        String[] keys = {"W", "A", "S", "D"};
        int[][] pos = {{1, 0}, {0, 1}, {1, 1}, {2, 1}};
        int pressed = (int) (t * 3) % 4;
        for (int i = 0; i < 4; i++) {
            int kx = bx + pos[i][0] * (k + gap), ky = by + pos[i][1] * (k + gap);
            boolean on = i == 0 || i == pressed;
            d.round(kx, ky, k, k, 3, on ? Colors.withAlpha(accent, 0xD8) : 0xB0101018);
            d.textCenter(keys[i], kx + k / 2, ky + 4, on ? 0xFF0A0A0E : 0xFFE9E9F1);
        }
        d.round(bx, by + 2 * (k + gap), 3 * k + 2 * gap, 10, 3, 0xB0101018);
        d.textCenter((8 + (int) (t * 2) % 5) + " CPS", bx + (3 * k + 2 * gap) / 2, by + 2 * (k + gap) + 1, 0xFFE9E9F1);
    }

    private static void hud(Draw d, int x, int y, int accent) {
        d.round(x + 12, y + 12, 92, 30, 4, 0xA0101018);
        d.text("144 FPS", x + 18, y + 16, accent);
        d.text("XYZ 56 / 17 / 56", x + 18, y + 28, 0xFFE9E9F1);
    }

    private static void music(Draw d, int x, int y, int w, float t, int accent) {
        int cw = 150, cx = x + w - cw - 16, cy = y + 30;
        d.card(cx, cy, cw, 34, 5, 0xE00C0C12, 0x20FFFFFF);
        d.round(cx + 5, cy + 5, 24, 24, 4, Colors.lerp(accent, 0xFF000000, 0.4f));
        for (int i = 0; i < 5; i++) {
            float v = 0.3f + 0.7f * Math.abs((float) Math.sin(t * 3 + i));
            d.rectF(cx + 9 + i * 3.6f, cy + 26 - v * 16, 2.4f, v * 16, 0xFFE9E9F1);
        }
        d.text("Night Drive", Fonts.Kind.BOLD, cx + 35, cy + 8, 0xFFE9E9F1);
        d.text("Spotify", cx + 35, cy + 19, 0xFF9B9BAB);
        float p = (t * 0.05f) % 1f;
        d.rect(cx + 35, cy + 29, cw - 42, 1, 0x30FFFFFF);
        d.rect(cx + 35, cy + 29, Math.round((cw - 42) * p), 1, accent);
    }

    private static void particles(Draw d, int x, int y, int w, int h, float t, int accent) {
        for (int i = 0; i < 70; i++) {
            float fx = x + hash(i * 11) * w + (float) Math.sin(t + i) * 3;
            float fy = y + ((hash(i * 19) * h + t * (10 + 12 * hash(i))) % h);
            boolean fire = i % 3 == 0;
            float glow = 0.5f + 0.5f * (float) Math.sin(t * 3 + i);
            d.rectF(fx, fy, 1.2f, 1.2f, fire ? Colors.withAlpha(accent, (int) (120 + 120 * glow)) : Colors.withAlpha(0xFFFFFF, 160));
        }
    }

    private static void swing(Draw d, int x, int y, int w, int h, float t, int accent) {
        float k = (t % 1.0f);
        if (k > 0.35f) return;
        float p = k / 0.35f;
        float cx = x + w * 0.80f, cy = y + h * 0.78f;
        for (int i = 0; i < 14; i++) {
            float a0 = (float) (Math.PI * (1.05 + 0.5 * (p - i * 0.03)));
            float r = h * 0.55f;
            float px = cx + (float) Math.cos(a0) * r, py = cy + (float) Math.sin(a0) * r;
            d.disc(px, py, 1.6f * (1 - i / 14f), Colors.withAlpha(accent, (int) (200 * (1 - i / 14f) * (1 - p))));
        }
    }

    private static void glow(Draw d, Ent p, float t, int accent) {
        float pulse = 0.5f + 0.5f * (float) Math.sin(t * 2);
        poly(d, p.ring, 0.55f + pulse * 0.15f, p.feet, 1.2f, Colors.withAlpha(accent, 150 + (int) (80 * pulse)));
    }

    /** Затмение в небе ночных сцен. */
    private static void eclipse(Draw d, float cx, float cy, float r, float t, int accent) {
        for (int i = 6; i > 0; i--) d.disc(cx, cy, r + i * 1.8f, Colors.withAlpha(accent, 6 + (6 - i) * 5));
        d.disc(cx, cy, r + 0.9f, 0xFFE8E4FF);
        d.disc(cx, cy, r, 0xFF040406);
    }

    private static float hash(int n) {
        n = (n << 13) ^ n;
        int v = (n * (n * n * 15731 + 789221) + 1376312589) & 0x7fffffff;
        return v / (float) 0x7fffffff;
    }

    private static int hashI(int n) {
        return (int) (hash(n) * 1000);
    }
}

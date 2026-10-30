package dev.mraz1visual.ui;

import dev.mraz1visual.core.Module;
import dev.mraz1visual.setting.ColorSetting;
import dev.mraz1visual.setting.EnumSetting;
import dev.mraz1visual.setting.Setting;
import dev.mraz1visual.setting.SliderSetting;

import java.util.Map;

/**
 * Живое превью модуля в баннере меню: ночная сцена (небо, кирпичная стена, фонари, мостовая), манекен игрока
 * и манекен-цель. Сценарий зависит от модуля (бой, движение, косметика, HUD, атмосфера) и длится 3–6 с по кругу;
 * цвет, стиль и размер берутся из настроек модуля — превью сразу отвечает на изменения.
 * Слои двигаются с параллаксом от мыши, камера «дышит». Всё рисуется 2D-примитивами окна: без отдельного буфера
 * и без аллокаций в кадре, поэтому превью почти ничего не стоит по FPS.
 */
public final class PreviewScene {
    /** Тип сценария. */
    public enum Kind { COMBAT, MOVE, COSMETIC, HUD, ATMOSPHERE, GENERIC }

    private static final Map<String, Kind> KIND = Map.ofEntries(
            // бой: игрок бьёт цель
            Map.entry("target_esp", Kind.COMBAT), Map.entry("target_hud", Kind.COMBAT), Map.entry("hit_marker", Kind.COMBAT),
            Map.entry("crit_indicator", Kind.COMBAT), Map.entry("damage_numbers", Kind.COMBAT), Map.entry("hit_bubbles", Kind.COMBAT),
            Map.entry("hit_color", Kind.COMBAT), Map.entry("hit_effects", Kind.COMBAT), Map.entry("attack_effects", Kind.COMBAT),
            Map.entry("kill_effect", Kind.COMBAT), Map.entry("death_animation", Kind.COMBAT), Map.entry("totem_pops", Kind.COMBAT),
            Map.entry("totem_fx", Kind.COMBAT), Map.entry("shield_indicator", Kind.COMBAT), Map.entry("hit_sounds", Kind.COMBAT),
            Map.entry("name_tags", Kind.COMBAT), Map.entry("hitboxes", Kind.COMBAT), Map.entry("swing_animations", Kind.COMBAT),
            Map.entry("animations", Kind.COMBAT), Map.entry("kill_message", Kind.COMBAT), Map.entry("visual_fx", Kind.COMBAT),
            // движение: игрок бежит и прыгает
            Map.entry("trails", Kind.MOVE), Map.entry("dash_trail", Kind.MOVE), Map.entry("speed_lines", Kind.MOVE),
            Map.entry("jump_circle", Kind.MOVE), Map.entry("jump_particles", Kind.MOVE), Map.entry("landing_dust", Kind.MOVE),
            Map.entry("light_steps", Kind.MOVE), Map.entry("frost_breath", Kind.MOVE), Map.entry("sonar", Kind.MOVE),
            Map.entry("auto_sprint", Kind.MOVE), Map.entry("free_look", Kind.MOVE), Map.entry("third_person_cam", Kind.MOVE),
            // косметика на игроке
            Map.entry("halo", Kind.COSMETIC), Map.entry("player_aura", Kind.COSMETIC), Map.entry("china_hat", Kind.COSMETIC),
            Map.entry("wings", Kind.COSMETIC), Map.entry("wings_3d", Kind.COSMETIC), Map.entry("cape", Kind.COSMETIC),
            Map.entry("accessories", Kind.COSMETIC), Map.entry("emblem", Kind.COSMETIC), Map.entry("pets", Kind.COSMETIC),
            Map.entry("custom_pet", Kind.COSMETIC), Map.entry("spirit_pet", Kind.COSMETIC), Map.entry("model_pets", Kind.COSMETIC),
            Map.entry("model_cosmetics", Kind.COSMETIC), Map.entry("fire_hand", Kind.COSMETIC), Map.entry("hands", Kind.COSMETIC),
            Map.entry("custom_swords", Kind.COSMETIC), Map.entry("weapon_skins", Kind.COSMETIC), Map.entry("local_skin", Kind.COSMETIC),
            Map.entry("item_glow", Kind.COSMETIC), Map.entry("enchant_glint", Kind.COSMETIC), Map.entry("view_model", Kind.COSMETIC),
            Map.entry("emotes", Kind.COSMETIC),
            // интерфейс поверх мира
            Map.entry("hud", Kind.HUD), Map.entry("keystrokes", Kind.HUD), Map.entry("armor_hud", Kind.HUD),
            Map.entry("potions_hud", Kind.HUD), Map.entry("inventory_hud", Kind.HUD), Map.entry("keybinds_hud", Kind.HUD),
            Map.entry("watermark", Kind.HUD), Map.entry("totem_counter", Kind.HUD), Map.entry("item_cooldowns", Kind.HUD),
            Map.entry("frame_stats", Kind.HUD), Map.entry("notifications", Kind.HUD), Map.entry("lyrics", Kind.HUD),
            Map.entry("music", Kind.HUD), Map.entry("custom_scoreboard", Kind.HUD), Map.entry("hotbar", Kind.HUD),
            Map.entry("crosshair", Kind.HUD), Map.entry("slot_pulse", Kind.HUD), Map.entry("ui_animations", Kind.HUD),
            Map.entry("staff_wheel", Kind.HUD), Map.entry("phrase_wheel", Kind.HUD), Map.entry("minimap", Kind.HUD),
            // атмосфера и картинка
            Map.entry("custom_sky", Kind.ATMOSPHERE), Map.entry("ambience", Kind.ATMOSPHERE), Map.entry("custom_fog", Kind.ATMOSPHERE),
            Map.entry("world_effects", Kind.ATMOSPHERE), Map.entry("ambient_fx", Kind.ATMOSPHERE), Map.entry("full_bright", Kind.ATMOSPHERE),
            Map.entry("cinematic", Kind.ATMOSPHERE), Map.entry("motion_blur", Kind.ATMOSPHERE), Map.entry("aspect_ratio", Kind.ATMOSPHERE),
            Map.entry("hurt_vignette", Kind.ATMOSPHERE), Map.entry("postfx", Kind.ATMOSPHERE), Map.entry("shaders", Kind.ATMOSPHERE),
            Map.entry("world_music", Kind.ATMOSPHERE));

    public static Kind kind(Module m) {
        if (m == null) return Kind.GENERIC;
        return KIND.getOrDefault(m.id, Kind.GENERIC);
    }

    // звёзды и кирпичи — один раз, детерминированно
    private static final int STARS = 14;
    private static final float[] SX = new float[STARS], SY = new float[STARS], SP = new float[STARS];

    static {
        long seed = 0x5DEECE66DL;
        for (int i = 0; i < STARS; i++) {
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            SX[i] = ((seed >>> 33) & 0xFFFF) / 65535f;
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            SY[i] = ((seed >>> 33) & 0xFFFF) / 65535f;
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            SP[i] = ((seed >>> 33) & 0xFFFF) / 65535f * 6.28f;
        }
    }

    // ── запечённая стена: 4 копии ряда по ширине, чтобы хватило на баннер с параллаксом ──
    private static final int WALL_W = 96, WALL_H = 42;
    private static final net.minecraft.resources.Identifier WALL_ID = net.minecraft.resources.Identifier.fromNamespaceAndPath("mraz1visual", "preview/wall");
    private static boolean wallReady;

    private static net.minecraft.resources.Identifier wall() {
        if (!wallReady) {
            wallReady = true;
            int tw = WALL_W * 4;
            com.mojang.blaze3d.platform.NativeImage img = new com.mojang.blaze3d.platform.NativeImage(tw, WALL_H, false);
            int bh = 6, bw = 14;
            for (int y = 0; y < WALL_H; y++) {
                int row = y / bh, ry = y % bh;
                for (int x = 0; x < tw; x++) {
                    int off = (row & 1) == 0 ? 0 : bw / 2;
                    int rx = (x + off) % bw, brick = (x + off) / bw;
                    boolean mortar = ry == 0 || rx == 0;
                    int c;
                    if (mortar) c = 0xFF2A1E22;
                    else {
                        int shade = ((brick % WALL_W) * 31 + row * 17) & 7;
                        c = Colors.lerp(0xFF4A2E2C, 0xFF5E3833, shade / 7f);
                        if (ry == 1) c = Colors.lerp(c, 0xFF7A4A42, 0.35f); // кромка сверху светлее
                    }
                    img.setPixel(x, y, c);
                }
            }
            net.minecraft.client.Minecraft.getInstance().getTextureManager().register(WALL_ID,
                    new net.minecraft.client.renderer.texture.DynamicTexture(() -> "mraz1visual preview wall", img));
        }
        return WALL_ID;
    }

    private float t;
    private Module last;
    private float swap = 1f;

    /** Сбросить сценарий (при смене модуля — с начала). */
    public void restart() {
        t = 0;
    }

    /**
     * @param px,py параллакс −1…1 (положение мыши над баннером)
     */
    public void render(Draw d, Module m, int x, int y, int w, int h, float dt, float px, float py) {
        if (m != last) {
            last = m;
            t = 0;
            swap = 0;
        }
        t += Math.min(dt, 0.05f);
        swap = Math.min(1f, swap + dt * 3f);
        Kind kind = kind(m);
        int accent = color(m, Theme.accent());
        float breathe = (float) Math.sin(t * 0.9f) * 1.2f;

        d.clip(x, y, w, h);
        // ── небо ──
        boolean atmosphere = kind == Kind.ATMOSPHERE;
        float grade = atmosphere ? 0.5f + 0.5f * (float) Math.sin(t * 0.8f) : 0f;
        int skyTop = Colors.lerp(0xFF070A18, 0xFF1A1036, grade * 0.6f), skyBot = Colors.lerp(0xFF1A2342, 0xFF3A2650, grade * 0.6f);
        d.gradientV(x, y, w, h, skyTop, skyBot);
        float p1x = px * 4, p1y = py * 2 + breathe * 0.3f;
        for (int i = 0; i < STARS; i++) {
            float a = 0.35f + 0.35f * (float) Math.sin(t * 1.7f + SP[i]);
            float sx = x + SX[i] * w + p1x, sy = y + SY[i] * h * 0.42f + p1y;
            d.rectF(sx, sy, i % 7 == 0 ? 1.5f : 1f, i % 7 == 0 ? 1.5f : 1f, Colors.withAlpha(0xFFFFFF, Math.round(255 * a)));
        }
        // луна с ореолом
        float mx = x + w * 0.82f + px * 6, my = y + h * 0.16f + py * 3;
        d.shadow(Math.round(mx - 16), Math.round(my - 16), 32, 32, 16, 18f, 0x40B8C8FF);
        d.disc(mx, my, 7.5f, 0xFFEFF2FF);
        d.disc(mx + 2.6f, my - 1.8f, 6.4f, Colors.withAlpha(skyTop, 0xE0));

        // ── стена из кирпича ──
        float p2x = px * 9, p2y = py * 4 + breathe * 0.6f;
        int wallTop = Math.round(y + h * 0.40f + p2y), wallBot = Math.round(y + h * 0.80f + p2y);
        // кирпичи запечены в одну текстуру: один элемент GUI вместо сотен прямоугольников
        int wallH = wallBot - wallTop;
        float texW = WALL_W * (wallH / (float) WALL_H);
        float u0 = (-p2x / texW * WALL_W) % WALL_W;
        if (u0 < 0) u0 += WALL_W;
        d.g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, wall(), x, wallTop, u0, 0f, w, wallH,
                Math.round(w / texW * WALL_W), WALL_H, WALL_W * 4, WALL_H, 0xFFFFFFFF);
        // фонари: тёплый свет мерцает
        for (int i = 0; i < 2; i++) {
            float lx = x + w * (i == 0 ? 0.14f : 0.9f) + p2x, ly = wallTop + (wallBot - wallTop) * 0.22f;
            float flick = 0.85f + 0.15f * (float) Math.sin(t * 9.3f + i * 2.1f) * (float) Math.sin(t * 3.1f + i);
            d.shadow(Math.round(lx - 26), Math.round(ly - 22), 52, 52, 26, 26f, Colors.withAlpha(0xFFB45A, Math.round(110 * flick)));
            d.rect(Math.round(lx - 1), Math.round(ly - 10), 2, 6, 0xFF2B2B30);
            d.round(Math.round(lx - 4), Math.round(ly - 4), 8, 10, 2, 0xFF3A3A42);
            d.round(Math.round(lx - 3), Math.round(ly - 3), 6, 8, 2, Colors.withAlpha(0xFFD27A, Math.round(255 * flick)));
        }
        // ── мостовая ──
        float p3x = px * 14, p3y = py * 6 + breathe;
        int ground = Math.round(y + h * 0.80f + p3y);
        d.gradientV(x, ground, w, y + h - ground, 0xFF23232B, 0xFF121217);
        d.rect(x, ground, w, 1, 0x30FFFFFF);
        // свет фонарей на камне
        d.shadow(Math.round(x + w * 0.14f + p2x - 30), ground - 2, 60, 10, 5, 14f, 0x30FFB45A);
        d.shadow(Math.round(x + w * 0.9f + p2x - 30), ground - 2, 60, 10, 5, 14f, 0x30FFB45A);

        // ── персонажи ──
        float unit = h / 64f;
        float plX = x + w * 0.36f + p3x, tgX = x + w * 0.64f + p3x, feet = ground - 1;
        if (m != null && m.id.equals("block_outline")) blockPreview(d, m, x + w * 0.5f + p3x, feet - 22 * unit, 13 * unit);
        else switch (kind) {
            case COMBAT -> combat(d, m, accent, plX, tgX, feet, unit);
            case MOVE -> move(d, m, accent, x, w, feet, unit, p3x);
            case COSMETIC -> cosmetic(d, m, accent, x + w * 0.5f + p3x, feet, unit);
            case HUD -> {
                mannequin(d, x + w * 0.5f + p3x, feet, unit, 0, (float) Math.sin(t * 2) * 0.2f, PLAYER, 0);
                hud(d, m, accent, x, y, w, h);
            }
            case ATMOSPHERE -> {
                mannequin(d, x + w * 0.5f + p3x, feet, unit, 0, 0, PLAYER, 0);
                atmosphere(d, m, accent, x, y, w, h, grade);
            }
            default -> {
                mannequin(d, plX, feet, unit, 0, (float) Math.sin(t * 2) * 0.15f, PLAYER, 0);
                mannequin(d, tgX, feet, unit, 0, 0, DUMMY, 0);
                // нейтральный сценарий: значок модуля мягко пульсирует над сценой
                float pulse = 0.5f + 0.5f * (float) Math.sin(t * 2.4f);
                int cx = x + w / 2, cy = Math.round(y + h * 0.26f);
                d.shadow(cx - 18, cy - 18, 36, 36, 18, 16f, Colors.withAlpha(accent, Math.round(60 + 60 * pulse)));
                d.glass(cx - 14, cy - 14, 28, 28, 10, Colors.withAlpha(accent, 0x50), 1.1f);
                if (m != null) d.icon(Icons.forModule(m.id, Icons.forCategory(m.category)), cx - 6, cy - 6, 12, 0xFFFFFFFF);
            }
        }
        // лёгкая виньетка и появление при смене модуля
        d.shadow(x - 20, y - 20, w + 40, 26, 13, 18f, 0x50000000);
        if (swap < 1f) d.rect(x, y, w, h, Colors.withAlpha(0x0A0A0E, Math.round(200 * (1 - swap))));
        d.unclip();
    }

    // ── манекены ─────────────────────────────────────────────

    private static final int PLAYER = 0, DUMMY = 1;

    /**
     * Блочный человечек: голова, тело, руки и ноги — толстыми линиями (их можно поворачивать).
     * @param swing фаза взмаха правой руки 0…1, @param walk фаза шага −1…1, @param hurt красная вспышка 0…1
     */
    private void mannequin(Draw d, float cx, float feet, float u, float swing, float walk, int type, float hurt) {
        int skin = type == PLAYER ? 0xFFE3B48E : 0xFFB89566;
        int shirt = type == PLAYER ? 0xFF3A7BD5 : 0xFF8C6A42;
        int pants = type == PLAYER ? 0xFF2D3354 : 0xFF6E5233;
        if (hurt > 0) {
            skin = Colors.lerp(skin, 0xFFFF4040, hurt * 0.6f);
            shirt = Colors.lerp(shirt, 0xFFFF4040, hurt * 0.6f);
            pants = Colors.lerp(pants, 0xFFFF4040, hurt * 0.6f);
        }
        float legH = 12 * u, bodyH = 12 * u, headS = 8 * u, limbW = 4 * u;
        float hip = feet - legH, neck = hip - bodyH;
        // тень под ногами
        d.shadow(Math.round(cx - 7 * u), Math.round(feet - u), Math.round(14 * u), Math.round(2 * u), Math.round(u), 4 * u, 0x60000000);
        // ноги
        float sw = walk * 0.5f;
        d.line(cx - 2 * u, hip, cx - 2 * u + (float) Math.sin(sw) * legH, hip + (float) Math.cos(sw) * legH, limbW / 2, pants);
        d.line(cx + 2 * u, hip, cx + 2 * u - (float) Math.sin(sw) * legH, hip + (float) Math.cos(sw) * legH, limbW / 2, Colors.lerp(pants, 0xFF000000, 0.15f));
        // тело
        d.rectF(cx - 4 * u, neck, 8 * u, bodyH, shirt);
        if (type == DUMMY) d.rectF(cx - 0.5f * u, hip, u, legH, 0xFF5A4630); // стойка манекена
        // левая рука — покачивается, правая — взмах
        d.line(cx - 6 * u, neck + u, cx - 6 * u - (float) Math.sin(-sw) * bodyH, neck + u + (float) Math.cos(sw) * bodyH, limbW / 2, Colors.lerp(shirt, 0xFF000000, 0.12f));
        float a = type == PLAYER ? -swing * 2.3f + sw : 0;
        float hx = cx + 6 * u + (float) Math.sin(-a) * bodyH, hy = neck + u + (float) Math.cos(a) * bodyH;
        d.line(cx + 6 * u, neck + u, hx, hy, limbW / 2, shirt);
        // голова
        d.round(Math.round(cx - headS / 2), Math.round(neck - headS), Math.round(headS), Math.round(headS), Math.max(1, Math.round(u)), skin);
        if (type == PLAYER) {
            d.rectF(cx - headS / 2, neck - headS, headS, headS * 0.28f, 0xFF4A3426); // волосы
            d.rectF(cx + u, neck - headS * 0.55f, u, u, 0xFF2A2A40); // глаз
        }
        lastHandX = hx;
        lastHandY = hy;
        lastHeadY = neck - headS;
    }

    private float lastHandX, lastHandY, lastHeadY;

    // ── сценарии ─────────────────────────────────────────────

    private static final int[][] CUBE_EDGES = {{0, 1}, {1, 3}, {3, 2}, {2, 0}, {4, 5}, {5, 7}, {7, 6}, {6, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
    private final float[] cubeX = new float[8], cubeY = new float[8];

    /** Живое превью Block Outline: изометрический кубик, рамка — выбранным стилем, цветом и толщиной. */
    private void blockPreview(Draw d, Module m, float cx, float cy, float s) {
        String style = "edges";
        int edge = 0xFFE6E4FF;
        float width = 2, speed = 1;
        for (Setting<?> st : m.settings()) {
            if (st instanceof dev.mraz1visual.setting.EnumSetting e && st.id.equals("style")) style = e.getId();
            if (st instanceof ColorSetting c && st.id.equals("edge")) edge = c.get() | 0xFF000000;
            if (st instanceof dev.mraz1visual.setting.SliderSetting sl && st.id.equals("width")) width = sl.getF();
            if (st instanceof dev.mraz1visual.setting.SliderSetting sl && st.id.equals("gradient_speed")) speed = sl.getF();
        }
        float rot = t * 0.6f;
        // 8 вершин куба, вращение вокруг вертикали и изометрическая проекция
        float[] px = cubeX, py = cubeY;
        for (int i = 0; i < 8; i++) {
            float vx = (i & 1) == 0 ? -1 : 1, vy = (i & 2) == 0 ? -1 : 1, vz = (i & 4) == 0 ? -1 : 1;
            float rx = vx * (float) Math.cos(rot) - vz * (float) Math.sin(rot), rz = vx * (float) Math.sin(rot) + vz * (float) Math.cos(rot);
            px[i] = cx + rx * s;
            py[i] = cy - vy * s * 0.95f + rz * s * 0.45f;
        }
        // сам блок — тёмный камень
        for (int k = 0; k < 10; k++) {
            float f = k / 9f;
            d.line(px[2] + (px[6] - px[2]) * f, py[2] + (py[6] - py[2]) * f, px[3] + (px[7] - px[3]) * f, py[3] + (py[7] - py[3]) * f, s * 0.22f, 0x30FFFFFF);
        }
        if (style.equals("fill") || style.equals("glass")) {
            for (int k = 0; k < 14; k++) {
                float f = k / 13f;
                d.line(px[0] + (px[2] - px[0]) * f, py[0] + (py[2] - py[0]) * f, px[1] + (px[3] - px[1]) * f, py[1] + (py[3] - py[1]) * f, s * 0.16f, Colors.withAlpha(edge, 0x22));
            }
        }
        if (style.equals("glow")) d.shadow(Math.round(cx - s * 1.6f), Math.round(cy - s * 1.6f), Math.round(s * 3.2f), Math.round(s * 3.2f), Math.round(s), s, Colors.withAlpha(edge, 0x70));
        float tw = 0.5f + width * 0.35f;
        for (int e = 0; e < CUBE_EDGES.length; e++) {
            int a = CUBE_EDGES[e][0], b = CUBE_EDGES[e][1];
            int c = style.equals("gradient") ? dev.mraz1visual.render.HudStyle.grad((float) (0.5 + 0.5 * Math.sin(t * speed * 2 + e * 0.52))) : edge;
            if (style.equals("glow")) d.line(px[a], py[a], px[b], py[b], tw * 3, Colors.withAlpha(c, 0x40));
            d.line(px[a], py[a], px[b], py[b], tw, c);
        }
    }

    private void combat(Draw d, Module m, int c, float plX, float tgX, float feet, float u) {
        float period = 1.6f, ph = (t % period) / period;
        float swing = ph < 0.25f ? ph / 0.25f : ph < 0.45f ? 1 - (ph - 0.25f) / 0.2f : 0;
        boolean hit = ph >= 0.18f && ph < 0.6f;
        float hitT = hit ? (ph - 0.18f) / 0.42f : 0;
        float hurt = hit ? 1 - hitT : 0;
        boolean kill = m != null && (m.id.equals("kill_effect") || m.id.equals("death_animation") || m.id.equals("kill_message"));
        float killPhase = kill ? (t % 4f) / 4f : 0;
        mannequin(d, plX, feet, u, swing, 0, PLAYER, 0);
        float tgAlpha = kill && killPhase > 0.55f ? Math.max(0, 1 - (killPhase - 0.55f) / 0.2f) : 1;
        if (tgAlpha > 0.02f) mannequin(d, tgX + hurt * 2 * u, feet, u, 0, 0, DUMMY, m != null && m.id.equals("hit_color") ? hurt : hurt * 0.4f);
        float tcx = tgX, tcy = feet - 16 * u;
        String id = m == null ? "" : m.id;
        switch (id) {
            case "target_esp" -> {
                // кольцо-маркер вокруг цели, вращается
                float r = 13 * u * size(m, 1f);
                for (int i = 0; i < 3; i++) {
                    float a = t * 2.2f + i * 2.094f;
                    d.disc(tcx + (float) Math.cos(a) * r, feet - 6 * u + (float) Math.sin(a) * r * 0.32f - 10 * u * (0.5f + 0.5f * (float) Math.sin(t * 1.6f)), 1.6f * u, c);
                }
                d.ellipseRing(tcx, feet - u, r, r * 0.3f, 1.2f * u, Colors.withAlpha(c, 0xC0));
            }
            case "hitboxes" -> {
                int bx = Math.round(tgX - 5 * u), by = Math.round(feet - 32 * u), bw = Math.round(10 * u), bhh = Math.round(32 * u);
                d.rect(bx, by, bw, 1, c);
                d.rect(bx, by + bhh, bw, 1, c);
                d.rect(bx, by, 1, bhh, c);
                d.rect(bx + bw, by, 1, bhh + 1, c);
                d.rect(bx, Math.round(feet - 26 * u), bw, 1, 0xFFFF5050); // линия глаз
            }
            case "name_tags" -> {
                String n = "Манекен  20 HP";
                int tw = d.width(n) + 10, tx = Math.round(tcx - tw / 2f), ty = Math.round(lastHeadY - 14 * u);
                d.round(tx, ty, tw, 12, 4, 0x90000000);
                d.text(n, tx + 5, ty + 2, 0xFFFFFFFF);
            }
            case "target_hud" -> {
                int bx = Math.round(tcx + 10 * u), by = Math.round(feet - 40 * u), bw = 86, bh = 26;
                d.glass(bx, by, bw, bh, 8, 0x60101018, 1f);
                d.text("Манекен", bx + 6, by + 4, 0xFFFFFFFF);
                float hp = 1 - 0.3f * (t % 4f) / 4f;
                d.round(bx + 6, by + 16, bw - 12, 4, 2, 0x40FFFFFF);
                d.round(bx + 6, by + 16, Math.round((bw - 12) * hp), 4, 2, c);
            }
            case "damage_numbers" -> {
                if (hit) {
                    float up = hitT * 14 * u;
                    d.text("-4.5", Math.round(tcx + 6 * u), Math.round(lastHeadY - up), Colors.withAlpha(c, Math.round(255 * (1 - hitT))));
                }
            }
            case "hit_marker", "crit_indicator", "crosshair" -> {
                float cx = tcx, cy = tcy - 4 * u, s = 3 * u + (hit ? 2 * u * (1 - hitT) : 0);
                int col = hit ? c : 0xC0FFFFFF;
                d.line(cx - s - 2 * u, cy - s - 2 * u, cx - s, cy - s, 0.8f * u, col);
                d.line(cx + s + 2 * u, cy - s - 2 * u, cx + s, cy - s, 0.8f * u, col);
                d.line(cx - s - 2 * u, cy + s + 2 * u, cx - s, cy + s, 0.8f * u, col);
                d.line(cx + s + 2 * u, cy + s + 2 * u, cx + s, cy + s, 0.8f * u, col);
            }
            case "totem_pops", "totem_fx" -> {
                float tp = (t % 3f) / 3f;
                if (tp < 0.5f) {
                    float e = tp / 0.5f;
                    for (int i = 0; i < 14; i++) {
                        float a = i * 0.449f + t;
                        float r = e * 22 * u;
                        d.disc(tcx + (float) Math.cos(a) * r, tcy + (float) Math.sin(a) * r * 0.8f - e * 6 * u, 1.3f * u * (1 - e), i % 2 == 0 ? 0xFFF2D25A : 0xFF5AD27A);
                    }
                }
            }
            default -> {
                // частицы удара, цвет — из настроек модуля
                if (hit) {
                    for (int i = 0; i < 10; i++) {
                        float a = i * 0.628f + 0.3f;
                        float r = hitT * 14 * u * size(m, 1f);
                        d.disc(tcx + (float) Math.cos(a) * r, tcy + (float) Math.sin(a) * r, 1.4f * u * (1 - hitT), Colors.withAlpha(c, Math.round(255 * (1 - hitT))));
                    }
                    d.ring(tcx, tcy, hitT * 12 * u, 0.8f * u, Colors.withAlpha(c, Math.round(180 * (1 - hitT))));
                }
                if (kill && killPhase > 0.55f && killPhase < 0.85f) {
                    float e = (killPhase - 0.55f) / 0.3f;
                    for (int i = 0; i < 18; i++) {
                        float a = i * 0.349f;
                        d.disc(tcx + (float) Math.cos(a) * e * 20 * u, tcy - e * 10 * u + (float) Math.sin(a) * e * 14 * u, 1.6f * u * (1 - e), Colors.withAlpha(c, Math.round(230 * (1 - e))));
                    }
                }
            }
        }
    }

    private void move(Draw d, Module m, int c, int x, int w, float feet, float u, float p3x) {
        // игрок пробегает сцену слева направо и подпрыгивает посередине
        float period = 4f, ph = (t % period) / period;
        float px = x + w * (0.12f + 0.76f * ph) + p3x;
        float jumpPh = (ph - 0.42f) / 0.2f;
        boolean air = jumpPh > 0 && jumpPh < 1;
        float jump = air ? (float) Math.sin(jumpPh * Math.PI) * 12 * u : 0;
        float walk = (float) Math.sin(t * 10f);
        String id = m == null ? "" : m.id;
        // след за игроком
        if (id.equals("trails") || id.equals("dash_trail") || id.equals("light_steps")) {
            for (int i = 1; i <= 14; i++) {
                float back = i * 3.2f * u;
                float tx = px - back;
                if (tx < x) break;
                float a = 1 - i / 15f;
                float ty = feet - (id.equals("light_steps") ? 0.5f * u : 12 * u);
                if (id.equals("light_steps")) {
                    if (i % 3 == 0) d.disc(tx, ty, 1.6f * u, Colors.withAlpha(c, Math.round(200 * a)));
                } else d.disc(tx, ty, (id.equals("dash_trail") ? 2.4f : 1.8f) * u * a, Colors.withAlpha(c, Math.round(220 * a)));
            }
        }
        if (id.equals("speed_lines")) {
            for (int i = 0; i < 10; i++) {
                float ly = feet - (6 + (i * 7 % 30)) * u, lx = px - (10 + (i * 13 % 40)) * u - (t * 120 % 30);
                d.line(lx, ly, lx + 12 * u, ly, 0.5f * u, Colors.withAlpha(c, 0x90));
            }
        }
        mannequin(d, px, feet - jump, u, 0, air ? 0.4f : walk, PLAYER, 0);
        if ((id.equals("jump_circle") || id.equals("landing_dust") || id.equals("jump_particles")) && jumpPh > 0) {
            float e = Math.min(1, jumpPh / 1.6f);
            float jx = x + w * (0.12f + 0.76f * 0.42f) + p3x;
            if (id.equals("jump_circle")) d.ellipseRing(jx, feet, 4 * u + e * 14 * u * size(m, 1f), (4 * u + e * 14 * u) * 0.3f, 1.2f * u, Colors.withAlpha(c, Math.round(230 * (1 - e))));
            else for (int i = 0; i < 8; i++) {
                float a = i * 0.785f;
                d.disc(jx + (float) Math.cos(a) * e * 12 * u, feet - (float) Math.abs(Math.sin(a)) * e * 6 * u, 1.2f * u * (1 - e), Colors.withAlpha(c, Math.round(220 * (1 - e))));
            }
        }
        if (id.equals("frost_breath")) {
            float bp = (t % 1.5f) / 1.5f;
            d.disc(px + 6 * u + bp * 8 * u, lastHeadY + 4 * u - bp * 2 * u, 1.5f * u + bp * 2 * u, Colors.withAlpha(0xDDEEFF, Math.round(160 * (1 - bp))));
        }
        if (id.equals("sonar")) {
            float sp = (t % 2f) / 2f;
            d.ellipseRing(px, feet, sp * 40 * u, sp * 12 * u, 0.8f * u, Colors.withAlpha(c, Math.round(200 * (1 - sp))));
        }
    }

    private void cosmetic(Draw d, Module m, int c, float cx, float feet, float u) {
        String id = m == null ? "" : m.id;
        float bob = (float) Math.sin(t * 2.2f) * u;
        // крылья и плащ — за спиной, их рисуем до игрока
        if (id.startsWith("wings")) {
            float flap = (float) Math.sin(t * 3f) * 0.35f;
            for (int side = -1; side <= 1; side += 2) {
                for (int f = 0; f < 5; f++) {
                    float a = (0.4f + f * 0.22f + flap) * side;
                    float len = (16 - f * 1.6f) * u;
                    float ox = cx + side * 2 * u, oy = feet - 20 * u;
                    d.line(ox, oy, ox + (float) Math.sin(a) * len, oy - (float) Math.cos(a) * len * 0.7f, (2.2f - f * 0.25f) * u, Colors.withAlpha(c, 0xD0 - f * 18));
                }
            }
        }
        if (id.equals("cape")) {
            float wave = (float) Math.sin(t * 2.6f) * 2 * u;
            d.rectF(cx - 5 * u + wave * 0.3f, feet - 23 * u, 10 * u, 20 * u + wave, Colors.lerp(c, 0xFF000000, 0.2f));
        }
        if (id.equals("player_aura")) {
            float p = 0.5f + 0.5f * (float) Math.sin(t * 2f);
            d.shadow(Math.round(cx - 14 * u), Math.round(feet - 34 * u), Math.round(28 * u), Math.round(34 * u), Math.round(10 * u), 10 * u, Colors.withAlpha(c, Math.round(70 + 60 * p)));
        }
        mannequin(d, cx, feet + bob * 0.2f, u, (float) Math.max(0, Math.sin(t * 1.4f)) * 0.5f, (float) Math.sin(t * 1.2f) * 0.1f, PLAYER, 0);
        float headTop = lastHeadY;
        switch (id) {
            case "halo" -> d.ellipseRing(cx, headTop - 3 * u + bob, 5 * u, 1.6f * u, 0.9f * u, c);
            case "china_hat" -> {
                float hy = headTop - u;
                d.line(cx - 9 * u, hy + 2 * u, cx, hy - 4 * u, 0.8f * u, Colors.withAlpha(c, 0xE0));
                d.line(cx, hy - 4 * u, cx + 9 * u, hy + 2 * u, 0.8f * u, Colors.withAlpha(c, 0xE0));
                d.ellipseRing(cx, hy + 2 * u, 9 * u, 2 * u, 0.6f * u, Colors.withAlpha(c, 0x90));
            }
            case "pets", "custom_pet", "spirit_pet", "model_pets" -> {
                float a = t * 1.3f;
                float ox = cx + (float) Math.cos(a) * 14 * u, oy = feet - 22 * u + (float) Math.sin(t * 2.4f) * 2 * u;
                d.shadow(Math.round(ox - 5 * u), Math.round(oy - 5 * u), Math.round(10 * u), Math.round(10 * u), Math.round(5 * u), 6 * u, Colors.withAlpha(c, 0x60));
                d.round(Math.round(ox - 3 * u), Math.round(oy - 3 * u), Math.round(6 * u), Math.round(5 * u), Math.round(1.5f * u), c);
                d.rectF(ox + (float) Math.cos(a) * u, oy - 1.5f * u, 0.8f * u, 0.8f * u, 0xFF101018);
            }
            case "fire_hand", "hands", "custom_swords", "weapon_skins", "item_glow", "enchant_glint", "view_model" -> {
                // предмет в руке: светящийся клинок
                float hx = lastHandX, hy = lastHandY;
                float g = 0.5f + 0.5f * (float) Math.sin(t * 3f);
                d.line(hx, hy, hx + 7 * u, hy - 10 * u, 1.1f * u, 0xFFDDE6F0);
                d.shadow(Math.round(hx), Math.round(hy - 11 * u), Math.round(8 * u), Math.round(12 * u), Math.round(3 * u), 6 * u, Colors.withAlpha(c, Math.round(80 + 80 * g)));
            }
            default -> {
                // аксессуары, эмблемы, модели: мягкое сияние акцентом вокруг игрока
                d.ellipseRing(cx, feet, 10 * u, 3 * u, 0.8f * u, Colors.withAlpha(c, 0x90));
            }
        }
    }

    private void hud(Draw d, Module m, int c, int x, int y, int w, int h) {
        String id = m == null ? "" : m.id;
        // хотбар внизу сцены
        int hbW = Math.min(w - 40, 162), hbX = x + (w - hbW) / 2, hbY = y + h - 22;
        d.round(hbX, hbY, hbW, 16, 5, 0x70000000);
        int sel = (int) (t * 0.8f) % 9;
        for (int i = 0; i < 9; i++) {
            int sx = hbX + 2 + i * (hbW - 4) / 9;
            if (i == sel) d.round(sx, hbY + 1, (hbW - 4) / 9, 14, 4, id.equals("hotbar") || id.equals("slot_pulse") ? Colors.withAlpha(c, 0x90) : 0x40FFFFFF);
        }
        float hl = 0.6f + 0.4f * (float) Math.sin(t * 3f);
        int hi = Colors.withAlpha(c, Math.round(255 * hl));
        switch (id) {
            case "keystrokes" -> {
                int kx = x + 12, ky = y + h - 66, s = 14;
                String[] keys = {"W", "A", "S", "D"};
                int[][] pos = {{1, 0}, {0, 1}, {1, 1}, {2, 1}};
                int pressed = (int) (t * 3) % 4;
                for (int i = 0; i < 4; i++) {
                    int bx = kx + pos[i][0] * (s + 2), by = ky + pos[i][1] * (s + 2);
                    d.round(bx, by, s, s, 4, i == pressed ? hi : 0x70000000);
                    d.textCenter(keys[i], bx + s / 2, Draw.textY(by, s), 0xFFFFFFFF);
                }
            }
            case "watermark" -> {
                d.glass(x + 10, y + 10, 92, 18, 9, 0x60101018, 1f);
                d.text("Mraz1Visual", Fonts.Kind.BOLD, x + 18, y + 15, 0xFFFFFFFF);
                d.disc(x + 94, y + 19, 2.5f, hi);
            }
            case "armor_hud", "totem_counter", "item_cooldowns" -> {
                for (int i = 0; i < 4; i++) {
                    d.round(hbX + hbW + 6, hbY - 4 - i * 14, 12, 12, 3, i == (int) (t * 2) % 4 ? hi : 0x70000000);
                }
            }
            case "potions_hud", "keybinds_hud", "custom_scoreboard", "notifications", "frame_stats" -> {
                int bx = x + w - 104, by = y + 12;
                d.glass(bx, by, 92, 54, 8, 0x60101018, 1f);
                d.text(m.name, Fonts.Kind.BOLD, bx + 6, by + 5, 0xFFFFFFFF);
                for (int i = 0; i < 3; i++) {
                    d.round(bx + 6, by + 19 + i * 11, 54 - i * 10, 6, 3, i == 0 ? hi : 0x40FFFFFF);
                }
            }
            case "music", "lyrics" -> {
                int bx = x + (w - 150) / 2, by = y + 10;
                d.glass(bx, by, 150, 26, 13, 0x70101018, 1.1f);
                d.disc(bx + 13, by + 13, 7, hi);
                d.text("Сейчас играет", Fonts.Kind.BOLD, bx + 26, by + 5, 0xFFFFFFFF);
                d.round(bx + 26, by + 17, 110, 3, 1, 0x40FFFFFF);
                d.round(bx + 26, by + 17, Math.round(110 * ((t % 6) / 6)), 3, 1, 0xFFFFFFFF);
            }
            case "crosshair" -> {
                float cx = x + w / 2f, cy = y + h * 0.45f;
                d.line(cx - 6, cy, cx - 2, cy, 0.8f, hi);
                d.line(cx + 2, cy, cx + 6, cy, 0.8f, hi);
                d.line(cx, cy - 6, cx, cy - 2, 0.8f, hi);
                d.line(cx, cy + 2, cx, cy + 6, 0.8f, hi);
            }
            default -> {
                // общий HUD: карточка-индикатор в углу
                d.glass(x + 10, y + 10, 70, 30, 8, 0x60101018, 1f);
                d.text(m == null ? "HUD" : m.name, Fonts.Kind.BOLD, x + 16, y + 15, 0xFFFFFFFF);
                d.round(x + 16, y + 28, 50, 4, 2, hi);
            }
        }
    }

    private void atmosphere(Draw d, Module m, int c, int x, int y, int w, int h, float grade) {
        String id = m == null ? "" : m.id;
        switch (id) {
            case "custom_fog" -> d.gradientV(x, y + h / 3, w, h * 2 / 3, 0x00000000, Colors.withAlpha(c, 0x70));
            case "full_bright" -> d.rect(x, y, w, h, Colors.withAlpha(0xFFFFFF, Math.round(40 * grade)));
            case "cinematic", "aspect_ratio" -> {
                int bar = Math.round(h * 0.11f * grade);
                d.rect(x, y, w, bar, 0xFF000000);
                d.rect(x, y + h - bar, w, bar, 0xFF000000);
            }
            case "hurt_vignette" -> {
                float p = (t % 2f) / 2f;
                d.shadow(x - 10, y - 10, w + 20, h + 20, 30, 40f, Colors.withAlpha(0xFF2020, Math.round(140 * (1 - p))));
            }
            case "world_effects", "ambient_fx" -> {
                // снег/искры падают
                for (int i = 0; i < 30; i++) {
                    float fx = x + ((i * 53 + (int) (t * 12) * (i % 3 + 1)) % w);
                    float fy = y + ((i * 37 + t * (18 + i % 5 * 6)) % h);
                    d.disc(fx, fy, 0.8f + (i % 3) * 0.4f, Colors.withAlpha(c, 0xC0));
                }
            }
            case "motion_blur" -> {
                for (int i = 1; i < 4; i++) d.rect(x, y, w, h, Colors.withAlpha(0xFFFFFF, 6 / i));
            }
            default -> d.gradientV(x, y, w, h / 2, Colors.withAlpha(c, Math.round(40 * grade)), 0x00000000);
        }
    }

    // ── настройки модуля → вид превью ────────────────────────

    /** Цвет модуля: первая настройка цвета (или акцент). */
    public static int color(Module m, int fallback) {
        if (m == null) return fallback;
        for (Setting<?> s : m.settings()) if (s instanceof ColorSetting cs) return cs.get() | 0xFF000000;
        return fallback;
    }

    /** Множитель размера: настройка «size»/«scale»/«radius», приведённая к ~1. */
    private static float size(Module m, float fallback) {
        if (m == null) return fallback;
        for (Setting<?> s : m.settings()) {
            if (s instanceof SliderSetting sl && (s.id.equals("size") || s.id.equals("scale") || s.id.equals("radius"))) {
                return 0.6f + 0.8f * (float) sl.fraction();
            }
        }
        return fallback;
    }

    /** Подпись стиля для бейджа баннера: значение первой настройки-перечисления. */
    public static String style(Module m) {
        if (m == null) return "";
        for (Setting<?> s : m.settings()) if (s instanceof EnumSetting e) return e.labels[e.get()];
        return "";
    }
}

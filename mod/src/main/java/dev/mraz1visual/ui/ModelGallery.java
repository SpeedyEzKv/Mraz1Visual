package dev.mraz1visual.ui;

import dev.mraz1visual.core.Modules;
import dev.mraz1visual.models.AnimPlayer;
import dev.mraz1visual.models.Anim;
import dev.mraz1visual.models.ModelEntry;
import dev.mraz1visual.models.ModelPrefs;
import dev.mraz1visual.models.ModelRegistry;
import dev.mraz1visual.module.ModelCosmetics;
import dev.mraz1visual.module.ModelPets;
import dev.mraz1visual.module.WeaponSkins;
import dev.mraz1visual.ui.widget.Widget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Галерея импортированных моделей в стиле Liquid Glass: «Скины» (сетка карточек с 3D-превью, привязка к типу
 * оружия, избранное, поиск, ручная поза), «Питомцы» (выбор активного, кнопки анимаций) и «Косметика» (шляпа, крылья).
 */
public final class ModelGallery extends Screen {
    public enum Tab { SKINS("Скины"), PETS("Питомцы"), COSMETICS("Косметика");
        final String title;
        Tab(String t) { title = t; }
    }

    private static final int CARD_W = 92, CARD_H = 114, GAP = 8, SLOT_W = 124, SIDE_W = 156, TOP_H = 40, FOOT_H = 30;
    private static final String[] AXES = {"Поворот X", "Поворот Y", "Поворот Z", "Сдвиг X", "Сдвиг Y", "Сдвиг Z", "Масштаб"};
    private static final float[] STEP = {5, 5, 5, 0.5f, 0.5f, 0.5f, 0.05f};
    private static final String[] GROUPS = {"1-е лицо", "3-е лицо", "Иконка"};

    private final Screen parent;
    private Tab tab;
    private final Draw d = new Draw();
    /** Единиц окна в одной единице GUI (окно меньше 700×420 — уменьшается целиком). */
    private float k = 1f;
    private int wx, wy, ww, wh;
    private String slot = "all_swords";
    private int poseGroup = ModelEntry.FP;
    private final StringBuilder search = new StringBuilder();
    private float scroll, scrollTarget;
    private int scrollMax;
    private String hover;
    private float spin;
    private long last;
    private float openT;
    private final Map<String, AnimPlayer> players = new HashMap<>();
    private final AnimPlayer petPreview = new AnimPlayer();
    private String petPreviewId;
    /** Карточки текущего кадра: прямоугольники для кликов. */
    private final List<Hit> hits = new ArrayList<>();

    private record Hit(int x, int y, int w, int h, Runnable action) {}

    private ModelGallery(Tab tab, Screen parent) {
        super(Component.literal("3D-модели"));
        this.tab = tab;
        this.parent = parent;
    }

    public static void open(Tab tab) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> mc.setScreen(new ModelGallery(tab, mc.screen)));
    }

    public static void openFolder(Path dir) {
        try {
            java.nio.file.Files.createDirectories(dir);
            net.minecraft.util.Util.getPlatform().openPath(dir);
        } catch (Exception e) {
            dev.mraz1visual.core.ModuleManager.LOG.warn("Не удалось открыть папку {}", dir, e);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (minecraft.level != null && GlassBlur.update(4)) GlassBlur.draw(g, 0, 0, width, height, 0xFFFFFFFF);
        else super.renderBackground(g, mouseX, mouseY, partialTick);
        g.fill(0, 0, width, height, 0x55000000);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        boolean light = Modules.client.theme.getId().equals("light");
        if (light) Theme.light();
        try {
            draw(g, mouseX, mouseY);
        } finally {
            if (light) Theme.dark();
        }
    }

    // ── кадр ─────────────────────────────────────────────────

    private void draw(GuiGraphics g, int gmx, int gmy) {
        long now = System.nanoTime();
        float dt = last == 0 ? 0 : Math.min(0.1f, (now - last) / 1e9f);
        last = now;
        spin += dt * 70f;
        openT = Math.min(1, openT + dt / 0.25f);
        scroll = dev.mraz1visual.ui.Anim.to(scroll, scrollTarget, dt);
        k = Math.min(1f, Math.min(width / 720f, height / 440f));
        int vw = Math.round(width / k), vh = Math.round(height / k);
        int mx = Math.round(gmx / k), my = Math.round(gmy / k);
        ww = Math.min(vw - 24, 800);
        wh = Math.min(vh - 16, 470);
        wx = (vw - ww) / 2;
        wy = (vh - wh) / 2;
        hits.clear();
        hover = null;

        d.begin(g, font, k);
        d.shadow(wx, wy + 8, ww, wh, Theme.RADIUS, 30f, Theme.SHADOW);
        d.glass(wx, wy, ww, wh, Theme.RADIUS, Theme.GLASS_WINDOW, 1.1f);
        drawTop(mx, my);
        int bodyY = wy + TOP_H, bodyH = wh - TOP_H - FOOT_H;
        switch (tab) {
            case SKINS -> drawSkins(g, mx, my, bodyY, bodyH, dt);
            case PETS -> drawPets(g, mx, my, bodyY, bodyH, dt);
            case COSMETICS -> drawCosmetics(g, mx, my, bodyY, bodyH);
        }
        drawFoot(mx, my);
        if (openT < 1) d.round(wx, wy, ww, wh, Theme.RADIUS, Colors.withAlpha(Theme.isLight() ? 0xF4F4F5 : 0x0A0A0C, Math.round(255 * (1 - openT))));
        d.end();
    }

    private void drawTop(int mx, int my) {
        d.text("3D-модели", Fonts.Kind.HEAD, wx + 16, wy + 16, Theme.TEXT);
        // вкладки — капсула с подсветкой выбранной
        int tw = 0;
        for (Tab t : Tab.values()) tw += d.width(t.title) + 24;
        int tx = wx + (ww - tw) / 2, ty = wy + 10;
        d.round(tx - 3, ty - 3, tw + 6, 26, 13, Theme.GLASS_RAIL);
        for (Tab t : Tab.values()) {
            int w = d.width(t.title) + 24;
            boolean on = t == tab, hov = Widget.hit(mx, my, tx, ty, w, 20);
            if (on) d.round(tx, ty, w, 20, 10, Theme.GLASS_ACTIVE);
            else if (hov) d.round(tx, ty, w, 20, 10, Theme.HOVER);
            d.textCenter(t.title, tx + w / 2, Draw.textY(ty, 20), on ? Theme.TEXT : Theme.TEXT_DIM);
            Tab target = t;
            hits.add(new Hit(tx, ty, w, 20, () -> { tab = target; scrollTarget = scroll = 0; }));
            tx += w;
        }
        // поиск
        int sw = 130, sx = wx + ww - sw - 14, sy = wy + 10;
        d.round(sx, sy, sw, 20, 10, Theme.FIELD);
        String q = search.toString();
        d.text(q.isEmpty() ? "Поиск…" : Draw.ellipsize(q, sw - 18), sx + 10, Draw.textY(sy, 20), q.isEmpty() ? Theme.TEXT_MUTED : Theme.TEXT);
    }

    private void drawFoot(int mx, int my) {
        int fy = wy + wh - FOOT_H + 6;
        String st;
        if (ModelRegistry.loading()) {
            st = "Загрузка моделей… " + Math.round(ModelRegistry.progress() * 100) + "%";
            int bw = 120, bx = wx + 16 + d.width(st) + 10;
            d.round(bx, fy + 7, bw, 4, 2, Theme.TRACK);
            d.round(bx, fy + 7, Math.max(4, Math.round(bw * ModelRegistry.progress())), 4, 2, Theme.accent());
        } else {
            st = ModelRegistry.status();
        }
        d.text(st, wx + 16, Draw.textY(fy, 18), Theme.TEXT_DIM);
        Path folder = tab == Tab.PETS ? ModelRegistry.PETS : ModelRegistry.WEAPONS;
        int bx = wx + ww - 14;
        bx = button(mx, my, bx, fy, "Открыть папку", () -> openFolder(folder));
        button(mx, my, bx - 6, fy, "Обновить папку", ModelRegistry::reload);
    }

    /** Кнопка-капсула, выровненная по правому краю right. @return левый край. */
    private int button(int mx, int my, int right, int y, String label, Runnable action) {
        int w = d.width(label) + 20, x = right - w;
        boolean hov = Widget.hit(mx, my, x, y, w, 18);
        d.round(x, y, w, 18, 9, hov ? Theme.GLASS_ACTIVE : Theme.GLASS_CARD);
        d.textCenter(label, x + w / 2, Draw.textY(y, 18), Theme.TEXT);
        hits.add(new Hit(x, y, w, 18, action));
        return x;
    }

    private boolean matches(ModelEntry e) {
        if (search.isEmpty()) return true;
        String q = search.toString().toLowerCase(Locale.ROOT);
        return e.name.toLowerCase(Locale.ROOT).contains(q) || e.source.toLowerCase(Locale.ROOT).contains(q);
    }

    // ── скины ────────────────────────────────────────────────

    private void drawSkins(GuiGraphics g, int mx, int my, int y, int h, float dt) {
        // слоты привязки слева
        int sx = wx + 12, sy = y + 4;
        d.text("Привязка", Fonts.Kind.BOLD, sx + 4, sy, Theme.TEXT_DIM);
        sy += 14;
        for (String[] s : WeaponSkins.SLOTS) {
            boolean on = s[0].equals(slot), hov = Widget.hit(mx, my, sx, sy, SLOT_W, 18);
            if (on) d.round(sx, sy, SLOT_W, 18, 9, Theme.GLASS_ACTIVE);
            else if (hov) d.round(sx, sy, SLOT_W, 18, 9, Theme.HOVER);
            boolean set = ModelRegistry.get(ModelPrefs.weapons.get(s[0])) != null;
            if (set) d.disc(sx + 9, sy + 9, 2.5f, Theme.accent());
            d.text(s[1], sx + 16, Draw.textY(sy, 18), on ? Theme.TEXT : Theme.TEXT_DIM);
            String id = s[0];
            hits.add(new Hit(sx, sy, SLOT_W, 18, () -> slot = id));
            sy += 19;
        }
        if (!WeaponSkins.active()) {
            d.text("Модуль выключен —", sx + 4, sy + 10, Theme.TEXT_MUTED);
            button(mx, my, sx + SLOT_W, sy + 24, "Включить", () -> setModule("weapon_skins"));
        }
        ModelEntry chosen = ModelRegistry.get(ModelPrefs.weapons.get(slot));
        int gx = wx + 12 + SLOT_W + 12, gw = ww - (gx - wx) - 12 - (chosen != null ? SIDE_W + 10 : 0);
        List<ModelEntry> list = new ArrayList<>();
        for (ModelEntry e : ModelRegistry.of(ModelEntry.Kind.WEAPON)) if (matches(e)) list.add(e);
        list.sort((a, b) -> Boolean.compare(ModelPrefs.favorites.contains(b.id), ModelPrefs.favorites.contains(a.id)));
        grid(g, mx, my, gx, y, gw, h, list, chosen, e -> {
            if (e.broken()) return;
            if (e == chosen) ModelPrefs.weapons.remove(slot);
            else ModelPrefs.weapons.put(slot, e.id);
            ModelPrefs.save();
        }, "Положите паки (zip, папки, .bbmodel, png) в mraz1visual/weapon_skins и нажмите «Обновить папку».");
        if (chosen != null) poseEditor(mx, my, gx + gw + 10, y + 4, chosen);
    }

    private void setModule(String id) {
        var m = dev.mraz1visual.Mraz1Visual.MODULES.find(id);
        if (m != null) m.setEnabled(true);
    }

    private interface Pick { void pick(ModelEntry e); }

    /** Сетка карточек с прокруткой; превью крутится под мышкой. */
    private void grid(GuiGraphics g, int mx, int my, int x, int y, int w, int h, List<ModelEntry> list, ModelEntry selected, Pick pick, String empty) {
        int cols = Math.max(1, (w + GAP) / (CARD_W + GAP));
        int rows = (list.size() + cols - 1) / cols;
        scrollMax = Math.max(0, rows * (CARD_H + GAP) - h + 8);
        scrollTarget = Math.max(0, Math.min(scrollMax, scrollTarget));
        if (list.isEmpty()) {
            d.text(ModelRegistry.loading() ? "Загрузка…" : Draw.ellipsize(empty, w - 16), x + 8, y + 12, Theme.TEXT_MUTED);
            return;
        }
        d.clip(x, y, w, h);
        int ox = x + (w - (cols * (CARD_W + GAP) - GAP)) / 2;
        for (int i = 0; i < list.size(); i++) {
            ModelEntry e = list.get(i);
            int cx = ox + (i % cols) * (CARD_W + GAP), cy = y + 4 + (i / cols) * (CARD_H + GAP) - Math.round(scroll);
            if (cy + CARD_H < y || cy > y + h) continue;
            boolean hov = Widget.hit(mx, my, cx, cy, CARD_W, CARD_H) && my >= y && my < y + h;
            boolean on = e == selected;
            d.glass(cx, cy, CARD_W, CARD_H, Theme.RADIUS_CARD, on ? Theme.GLASS_ACTIVE : hov ? Theme.GLASS_PANEL : Theme.GLASS_CARD, on ? 1.2f : 0.8f);
            if (on) d.rim(cx, cy, CARD_W, CARD_H, Theme.RADIUS_CARD, Theme.accent(), Colors.mulAlpha(Theme.accent(), 0.5f));
            if (e.broken()) {
                d.textCenter("!", cx + CARD_W / 2, cy + 30, 0xFFFF6B6B);
                d.textCenter("ошибка", cx + CARD_W / 2, cy + 44, 0xFFFF6B6B);
            } else {
                float[] pose = null;
                if (!e.anims.isEmpty()) pose = idlePose(e);
                if (e.kind == ModelEntry.Kind.PET || e.kind == ModelEntry.Kind.HAT) preview(g, e, pose, 200 + (hov ? spin : 0), -18, 0, 0.78f, cx + 6, cy + 6, CARD_W - 12, 74);
                else if (e.kind == ModelEntry.Kind.WING) preview(g, e, null, 90 + (hov ? spin : 0), -12, 0, 0.95f, cx + 4, cy + 4, CARD_W - 8, 78);
                else preview(g, e, null, hov ? spin : 0, 0, -45, 1.05f, cx + 4, cy + 4, CARD_W - 8, 78);
            }
            d.text(Draw.ellipsize(e.name, CARD_W - 12), cx + 7, cy + 84, Theme.TEXT);
            d.text(Draw.ellipsize(e.source, CARD_W - 12), cx + 7, cy + 97, Theme.TEXT_MUTED);
            // избранное
            if (e.kind == ModelEntry.Kind.WEAPON) {
                boolean fav = ModelPrefs.favorites.contains(e.id);
                boolean sh = Widget.hit(mx, my, cx + CARD_W - 18, cy + 4, 14, 14);
                if (fav || hov) d.text(fav ? "★" : "☆", cx + CARD_W - 15, cy + 6, fav ? 0xFFFFD25A : sh ? Theme.TEXT : Theme.TEXT_MUTED);
                hits.add(new Hit(cx + CARD_W - 18, cy + 4, 14, 14, () -> {
                    if (!ModelPrefs.favorites.remove(e.id)) ModelPrefs.favorites.add(e.id);
                    ModelPrefs.save();
                }));
            }
            if (hov) hover = e.broken() ? "Ошибка: " + e.error : e.name + " — " + e.source + " (" + e.format + ")"
                    + (e.warnings.isEmpty() ? "" : ", предупреждений: " + e.warnings.size());
            if (my >= y && my < y + h) hits.add(new Hit(cx, cy, CARD_W, CARD_H, () -> pick.pick(e)));
        }
        d.unclip();
        if (hover != null) {
            int tw = Math.min(w - 8, d.width(hover) + 16);
            d.round(x + 4, y + h - 22, tw, 18, 9, Theme.POPUP);
            d.text(Draw.ellipsize(hover, tw - 16), x + 12, Draw.textY(y + h - 22, 18), Theme.TEXT_DIM);
        }
    }

    /** 3D-превью в координатах окна (PIP рисуется в координатах GUI — переводим). */
    private void preview(GuiGraphics g, ModelEntry e, float[] pose, float yaw, float pitch, float roll, float fit, int x, int y, int w, int h) {
        ModelPreview.draw(g, e.mesh(), pose, yaw, pitch, roll, fit, true, Math.round(x * k), Math.round(y * k), Math.round(w * k), Math.round(h * k));
    }

    private float[] idlePose(ModelEntry e) {
        AnimPlayer p = players.computeIfAbsent(e.id, k -> new AnimPlayer());
        if (p.current() == null) {
            Anim a = e.anim("idle");
            if (a == null) a = e.anims.get(0);
            p.play(a, 0, true);
        }
        p.update(1 / 60f);
        if (p.finished()) p.play(p.current(), 0, true);
        return p.pose(e.mesh());
    }

    private void poseEditor(int mx, int my, int x, int y, ModelEntry e) {
        int w = SIDE_W;
        d.glass(x, y, w, 250, Theme.RADIUS_CARD, Theme.GLASS_PANEL, 0.9f);
        d.text("Поза", Fonts.Kind.BOLD, x + 10, y + 9, Theme.TEXT);
        d.text(Draw.ellipsize(e.name, w - 50), x + 42, y + 9, Theme.TEXT_DIM);
        int gy = y + 24, gx = x + 6, segW = (w - 12) / 3;
        for (int i = 0; i < 3; i++) {
            boolean on = i == poseGroup;
            if (on) d.round(gx + i * segW, gy, segW, 18, 9, Theme.GLASS_ACTIVE);
            d.textCenter(GROUPS[i], gx + i * segW + segW / 2, Draw.textY(gy, 18), on ? Theme.TEXT : Theme.TEXT_DIM);
            int gi = i;
            hits.add(new Hit(gx + i * segW, gy, segW, 18, () -> poseGroup = gi));
        }
        float[] a = ModelPrefs.poses.containsKey(e.id) && ModelPrefs.poses.get(e.id)[poseGroup] != null
                ? ModelPrefs.poses.get(e.id)[poseGroup] : ModelPrefs.defaultAdjust();
        int ry = gy + 26;
        for (int k = 0; k < 7; k++) {
            d.text(AXES[k], x + 10, Draw.textY(ry, 20), Theme.TEXT_DIM);
            String v = k == 6 ? String.format(Locale.ROOT, "×%.2f", a[k]) : k < 3 ? Math.round(a[k]) + "°" : String.format(Locale.ROOT, "%.1f", a[k]);
            d.textRight(v, x + w - 48, Draw.textY(ry, 20), Theme.TEXT);
            int kk = k;
            stepButton(mx, my, x + w - 42, ry + 2, "−", () -> nudge(e, kk, -1));
            stepButton(mx, my, x + w - 22, ry + 2, "+", () -> nudge(e, kk, 1));
            ry += 22;
        }
        button(mx, my, x + w - 8, ry + 4, "Сбросить", () -> ModelPrefs.resetPose(e));
    }

    private void stepButton(int mx, int my, int x, int y, String s, Runnable r) {
        boolean hov = Widget.hit(mx, my, x, y, 16, 16);
        d.round(x, y, 16, 16, 8, hov ? Theme.GLASS_ACTIVE : Theme.GLASS_CARD);
        d.textCenter(s, x + 8, Draw.textY(y, 16), Theme.TEXT);
        hits.add(new Hit(x, y, 16, 16, r));
    }

    private void nudge(ModelEntry e, int k, int dir) {
        float[] a = ModelPrefs.adjust(e.id, poseGroup);
        boolean fast = hasShiftDown();
        a[k] += dir * STEP[k] * (fast ? 4 : 1);
        if (k == 6) a[k] = Math.max(0.1f, Math.min(4f, a[k]));
        ModelPrefs.poseChanged(e);
    }

    private static boolean hasShiftDown() {
        long win = Minecraft.getInstance().getWindow().handle();
        return GLFW.glfwGetKey(win, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS || GLFW.glfwGetKey(win, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
    }

    // ── питомцы ──────────────────────────────────────────────

    private void drawPets(GuiGraphics g, int mx, int my, int y, int h, float dt) {
        List<ModelEntry> list = new ArrayList<>();
        for (ModelEntry e : ModelRegistry.of(ModelEntry.Kind.PET)) if (matches(e)) list.add(e);
        ModelEntry active = ModelRegistry.get(ModelPrefs.pet);
        int gx = wx + 12, gw = ww - 24 - SIDE_W - 10;
        ModelEntry sel = active;
        grid(g, mx, my, gx, y, gw, h, list, sel, e -> {
            if (e.broken()) return;
            ModelPets mp = ModelPets.get();
            if (mp != null) {
                mp.select(e);
                if (!mp.isEnabled()) mp.setEnabled(true);
            }
        }, "Положите паки питомцев (.bbmodel или zip) в mraz1visual/pets и нажмите «Обновить папку».");
        int px = gx + gw + 10, py = y + 4;
        d.glass(px, py, SIDE_W, h - 8, Theme.RADIUS_CARD, Theme.GLASS_PANEL, 0.9f);
        if (sel == null) {
            d.text("Выберите питомца", px + 10, py + 10, Theme.TEXT_DIM);
            return;
        }
        d.text(Draw.ellipsize(sel.name, SIDE_W - 20), Fonts.Kind.BOLD, px + 10, py + 9, Theme.TEXT);
        d.text(sel.flying ? "летает" : "ходит", px + 10, py + 22, Theme.TEXT_MUTED);
        if (!sel.id.equals(petPreviewId)) {
            petPreviewId = sel.id;
            petPreview.play(sel.anim("idle") != null ? sel.anim("idle") : sel.anims.isEmpty() ? null : sel.anims.get(0), 0, true);
        }
        petPreview.update(dt);
        if (petPreview.finished()) {
            Anim idle = sel.anim("idle");
            petPreview.play(idle != null ? idle : petPreview.current(), 0.25f, true);
        }
        if (!sel.broken()) preview(g, sel, petPreview.pose(sel.mesh()), 200 + spin * 0.4f, -15, 0, 0.78f, px + 8, py + 34, SIDE_W - 16, 100);
        d.text("Анимации", Fonts.Kind.BOLD, px + 10, py + 142, Theme.TEXT_DIM);
        int ay = py + 156, ax = px + 8;
        for (Anim a : sel.anims) {
            int bw = Math.min(SIDE_W - 16, d.width(a.name) + 16);
            if (ax + bw > px + SIDE_W - 8) { ax = px + 8; ay += 21; }
            if (ay > py + h - 30) break;
            boolean playing = petPreview.current() == a, hov = Widget.hit(mx, my, ax, ay, bw, 18);
            d.round(ax, ay, bw, 18, 9, playing ? Theme.GLASS_ACTIVE : hov ? Theme.HOVER : Theme.GLASS_CARD);
            d.textCenter(Draw.ellipsize(a.name, bw - 8), ax + bw / 2, Draw.textY(ay, 18), playing ? Theme.TEXT : Theme.TEXT_DIM);
            hits.add(new Hit(ax, ay, bw, 18, () -> {
                petPreview.play(a, 0.2f, true);
                ModelPets mp = ModelPets.get();
                if (mp != null) mp.playNow(a.name);
            }));
            ax += bw + 4;
        }
        if (!sel.effects.isEmpty()) d.text("Эффекты: " + sel.effects.size(), px + 10, py + h - 24, Theme.TEXT_MUTED);
    }

    // ── косметика ────────────────────────────────────────────

    private void drawCosmetics(GuiGraphics g, int mx, int my, int y, int h) {
        List<ModelEntry> list = new ArrayList<>();
        for (ModelEntry e : ModelRegistry.all()) if ((e.kind == ModelEntry.Kind.HAT || e.kind == ModelEntry.Kind.WING) && matches(e)) list.add(e);
        ModelEntry hat = ModelCosmetics.pick(ModelEntry.Kind.HAT), wing = ModelCosmetics.pick(ModelEntry.Kind.WING);
        int gx = wx + 12, gw = ww - 24;
        d.text("Шляпа: " + (hat == null ? "нет" : hat.name) + "     Крылья: " + (wing == null ? "нет" : wing.name), gx + 6, y + 4, Theme.TEXT_DIM);
        grid(g, mx, my, gx, y + 16, gw, h - 16, list, null, e -> {
            if (e.broken()) return;
            if (e.kind == ModelEntry.Kind.HAT) ModelPrefs.hat = e.id;
            else ModelPrefs.wing = e.id;
            ModelPrefs.save();
            setModule("model_cosmetics");
        }, "Модели с именами hat и wing из папки weapon_skins появятся здесь (например, из Carnivoret Weapon Set).");
    }

    // ── ввод ─────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        double mx = e.x() / k, my = e.y() / k;
        if (!Widget.hit(mx, my, wx, wy, ww, wh)) {
            onClose();
            return true;
        }
        // сверху вниз: последние добавленные — поверх (звёздочка над карточкой)
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (Widget.hit(mx, my, h.x, h.y, h.w, h.h)) {
                h.action.run();
                return true;
            }
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scrollTarget = Math.max(0, Math.min(scrollMax, scrollTarget - (float) scrollY * 40));
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        int key = e.key();
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (!search.isEmpty()) { search.setLength(0); scrollTarget = 0; }
            else onClose();
            return true;
        }
        if (key == GLFW.GLFW_KEY_BACKSPACE && !search.isEmpty()) {
            search.setLength(search.length() - Character.charCount(search.codePointBefore(search.length())));
            scrollTarget = 0;
            return true;
        }
        if (key == GLFW.GLFW_KEY_F5) {
            ModelRegistry.reload();
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(CharacterEvent e) {
        int cp = e.codepoint();
        if (Character.isISOControl(cp) || search.length() > 30) return false;
        search.appendCodePoint(cp);
        scrollTarget = 0;
        return true;
    }
}

package dev.mraz1visual.overlay;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import dev.mraz1visual.Mraz1Visual;
import dev.mraz1visual.config.ConfigManager;
import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.core.Modules;
import dev.mraz1visual.ui.Anim;
import dev.mraz1visual.ui.Colors;
import dev.mraz1visual.ui.Draw;
import dev.mraz1visual.ui.Fonts;
import dev.mraz1visual.ui.Theme;
import dev.mraz1visual.ui.widget.Widget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Оверлей по Shift+Tab, как в Steam: время в игре, скриншоты, быстрые переключатели, ссылки.
 * Пока закрыт — не стоит ничего; текстуры скриншотов освобождаются при закрытии.
 */
public final class OverlayScreen extends Screen {
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.of("ru"));
    private static final int CARD_GAP = 14;

    private final Draw d = new Draw();
    private final Screenshots shots = new Screenshots();
    private float k = 1f;
    private int vw, vh;
    private long lastNanos;
    private float fade;

    // кэш строк — пересчитывается раз в секунду, не в каждом кадре
    private long lastSecond = -1;
    private String clock = "", date = "", total = "", today = "", session = "";
    private final List<String> topServers = new ArrayList<>(), topTimes = new ArrayList<>();
    private final long[] week = new long[7];
    private final String[] weekLabels = {"", "", "", "", "", "", ""};

    // раскладка
    private int c1x, c2x, c3x, cy, cw, ch;
    private final List<Module> quick = new ArrayList<>();
    private final float[] quickAnim = new float[16];
    private final float[] hover = new float[32];
    private List<ConfigManager.ProfileInfo> profiles = List.of();

    private Screenshots.Shot viewing;

    public OverlayScreen() {
        super(Component.literal("Mraz1Visual overlay"));
    }

    @Override
    protected void init() {
        d.font = font;
        Window win = minecraft.getWindow();
        int unit = Math.max(1, Math.round(win.getHeight() / 540f));
        k = (float) (unit / win.getGuiScale());
        d.scale = k;
        vw = (int) (width / k);
        vh = (int) (height / k);
        cw = Math.min(290, (vw - 2 * 30 - 2 * CARD_GAP) / 3);
        int totalW = cw * 3 + CARD_GAP * 2;
        c1x = (vw - totalW) / 2;
        c2x = c1x + cw + CARD_GAP;
        c3x = c2x + cw + CARD_GAP;
        cy = 92;
        ch = Math.min(360, vh - cy - 40);

        quick.clear();
        for (Module m : Mraz1Visual.MODULES.all()) {
            if (m.alwaysOn() || m.category == Category.CONFIGS) continue;
            if (quick.size() < 12) quick.add(m);
        }
        profiles = Mraz1Visual.CONFIG.list();
        if (shots.shots().isEmpty()) shots.open(9);
        lastSecond = -1;
    }

    private void refreshStrings() {
        long sec = System.currentTimeMillis() / 1000;
        if (sec == lastSecond) return;
        lastSecond = sec;
        clock = LocalTime.now().format(CLOCK);
        String dt = LocalDate.now().format(DATE);
        date = dt.substring(0, 1).toUpperCase() + dt.substring(1);
        total = PlayStats.format(PlayStats.totalSec);
        today = PlayStats.format(PlayStats.today());
        session = PlayStats.format(PlayStats.sessionSec);
        LocalDate now = LocalDate.now();
        for (int i = 0; i < 7; i++) {
            LocalDate day = now.minusDays(6 - i);
            week[i] = PlayStats.byDay.getOrDefault(day.toString(), 0L);
            weekLabels[i] = day.getDayOfWeek().getDisplayName(java.time.format.TextStyle.SHORT, Locale.of("ru"));
        }
        topServers.clear();
        topTimes.clear();
        PlayStats.byServer.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(3)
                .forEach(e -> {
                    topServers.add(e.getKey());
                    topTimes.add(PlayStats.format(e.getValue()));
                });
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float pt) {
        super.renderBackground(g, mouseX, mouseY, pt);
        g.fill(0, 0, width, height, 0x99000000);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 0 : Math.min(0.1f, (now - lastNanos) / 1e9f);
        lastNanos = now;
        Anim.enabled = Modules.client.animations.get();
        fade = Anim.enabled ? Math.min(1, fade + dt * 5) : 1;
        refreshStrings();
        double mx = mouseX / k, my = mouseY / k;

        d.begin(g, font, k);
        int lift = Math.round((1 - fade) * 12);

        // шапка: часы, дата, сессия
        d.textScaled(clock, Fonts.Kind.TITLE, vw / 2 - Math.round(Fonts.width(clock, Fonts.Kind.TITLE) * 1.6f) / 2, 22 + lift, 1.6f, Colors.mulAlpha(Theme.TEXT, fade));
        String sub = date + "  ·  сессия " + session;
        d.textCenter(sub, vw / 2, 56 + lift, Colors.mulAlpha(Theme.TEXT_DIM, fade));

        if (viewing != null) {
            renderViewer(g, mx, my);
        } else {
            renderTime(c1x, cy + lift);
            renderShots(g, c2x, cy + lift, mx, my, dt);
            renderQuick(c3x, cy + lift, mx, my, dt);
        }
        d.textCenter("Shift+Tab или Esc — закрыть", vw / 2, vh - 22, Colors.mulAlpha(Theme.TEXT_MUTED, fade));
        d.end();
    }

    private void card(int x, int y, String title) {
        d.card(x, y, cw, ch, Theme.RADIUS, Colors.mulAlpha(0xE00E0E15, fade), Theme.CARD_EDGE);
        d.text(title, Fonts.Kind.HEAD, x + 14, y + 14, Colors.mulAlpha(Theme.TEXT_MUTED, fade));
    }

    // ── время в игре ─────────────────────────────────────────

    private void renderTime(int x, int y) {
        card(x, y, "ВРЕМЯ В ИГРЕ");
        d.textScaled(total, Fonts.Kind.HEAD, x + 14, y + 36, 1.6f, Colors.mulAlpha(Theme.TEXT, fade));
        d.text("всего", x + 14, y + 60, Theme.TEXT_MUTED);
        d.text("Сегодня", x + 14, y + 82, Theme.TEXT_DIM);
        d.textRight(today, x + cw - 14, y + 82, Theme.TEXT);
        d.text("Эта сессия", x + 14, y + 98, Theme.TEXT_DIM);
        d.textRight(session, x + cw - 14, y + 98, Theme.TEXT);

        // 7 дней столбиками
        int bx = x + 14, by = y + 124, bw = cw - 28, bh = 70;
        long max = 1;
        for (long v : week) max = Math.max(max, v);
        int col = (bw - 6 * 8) / 7;
        for (int i = 0; i < 7; i++) {
            int h = Math.max(2, Math.round(bh * (week[i] / (float) max)));
            int cx = bx + i * (col + 8);
            d.round(cx, by + bh - h, col, h, 3, i == 6 ? Theme.accent() : Theme.accentSoft());
            d.textCenter(weekLabels[i], cx + col / 2, by + bh + 6, Theme.TEXT_MUTED);
        }

        int sy = by + bh + 30;
        d.text("СЕРВЕРЫ", Fonts.Kind.HEAD, x + 14, sy, Theme.TEXT_MUTED);
        sy += 18;
        if (topServers.isEmpty()) d.text("Пока пусто", x + 14, sy, Theme.TEXT_MUTED);
        for (int i = 0; i < topServers.size(); i++) {
            d.text(Draw.ellipsize(topServers.get(i), cw - 110), x + 14, sy, Theme.TEXT_DIM);
            d.textRight(topTimes.get(i), x + cw - 14, sy, Theme.TEXT);
            sy += 16;
        }
    }

    // ── скриншоты ────────────────────────────────────────────

    private int thumbW() { return (cw - 28 - 2 * 6) / 3; }
    private int thumbH() { return thumbW() * 9 / 16; }

    private void renderShots(GuiGraphics g, int x, int y, double mx, double my, float dt) {
        card(x, y, "СКРИНШОТЫ");
        List<Screenshots.Shot> list = shots.shots();
        int tw = thumbW(), th = thumbH();
        if (list.isEmpty()) d.text("Скриншотов нет — F2 в игре.", x + 14, y + 40, Theme.TEXT_MUTED);
        for (int i = 0; i < list.size(); i++) {
            int tx = x + 14 + (i % 3) * (tw + 6), ty = y + 38 + (i / 3) * (th + 6);
            Screenshots.Shot s = list.get(i);
            boolean hov = Widget.hit(mx, my, tx, ty, tw, th);
            d.round(tx, ty, tw, th, 3, 0xFF1A1A24);
            if (s.texture != null) blitIn(g, s, tx, ty, tw, th);
            if (hov) d.round(tx, ty, tw, th, 3, 0x30FFFFFF);
        }
        int by = y + ch - 26;
        link(20, "Открыть папку", x + 14, by, mx, my, dt);
    }

    private void blitIn(GuiGraphics g, Screenshots.Shot s, int x, int y, int w, int h) {
        // вписываем с сохранением пропорций
        float kk = Math.min(w / (float) s.texW, h / (float) s.texH);
        int dw = Math.round(s.texW * kk), dh = Math.round(s.texH * kk);
        int ox = x + (w - dw) / 2, oy = y + (h - dh) / 2;
        g.blit(RenderPipelines.GUI_TEXTURED, s.texture, ox, oy, 0f, 0f, dw, dh, s.texW, s.texH, s.texW, s.texH, 0xFFFFFFFF);
    }

    private void renderViewer(GuiGraphics g, double mx, double my) {
        int w = Math.min(vw - 80, 960), h = w * 9 / 16;
        if (h > vh - 140) {
            h = vh - 140;
            w = h * 16 / 9;
        }
        int x = (vw - w) / 2, y = 84;
        d.round(x - 2, y - 2, w + 4, h + 4, 6, 0xFF0E0E15);
        if (viewing.texture != null) blitIn(g, viewing, x, y, w, h);
        else d.textCenter("Загрузка…", vw / 2, y + h / 2, Theme.TEXT_DIM);
        d.textCenter(viewing.name + "   ·   клик — назад", vw / 2, y + h + 12, Theme.TEXT_DIM);
    }

    // ── быстрые переключатели и ссылки ───────────────────────

    private void renderQuick(int x, int y, double mx, double my, float dt) {
        card(x, y, "БЫСТРО");
        // профили
        int px = x + 14, py = y + 36;
        String active = Mraz1Visual.CONFIG.activeFile();
        for (int i = 0; i < Math.min(4, profiles.size()); i++) {
            ConfigManager.ProfileInfo p = profiles.get(i);
            int w = Fonts.width(p.name()) + 16;
            if (px + w > x + cw - 14) break;
            boolean on = p.file().equals(active);
            d.round(px, py, w, 16, 8, on ? Theme.accentSoft() : Theme.FIELD);
            d.text(p.name(), px + 8, py + 4, on ? Theme.TEXT : Theme.TEXT_DIM);
            px += w + 6;
        }
        // модули
        int ry = py + 26;
        for (int i = 0; i < quick.size() && ry + 18 < y + ch - 60; i++) {
            Module m = quick.get(i);
            boolean hov = Widget.hit(mx, my, x + 8, ry, cw - 16, 18);
            hover[i] = Anim.to(hover[i], hov ? 1 : 0, dt);
            quickAnim[i] = Anim.to(quickAnim[i], m.isEnabled() ? 1 : 0, dt);
            if (hover[i] > 0.01f) d.round(x + 8, ry, cw - 16, 18, 4, Colors.mulAlpha(Theme.HOVER, hover[i] * 2));
            d.text(m.name, x + 14, ry + 5, m.isEnabled() ? Theme.TEXT : Theme.TEXT_DIM);
            d.toggle(x + cw - 14 - Theme.SWITCH_W, ry + 4, quickAnim[i]);
            ry += 20;
        }
        int ly = y + ch - 44;
        link(21, "Браузер", x + 14, ly, mx, my, dt);
        link(22, "Настройки Mraz1Visual", x + 14, ly + 18, mx, my, dt);
    }

    private final int[] lx = new int[32], ly = new int[32], lw = new int[32];

    private void link(int id, String text, int x, int y, double mx, double my, float dt) {
        int w = Fonts.width(text) + 12;
        lx[id] = x; ly[id] = y; lw[id] = w;
        boolean hov = Widget.hit(mx, my, x - 2, y - 3, w, 14);
        hover[id] = Anim.to(hover[id], hov ? 1 : 0, dt);
        d.text(text + " →", x, y, Colors.lerp(Theme.TEXT_DIM, Theme.TEXT, hover[id]));
    }

    private boolean hitLink(int id, double mx, double my) {
        return lw[id] > 0 && Widget.hit(mx, my, lx[id] - 2, ly[id] - 3, lw[id] + 10, 14);
    }

    // ── ввод ─────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        double mx = e.x() / k, my = e.y() / k;
        if (viewing != null) {
            viewing = null;
            return true;
        }
        // миниатюры
        List<Screenshots.Shot> list = shots.shots();
        int tw = thumbW(), th = thumbH();
        for (int i = 0; i < list.size(); i++) {
            int tx = c2x + 14 + (i % 3) * (tw + 6), ty = cy + 38 + (i / 3) * (th + 6);
            if (Widget.hit(mx, my, tx, ty, tw, th)) {
                viewing = shots.openFull(list.get(i));
                return true;
            }
        }
        if (hitLink(20, mx, my)) {
            Screenshots.openExternal(Screenshots.dir().toString());
            return true;
        }
        if (hitLink(21, mx, my)) {
            dev.mraz1visual.browser.Browser.open(this);
            return true;
        }
        if (hitLink(22, mx, my)) {
            onClose();
            Mraz1Visual.openMenu(minecraft);
            return true;
        }
        // профили
        int px = c3x + 14, py = cy + 36;
        for (int i = 0; i < Math.min(4, profiles.size()); i++) {
            ConfigManager.ProfileInfo p = profiles.get(i);
            int w = Fonts.width(p.name()) + 16;
            if (Widget.hit(mx, my, px, py, w, 16)) {
                Mraz1Visual.CONFIG.switchTo(p.file());
                return true;
            }
            px += w + 6;
        }
        // модули
        int ry = py + 26;
        for (int i = 0; i < quick.size() && ry + 18 < cy + ch - 60; i++) {
            if (Widget.hit(mx, my, c3x + 8, ry, cw - 16, 18)) {
                quick.get(i).toggle();
                return true;
            }
            ry += 20;
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        int key = e.key();
        boolean shift = (e.modifiers() & InputConstants.MOD_SHIFT) != 0;
        if (key == InputConstants.KEY_ESCAPE || (shift && key == Modules.client.overlayKey.get())) {
            if (viewing != null && key == InputConstants.KEY_ESCAPE) viewing = null;
            else onClose();
            return true;
        }
        return false;
    }

    @Override
    public void removed() {
        shots.close();
        viewing = null;
        lastNanos = 0;
        fade = 0;
        Mraz1Visual.CONFIG.saveIfDirty();
        PlayStats.save();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

package dev.mraz1visual.ui.desktop;

import com.mojang.blaze3d.platform.InputConstants;
import dev.mraz1visual.Mraz1Visual;
import dev.mraz1visual.browser.Browser;
import dev.mraz1visual.core.Modules;
import dev.mraz1visual.module.Desktop;
import dev.mraz1visual.overlay.Screenshots;
import dev.mraz1visual.ui.Anim;
import dev.mraz1visual.ui.Colors;
import dev.mraz1visual.ui.Draw;
import dev.mraz1visual.ui.Theme;
import dev.mraz1visual.ui.menu.WallpaperScreen;
import dev.mraz1visual.ui.widget.Widget;
import dev.mraz1visual.wallpaper.WallpaperPlayer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * «Рабочий стол» внутри Minecraft: обои, ярлыки, панель задач с часами, меню «Пуск»
 * и окна-браузеры (YouTube, Twitch и т. д.), которые можно двигать, разворачивать и сворачивать.
 * Окна живут и после закрытия стола — видео продолжает играть, пока окно не закрыто крестиком.
 */
public final class DesktopScreen extends Screen {
    static final int TASKBAR_H = 28;
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    /** Ярлык: веб-приложение (url) или действие клиента. */
    record App(String name, String glyph, int color, String url, Runnable action) {}

    /** Открытые окна. Последнее в списке — сверху. Статично: переживает закрытие стола. */
    static final List<DesktopWindow> WINDOWS = new ArrayList<>();

    private final Screen parent;
    private final Draw d = new Draw();
    private final List<App> apps = new ArrayList<>();
    private boolean startOpen;
    private float startAnim;
    private long lastNanos;
    private String toast;
    private long toastUntil;

    // захват мыши: окно, которое двигаем / тянем за угол / в котором держим кнопку на странице
    private DesktopWindow grabbed;
    private int grabMode; // 1 — двигаем, 2 — размер, 3 — страница
    private double grabDx, grabDy;

    public DesktopScreen(Screen parent) {
        super(Component.literal("Рабочий стол"));
        this.parent = parent;
        apps.add(new App("Браузер", "G", 0xFF4C8BF5, "https://www.google.com", null));
        apps.add(new App("YouTube", "▶", 0xFFFF3B30, "https://www.youtube.com", null));
        apps.add(new App("Twitch", "T", 0xFF9146FF, "https://www.twitch.tv", null));
        apps.add(new App("VK Видео", "V", 0xFF2787F5, "https://vkvideo.ru", null));
        apps.add(new App("Discord", "D", 0xFF5865F2, "https://discord.com/app", null));
        apps.add(new App("Modrinth", "M", 0xFF1BD96A, "https://modrinth.com", null));
        apps.add(new App("Настройки", "⚙", 0xFF3A3A44, null, () -> Mraz1Visual.openMenu(minecraft)));
        apps.add(new App("Обои", "✦", 0xFFB8B4F2, null, () -> minecraft.setScreen(new WallpaperScreen(this))));
    }

    @Override
    protected void init() {
        d.font = font;
        for (DesktopWindow w : WINDOWS) w.fit(width, height - TASKBAR_H);
    }

    // ── окна ─────────────────────────────────────────────────

    void open(App app) {
        startOpen = false;
        if (app.action() != null) {
            app.action().run();
            return;
        }
        if (!Browser.available()) {
            Screenshots.openExternal(app.url());
            toast("Встроенный браузер не установлен — открыл в Windows");
            return;
        }
        int n = WINDOWS.size();
        int w = Math.min(width - 40, Math.max(320, width * 2 / 3)), h = Math.min(height - TASKBAR_H - 30, Math.max(200, height * 2 / 3));
        int x = 20 + (n * 18) % Math.max(1, width - w - 30), y = 12 + (n * 14) % Math.max(1, height - TASKBAR_H - h - 20);
        DesktopWindow win = new DesktopWindow(app.name(), app.glyph(), app.color(), app.url(), x, y, w, h);
        WINDOWS.add(win);
        focus(win);
    }

    void focus(DesktopWindow w) {
        WINDOWS.remove(w);
        WINDOWS.add(w);
        w.minimized = false;
        for (DesktopWindow o : WINDOWS) o.setFocus(o == w);
    }

    DesktopWindow top() {
        for (int i = WINDOWS.size() - 1; i >= 0; i--) if (!WINDOWS.get(i).minimized) return WINDOWS.get(i);
        return null;
    }

    void close(DesktopWindow w) {
        w.close();
        WINDOWS.remove(w);
        if (grabbed == w) grabbed = null;
    }

    static void closeAll() {
        for (DesktopWindow w : WINDOWS) w.close();
        WINDOWS.clear();
    }

    private void toast(String s) {
        toast = s;
        toastUntil = System.currentTimeMillis() + 3500;
    }

    private DesktopWindow windowAt(double mx, double my) {
        for (int i = WINDOWS.size() - 1; i >= 0; i--) {
            DesktopWindow w = WINDOWS.get(i);
            if (!w.minimized && w.contains(mx, my)) return w;
        }
        return null;
    }

    // ── рисование ────────────────────────────────────────────

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float pt) {
        Desktop m = Desktop.instance();
        double dim = m == null ? 20 : m.dim();
        if (!WallpaperPlayer.render(g, 0, 0, width, height, 0, dim)) {
            d.begin(g, font, 1f);
            d.gradientV(0, 0, width, height, 0xFF141226, 0xFF06060C);
            d.end();
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 0 : Math.min(0.1f, (now - lastNanos) / 1e9f);
        lastNanos = now;
        Anim.enabled = Modules.client.animations.get();
        double guiScale = minecraft.getWindow().getGuiScale();

        renderIcons(g, mouseX, mouseY);

        DesktopWindow focused = top();
        for (DesktopWindow w : WINDOWS) {
            if (w.minimized) continue;
            g.nextStratum();
            // новое окно «выплывает» из центра за 0.2 с
            float t = Math.min(1f, (System.nanoTime() - w.openedAt) / 2e8f);
            boolean anim = t < 1f && Modules.client.animations.get();
            if (anim) {
                float e = 1f - (float) Math.pow(1f - t, 3);
                float sc = 0.85f + 0.15f * e, cx = w.x + w.w / 2f, cy = w.y + w.h / 2f;
                g.pose().pushMatrix();
                g.pose().translate(cx, cy);
                g.pose().scale(sc, sc);
                g.pose().translate(-cx, -cy);
            }
            w.render(g, d, font, mouseX, mouseY, dt, guiScale, w == focused);
            if (anim) g.pose().popMatrix();
        }

        g.nextStratum();
        renderTaskbar(g, mouseX, mouseY, dt, focused);
        startAnim = Anim.to(startAnim, startOpen ? 1 : 0, dt);
        if (startAnim > 0.02f) renderStart(g, mouseX, mouseY);

        if (toast != null && System.currentTimeMillis() < toastUntil) {
            d.begin(g, font, 1f);
            int tw = d.width(toast) + 20;
            d.card(width / 2 - tw / 2, 10, tw, 20, 6, 0xF00E0E15, Theme.CARD_EDGE);
            d.textCenter(toast, width / 2, 16, Theme.TEXT);
            d.end();
        }

        DesktopWindow hov = windowAt(mouseX, mouseY);
        if (hov != null && hov.inPage(mouseX, mouseY)) hov.applyCursor(g);
    }

    private void renderIcons(GuiGraphics g, int mx, int my) {
        d.begin(g, font, 1f);
        int cell = 52, x = 10, y = 10;
        for (App a : apps) {
            boolean hov = Widget.hit(mx, my, x, y, cell, cell);
            if (hov) d.round(x, y, cell, cell, 6, 0x30FFFFFF);
            d.round(x + 12, y + 5, 28, 28, 7, a.color());
            d.textCenter(a.glyph(), x + 26, y + 15, 0xFFFFFFFF);
            d.textCenter(a.name(), x + 26, y + 38, 0xFFFFFFFF);
            y += cell + 4;
            if (y + cell > height - TASKBAR_H - 4) { y = 10; x += cell + 4; }
        }
        d.end();
    }

    private void renderTaskbar(GuiGraphics g, int mx, int my, float dt, DesktopWindow focused) {
        int ty = height - TASKBAR_H;
        d.begin(g, font, 1f);
        d.rect(0, ty, width, TASKBAR_H, 0xE80B0B12);
        d.hLine(0, ty, width, 0x22FFFFFF);

        int count = 1 + WINDOWS.size();
        int slot = 26, total = count * (slot + 4);
        int x = width / 2 - total / 2;
        // «Пуск»
        boolean sh = Widget.hit(mx, my, x, ty + 1, slot, slot);
        d.round(x, ty + 2, slot, slot - 2, 6, sh || startOpen ? 0x40FFFFFF : 0x18FFFFFF);
        d.textCenter("M", x + slot / 2, ty + 10, Theme.accent());
        x += slot + 4;
        for (DesktopWindow w : WINDOWS) {
            boolean h = Widget.hit(mx, my, x, ty + 1, slot, slot);
            d.round(x, ty + 2, slot, slot - 2, 6, h ? 0x30FFFFFF : 0x10FFFFFF);
            d.round(x + 6, ty + 6, slot - 12, slot - 12, 4, w.color);
            d.textCenter(w.glyph, x + slot / 2, ty + 10, 0xFFFFFFFF);
            int lineW = w == focused ? 12 : 5;
            d.round(x + slot / 2 - lineW / 2, ty + TASKBAR_H - 4, lineW, 2, 1, w.minimized ? 0x80FFFFFF : Theme.accent());
            x += slot + 4;
        }

        LocalDateTime t = LocalDateTime.now();
        d.textRight(t.format(CLOCK), width - 10, ty + 5, Theme.TEXT);
        d.textRight(t.format(DATE), width - 10, ty + 16, Theme.TEXT_MUTED);
        d.end();
    }

    private int startX() {
        int count = 1 + WINDOWS.size();
        return width / 2 - count * 30 / 2;
    }

    private void renderStart(GuiGraphics g, int mx, int my) {
        int w = 150, rows = apps.size() + 2, h = rows * 18 + 26;
        int x = Math.max(4, startX()), y = height - TASKBAR_H - 6 - h;
        d.begin(g, font, 1f);
        d.card(x, y, w, h, 8, Colors.mulAlpha(0xF40E0E15, startAnim), Colors.mulAlpha(Theme.CARD_EDGE, startAnim));
        d.text("Mraz1Visual", x + 10, y + 8, Colors.mulAlpha(Theme.accent(), startAnim));
        int ry = y + 22;
        for (App a : apps) {
            if (Widget.hit(mx, my, x + 4, ry, w - 8, 18)) d.round(x + 4, ry, w - 8, 18, 4, 0x30FFFFFF);
            d.round(x + 8, ry + 3, 12, 12, 3, a.color());
            d.text(a.name(), x + 26, ry + 5, Colors.mulAlpha(Theme.TEXT, startAnim));
            ry += 18;
        }
        String[] extra = {"Закрыть все окна", "Выйти в игру"};
        for (String s : extra) {
            if (Widget.hit(mx, my, x + 4, ry, w - 8, 18)) d.round(x + 4, ry, w - 8, 18, 4, 0x30FFFFFF);
            d.text(s, x + 26, ry + 5, Colors.mulAlpha(Theme.TEXT_MUTED, startAnim));
            ry += 18;
        }
        d.end();
    }

    // ── ввод ─────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubled) {
        double mx = e.x(), my = e.y();
        int ty = height - TASKBAR_H;

        if (startOpen) {
            int w = 150, rows = apps.size() + 2, h = rows * 18 + 26;
            int x = Math.max(4, startX()), y = ty - 6 - h;
            if (Widget.hit(mx, my, x, y, w, h)) {
                int ry = y + 22;
                for (App a : apps) {
                    if (Widget.hit(mx, my, x + 4, ry, w - 8, 18)) { open(a); return true; }
                    ry += 18;
                }
                if (Widget.hit(mx, my, x + 4, ry, w - 8, 18)) { closeAll(); startOpen = false; return true; }
                ry += 18;
                if (Widget.hit(mx, my, x + 4, ry, w - 8, 18)) { onClose(); return true; }
                return true;
            }
            startOpen = false;
        }

        if (my >= ty) {
            int count = 1 + WINDOWS.size(), slot = 26;
            int x = width / 2 - count * (slot + 4) / 2;
            if (Widget.hit(mx, my, x, ty, slot, TASKBAR_H)) { startOpen = !startOpen; return true; }
            x += slot + 4;
            for (DesktopWindow w : new ArrayList<>(WINDOWS)) {
                if (Widget.hit(mx, my, x, ty, slot, TASKBAR_H)) {
                    if (w == top()) w.minimized = true;
                    else focus(w);
                    return true;
                }
                x += slot + 4;
            }
            return true;
        }

        DesktopWindow w = windowAt(mx, my);
        if (w != null) {
            if (w != top()) focus(w);
            int hit = w.clickChrome(mx, my, e.button(), minecraft);
            switch (hit) {
                case DesktopWindow.HIT_CLOSE -> close(w);
                case DesktopWindow.HIT_MIN -> w.minimized = true;
                case DesktopWindow.HIT_MAX -> w.toggleMax(width, height - TASKBAR_H);
                case DesktopWindow.HIT_TITLE -> {
                    if (doubled) w.toggleMax(width, height - TASKBAR_H);
                    else { grabbed = w; grabMode = 1; grabDx = mx - w.x; grabDy = my - w.y; }
                }
                case DesktopWindow.HIT_RESIZE -> { grabbed = w; grabMode = 2; grabDx = w.x + w.w - mx; grabDy = w.y + w.h - my; }
                case DesktopWindow.HIT_PAGE -> {
                    grabbed = w; grabMode = 3;
                    w.pageClicked(mx, my, e, doubled);
                }
                default -> {}
            }
            return true;
        }

        // ярлыки
        int cell = 52, x = 10, y = 10;
        for (App a : apps) {
            if (Widget.hit(mx, my, x, y, cell, cell)) { open(a); return true; }
            y += cell + 4;
            if (y + cell > height - TASKBAR_H - 4) { y = 10; x += cell + 4; }
        }
        for (DesktopWindow o : WINDOWS) o.setFocus(false);
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        if (grabbed != null && grabMode == 3) grabbed.pageReleased(e.x(), e.y(), e);
        grabbed = null;
        grabMode = 0;
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        double mx = e.x(), my = e.y();
        if (grabbed == null) return true;
        int areaH = height - TASKBAR_H;
        switch (grabMode) {
            case 1 -> {
                if (grabbed.maximized) {
                    // тянем развёрнутое окно — оно восстанавливается под курсором
                    grabbed.toggleMax(width, areaH);
                    grabDx = Math.min(grabDx, grabbed.w - 40);
                }
                grabbed.moveTo((int) (mx - grabDx), (int) (my - grabDy), width, areaH);
            }
            case 2 -> grabbed.resizeTo((int) (mx + grabDx) - grabbed.x, (int) (my + grabDy) - grabbed.y, width, areaH);
            case 3 -> grabbed.pageMoved(mx, my);
            default -> {}
        }
        return true;
    }

    @Override
    public void mouseMoved(double x, double y) {
        DesktopWindow w = windowAt(x, y);
        if (w != null && w.inPage(x, y)) w.pageMoved(x, y);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double sx, double sy) {
        DesktopWindow w = windowAt(x, y);
        if (w != null && w.inPage(x, y)) w.pageScrolled(x, y, sy);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        DesktopWindow w = top();
        if (w != null && w.keyPressed(e, minecraft)) return true;
        if (e.key() == InputConstants.KEY_ESCAPE) {
            if (startOpen) startOpen = false;
            else if (w != null && w.pageFocused) w.setFocus(false); // первый Esc — выйти со страницы
            else onClose();
            return true;
        }
        Desktop m = Desktop.instance();
        if (m != null && e.key() == m.key() && (w == null || !w.pageFocused)) {
            onClose();
            return true;
        }
        if (w != null) w.pageKeyPressed(e);
        return true;
    }

    @Override
    public boolean keyReleased(KeyEvent e) {
        DesktopWindow w = top();
        if (w != null) w.pageKeyReleased(e);
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent e) {
        DesktopWindow w = top();
        if (w != null) w.charTyped(e);
        return true;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public void removed() {
        lastNanos = 0;
        grabbed = null;
        Desktop m = Desktop.instance();
        if (m != null && m.closeOnExit()) closeAll();
        else for (DesktopWindow w : WINDOWS) w.setFocus(false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

package dev.mraz1visual.ui.desktop;

import com.mojang.blaze3d.platform.InputConstants;
import dev.mraz1visual.browser.WebView;
import dev.mraz1visual.overlay.Screenshots;
import dev.mraz1visual.ui.Draw;
import dev.mraz1visual.ui.Theme;
import dev.mraz1visual.ui.widget.TextInput;
import dev.mraz1visual.ui.widget.Widget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

import java.net.URI;

/** Окно рабочего стола: заголовок с кнопками, строка адреса и страница браузера. */
final class DesktopWindow {
    static final int HIT_NONE = 0, HIT_TITLE = 1, HIT_CLOSE = 2, HIT_MAX = 3, HIT_MIN = 4, HIT_RESIZE = 5, HIT_PAGE = 6, HIT_NAV = 7;
    private static final int TITLE_H = 18, NAV_H = 22, MIN_W = 240, MIN_H = 150;

    final String name, glyph;
    final int color;
    int x, y, w, h;
    boolean minimized, maximized, pageFocused;
    private int rx, ry, rw, rh; // размер до разворота
    private final WebView view;
    private final TextInput address = new TextInput("Адрес или запрос", 512);
    private String lastUrl = "";
    private double guiScale = 1;
    /** Когда окно открылось — для анимации появления. */
    final long openedAt = System.nanoTime();

    DesktopWindow(String name, String glyph, int color, String url, int x, int y, int w, int h) {
        this.name = name;
        this.glyph = glyph;
        this.color = color;
        this.x = x; this.y = y; this.w = w; this.h = h;
        this.view = new WebView(url);
        address.set(url);
    }

    // ── геометрия ────────────────────────────────────────────

    int pageX() { return x + 1; }
    int pageY() { return y + TITLE_H + NAV_H; }
    int pageW() { return w - 2; }
    int pageH() { return h - TITLE_H - NAV_H - 1; }

    boolean contains(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }

    boolean inPage(double mx, double my) {
        return mx >= pageX() && mx < pageX() + pageW() && my >= pageY() && my < pageY() + pageH();
    }

    private boolean inResize(double mx, double my) {
        return !maximized && mx >= x + w - 8 && mx < x + w && my >= y + h - 8 && my < y + h;
    }

    void fit(int areaW, int areaH) {
        if (maximized) { x = 0; y = 0; w = areaW; h = areaH; return; }
        w = Math.max(MIN_W, Math.min(w, areaW));
        h = Math.max(MIN_H, Math.min(h, areaH));
        moveTo(x, y, areaW, areaH);
    }

    void moveTo(int nx, int ny, int areaW, int areaH) {
        x = Math.max(-w + 60, Math.min(areaW - 60, nx));
        y = Math.max(0, Math.min(areaH - TITLE_H, ny));
    }

    void resizeTo(int nw, int nh, int areaW, int areaH) {
        w = Math.max(MIN_W, Math.min(nw, areaW - x));
        h = Math.max(MIN_H, Math.min(nh, areaH - y));
    }

    void toggleMax(int areaW, int areaH) {
        if (maximized) {
            maximized = false;
            x = rx; y = ry; w = rw; h = rh;
            fit(areaW, areaH);
        } else {
            rx = x; ry = y; rw = w; rh = h;
            maximized = true;
            fit(areaW, areaH);
        }
    }

    private String title() {
        try {
            String host = URI.create(view.url()).getHost();
            if (host != null) return name + " — " + (host.startsWith("www.") ? host.substring(4) : host);
        } catch (Throwable ignored) {
        }
        return name;
    }

    // ── рисование ────────────────────────────────────────────

    void render(GuiGraphics g, Draw d, Font font, int mx, int my, float dt, double guiScale, boolean focused) {
        this.guiScale = guiScale;
        view.tick();
        view.resize((int) Math.round(pageW() * guiScale), (int) Math.round(pageH() * guiScale));
        if (!address.focused) {
            String u = view.url();
            if (u != null && !u.equals(lastUrl)) { lastUrl = u; address.set(u); }
        }

        int r = maximized ? 0 : 8;
        d.begin(g, font, 1f);
        if (!maximized) d.round(x - 2, y + 2, w + 4, h + 2, 10, 0x50000000);
        d.card(x, y, w, h, r, focused ? 0xFA101018 : 0xF00C0C12, focused ? 0x40FFFFFF : Theme.CARD_EDGE);
        // заголовок
        d.round(x + 6, y + 4, 10, 10, 3, color);
        d.textCenter(glyph, x + 11, y + 5, 0xFFFFFFFF);
        d.text(Draw.ellipsize(title(), w - 90), x + 21, y + 5, focused ? Theme.TEXT : Theme.TEXT_MUTED);
        chromeButton(d, "—", x + w - 54, mx, my, 0x30FFFFFF);
        chromeButton(d, maximized ? "❐" : "□", x + w - 36, mx, my, 0x30FFFFFF);
        chromeButton(d, "✕", x + w - 18, mx, my, 0xC0E0393E);
        // навигация
        int ny = y + TITLE_H;
        navButton(d, "←", x + 4, ny, mx, my, view.canGoBack());
        navButton(d, "→", x + 24, ny, mx, my, view.canGoForward());
        navButton(d, "↻", x + 44, ny, mx, my, view.ready());
        navButton(d, "↗", x + w - 22, ny, mx, my, true);
        address.setBounds(x + 66, ny + 2, Math.max(40, w - 66 - 28), 17);
        address.render(d, mx, my, dt);
        // страница
        d.rect(pageX(), pageY(), pageW(), pageH(), 0xFF0E0E15);
        if (!view.ready()) d.textCenter(view.status(), pageX() + pageW() / 2, pageY() + pageH() / 2 - 4, Theme.TEXT_MUTED);
        if (inResize(mx, my)) d.round(x + w - 7, y + h - 7, 5, 5, 2, 0x80FFFFFF);
        d.end();

        view.render(g, pageX(), pageY(), pageX() + pageW(), pageY() + pageH());
    }

    private void chromeButton(Draw d, String s, int bx, int mx, int my, int hoverColor) {
        boolean hov = Widget.hit(mx, my, bx, y, 18, TITLE_H);
        if (hov) d.round(bx + 1, y + 1, 16, TITLE_H - 2, 4, hoverColor);
        d.textCenter(s, bx + 9, y + 5, hov ? 0xFFFFFFFF : Theme.TEXT_MUTED);
    }

    private void navButton(Draw d, String s, int bx, int ny, int mx, int my, boolean enabled) {
        boolean hov = enabled && Widget.hit(mx, my, bx, ny + 2, 18, 17);
        if (hov) d.round(bx, ny + 2, 18, 17, 4, 0x30FFFFFF);
        d.textCenter(s, bx + 9, ny + 7, enabled ? (hov ? Theme.TEXT : Theme.TEXT_DIM) : 0x40FFFFFF);
    }

    void applyCursor(GuiGraphics g) { view.applyCursor(g); }

    // ── ввод ─────────────────────────────────────────────────

    /** Клик не по странице: что нажали. Кнопки навигации и адрес обрабатываются здесь же. */
    int clickChrome(double mx, double my, int button, Minecraft mc) {
        if (inResize(mx, my)) return HIT_RESIZE;
        if (my < y + TITLE_H) {
            if (mx >= x + w - 18) return HIT_CLOSE;
            if (mx >= x + w - 36) return HIT_MAX;
            if (mx >= x + w - 54) return HIT_MIN;
            return HIT_TITLE;
        }
        if (inPage(mx, my)) {
            address.focused = false;
            return HIT_PAGE;
        }
        pageFocused = false;
        view.setFocus(false);
        int ny = y + TITLE_H;
        if (address.mouseClicked(mx, my, button)) return HIT_NAV;
        address.focused = false;
        if (Widget.hit(mx, my, x + 4, ny + 2, 18, 17)) view.back();
        else if (Widget.hit(mx, my, x + 24, ny + 2, 18, 17)) view.forward();
        else if (Widget.hit(mx, my, x + 44, ny + 2, 18, 17)) view.reload();
        else if (Widget.hit(mx, my, x + w - 22, ny + 2, 18, 17)) Screenshots.openExternal(view.url());
        return HIT_NAV;
    }

    private int px(double gx) { return (int) Math.round((gx - pageX()) * guiScale); }
    private int py(double gy) { return (int) Math.round((gy - pageY()) * guiScale); }

    void pageClicked(double mx, double my, MouseButtonEvent e, boolean doubled) {
        pageFocused = true;
        view.setFocus(true);
        view.mouseClicked(px(mx), py(my), e, doubled);
    }

    void pageReleased(double mx, double my, MouseButtonEvent e) { view.mouseReleased(px(mx), py(my), e); }

    void pageMoved(double mx, double my) { view.mouseMoved(px(mx), py(my)); }

    void pageScrolled(double mx, double my, double amount) { view.mouseScrolled(px(mx), py(my), amount); }

    /** true — клавишу съела строка адреса. */
    boolean keyPressed(KeyEvent e, Minecraft mc) {
        if (!address.focused) return false;
        int key = e.key();
        if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
            view.navigate(address.value());
            lastUrl = "";
            address.focused = false;
            pageFocused = true;
            view.setFocus(true);
            return true;
        }
        if (key == InputConstants.KEY_ESCAPE) {
            address.focused = false;
            return true;
        }
        boolean ctrl = (e.modifiers() & InputConstants.MOD_CONTROL) != 0;
        address.keyPressed(key, ctrl, mc.keyboardHandler::getClipboard);
        return true;
    }

    void pageKeyPressed(KeyEvent e) { if (pageFocused) view.keyPressed(e); }

    void pageKeyReleased(KeyEvent e) { if (pageFocused && !address.focused) view.keyReleased(e); }

    void charTyped(CharacterEvent e) {
        if (address.focused) address.charTyped(e.codepoint());
        else if (pageFocused) view.charTyped(e);
    }

    void setFocus(boolean f) {
        if (!f) { pageFocused = false; address.focused = false; }
        view.setFocus(f && pageFocused);
    }

    void close() { view.close(); }
}

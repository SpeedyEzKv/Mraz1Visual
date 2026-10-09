package dev.umbra.browser;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.umbra.core.ModuleManager;
import dev.umbra.core.Modules;
import dev.umbra.ui.Anim;
import dev.umbra.ui.Colors;
import dev.umbra.ui.Draw;
import dev.umbra.ui.Fonts;
import dev.umbra.ui.Theme;
import dev.umbra.ui.widget.TextInput;
import dev.umbra.ui.widget.Widget;
import net.dimaskama.mcef.api.MCEFApi;
import net.dimaskama.mcef.api.MCEFBrowser;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.BlitRenderState;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2f;
import org.lwjgl.glfw.GLFW;

/**
 * Встроенный браузер (Chromium через MCEF Modern) в стиле клиента:
 * назад / вперёд / обновить, адресная строка, закладки, сама страница.
 * Открывается из оверлея Shift+Tab. Закрыли — браузер уничтожен, память освобождена.
 * Этот класс загружается только если MCEF Modern установлен (см. Browser.open).
 */
public final class BrowserScreen extends Screen {
    private static final String HOME = "https://www.google.com";
    private static final String[][] BOOKMARKS = {
            {"Google", "https://www.google.com"},
            {"YouTube", "https://www.youtube.com"},
            {"Modrinth", "https://modrinth.com"},
            {"Twitch", "https://www.twitch.tv"}};
    private static final int BAR_H = 34;

    private final Screen parent;
    private final Draw d = new Draw();
    private final TextInput address = new TextInput("Адрес или запрос", 512);
    private MCEFBrowser browser;
    private float k = 1f;
    private int vw, vh;
    // область страницы: в единицах окна и в GUI-единицах Minecraft
    private int ax, ay, aw, ah;
    private float gx, gy, gw, gh;
    private double guiScale = 1;
    private final float[] hover = new float[16];
    private long lastNanos;
    private String lastUrl = "";

    public BrowserScreen(Screen parent) {
        super(Component.literal("Браузер"));
        this.parent = parent;
        MCEFApi.initialize(); // первый раз скачает Chromium (~150 МБ) в фоне
    }

    @Override
    protected void init() {
        d.font = font;
        Window win = minecraft.getWindow();
        guiScale = win.getGuiScale();
        int unit = Math.max(1, Math.round(win.getHeight() / 540f));
        k = (float) (unit / guiScale);
        d.scale = k;
        vw = (int) (width / k);
        vh = (int) (height / k);
        ax = 16;
        ay = 16 + BAR_H + 8;
        aw = vw - 32;
        ah = vh - ay - 16;
        gx = ax * k; gy = ay * k; gw = aw * k; gh = ah * k;
        address.setBounds(16 + 3 * 26 + 8, 16 + 7, Math.max(120, vw - 32 - 3 * 26 - 16 - 260), 20);
        if (browser != null) resizeBrowser();
    }

    private void resizeBrowser() {
        browser.resize(Math.max(1, (int) Math.round(gw * guiScale)), Math.max(1, (int) Math.round(gh * guiScale)));
    }

    private void ensureBrowser() {
        if (browser != null) return;
        MCEFApi.Initialization init = MCEFApi.initialize();
        if (!init.isDone()) return;
        try {
            browser = MCEFApi.getInstance().createBrowser(HOME, false);
            resizeBrowser();
            browser.setFocus(true);
            address.set(HOME);
        } catch (Throwable t) {
            ModuleManager.LOG.error("Browser creation failed", t);
        }
    }

    // ── рисование ────────────────────────────────────────────

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float pt) {
        super.renderBackground(g, mouseX, mouseY, pt);
        g.fill(0, 0, width, height, 0xB0000000);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 0 : Math.min(0.1f, (now - lastNanos) / 1e9f);
        lastNanos = now;
        Anim.enabled = Modules.client.animations.get();
        ensureBrowser();
        double mx = mouseX / k, my = mouseY / k;

        // адрес обновляем, когда страница сменилась и поле не в фокусе
        if (browser != null && !address.focused) {
            String url = browser.getCefBrowser().getURL();
            if (url != null && !url.equals(lastUrl)) {
                lastUrl = url;
                address.set(url);
            }
        }

        d.begin(g, font, k);
        d.card(16, 16, vw - 32, BAR_H, Theme.RADIUS, 0xF00E0E15, Theme.CARD_EDGE);
        navButton(0, "←", 16 + 6, mx, my, dt, browser != null && browser.getCefBrowser().canGoBack());
        navButton(1, "→", 16 + 6 + 26, mx, my, dt, browser != null && browser.getCefBrowser().canGoForward());
        navButton(2, "↻", 16 + 6 + 52, mx, my, dt, browser != null);
        address.render(d, mx, my, dt);
        int bx = address.x + address.w + 12;
        for (int i = 0; i < BOOKMARKS.length; i++) {
            String t = BOOKMARKS[i][0];
            int w = Fonts.width(t);
            boolean hov = Widget.hit(mx, my, bx - 4, 16 + 8, w + 8, 18);
            hover[4 + i] = Anim.to(hover[4 + i], hov ? 1 : 0, dt);
            d.text(t, bx, 16 + 13, Colors.lerp(Theme.TEXT_MUTED, Theme.TEXT, hover[4 + i]));
            bx += w + 14;
        }
        boolean ch = Widget.hit(mx, my, vw - 16 - 26, 16 + 6, 22, 22);
        hover[3] = Anim.to(hover[3], ch ? 1 : 0, dt);
        d.textCenter("✕", vw - 16 - 15, 16 + 13, Colors.lerp(Theme.TEXT_MUTED, Theme.TEXT, hover[3]));

        d.round(ax - 1, ay - 1, aw + 2, ah + 2, Theme.RADIUS_SM, 0xFF0E0E15);
        if (browser == null) {
            MCEFApi.Initialization init = MCEFApi.initialize();
            float p = init.getPercentage();
            String stage = switch (init.getStage()) {
                case DOWNLOADING -> "Скачиваю Chromium (один раз)";
                case EXTRACTING -> "Распаковываю";
                case INSTALL -> "Устанавливаю";
                case INITIALIZING -> "Запускаю";
                default -> "Готовлю браузер";
            };
            d.textCenter(stage, ax + aw / 2, ay + ah / 2 - 10, Theme.TEXT);
            if (p >= 0) d.textCenter(Math.round(p) + "%", ax + aw / 2, ay + ah / 2 + 6, Theme.TEXT_MUTED);
        }
        d.end();

        if (browser != null) {
            GpuTextureView view = browser.getTextureView();
            if (view != null) {
                g.guiRenderState.submitGuiElement(new BlitRenderState(
                        RenderPipelines.GUI_TEXTURED,
                        TextureSetup.singleTexture(view, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)),
                        new Matrix3x2f(g.pose()),
                        Math.round(gx), Math.round(gy), Math.round(gx + gw), Math.round(gy + gh),
                        0f, 1f, 0f, 1f,
                        0xFFFFFFFF,
                        g.scissorStack.peek()));
            }
            if (inPage(mouseX, mouseY)) g.requestCursor(browser.getCursorType());
        }
    }

    private void navButton(int i, String label, int x, double mx, double my, float dt, boolean enabled) {
        boolean hov = enabled && Widget.hit(mx, my, x, 16 + 6, 22, 22);
        hover[i] = Anim.to(hover[i], hov ? 1 : 0, dt);
        if (hover[i] > 0.01f) d.round(x, 16 + 6, 22, 22, 4, Colors.mulAlpha(Theme.SELECTED, hover[i]));
        d.textCenter(label, x + 11, 16 + 13, enabled ? Colors.lerp(Theme.TEXT_DIM, Theme.TEXT, hover[i]) : Theme.TEXT_MUTED);
    }

    // ── ввод ─────────────────────────────────────────────────

    private boolean inPage(double guiX, double guiY) {
        return guiX >= gx && guiX < gx + gw && guiY >= gy && guiY < gy + gh;
    }

    /** GUI-координаты Minecraft → пиксели страницы. */
    private int px(double guiX) { return (int) Math.round((guiX - gx) * guiScale); }
    private int py(double guiY) { return (int) Math.round((guiY - gy) * guiScale); }

    private MouseButtonEvent toPage(MouseButtonEvent e) {
        return new MouseButtonEvent(px(e.x()), py(e.y()), e.buttonInfo());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubled) {
        double mx = e.x() / k, my = e.y() / k;
        if (browser != null && inPage(e.x(), e.y())) {
            address.focused = false;
            browser.setFocus(true);
            browser.onMouseClicked(toPage(e), doubled);
            return true;
        }
        if (address.mouseClicked(mx, my, e.button())) {
            if (browser != null) browser.setFocus(false);
            return true;
        }
        if (browser != null) {
            if (Widget.hit(mx, my, 22, 22, 22, 22)) browser.getCefBrowser().goBack();
            else if (Widget.hit(mx, my, 48, 22, 22, 22)) browser.getCefBrowser().goForward();
            else if (Widget.hit(mx, my, 74, 22, 22, 22)) browser.getCefBrowser().reload();
            int bx = address.x + address.w + 12;
            for (String[] b : BOOKMARKS) {
                int w = Fonts.width(b[0]);
                if (Widget.hit(mx, my, bx - 4, 24, w + 8, 18)) navigate(b[1]);
                bx += w + 14;
            }
        }
        if (Widget.hit(mx, my, vw - 42, 22, 22, 22)) onClose();
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        if (browser != null) browser.onMouseReleased(toPage(e));
        return true;
    }

    @Override
    public void mouseMoved(double x, double y) {
        if (browser != null) browser.onMouseMoved(px(x), py(y));
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (browser != null) browser.onMouseMoved(px(e.x()), py(e.y()));
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double sx, double sy) {
        if (browser != null && inPage(x, y)) browser.onMouseScrolled(px(x), py(y), sy);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (address.focused) {
            int key = e.key();
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                navigate(address.value());
                address.focused = false;
                if (browser != null) browser.setFocus(true);
                return true;
            }
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                address.focused = false;
                return true;
            }
            boolean ctrl = (e.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0;
            address.keyPressed(key, ctrl, minecraft.keyboardHandler::getClipboard);
            return true;
        }
        if (e.key() == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (browser != null) browser.onKeyPressed(e);
        return true;
    }

    @Override
    public boolean keyReleased(KeyEvent e) {
        if (browser != null && !address.focused) browser.onKeyReleased(e);
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent e) {
        if (address.focused) return address.charTyped(e.codepoint());
        if (browser != null) browser.onCharTyped(e);
        return true;
    }

    private void navigate(String input) {
        if (browser == null) return;
        String s = input.trim();
        if (s.isEmpty()) return;
        String url;
        if (s.contains("://")) url = s;
        else if (s.contains(".") && !s.contains(" ")) url = "https://" + s;
        else url = "https://www.google.com/search?q=" + java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
        browser.getCefBrowser().loadURL(url);
        address.set(url);
        lastUrl = url;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public void removed() {
        if (browser != null) {
            browser.close();
            browser = null;
        }
        lastNanos = 0;
        lastUrl = "";
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

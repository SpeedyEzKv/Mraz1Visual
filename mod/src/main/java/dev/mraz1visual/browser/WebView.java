package dev.mraz1visual.browser;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.mraz1visual.core.ModuleManager;
import net.dimaskama.mcef.api.MCEFApi;
import net.dimaskama.mcef.api.MCEFBrowser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.BlitRenderState;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import org.joml.Matrix3x2f;

/**
 * Одна страница Chromium (MCEF Modern) без своего экрана — для окон рабочего стола.
 * Подмена user agent та же, что в {@link BrowserScreen}: без неё YouTube и Google не работают.
 * Класс загружается только если MCEF установлен.
 */
public final class WebView {
    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36";
    private static final String UA_JSON = "{\"userAgent\":\"" + UA + "\",\"acceptLanguage\":\"ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7\",\"platform\":\"Win32\","
            + "\"userAgentMetadata\":{\"brands\":[{\"brand\":\"Chromium\",\"version\":\"146\"},{\"brand\":\"Google Chrome\",\"version\":\"146\"},"
            + "{\"brand\":\"Not.A/Brand\",\"version\":\"99\"}],\"fullVersion\":\"146.0.0.0\",\"platform\":\"Windows\",\"platformVersion\":\"10.0.0\","
            + "\"architecture\":\"x86\",\"model\":\"\",\"mobile\":false,\"bitness\":\"64\"}}";

    private MCEFBrowser browser;
    private org.cef.browser.CefDevToolsClient devTools;
    private int uaState; // 0 — не начато, 1 — ждём ответа, 2 — готово
    private String pendingUrl;
    private String failure;
    private int pw = 1, ph = 1;

    public WebView(String url) {
        this.pendingUrl = url;
        MCEFApi.initialize(); // первый раз скачает Chromium в фоне
    }

    /** Каждый кадр: создать браузер, когда Chromium готов, и подменить user agent. */
    public void tick() {
        if (browser != null) {
            applyUserAgent();
            return;
        }
        if (failure != null) return;
        MCEFApi.Initialization init = MCEFApi.initialize();
        if (!init.isDone()) return;
        if (init.getFuture().isCompletedExceptionally()) {
            failure = "Chromium не запустился";
            return;
        }
        try {
            browser = MCEFApi.getInstance().createBrowser("about:blank", false);
            browser.resize(pw, ph);
        } catch (Throwable t) {
            failure = "Не удалось открыть страницу: " + t.getClass().getSimpleName();
            ModuleManager.LOG.error("WebView creation failed", t);
        }
    }

    private void applyUserAgent() {
        if (uaState != 0) return;
        try {
            org.cef.browser.CefBrowser cb = browser.getCefBrowser();
            devTools = cb.getDevToolsClient();
            if (devTools == null) return;
            uaState = 1;
            devTools.executeDevToolsMethod("Emulation.setUserAgentOverride", UA_JSON).whenComplete((r, err) -> {
                if (err != null) ModuleManager.LOG.warn("User agent override failed: {}", err.toString());
                Minecraft.getInstance().execute(() -> {
                    uaState = 2;
                    if (browser != null) browser.getCefBrowser().loadURL(pendingUrl);
                });
            });
        } catch (Throwable t) {
            uaState = 2;
            browser.getCefBrowser().loadURL(pendingUrl);
        }
    }

    public boolean ready() { return browser != null; }

    public String failure() { return failure; }

    /** Текст, пока страницы ещё нет. */
    public String status() {
        if (failure != null) return failure;
        MCEFApi.Initialization init = MCEFApi.initialize();
        float p = init.getPercentage();
        String stage = switch (init.getStage()) {
            case DOWNLOADING -> "Скачиваю Chromium (один раз)";
            case EXTRACTING -> "Распаковываю";
            case INSTALL -> "Устанавливаю";
            case INITIALIZING -> "Запускаю";
            default -> "Готовлю браузер";
        };
        return p >= 0 ? stage + " · " + Math.round(p) + "%" : stage;
    }

    /** Размер страницы в настоящих пикселях окна. */
    public void resize(int pixelW, int pixelH) {
        pixelW = Math.max(1, pixelW);
        pixelH = Math.max(1, pixelH);
        if (pixelW == pw && pixelH == ph) return;
        pw = pixelW;
        ph = pixelH;
        if (browser != null) browser.resize(pw, ph);
    }

    /** Нарисовать страницу в прямоугольник (GUI-координаты). */
    public void render(GuiGraphics g, int x0, int y0, int x1, int y1) {
        if (browser == null) return;
        GpuTextureView view = browser.getTextureView();
        if (view == null) return;
        g.guiRenderState.submitGuiElement(new BlitRenderState(
                RenderPipelines.GUI_TEXTURED,
                TextureSetup.singleTexture(view, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)),
                new Matrix3x2f(g.pose()),
                x0, y0, x1, y1,
                0f, 1f, 0f, 1f,
                0xFFFFFFFF,
                g.scissorStack.peek()));
    }

    public void applyCursor(GuiGraphics g) {
        if (browser != null) g.requestCursor(browser.getCursorType());
    }

    public String url() {
        if (browser == null) return pendingUrl;
        String u = browser.getCefBrowser().getURL();
        return u == null || u.isEmpty() || u.startsWith("about:") ? pendingUrl : u;
    }

    public boolean canGoBack() { return browser != null && browser.getCefBrowser().canGoBack(); }
    public boolean canGoForward() { return browser != null && browser.getCefBrowser().canGoForward(); }
    public void back() { if (browser != null) browser.getCefBrowser().goBack(); }
    public void forward() { if (browser != null) browser.getCefBrowser().goForward(); }
    public void reload() { if (browser != null) browser.getCefBrowser().reload(); }

    /** Адрес, домен или поисковый запрос. */
    public void navigate(String input) {
        String s = input == null ? "" : input.trim();
        if (s.isEmpty()) return;
        String url;
        if (s.contains("://")) url = s;
        else if (s.contains(".") && !s.contains(" ")) url = "https://" + s;
        else url = "https://www.google.com/search?q=" + java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
        pendingUrl = url;
        if (browser != null && uaState == 2) browser.getCefBrowser().loadURL(url);
    }

    // ── ввод: координаты уже в пикселях страницы ─────────────

    public void setFocus(boolean f) { if (browser != null) browser.setFocus(f); }

    public void mouseClicked(int px, int py, MouseButtonEvent e, boolean doubled) {
        if (browser != null) browser.onMouseClicked(new MouseButtonEvent(px, py, e.buttonInfo()), doubled);
    }

    public void mouseReleased(int px, int py, MouseButtonEvent e) {
        if (browser != null) browser.onMouseReleased(new MouseButtonEvent(px, py, e.buttonInfo()));
    }

    public void mouseMoved(int px, int py) { if (browser != null) browser.onMouseMoved(px, py); }

    public void mouseScrolled(int px, int py, double amount) { if (browser != null) browser.onMouseScrolled(px, py, amount); }

    public void keyPressed(KeyEvent e) { if (browser != null) browser.onKeyPressed(e); }

    public void keyReleased(KeyEvent e) { if (browser != null) browser.onKeyReleased(e); }

    public void charTyped(CharacterEvent e) { if (browser != null) browser.onCharTyped(e); }

    public void close() {
        if (devTools != null) {
            try { devTools.close(); } catch (Throwable ignored) {}
            devTools = null;
        }
        if (browser != null) {
            browser.close();
            browser = null;
        }
        uaState = 0;
    }
}

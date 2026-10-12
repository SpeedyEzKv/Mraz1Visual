package dev.mraz1visual.wallpaper;

import com.mojang.blaze3d.textures.GpuTextureView;
import dev.mraz1visual.core.ModuleManager;
import net.dimaskama.mcef.api.MCEFApi;
import net.dimaskama.mcef.api.MCEFBrowser;

/**
 * Веб-обои Wallpaper Engine (index.html) — настоящая страница в Chromium из MCEF Modern,
 * отрисованная в текстуру. Класс трогается только если MCEF установлен.
 */
final class WebWallpaper {
    private static MCEFBrowser browser;
    private static String url = "";
    private static int bw, bh;
    private static boolean failed;

    private WebWallpaper() {}

    /** Текстура страницы или null, пока Chromium готовится. */
    static GpuTextureView view(String pageUrl, int w, int h) {
        if (failed) return null;
        try {
            MCEFApi.Initialization init = MCEFApi.initialize();
            if (!init.isDone()) return null;
            if (init.getFuture().isCompletedExceptionally()) {
                failed = true;
                return null;
            }
            if (browser == null || !url.equals(pageUrl)) {
                close();
                browser = MCEFApi.getInstance().createBrowser(pageUrl, false);
                url = pageUrl;
                bw = bh = 0;
            }
            if (bw != w || bh != h) {
                browser.resize(w, h);
                bw = w;
                bh = h;
            }
            return browser.getTextureView();
        } catch (Throwable t) {
            failed = true;
            ModuleManager.LOG.warn("Web wallpaper failed", t);
            close();
            return null;
        }
    }

    static String stage() {
        try {
            MCEFApi.Initialization init = MCEFApi.initialize();
            if (init.isDone()) return "";
            float p = init.getPercentage();
            return "Готовлю Chromium для веб-обоев" + (p >= 0 ? " " + Math.round(p) + "%" : "…");
        } catch (Throwable t) {
            return "";
        }
    }

    static boolean failed() { return failed; }

    static void close() {
        if (browser != null) {
            try {
                browser.close();
            } catch (Throwable ignored) {
            }
        }
        browser = null;
        url = "";
    }
}

package dev.mraz1visual.ui.menu;

import com.mojang.blaze3d.platform.NativeImage;
import dev.mraz1visual.core.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * Экран загрузки в стиле клиента: видео-фон, логотип и полоса прогресса.
 * Логотип — картинка из jar (шрифты игры при первой загрузке ещё не готовы).
 * Используется вместо ванильной заставки Mojang и поверх экранов «Загрузка мира», «Сохранение», «Подключение».
 */
public final class LoadingView {
    private static final Identifier LOGO = Identifier.fromNamespaceAndPath("mraz1visual", "gui/brand_logo");
    private static final int LOGO_W = 1440, LOGO_H = 84;
    private static boolean logoReady, logoFailed;
    private static float shownProgress;

    private LoadingView() {}

    private static boolean logo() {
        if (logoReady || logoFailed) return logoReady;
        try (InputStream in = LoadingView.class.getResourceAsStream("/assets/mraz1visual/textures/gui/brand.png")) {
            if (in == null) {
                logoFailed = true;
                return false;
            }
            NativeImage img = NativeImage.read(in);
            Minecraft.getInstance().getTextureManager().register(LOGO, new DynamicTexture(() -> "mraz1visual logo", img));
            logoReady = true;
        } catch (Throwable t) {
            logoFailed = true;
            ModuleManager.LOG.warn("Logo load failed", t);
        }
        return logoReady;
    }

    /**
     * Нарисовать экран загрузки на весь экран.
     * @param progress 0..1 или &lt; 0 — бегущая полоса без процентов
     * @param alpha    прозрачность всего экрана (для плавного появления/исчезновения)
     */
    public static void render(GuiGraphics g, float progress, float alpha) {
        if (alpha <= 0.003f) return;
        int w = g.guiWidth(), h = g.guiHeight();
        int a = Math.round(255 * Math.min(1f, alpha));
        g.fill(0, 0, w, h, a << 24 | 0x030308);
        if (alpha > 0.5f) VideoBackground.render(g, 0, 0, w, h);
        // затемнение, чтобы логотип читался
        g.fill(0, 0, w, h, Math.round(a * 0.55f) << 24 | 0x030308);
        g.fillGradient(0, h / 2, w, h, 0, Math.round(a * 0.6f) << 24);

        int lw = Math.min(w - 40, Math.round(w * 0.42f));
        int lh = Math.round(lw * LOGO_H / (float) LOGO_W);
        int lx = (w - lw) / 2, ly = h / 2 - lh - 6;
        if (logo()) {
            g.blit(RenderPipelines.GUI_TEXTURED, LOGO, lx, ly, 0f, 0f, lw, lh, LOGO_W, LOGO_H, LOGO_W, LOGO_H, a << 24 | 0xFFFFFF);
        }
        // полоса прогресса под логотипом
        int bw = lw, bx = lx, by = h / 2 + 14;
        g.fill(bx, by, bx + bw, by + 2, Math.round(a * 0.18f) << 24 | 0xFFFFFF);
        int accent = 0xB8B4F2;
        if (progress >= 0) {
            shownProgress += (Math.min(1f, progress) - shownProgress) * 0.15f;
            int fw = Math.round(bw * shownProgress);
            g.fill(bx, by, bx + fw, by + 2, a << 24 | accent);
            g.fill(bx + fw - 6, by - 1, bx + fw, by + 3, Math.round(a * 0.9f) << 24 | 0xFFFFFF);
        } else {
            // бегущий отрезок
            float t = (System.nanoTime() % 1_600_000_000L) / 1.6e9f;
            int seg = bw / 4;
            int sx = bx + Math.round((bw + seg) * t) - seg;
            int x0 = Math.max(bx, sx), x1 = Math.min(bx + bw, sx + seg);
            if (x1 > x0) g.fill(x0, by, x1, by + 2, a << 24 | accent);
        }
    }

    /** Подпись под полосой (название этапа). Шрифт может быть ещё не готов — тогда ничего не рисуем. */
    public static void caption(GuiGraphics g, String text, float alpha) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.font == null || text == null || text.isEmpty()) return;
            int a = Math.round(220 * alpha);
            if (a < 8) return;
            int w = mc.font.width(text);
            g.drawString(mc.font, text, (g.guiWidth() - w) / 2, g.guiHeight() / 2 + 26, a << 24 | 0xC9C6F5, false);
        } catch (Throwable ignored) {
        }
    }

    // ── ванильная заставка: читаем её состояние по типам полей (без имён — надёжно в любой версии) ──

    private static Field progressF, fadeOutF, fadeInStartF, fadeInF;
    private static boolean reflected;

    private static void reflect(Class<?> c) {
        reflected = true;
        try {
            List<Field> floats = new ArrayList<>(), longs = new ArrayList<>(), bools = new ArrayList<>();
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                f.setAccessible(true);
                if (f.getType() == float.class) floats.add(f);
                else if (f.getType() == long.class) longs.add(f);
                else if (f.getType() == boolean.class) bools.add(f);
            }
            if (!floats.isEmpty()) progressF = floats.get(0);
            if (longs.size() >= 2) {
                fadeOutF = longs.get(0);
                fadeInStartF = longs.get(1);
            }
            if (!bools.isEmpty()) fadeInF = bools.get(0);
        } catch (Throwable t) {
            ModuleManager.LOG.warn("Loading overlay reflection failed", t);
        }
    }

    /** Из миксина LoadingOverlay.render (в конце): рисуем свой экран поверх ванильного. */
    public static void overlay(Object overlay, GuiGraphics g) {
        try {
            if (!reflected) reflect(overlay.getClass());
            float progress = progressF != null ? progressF.getFloat(overlay) : -1f;
            long fadeOut = fadeOutF != null ? fadeOutF.getLong(overlay) : -1L;
            long fadeInStart = fadeInStartF != null ? fadeInStartF.getLong(overlay) : -1L;
            boolean fadeIn = fadeInF != null && fadeInF.getBoolean(overlay);
            long now = System.nanoTime() / 1_000_000L;
            float fo = fadeOut > -1 ? (now - fadeOut) / 1000f : -1f;
            float fi = fadeInStart > -1 ? (now - fadeInStart) / 500f : -1f;
            float alpha;
            if (fo >= 1f) alpha = 1f - Math.max(0f, Math.min(1f, fo - 1f));
            else if (fadeIn) alpha = Math.max(0f, Math.min(1f, fi));
            else alpha = 1f;
            g.nextStratum();
            render(g, progress, alpha);
        } catch (Throwable t) {
            // не сломать загрузку игры из-за картинки
        }
    }
}

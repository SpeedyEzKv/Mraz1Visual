package dev.mraz1visual.ui.menu;

import com.mojang.blaze3d.platform.NativeImage;
import dev.mraz1visual.core.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.InputStream;

/** Splash artwork is read directly from the JAR before the resource and font reload completes. */
public final class LoadingView {
    private static final Identifier LOGO = Identifier.fromNamespaceAndPath("mraz1visual", "gui/brand_logo");
    private static final int LOGO_W = 1440, LOGO_H = 84;
    private static boolean logoReady, logoFailed;

    private LoadingView() {}

    private static final Identifier DIGITS = Identifier.fromNamespaceAndPath("mraz1visual", "gui/loading_digits");

    private static void register(Identifier id, String filename) throws java.io.IOException {
        try (InputStream in = LoadingView.class.getResourceAsStream("/assets/mraz1visual/textures/gui/" + filename)) {
            if (in == null) throw new java.io.FileNotFoundException(filename);
            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(id::toString, NativeImage.read(in)));
        }
    }

    private static boolean logo() {
        if (!logoReady && !logoFailed) {
            try {
                register(LOGO, "brand.png");
                register(DIGITS, "loading_digits.png");
                logoReady = true;
            } catch (java.io.IOException e) {
                logoFailed = true;
                ModuleManager.LOG.warn("Loading artwork unavailable", e);
            }
        }
        return logoReady;
    }

    /**
     * Нарисовать экран загрузки на весь экран.
     * @param progress Действительный прогресс перезагрузки, 0..1
     * @param alpha    прозрачность всего экрана (для плавного появления/исчезновения)
     */
    private static void render(GuiGraphics g, float progress, float alpha) {
        if (alpha <= 0.003f) return;
        int w = g.guiWidth(), h = g.guiHeight();
        int a = Math.round(255 * Math.min(1f, alpha));
        g.fill(0, 0, w, h, a << 24 | 0x030308);
        // затемнение, чтобы логотип читался
        g.fill(0, 0, w, h, Math.round(a * 0.55f) << 24 | 0x030308);
        g.fillGradient(0, h / 2, w, h, 0, Math.round(a * 0.6f) << 24);

        int lw = Math.min(w - 40, Math.round(w * 0.42f));
        int lh = Math.round(lw * LOGO_H / (float) LOGO_W);
        int lx = (w - lw) / 2, ly = h / 2 - lh - 6;
        if (logo()) {
            g.blit(RenderPipelines.GUI_TEXTURED, LOGO, lx, ly, 0f, 0f, lw, lh, LOGO_W, LOGO_H, LOGO_W, LOGO_H, a << 24 | 0xFFFFFF);
        }
        // свечение за логотипом: мягкие слои акцентного цвета
        for (int i = 0; i < 6; i++) {
            int gx = Math.round(lw * (0.12f + i * 0.05f)), gy = Math.round(lh * (0.25f + i * 0.08f));
            g.fill(lx + lw / 2 - gx, ly + lh / 2 - gy, lx + lw / 2 + gx, ly + lh / 2 + gy, Math.round(a * 0.035f) << 24 | 0x8B6CFF);
        }
        // полоса прогресса под логотипом: градиент #8B6CFF → #4FD1FF
        int bw = lw, bx = lx, by = h / 2 + 14;
        g.fill(bx, by, bx + bw, by + 2, Math.round(a * 0.18f) << 24 | 0xFFFFFF);
        float shownProgress = Math.clamp(progress, 0f, 1f);
        int fw = Math.round(bw * shownProgress);
        int segs = 24;
        for (int i = 0; i < segs; i++) {
            int x0 = bx + fw * i / segs, x1 = bx + fw * (i + 1) / segs;
            if (x1 > x0) g.fill(x0, by, x1, by + 2, a << 24 | (dev.mraz1visual.ui.Colors.lerp(0xFF8B6CFF, 0xFF4FD1FF, (x0 - bx) / (float) Math.max(1, bw)) & 0xFFFFFF));
        }
        if (fw > 0) g.fill(bx + Math.max(0, fw - 6), by - 1, bx + fw, by + 3, Math.round(a * 0.9f) << 24 | 0xFFFFFF);
        percent(g, Math.round(shownProgress * 100), bx + bw, by + 6, alpha);
    }

    // Pre-rasterized from the same Inter face as the UI, available before Minecraft loads fonts.
    private static void percent(GuiGraphics g, int p, int right, int y, float alpha) {
        if (!logoReady) return;
        String label = Math.clamp(p, 0, 100) + "%";
        int x = right - label.length() * 7;
        int color = Math.round(220 * alpha) << 24 | 0xC9C6F5;
        for (int i = 0; i < label.length(); i++) {
            char c = label.charAt(i);
            int index = c == '%' ? 10 : c - '0';
            g.blit(RenderPipelines.GUI_TEXTURED, DIGITS, x, y, index * 32f, 0, 9, 10, 32, 36, 352, 36, color);
            x += 7;
        }
    }

    public static void overlay(GuiGraphics g, float progress, long fadeOut, long fadeInStart, boolean fadeIn) {
        long now = net.minecraft.util.Util.getMillis();
        float alpha = fadeOut >= 0 ? 1f - Math.clamp((now - fadeOut - 1000) / 1000f, 0f, 1f)
                : fadeIn ? Math.clamp((now - fadeInStart) / 500f, 0f, 1f) : 1f;
        g.nextStratum();
        render(g, progress, alpha);
    }
}

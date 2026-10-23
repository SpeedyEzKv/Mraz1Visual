package dev.mraz1visual.ui;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import dev.mraz1visual.core.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.BlitRenderState;
import net.minecraft.client.renderer.RenderPipelines;
import org.joml.Matrix3x2f;
import org.lwjgl.opengl.GL30;

/**
 * Размытый фон для «жидкого стекла» почти бесплатно: картинка мира уменьшается пирамидой
 * 1/2 → 1/4 → 1/8 → 1/16 (линейная фильтрация при каждом шаге — это и есть размытие) и растягивается обратно до 1/4.
 * Ванильное размытие меню — 6 полноэкранных проходов в полном разрешении (~1 мс на кадр), здесь — доли миллисекунды.
 * Делается в момент сборки GUI: мир за этот кадр уже нарисован, HUD и окно — ещё нет.
 */
public final class GlassBlur {
    private static final int LEVELS = 4;
    private static final GpuTexture[] tex = new GpuTexture[LEVELS];
    private static final int[] fbo = new int[LEVELS];
    private static GpuTextureView result;
    private static int srcFbo, srcTexId = -1, baseW, baseH;
    private static boolean failed;

    private GlassBlur() {}

    private static boolean requested;
    private static int requestedLevels = 4;

    /**
     * Обновить размытие под текущий кадр. false — не получилось (тогда рисуйте ванильный фон).
     * В 26.x GUI собирается раньше мира — тогда размытие считается позже, сразу после мира (afterLevel),
     * а здесь только заказывается на следующий кадр.
     */
    public static boolean update(int levels) {
        //? if >=26.1 {
        /*requested = true;
        requestedLevels = levels;
        return !failed && result != null;
        *///?} else {
        return compute(levels);
        //?}
    }

    /** Из GameRendererPostMixin: мир уже в кадре — посчитать заказанное размытие. */
    public static void afterLevel() {
        if (!requested) return;
        requested = false;
        compute(requestedLevels);
    }

    private static boolean compute(int levels) {
        if (failed) return false;
        try {
            RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
            int w = main.width, h = main.height;
            if (w < 16 || h < 16) return false;
            ensure(w, h);
            int mainId = ((GlTexture) main.getColorTexture()).glId();
            int prevRead = GlStateManager.getFrameBuffer(GL30.GL_READ_FRAMEBUFFER), prevDraw = GlStateManager.getFrameBuffer(GL30.GL_DRAW_FRAMEBUFFER);
            if (mainId != srcTexId) {
                GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, srcFbo);
                GlStateManager._glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL30.GL_TEXTURE_2D, mainId, 0);
                srcTexId = mainId;
            }
            int n = Math.max(1, Math.min(LEVELS, levels));
            // вниз: каждый шаг — среднее 2×2 (линейная выборка), к 1/16 картинка уже мягкая
            blit(srcFbo, w, h, fbo[0], tex[0].getWidth(0), tex[0].getHeight(0));
            for (int i = 1; i < n; i++) blit(fbo[i - 1], tex[i - 1].getWidth(0), tex[i - 1].getHeight(0), fbo[i], tex[i].getWidth(0), tex[i].getHeight(0));
            // вверх до 1/4: билинейное растяжение сглаживает «кубики» нижних уровней
            for (int i = n - 1; i > 1; i--) blit(fbo[i], tex[i].getWidth(0), tex[i].getHeight(0), fbo[i - 1], tex[i - 1].getWidth(0), tex[i - 1].getHeight(0));
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevRead);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, prevDraw);
            return true;
        } catch (Throwable t) {
            failed = true;
            ModuleManager.LOG.warn("Glass blur unavailable, using vanilla blur", t);
            return false;
        }
    }

    private static void blit(int from, int fw, int fh, int to, int tw, int th) {
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, from);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, to);
        GlStateManager._glBlitFrameBuffer(0, 0, fw, fh, 0, 0, tw, th, GL30.GL_COLOR_BUFFER_BIT, GL30.GL_LINEAR);
    }

    private static void ensure(int w, int h) {
        if (w == baseW && h == baseH && tex[0] != null) return;
        release();
        baseW = w;
        baseH = h;
        var device = RenderSystem.getDevice();
        int usage = GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT;
        for (int i = 0; i < LEVELS; i++) {
            int lw = Math.max(1, w >> (i + 1)), lh = Math.max(1, h >> (i + 1));
            tex[i] = device.createTexture("mraz1visual glass blur " + i, usage, TextureFormat.RGBA8, lw, lh, 1, 1);
            fbo[i] = GlStateManager.glGenFramebuffers();
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo[i]);
            GlStateManager._glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL30.GL_TEXTURE_2D, ((GlTexture) tex[i]).glId(), 0);
        }
        if (srcFbo == 0) srcFbo = GlStateManager.glGenFramebuffers();
        srcTexId = -1;
        result = device.createTextureView(tex[1]);
    }

    private static void release() {
        if (result != null) result.close();
        result = null;
        for (int i = 0; i < LEVELS; i++) {
            if (tex[i] != null) tex[i].close();
            tex[i] = null;
            if (fbo[i] != 0) GlStateManager._glDeleteFramebuffers(fbo[i]);
            fbo[i] = 0;
        }
    }

    /**
     * Нарисовать размытый фон в прямоугольник (единицы текущей позы GUI), с оттенком color.
     * Текстура кадра лежит «снизу вверх» — v перевёрнут.
     */
    public static void draw(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        if (result == null) return;
        int sw = g.guiWidth(), sh = g.guiHeight();
        g.guiRenderState.submitGuiElement(new BlitRenderState(RenderPipelines.GUI_TEXTURED,
                TextureSetup.singleTexture(result, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)),
                new Matrix3x2f(g.pose()), x0, y0, x1, y1,
                x0 / (float) sw, x1 / (float) sw, 1f - y0 / (float) sh, 1f - y1 / (float) sh, color, g.scissorStack.peek()));
    }
}

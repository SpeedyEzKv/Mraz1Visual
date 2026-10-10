package dev.umbra.module;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import dev.umbra.core.Category;
import dev.umbra.core.HudLayer;
import dev.umbra.core.Module;
import dev.umbra.setting.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Размытие в движении: поверх кадра рисуется прошлый кадр с прозрачностью.
 * Две текстуры по очереди: в одну копируем текущий кадр, другую (прошлый) рисуем.
 */
public final class MotionBlur extends Module implements HudLayer {
    private static final Identifier[] IDS = {
            Identifier.fromNamespaceAndPath("umbra", "motion_blur/a"),
            Identifier.fromNamespaceAndPath("umbra", "motion_blur/b")};

    final SliderSetting strength = add(new SliderSetting("strength", "Сила", 0.5, 0.1, 0.9, 0.05, ""));

    private final DynamicTexture[] tex = new DynamicTexture[2];
    private int texW, texH, front;
    private boolean hasPrev;

    public MotionBlur() {
        super("motion_blur", "Motion Blur", "Плавный шлейф при повороте камеры.", Category.VISUALS);
    }

    @Override
    protected void onDisable() {
        release();
    }

    private void release() {
        Minecraft mc = Minecraft.getInstance();
        for (int i = 0; i < 2; i++) {
            if (tex[i] != null) mc.getTextureManager().release(IDS[i]);
            tex[i] = null;
        }
        hasPrev = false;
        texW = texH = 0;
    }

    @Override
    public void renderHud(GuiGraphics g, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        GpuTexture main = mc.getMainRenderTarget().getColorTexture();
        if (main == null) return;
        int w = main.getWidth(0), h = main.getHeight(0);
        if (w != texW || h != texH || tex[0] == null) {
            release();
            for (int i = 0; i < 2; i++) {
                tex[i] = new DynamicTexture(() -> "umbra motion blur", w, h, false);
                mc.getTextureManager().register(IDS[i], tex[i]);
            }
            texW = w;
            texH = h;
        }
        int back = 1 - front;
        // прошлый кадр — поверх текущего (рисуется позже, когда GUI выводится на экран)
        if (hasPrev) {
            int a = Math.round(strength.getF() * 255);
            // кадр в текстуре перевёрнут: v идёт снизу вверх
            g.blit(RenderPipelines.GUI_TEXTURED, IDS[front], 0, 0, 0f, (float) h, g.guiWidth(), g.guiHeight(), w, -h, w, h, a << 24 | 0xFFFFFF);
        }
        // текущий мир (без GUI) — в другую текстуру, её нарисуем в следующем кадре
        RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(main, tex[back].getTexture(), 0, 0, 0, 0, 0, w, h);
        front = back;
        hasPrev = true;
    }
}

package dev.mraz1visual.module;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.HudLayer;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.setting.SliderSetting;
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
            Identifier.fromNamespaceAndPath("mraz1visual", "motion_blur/a"),
            Identifier.fromNamespaceAndPath("mraz1visual", "motion_blur/b")};

    final SliderSetting strength = add(new SliderSetting("strength", "Сила", 0.45, 0, 1, 0.05, ""));
    private long lastNs;

    private final DynamicTexture[] tex = new DynamicTexture[2];
    private int texW, texH, front;
    private boolean hasPrev;

    public MotionBlur() {
        super("motion_blur", "Motion Blur", "Плавный шлейф при повороте камеры.", Category.SKY);
        markNew();
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
                tex[i] = new DynamicTexture(() -> "mraz1visual motion blur", w, h, false);
                mc.getTextureManager().register(IDS[i], tex[i]);
            }
            texW = w;
            texH = h;
        }
        int back = 1 - front;
        // прошлый кадр — поверх текущего (рисуется позже, когда GUI выводится на экран)
        if (hasPrev) {
            // сила задана для 60 FPS: на низком FPS прошлый кадр гаснет быстрее, иначе шлейф тянется
            long now = System.nanoTime();
            float dt = lastNs == 0 ? 1f / 60 : Math.min(0.25f, (now - lastNs) / 1e9f);
            lastNs = now;
            float s = (float) Math.pow(Math.min(0.95f, strength.getF()), dt * 60f);
            int a = Math.round(s * 255);
            // кадр в текстуре перевёрнут: v идёт снизу вверх
            g.blit(RenderPipelines.GUI_TEXTURED, IDS[front], 0, 0, 0f, (float) h, g.guiWidth(), g.guiHeight(), w, -h, w, h, a << 24 | 0xFFFFFF);
        }
        // текущий мир (без GUI) — в другую текстуру, её нарисуем в следующем кадре
        RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(main, tex[back].getTexture(), 0, 0, 0, 0, 0, w, h);
        front = back;
        hasPrev = true;
    }
}

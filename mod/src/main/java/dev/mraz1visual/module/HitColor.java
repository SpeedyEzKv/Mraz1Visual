package dev.mraz1visual.module;

import com.mojang.blaze3d.platform.NativeImage;
import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.mixin.OverlayTextureAccessor;
import dev.mraz1visual.setting.ColorSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;

/** Свой цвет вспышки существа при уроне вместо ванильного красного. Только у тебя на экране. */
public final class HitColor extends Module {
    private static final int VANILLA = 0xB2FF0000;
    final ColorSetting color = add(new ColorSetting("color", "Цвет вспышки", 0xB2B8B4F2));
    private int applied = VANILLA;

    public HitColor() {
        super("hit_color", "Hit Color", "Свой цвет вспышки при уроне.", Category.VISUALS);
    }

    @Override
    public void onTick(Minecraft mc) {
        if (color.get() != applied) paint(color.get());
    }

    @Override
    protected void onDisable() {
        paint(VANILLA);
    }

    private void paint(int argb) {
        try {
            Minecraft mc = Minecraft.getInstance();
            DynamicTexture tex = ((OverlayTextureAccessor) mc.gameRenderer.overlayTexture()).mraz1visual$texture();
            NativeImage img = tex.getPixels();
            if (img == null) return;
            for (int y = 0; y < 8; y++) for (int x = 0; x < 16; x++) img.setPixel(x, y, argb);
            tex.upload();
            applied = argb;
        } catch (Throwable t) {
            applied = argb; // не получилось — не пробуем каждый тик
        }
    }
}

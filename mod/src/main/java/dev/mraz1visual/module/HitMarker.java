package dev.mraz1visual.module;

import dev.mraz1visual.core.AttackListener;
import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.HudLayer;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.setting.ColorSetting;
import dev.mraz1visual.ui.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;

/** Крестик-отметка у прицела при каждом твоём ударе — как в шутерах. */
public final class HitMarker extends Module implements AttackListener, HudLayer {
    final ColorSetting color = add(new ColorSetting("color", "Цвет", 0xFFFFFFFF));
    final ColorSetting critColor = add(new ColorSetting("crit_color", "Цвет крита", 0xFFFF6A6A));
    private long at;
    private boolean crit;

    public HitMarker() {
        super("hit_marker", "Hit Marker", "Крестик у прицела при ударе.", Category.COMBAT);
    }

    @Override
    public void onAttack(Entity e) {
        Minecraft mc = Minecraft.getInstance();
        at = System.currentTimeMillis();
        crit = mc.player != null && CritIndicator.critReady(mc.player);
        if (crit) Crosshair.critHappened();
    }

    @Override
    public void renderHud(GuiGraphics g, float pt) {
        float t = (System.currentTimeMillis() - at) / 380f;
        if (t >= 1) return;
        // появление с лёгким «выстрелом» наружу (ease-out), затухание к концу
        float e = 1f - (float) Math.pow(1f - Math.min(1f, t * 2.2f), 3);
        float a = t < 0.5f ? 1f : 1f - (t - 0.5f) / 0.5f;
        float cx = g.guiWidth() / 2f, cy = g.guiHeight() / 2f;
        float dist = 5f + e * 3f, len = 4.5f - t * 1.5f;
        int c = Colors.mulAlpha(crit ? critColor.get() : color.get(), a);
        for (int i = 0; i < 4; i++) {
            g.pose().pushMatrix();
            g.pose().translate(cx, cy);
            g.pose().rotate((float) (Math.PI / 4 + i * Math.PI / 2));
            // капсула со сглаженными краями (SDF) и мягкой тенью
            dev.mraz1visual.ui.Sdf.fill(g, dist - 0.5f, -1.3f, len + 1f, 2.6f, 1.3f, Colors.mulAlpha(0x60000000, a));
            dev.mraz1visual.ui.Sdf.fill(g, dist, -0.8f, len, 1.6f, 0.8f, c);
            g.pose().popMatrix();
        }
    }
}

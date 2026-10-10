package dev.mraz1visual.module;

import dev.mraz1visual.core.AttackListener;
import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.HudLayer;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.setting.ColorSetting;
import dev.mraz1visual.ui.Colors;
import dev.mraz1visual.ui.Draw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;

/** Крестик-отметка у прицела при каждом твоём ударе — как в шутерах. */
public final class HitMarker extends Module implements AttackListener, HudLayer {
    final ColorSetting color = add(new ColorSetting("color", "Цвет", 0xFFFFFFFF));
    final ColorSetting critColor = add(new ColorSetting("crit_color", "Цвет крита", 0xFFFF6A6A));
    private final Draw d = new Draw();
    private long at;
    private boolean crit;

    public HitMarker() {
        super("hit_marker", "Hit Marker", "Крестик у прицела при ударе.", Category.VISUALS);
    }

    @Override
    public void onAttack(Entity e) {
        Minecraft mc = Minecraft.getInstance();
        at = System.currentTimeMillis();
        crit = mc.player != null && mc.player.fallDistance > 0 && !mc.player.onGround();
    }

    @Override
    public void renderHud(GuiGraphics g, float pt) {
        float t = (System.currentTimeMillis() - at) / 350f;
        if (t >= 1) return;
        float a = 1 - t * t;
        float cx = g.guiWidth() / 2f, cy = g.guiHeight() / 2f;
        float in = 3 + t * 3, out = 8 + t * 4;
        int c = Colors.mulAlpha(crit ? critColor.get() : color.get(), a);
        d.begin(g, Minecraft.getInstance().font, 1f);
        for (int i = 0; i < 4; i++) {
            float sx = (i & 1) == 0 ? -1 : 1, sy = i < 2 ? -1 : 1;
            d.line(cx + sx * in, cy + sy * in, cx + sx * out, cy + sy * out, 1.5f, c);
        }
        d.end();
    }
}

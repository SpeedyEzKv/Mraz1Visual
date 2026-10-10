package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.HudLayer;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.render.HudKit;
import dev.mraz1visual.ui.Colors;
import dev.mraz1visual.ui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Значок щита у прицела: подсвечен, пока блокируешь, и с полоской, пока щит на перезарядке (после топора). */
public final class ShieldIndicator extends Module implements HudLayer {
    public ShieldIndicator() {
        super("shield_indicator", "Shield Indicator", "Состояние твоего щита у прицела.", Category.VISUALS);
    }

    @Override
    public void renderHud(GuiGraphics g, float pt) {
        LocalPlayer p = Minecraft.getInstance().player;
        ItemStack s = p.getOffhandItem().is(Items.SHIELD) ? p.getOffhandItem() : p.getMainHandItem().is(Items.SHIELD) ? p.getMainHandItem() : null;
        if (s == null) return;
        float cd = p.getCooldowns().getCooldownPercent(s, pt);
        boolean blocking = p.isBlocking();
        if (cd <= 0 && !blocking) return;
        int x = g.guiWidth() / 2 - 30 - 20, y = g.guiHeight() / 2 - 10;
        HudKit.card(g, x - 2, y - 2, 24, 26, 1f);
        g.renderItem(s, x + 2, y + 1);
        if (cd > 0) {
            HudKit.bar(g, x + 2, y + 19, 16, 3, 1 - cd, 0xFFE05A5A, 1f);
        } else {
            g.fill(x + 2, y + 19, x + 18, y + 21, Colors.withAlpha(Theme.accent(), 0xFF));
        }
    }
}

package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.HudLayer;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.render.HudKit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Сколько у тебя тотемов (инвентарь + вторая рука) — у хотбара. */
public final class TotemCounter extends Module implements HudLayer {
    /** Создаётся при первой отрисовке: в 26.x предметы нельзя создавать до загрузки реестров. */
    private static ItemStack icon;

    public TotemCounter() {
        super("totem_counter", "Totem Counter", "Число твоих тотемов у хотбара.", Category.VISUALS);
    }

    @Override
    public void renderHud(GuiGraphics g, float pt) {
        LocalPlayer p = Minecraft.getInstance().player;
        int n = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (s.is(Items.TOTEM_OF_UNDYING)) n += s.getCount();
        }
        if (p.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) n += p.getOffhandItem().getCount();
        int x = g.guiWidth() / 2 - 91 - 52, y = g.guiHeight() - 22;
        HudKit.card(g, x, y, 22, 22, 1f);
        if (icon == null) icon = new ItemStack(Items.TOTEM_OF_UNDYING);
        g.renderItem(icon, x + 3, y + 3);
        String s = String.valueOf(n);
        int c = n == 0 ? 0xFFE05A5A : n == 1 ? 0xFFF2D25A : 0xFFE9E9F1;
        HudKit.bold(g, s, x + 20 - HudKit.width(s), y + 13, c);
    }
}

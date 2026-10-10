package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.HudLayer;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.render.HudKit;
import dev.mraz1visual.setting.EnumSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

/** Содержимое инвентаря (3 ряда над хотбаром) прямо на экране. */
public final class InventoryHud extends Module implements HudLayer {
    final EnumSetting corner = add(new EnumSetting("corner", "Угол", 0, "tl", "Слева сверху", "tr", "Справа сверху", "bl", "Слева снизу", "br", "Справа снизу"));

    public InventoryHud() {
        super("inventory_hud", "Inventory HUD", "Твой инвентарь прямо на экране.", Category.VISUALS);
    }

    @Override
    public void renderHud(GuiGraphics g, float pt) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        int w = 9 * 18 + 8, h = 3 * 18 + 18;
        int[] at = HudKit.anchor(g, corner.get(), w, h, 4);
        if (corner.get() == 1) at[1] += 64; // под Interface справа сверху
        int x = at[0], y = at[1];
        HudKit.card(g, x, y, w, h, 1f);
        HudKit.title(g, "ИНВЕНТАРЬ", x + 6, y + 5, 1f);
        for (int i = 0; i < 27; i++) {
            int sx = x + 4 + (i % 9) * 18, sy = y + 15 + (i / 9) * 18;
            g.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0x14FFFFFF);
            ItemStack st = p.getInventory().getItem(9 + i);
            if (st.isEmpty()) continue;
            g.renderItem(st, sx + 1, sy + 1);
            g.renderItemDecorations(mc.font, st, sx + 1, sy + 1);
        }
    }
}

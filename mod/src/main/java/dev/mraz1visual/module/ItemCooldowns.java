package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.HudLayer;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.setting.ColorSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Перезарядка предметов (жемчуг, хорус, щит…) полосками под прицелом. Та же информация, что в хотбаре. */
public final class ItemCooldowns extends Module implements HudLayer {
    final ColorSetting color = add(new ColorSetting("color", "Полоса", 0xFFB8B4F2));

    private final Item[] seen = new Item[10];

    public ItemCooldowns() {
        super("item_cooldowns", "Item Cooldowns", "Перезарядка жемчуга и др. под прицелом.", Category.VISUALS);
    }

    @Override
    public void renderHud(GuiGraphics g, float pt) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        int n = 0;
        int x = g.guiWidth() / 2 - 30, y = g.guiHeight() / 2 + 14;
        for (int i = 0; i <= 9; i++) {
            ItemStack s = i < 9 ? p.getInventory().getItem(i) : p.getOffhandItem();
            if (s.isEmpty()) continue;
            float f = p.getCooldowns().getCooldownPercent(s, pt);
            if (f <= 0) continue;
            boolean dup = false;
            for (int j = 0; j < n; j++) if (seen[j] == s.getItem()) dup = true;
            if (dup) continue;
            seen[n++] = s.getItem();
            int ry = y + (n - 1) * 18;
            g.renderItem(s, x, ry);
            g.fill(x + 20, ry + 7, x + 60, ry + 9, 0x60000000);
            g.fill(x + 20, ry + 7, x + 20 + Math.round(40 * f), ry + 9, color.get());
        }
        for (int j = 0; j < n; j++) seen[j] = null;
    }
}

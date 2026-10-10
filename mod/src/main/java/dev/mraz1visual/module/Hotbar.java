package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.render.HudKit;
import dev.mraz1visual.ui.Colors;
import dev.mraz1visual.ui.Theme;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

/** Хотбар в стиле клиента: стеклянная плашка, выбранный слот плавно переезжает. Слоты на тех же местах. */
public final class Hotbar extends Module {
    private static Hotbar instance;
    private float sel = -1;
    private long last;

    public Hotbar() {
        super("hotbar", "Hotbar", "Красивый хотбар с плавным выбором слота.", Category.VISUALS);
        instance = this;
    }

    public static void register() {
        HudElementRegistry.replaceElement(VanillaHudElements.HOTBAR, original -> (g, delta) -> {
            Hotbar h = instance;
            Minecraft mc = Minecraft.getInstance();
            if (h == null || !h.isEnabled() || mc.player == null || mc.player.isSpectator()) original.render(g, delta);
            else h.draw(g, mc.player);
        });
    }

    private void draw(GuiGraphics g, LocalPlayer p) {
        long now = System.nanoTime();
        float dt = last == 0 ? 0 : Math.min(0.1f, (now - last) / 1e9f);
        last = now;
        int target = p.getInventory().getSelectedSlot();
        if (sel < 0) sel = target;
        sel += (target - sel) * Math.min(1f, dt * 18f);
        int x0 = g.guiWidth() / 2 - 91, y = g.guiHeight() - 22;
        HudKit.card(g, x0, y, 182, 22, 1f);
        int sx = Math.round(x0 + 1 + sel * 20);
        g.fill(sx + 1, y + 1, sx + 21, y + 21, Colors.withAlpha(Theme.accent(), 0x30));
        g.fill(sx + 4, y + 20, sx + 18, y + 21, Theme.accent());
        Minecraft mc = Minecraft.getInstance();
        for (int i = 0; i < 9; i++) {
            ItemStack s = p.getInventory().getItem(i);
            int ix = x0 + 3 + i * 20, iy = y + 3;
            if (s.isEmpty()) continue;
            g.renderItem(s, ix, iy);
            g.renderItemDecorations(mc.font, s, ix, iy);
        }
        ItemStack off = p.getOffhandItem();
        if (!off.isEmpty()) {
            int ox = x0 - 26;
            HudKit.card(g, ox, y, 22, 22, 1f);
            g.renderItem(off, ox + 3, y + 3);
            g.renderItemDecorations(mc.font, off, ox + 3, y + 3);
        }
    }
}

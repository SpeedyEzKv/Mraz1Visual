package dev.mraz1visual.module;

import dev.mraz1visual.Mraz1Visual;
import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.HudLayer;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.render.HudKit;
import dev.mraz1visual.ui.Theme;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

/** Список функций с назначенными клавишами: включённые подсвечены. */
public final class KeybindsHud extends Module implements HudLayer {
    public KeybindsHud() {
        super("keybinds_hud", "Keybinds", "Твои бинды и их состояние на экране.", Category.VISUALS);
    }

    @Override
    public void renderHud(GuiGraphics g, float pt) {
        List<Module> list = new ArrayList<>();
        for (Module m : Mraz1Visual.MODULES.all()) if (m.bind.isBound() && !m.alwaysOn()) list.add(m);
        if (list.isEmpty()) return;
        int w = 100;
        for (Module m : list) w = Math.max(w, HudKit.width(m.name) + HudKit.width(m.bind.display()) + 28);
        int row = 11, h = 17 + list.size() * row;
        int x = 4, y = g.guiHeight() / 2 - h / 2;
        HudKit.card(g, x, y, w, h, 1f);
        HudKit.title(g, "БИНДЫ", x + 6, y + 5, 1f);
        for (int i = 0; i < list.size(); i++) {
            Module m = list.get(i);
            int ry = y + 16 + i * row;
            boolean on = m.isEnabled();
            g.fill(x + 6, ry + 2, x + 8, ry + 7, on ? Theme.accent() : 0xFF4A4A56);
            HudKit.text(g, m.name, x + 12, ry, on ? 0xFFE9E9F1 : 0xFF8E8EA0);
            String k = m.bind.display();
            HudKit.text(g, k, x + w - 6 - HudKit.width(k), ry, 0xFF6E6E7E);
        }
    }
}

package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.HudLayer;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.render.HudKit;
import dev.mraz1visual.setting.BoolSetting;
import dev.mraz1visual.ui.Colors;
import dev.mraz1visual.ui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

/** Всплывашки справа снизу, когда модуль включается или выключается клавишей. */
public final class Notifications extends Module implements HudLayer {
    private static Notifications instance;
    final BoolSetting inMenu = add(new BoolSetting("in_menu", "И при переключении в меню", false));

    private static final class Toast {
        String name;
        boolean on;
        long at;
    }

    private final List<Toast> toasts = new ArrayList<>();

    public Notifications() {
        super("notifications", "Notifications", "Всплывашка при включении и выключении функций.", Category.VISUALS);
        instance = this;
        loadEnabled(true);
    }

    /** Из Module.setEnabled. */
    public static void post(Module m, boolean on) {
        Notifications n = instance;
        if (n == null || !n.isEnabled() || m == n || m.alwaysOn()) return;
        Minecraft mc = Minecraft.getInstance();
        if (!n.inMenu.get() && mc.screen != null) return;
        Toast t = new Toast();
        t.name = m.name;
        t.on = on;
        t.at = System.currentTimeMillis();
        n.toasts.add(t);
        if (n.toasts.size() > 5) n.toasts.remove(0);
    }

    @Override
    public void renderHud(GuiGraphics g, float pt) {
        long now = System.currentTimeMillis();
        toasts.removeIf(t -> now - t.at > 2600);
        int y = g.guiHeight() - 34;
        for (int i = toasts.size() - 1; i >= 0; i--) {
            Toast t = toasts.get(i);
            float age = (now - t.at) / 1000f;
            float in = Math.min(1f, age / 0.25f), out = Math.min(1f, Math.max(0f, (2.6f - age) / 0.3f));
            float a = Math.min(in, out);
            float ease = 1 - (1 - a) * (1 - a);
            String state = t.on ? "включён" : "выключен";
            int w = Math.max(110, HudKit.width(t.name) + HudKit.width(state) + 30);
            int x = Math.round(g.guiWidth() - 4 - w * ease);
            HudKit.card(g, x, y, w, 26, a);
            int dot = t.on ? Theme.accent() : 0xFF6A6A78;
            g.fill(x + 6, y + 8, x + 8, y + 18, Colors.mulAlpha(dot, a));
            HudKit.bold(g, t.name, x + 13, y + 5, Colors.mulAlpha(0xFFE9E9F1, a));
            HudKit.text(g, state, x + 13, y + 15, Colors.mulAlpha(t.on ? 0xFFB8B4F2 : 0xFF8E8EA0, a));
            // полоска оставшегося времени
            float left = Math.max(0, 1 - age / 2.6f);
            g.fill(x + 5, y + 24, x + 5 + Math.round((w - 10) * left), y + 25, Colors.mulAlpha(dot, a * 0.6f));
            y -= 30;
        }
    }
}

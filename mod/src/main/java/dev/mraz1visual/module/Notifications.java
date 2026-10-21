package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.HudLayer;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.render.Ease;
import dev.mraz1visual.setting.BoolSetting;
import dev.mraz1visual.setting.EnumSetting;
import dev.mraz1visual.ui.Colors;
import dev.mraz1visual.ui.Fonts;
import dev.mraz1visual.ui.Sdf;
import dev.mraz1visual.ui.Spring;
import dev.mraz1visual.ui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Уведомления о включении и выключении функций — «остров» сверху экрана (в духе Dynamic Island): стеклянная
 * капсула пружиной растягивается под текст, новое уведомление «перетекает» в ту же капсулу, потом она
 * сжимается в точку и тает. Старый вид — карточки справа снизу — оставлен стилем.
 */
public final class Notifications extends Module implements HudLayer {
    private static Notifications instance;
    final EnumSetting style = add(new EnumSetting("style", "Вид", 0, "island", "Остров сверху", "cards", "Карточки справа"));
    final BoolSetting inMenu = add(new BoolSetting("in_menu", "И при переключении в меню", false));

    private String name = "", state = "";
    private boolean on;
    private long at;
    private float textW;
    private final Spring width = new Spring(320f, 22f, 0f);
    private float show, textA;
    private long last;

    public Notifications() {
        super("notifications", "Notifications", "«Остров» сверху экрана, когда функция включается или выключается.", Category.VISUALS);
        instance = this;
        loadEnabled(true);
    }

    /** Из Module.setEnabled. */
    public static void post(Module m, boolean on) {
        Notifications n = instance;
        if (n == null || !n.isEnabled() || m == n || m.alwaysOn()) return;
        Minecraft mc = Minecraft.getInstance();
        if (!n.inMenu.get() && mc.screen != null) return;
        n.name = m.name;
        n.on = on;
        n.state = on ? "включено" : "выключено";
        n.at = System.currentTimeMillis();
        n.textA = 0;
        n.textW = Fonts.width(n.name, Fonts.Kind.BOLD) + Fonts.width(n.state, Fonts.Kind.TEXT) + 12;
    }

    @Override
    public void renderHud(GuiGraphics g, float pt) {
        long nowNs = System.nanoTime();
        float dt = last == 0 ? 0 : Math.min(0.05f, (nowNs - last) / 1e9f);
        last = nowNs;
        float age = (System.currentTimeMillis() - at) / 1000f;
        boolean alive = at != 0 && age < 2.2f;
        show = Ease.approach(show, alive ? 1 : 0, alive ? 14 : 7, dt);
        if (show < 0.01f) {
            width.snap(0);
            return;
        }
        boolean cards = style.getId().equals("cards");
        float target = alive ? textW + 34 : 22;
        width.target = target;
        float w = Math.max(22, width.update(dt));
        textA = Ease.approach(textA, alive && w > target * 0.85f ? 1 : 0, 12, dt);
        float h = 22, sw = g.guiWidth();
        float x = cards ? sw - 6 - w : (sw - w) / 2f, y = cards ? g.guiHeight() - 34 : 6 + (1 - Ease.outCubic(show)) * -10;
        int a = Math.round(255 * show);
        Sdf.shape(g, x, y + 2, w, h, h / 2, Sdf.SHADOW, 10, Colors.withAlpha(0x000000, a / 3), Colors.withAlpha(0x000000, a / 3));
        Sdf.shape(g, x, y, w, h, h / 2, Sdf.GLASS, 8f, Colors.withAlpha(0x0B0B10, Math.round(0xD8 * show)), Colors.withAlpha(0x0B0B10, Math.round(0xD8 * show)));
        // индикатор: акцент — включено, серый — выключено
        int dot = on ? Theme.accent() : 0xFF6A6A78;
        Sdf.fill(g, x + 8, y + h / 2 - 3, 6, 6, 3, Colors.mulAlpha(dot, show));
        if (textA > 0.02f) {
            float ta = textA * show;
            g.enableScissor(Math.round(x), Math.round(y), Math.round(x + w), Math.round(y + h));
            float tx = x + 20;
            Fonts.draw(g, name, Fonts.Kind.BOLD, tx, y + 7, Colors.mulAlpha(0xFFF2F2F7, ta));
            Fonts.draw(g, state, Fonts.Kind.TEXT, tx + Fonts.width(name, Fonts.Kind.BOLD) + 6, y + 7, Colors.mulAlpha(on ? 0xFFB8D8FF : 0xFF8E8EA0, ta));
            g.disableScissor();
        }
    }
}

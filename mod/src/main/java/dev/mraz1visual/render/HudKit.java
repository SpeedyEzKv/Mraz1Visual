package dev.mraz1visual.render;

import dev.mraz1visual.ui.Colors;
import dev.mraz1visual.ui.Draw;
import dev.mraz1visual.ui.Fonts;
import dev.mraz1visual.ui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Общий вид HUD-карточек: тёмное стекло, тонкая рамка, акцентная точка у заголовка. */
public final class HudKit {
    private static final Draw D = new Draw();

    private HudKit() {}

    public static void card(GuiGraphics g, int x, int y, int w, int h, float alpha) {
        D.begin(g, Minecraft.getInstance().font, 1f);
        D.round(x - 1, y + 1, w + 2, h + 2, 6, Colors.mulAlpha(0x40000000, alpha));
        D.card(x, y, w, h, 5, Colors.mulAlpha(0xE00C0C12, alpha), Colors.mulAlpha(0x22FFFFFF, alpha));
        D.end();
    }

    /** Заголовок карточки: точка акцента + мелкий текст капсом. */
    public static void title(GuiGraphics g, String text, int x, int y, float alpha) {
        Minecraft mc = Minecraft.getInstance();
        D.begin(g, mc.font, 1f);
        D.disc(x + 2, y + 3.5f, 1.8f, Colors.mulAlpha(Theme.accent(), alpha));
        D.end();
        Fonts.draw(g, text, Fonts.Kind.HEAD, x + 7, y, Colors.mulAlpha(0xFF8E8EA0, alpha));
    }

    public static void text(GuiGraphics g, String s, int x, int y, int color) {
        Fonts.draw(g, s, Fonts.Kind.TEXT, x, y, color);
    }

    public static void bold(GuiGraphics g, String s, int x, int y, int color) {
        Fonts.draw(g, s, Fonts.Kind.BOLD, x, y, color);
    }

    public static int width(String s) {
        return Fonts.width(s, Fonts.Kind.TEXT);
    }

    /** Полоса прогресса со скруглением. */
    public static void bar(GuiGraphics g, int x, int y, int w, int h, float f, int color, float alpha) {
        D.begin(g, Minecraft.getInstance().font, 1f);
        D.round(x, y, w, h, h / 2, Colors.mulAlpha(0x30FFFFFF, alpha));
        int fw = Math.round(w * Math.max(0, Math.min(1, f)));
        if (fw > 0) D.round(x, y, fw, h, h / 2, Colors.mulAlpha(color, alpha));
        D.end();
    }

    private static final int[] ANCHOR = new int[2];

    /**
     * Точка по углу экрана: corner 0 — слева сверху, 1 — справа сверху, 2 — слева снизу, 3 — справа снизу.
     * Возвращает общий массив (без мусора в кадре) — читать сразу, не хранить.
     */
    public static int[] anchor(GuiGraphics g, int corner, int w, int h, int margin) {
        ANCHOR[0] = (corner & 1) == 0 ? margin : g.guiWidth() - w - margin;
        ANCHOR[1] = corner < 2 ? margin : g.guiHeight() - h - margin;
        return ANCHOR;
    }
}

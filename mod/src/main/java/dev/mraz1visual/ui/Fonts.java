package dev.mraz1visual.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Шрифты клиента: Manrope (текст) и Unbounded (заголовки), лицензия OFL.
 * Компоненты кэшируются по строке — в кадре ничего не создаётся.
 * Единственное место, где мод знает, как в Minecraft задаётся шрифт.
 */
public final class Fonts {
    private Fonts() {}

    public enum Kind {
        TEXT("text", 8),
        BOLD("bold", 8),
        HEAD("head", 8),
        TITLE("title", 16),
        BRAND("brand", 16);

        final Style style;
        /** Примерная высота заглавных — для вертикального центрирования. */
        public final int height;
        final Map<String, Component> cache = new HashMap<>();

        Kind(String id, int height) {
            this.style = Style.EMPTY.withFont(new FontDescription.Resource(Identifier.fromNamespaceAndPath("mraz1visual", id)));
            this.height = height;
        }
    }

    private static final int CACHE_LIMIT = 1024;

    public static Component of(String s, Kind k) {
        Component c = k.cache.get(s);
        if (c == null) {
            if (k.cache.size() > CACHE_LIMIT) k.cache.clear();
            c = Component.literal(s).withStyle(k.style);
            k.cache.put(s, c);
        }
        return c;
    }

    /**
     * Шрифты загружены в 3 раза крупнее и рисуются уменьшенными: так ширина букв округляется
     * в 3 раза точнее — нет «рваных» промежутков между буквами при любом масштабе окна.
     */
    public static final float SS = 3f;

    public static int width(String s, Kind k) {
        return Math.round(Minecraft.getInstance().font.width(of(s, k)) / SS);
    }

    public static float widthF(String s, Kind k) {
        return Minecraft.getInstance().font.width(of(s, k)) / SS;
    }

    /** Нарисовать текст шрифтом клиента в координатах x, y (левый верх). */
    public static void draw(net.minecraft.client.gui.GuiGraphics g, String s, Kind k, float x, float y, int color) {
        draw(g, s, k, x, y, 1f, color);
    }

    public static void drawShadow(net.minecraft.client.gui.GuiGraphics g, String s, Kind k, float x, float y, int color) {
        draw(g, s, k, x + 0.5f, y + 0.5f, 1f, (color >>> 24) / 2 << 24);
        draw(g, s, k, x, y, 1f, color);
    }

    public static void draw(net.minecraft.client.gui.GuiGraphics g, String s, Kind k, float x, float y, float scale, int color) {
        if (s == null || s.isEmpty() || (color >>> 24) == 0) return;
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        float f = scale / SS;
        g.pose().scale(f, f);
        g.drawString(Minecraft.getInstance().font, of(s, k), 0, 0, color, false);
        g.pose().popMatrix();
    }

    public static int width(String s) {
        return width(s, Kind.TEXT);
    }
}

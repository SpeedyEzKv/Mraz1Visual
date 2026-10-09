package dev.umbra.ui;

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
            this.style = Style.EMPTY.withFont(new FontDescription.Resource(Identifier.fromNamespaceAndPath("umbra", id)));
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

    public static int width(String s, Kind k) {
        return Minecraft.getInstance().font.width(of(s, k));
    }

    public static int width(String s) {
        return width(s, Kind.TEXT);
    }
}

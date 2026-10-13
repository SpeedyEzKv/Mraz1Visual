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
        TEXT("text", "text.ttf", 8),
        BOLD("bold", "bold.ttf", 8),
        HEAD("head", "head.ttf", 8),
        TITLE("title", "head.ttf", 16),
        BRAND("brand", "brand.ttf", 16);

        final Style style;
        final String file;
        /** Примерная высота заглавных — для вертикального центрирования. */
        public final int height;
        final Map<String, Component> cache = new HashMap<>();
        final Map<String, Float> widths = new HashMap<>();

        Kind(String id, String file, int height) {
            this.style = Style.EMPTY.withFont(new FontDescription.Resource(Identifier.fromNamespaceAndPath("mraz1visual", id)));
            this.file = file;
            this.height = height;
        }
    }

    /** Свой рендер текста (GlyphAtlas). Если что-то пойдёт не так — откат на шрифты Minecraft. */
    private static boolean crisp = true;
    /** Почему сглаженный текст выключился (пусто — всё хорошо). Показывается в GUI. */
    public static String crispError = "";

    public static boolean crisp() { return crisp && !dev.mraz1visual.core.Modules.client.vanillaFont.get(); }

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
        return Math.round(widthF(s, k));
    }

    public static float widthF(String s, Kind k) {
        if (!crisp()) return Minecraft.getInstance().font.width(of(s, k)) / SS;
        Float w = k.widths.get(s);
        if (w == null) {
            float sum = 0;
            for (int i = 0; i < s.length(); ) {
                int cp = s.codePointAt(i);
                sum += GlyphAtlas.advance(k.file, cp, k.height);
                i += Character.charCount(cp);
            }
            if (k.widths.size() > CACHE_LIMIT) k.widths.clear();
            k.widths.put(s, w = sum);
        }
        return w;
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
        if (crisp()) {
            try {
                drawCrisp(g, s, k, x, y, scale, color);
                return;
            } catch (Throwable t) {
                crisp = false;
                crispError = t.getClass().getSimpleName() + (t.getMessage() == null ? "" : ": " + t.getMessage());
                dev.mraz1visual.core.ModuleManager.LOG.warn("Crisp text disabled, using Minecraft fonts", t);
                for (Kind kk : Kind.values()) kk.widths.clear();
            }
        }
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

    public static void clearWidths() {
        for (Kind k : Kind.values()) k.widths.clear();
    }

    /**
     * Текст через GlyphAtlas: размер буквы считается в физических пикселях экрана (масштаб GUI × поза),
     * каждая буква ставится на целый пиксель — без «рваных» штрихов и прыгающей базовой линии.
     */
    private static void drawCrisp(net.minecraft.client.gui.GuiGraphics g, String s, Kind k, float x, float y, float scale, int color) {
        org.joml.Matrix3x2f pose = g.pose();
        double gs = Minecraft.getInstance().getWindow().getGuiScale();
        float sx = (float) Math.hypot(pose.m00(), pose.m01());
        float ps = (float) (sx * gs);                // физических пикселей в одной единице
        float em = k.height * scale;
        float px = Math.round(em * ps * 2f) / 2f;      // размер шрифта в пикселях (шаг 0,5 — меньше атласов)
        if (px < 3f) return;
        GlyphAtlas atlas = GlyphAtlas.get(k.file, Math.min(px, 160f));
        float ox = (float) (pose.m20() * gs), oy = (float) (pose.m21() * gs);
        float fx = ox - (float) Math.floor(ox), fy = oy - (float) Math.floor(oy);
        org.joml.Matrix3x2f phys = new org.joml.Matrix3x2f(pose).scale(1f / ps).translate(-fx, -fy);
        int base = Math.round((y + em * 0.95f) * ps + fy);
        float cx = x * ps + fx;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            GlyphAtlas.Glyph gl = atlas.glyph(cp);
            atlas.draw(g, phys, gl, Math.round(cx), base, color);
            cx += GlyphAtlas.advance(k.file, cp, em) * ps; // шаг — как в раскладке (widthF)
        }
    }
}

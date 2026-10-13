package dev.mraz1visual.ui;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.BlitRenderState;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Свой рендер текста: каждая буква растрируется Java-движком шрифтов со сглаживанием
 * ровно под физический размер на экране (а не крупно с последующим уменьшением без сглаживания,
 * от чего буквы «рвались»), складывается в атлас и рисуется с плавной фильтрацией,
 * с привязкой к пиксельной сетке экрана — текст ровный и мягкий на любом масштабе.
 */
final class GlyphAtlas {
    private static final int PAGE = 1024;
    private static final int MAX_ATLASES = 16;
    private static final Map<String, GlyphAtlas> ATLASES = new LinkedHashMap<>(16, 0.75f, true);
    private static final Map<String, Font> FONTS = new HashMap<>();
    private static Font fallback;
    private static int ids;

    record Glyph(int page, float u0, float v0, float u1, float v1, int w, int h, int bx, int by) {}

    private final Font font;
    private final Font fallbackFont;
    private final Map<Integer, Glyph> glyphs = new HashMap<>();
    private final List<NativeImage> pages = new ArrayList<>();
    private final List<DynamicTexture> textures = new ArrayList<>();
    private final List<Identifier> pageIds = new ArrayList<>();
    private final boolean[] dirty = new boolean[64];
    private int penX, penY, rowH;
    private static final FontRenderContext FRC = new FontRenderContext(null, true, true);

    private GlyphAtlas(Font base, float px) {
        this.font = base.deriveFont(px);
        this.fallbackFont = fallback().deriveFont(px);
    }

    static Font font(String file) {
        return FONTS.computeIfAbsent(file, f -> {
            try (var in = GlyphAtlas.class.getResourceAsStream("/assets/mraz1visual/font/" + f)) {
                return Font.createFont(Font.TRUETYPE_FONT, in);
            } catch (Throwable t) {
                return new Font(Font.SANS_SERIF, Font.PLAIN, 12);
            }
        });
    }

    private static Font fallback() {
        if (fallback == null) fallback = font("symbols.ttf");
        return fallback;
    }

    static GlyphAtlas get(String file, float px) {
        String key = file + "@" + px;
        GlyphAtlas a = ATLASES.get(key);
        if (a == null) {
            if (ATLASES.size() >= MAX_ATLASES) {
                var it = ATLASES.entrySet().iterator();
                it.next().getValue().release();
                it.remove();
            }
            a = new GlyphAtlas(font(file), px);
            ATLASES.put(key, a);
        }
        return a;
    }

    /** Ширина строки в единицах при размере em (точно, дробно — для раскладки). */
    static float advance(String file, int cp, float em) {
        Font f = font(file);
        Font use = f.canDisplay(cp) ? f : fallback();
        Map<Integer, Float> cache = ADV.computeIfAbsent(file, k -> new HashMap<>());
        Float a = cache.get(cp);
        if (a == null) {
            GlyphVector gv = use.deriveFont(64f).createGlyphVector(FRC, new String(Character.toChars(cp)));
            a = (float) gv.getGlyphMetrics(0).getAdvanceX() / 64f;
            cache.put(cp, a);
        }
        return a * em;
    }

    private static final Map<String, Map<Integer, Float>> ADV = new HashMap<>();

    Glyph glyph(int cp) {
        Glyph g = glyphs.get(cp);
        if (g != null) return g;
        Font use = font.canDisplay(cp) ? font : fallbackFont;
        // растрируем в 3 раза крупнее и усредняем 3×3 — мягкие, ровные края без «лесенки»
        final int SSF = 3;
        Font big = use.deriveFont(use.getSize2D() * SSF);
        GlyphVector gv = big.createGlyphVector(FRC, new String(Character.toChars(cp)));
        Rectangle rb = gv.getPixelBounds(FRC, 0, 0);
        if (rb.width <= 0 || rb.height <= 0) {
            g = new Glyph(-1, 0, 0, 0, 0, 0, 0, 0, 0); // пробел
            glyphs.put(cp, g);
            return g;
        }
        // границы в обычных пикселях (с запасом 1 px), начало кратно 3 — чтобы сетки совпали
        int bx = Math.floorDiv(rb.x, SSF) - 1, by = Math.floorDiv(rb.y, SSF) - 1;
        int w = Math.floorDiv(rb.x + rb.width - 1, SSF) - bx + 2, h = Math.floorDiv(rb.y + rb.height - 1, SSF) - by + 2;
        BufferedImage img = new BufferedImage(w * SSF, h * SSF, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setColor(java.awt.Color.WHITE);
        // контур буквы заливаем как фигуру — так сглаживание не зависит от настроек шрифтов системы
        g2.translate(-bx * SSF, -by * SSF);
        g2.fill(gv.getOutline());
        g2.dispose();
        int[] big3 = img.getRGB(0, 0, w * SSF, h * SSF, null, 0, w * SSF);
        int[] cov = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int sum = 0;
                for (int yy = 0; yy < SSF; yy++) {
                    int o = (y * SSF + yy) * w * SSF + x * SSF;
                    for (int xx = 0; xx < SSF; xx++) sum += big3[o + xx] >>> 24;
                }
                cov[y * w + x] = sum / (SSF * SSF);
            }
        }
        if (penX + w + 1 > PAGE) {
            penX = 0;
            penY += rowH + 1;
            rowH = 0;
        }
        if (pages.isEmpty() || penY + h + 1 > PAGE) newPage();
        int page = pages.size() - 1;
        NativeImage ni = pages.get(page);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int a = cov[y * w + x];
                // лёгкая гамма: на тёмном фоне сглаженные буквы иначе выглядят тоньше
                a = (int) Math.round(255 * Math.pow(a / 255.0, 0.8));
                ni.setPixel(penX + x, penY + y, a << 24 | 0xFFFFFF);
            }
        }
        dirty[page] = true;
        g = new Glyph(page, penX / (float) PAGE, penY / (float) PAGE, (penX + w) / (float) PAGE, (penY + h) / (float) PAGE,
                w, h, bx, by);
        penX += w + 1;
        rowH = Math.max(rowH, h);
        glyphs.put(cp, g);
        return g;
    }

    private void newPage() {
        NativeImage ni = new NativeImage(PAGE, PAGE, true);
        for (int y = 0; y < PAGE; y++) for (int x = 0; x < PAGE; x++) ni.setPixel(x, y, 0x00FFFFFF);
        DynamicTexture t = new DynamicTexture(() -> "mraz1visual font", ni);
        Identifier id = Identifier.fromNamespaceAndPath("mraz1visual", "font_atlas/" + (ids++));
        Minecraft.getInstance().getTextureManager().register(id, t);
        pages.add(ni);
        textures.add(t);
        pageIds.add(id);
        penX = penY = rowH = 0;
    }

    /** Нарисовать букву: (px, py) — левый верхний угол в физических пикселях относительно базовой матрицы. */
    void draw(GuiGraphics g, Matrix3x2f physical, Glyph gl, int px, int py, int color) {
        if (gl.page() < 0) return;
        if (dirty[gl.page()]) {
            textures.get(gl.page()).upload();
            dirty[gl.page()] = false;
        }
        var view = textures.get(gl.page()).getTextureView();
        if (view == null) return;
        g.guiRenderState.submitGuiElement(new BlitRenderState(
                RenderPipelines.GUI_TEXTURED,
                TextureSetup.singleTexture(view, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)),
                physical,
                px + gl.bx(), py + gl.by(), px + gl.bx() + gl.w(), py + gl.by() + gl.h(),
                gl.u0(), gl.u1(), gl.v0(), gl.v1(), color, g.scissorStack.peek()));
    }

    private void release() {
        for (Identifier id : pageIds) Minecraft.getInstance().getTextureManager().release(id);
    }
}

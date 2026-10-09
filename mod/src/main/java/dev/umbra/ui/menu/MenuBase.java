package dev.umbra.ui.menu;

import com.mojang.blaze3d.platform.Window;
import dev.umbra.core.Modules;
import dev.umbra.ui.Anim;
import dev.umbra.ui.Colors;
import dev.umbra.ui.Draw;
import dev.umbra.ui.Fonts;
import dev.umbra.ui.Theme;
import dev.umbra.ui.widget.Widget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Общее для экранов меню: фон-затмение, масштаб, крупные пункты слева.
 * Меню рисуется в «виртуальных» единицах: экран всегда ~540 единиц в высоту.
 */
public abstract class MenuBase extends Screen {
    private final EclipseBackground background = new EclipseBackground();

    protected static final int ITEM_IDLE = 0xFF9494AE;
    protected static final int ITEM_HOVER = 0xFFDCDCF5;
    protected static final float ITEM_SCALE = 1.35f;
    protected static final int ITEM_LINE = 36;
    protected static final int ITEM_GAP = 12;

    protected final Draw d = new Draw();
    protected float k = 1f;
    protected int vw, vh;
    private long lastNanos;
    /** 0..1 — плавное появление экрана. */
    protected float fade;

    protected final List<Item> items = new ArrayList<>();

    protected MenuBase(String title) {
        super(Component.literal(title));
    }

    /** Где на экране (доли) стоит центр затмения и во сколько раз картинка увеличена. */
    protected abstract float sunScreenX();
    protected abstract float sunScreenY();
    protected float zoom() { return 1f; }

    protected abstract void renderMenu(double mx, double my, float dt);

    // ── пункты меню ──────────────────────────────────────────

    protected static final class Item {
        final String label;
        final Runnable action;
        final int gapBefore;
        int x, y, w, h;
        float hover;
        boolean selected;

        Item(String label, int gapBefore, Runnable action) {
            this.label = label;
            this.gapBefore = gapBefore;
            this.action = action;
        }
    }

    protected float itemScale() { return ITEM_SCALE; }
    protected int itemLine() { return ITEM_LINE; }

    protected Item item(String label, Runnable action) { return addItem(label, 0, action); }

    protected Item itemAfterGap(String label, Runnable action) { return addItem(label, ITEM_GAP, action); }

    private Item addItem(String label, int gap, Runnable action) {
        Item it = new Item(label, gap, action);
        items.add(it);
        return it;
    }

    protected void layoutItems(int x, int y) {
        for (Item it : items) {
            y += it.gapBefore;
            it.x = x;
            it.y = y;
            it.w = Math.round(Fonts.width(it.label, Fonts.Kind.TITLE) * itemScale()) + 12;
            it.h = itemLine();
            y += itemLine();
        }
    }

    protected void renderItems(double mx, double my, float dt) {
        for (Item it : items) {
            boolean hov = Widget.hit(mx, my, it.x - 8, it.y, it.w + 8, it.h);
            it.hover = Anim.to(it.hover, hov || it.selected ? 1 : 0, dt);
            int shift = Math.round(it.hover * 3);
            int color = Colors.mulAlpha(Colors.lerp(ITEM_IDLE, ITEM_HOVER, it.hover), fade);
            if (it.selected) d.rect(it.x - 9, it.y + it.h / 2 - 3, 4, 4, Colors.mulAlpha(ITEM_HOVER, fade));
            d.textScaled(it.label, Fonts.Kind.TITLE, it.x + shift, it.y + Math.round(itemLine() * 0.17f), itemScale(), color);
        }
    }

    protected boolean clickItems(double mx, double my) {
        for (Item it : items) {
            if (Widget.hit(mx, my, it.x - 8, it.y, it.w + 8, it.h)) {
                it.action.run();
                return true;
            }
        }
        return false;
    }

    // ── масштаб и фон ────────────────────────────────────────

    private void computeScale() {
        Window win = minecraft.getWindow();
        double gui = win.getGuiScale();
        int unit = Math.max(1, Math.round(win.getHeight() / 540f));
        k = (float) (unit / gui);
        vw = (int) (width / k);
        vh = (int) (height / k);
    }

    @Override
    protected void init() {
        d.font = font;
        computeScale();
        d.scale = k;
        onLayout();
    }

    /** Пересчитать раскладку (вызывается при открытии и смене размера окна). */
    protected abstract void onLayout();

    /** Ванильный фон (в мире — размытие). */
    protected void vanillaBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        background.render(g, width, height, sunScreenX(), sunScreenY(), zoom(), mouseX, mouseY,
                Modules.client.animations.get());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 0f : Math.min(0.1f, (now - lastNanos) / 1e9f);
        lastNanos = now;
        Anim.enabled = Modules.client.animations.get();
        fade = Anim.enabled ? Math.min(1f, fade + dt * 3f) : 1f;

        d.begin(g, font, k);
        renderMenu(mouseX / k, mouseY / k, dt);
        d.end();
    }

    @Override
    public void removed() {
        lastNanos = 0;
    }

    /** Подпись внизу слева: широкий логотип, версия, автор. */
    protected void renderBrand(int x) {
        int y = vh - 88;
        float sc = 1.4f;
        d.textScaled("UMBRA", Fonts.Kind.BRAND, x, y, sc, Colors.mulAlpha(ITEM_HOVER, fade));
        int bw = Math.round(Fonts.width("UMBRA", Fonts.Kind.BRAND) * sc);
        d.text("0.1.0", x + bw + 2, y + 12, Colors.mulAlpha(0xFF8A8AA6, fade));
        d.text("by SpeedyEzKv", x, y + 30, Colors.mulAlpha(0xFF6E6E88, fade));
    }
}

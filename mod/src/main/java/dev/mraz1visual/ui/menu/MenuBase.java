package dev.mraz1visual.ui.menu;

import com.mojang.blaze3d.platform.Window;
import dev.mraz1visual.core.Modules;
import dev.mraz1visual.ui.Anim;
import dev.mraz1visual.ui.Colors;
import dev.mraz1visual.ui.Draw;
import dev.mraz1visual.ui.Fonts;
import dev.mraz1visual.ui.Theme;
import dev.mraz1visual.ui.widget.Widget;
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

    /** Ник, подпись и лицо скина сверху справа. */
    protected void renderAccount(String subtitle) {
        String name = minecraft.getUser().getName();
        int tile = 24, tx = vw - 36 - tile, ty = 24;
        net.minecraft.resources.Identifier skin = null;
        try {
            if (minecraft.player != null) skin = minecraft.player.getSkin().body().texturePath();
        } catch (Throwable ignored) {
        }
        if (skin != null) {
            int c = Colors.withAlpha(0xFFFFFF, Math.round(255 * fade));
            d.g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, skin, tx, ty, 8f, 8f, tile, tile, 8, 8, 64, 64, c);
            d.g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, skin, tx, ty, 40f, 8f, tile, tile, 8, 8, 64, 64, c);
        } else {
            d.round(tx, ty, tile, tile, 3, Colors.mulAlpha(0xFF2A2A3C, fade));
            d.textCenter(name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(), tx + tile / 2, ty + 8, Colors.mulAlpha(ITEM_HOVER, fade));
        }
        int right = tx - 9;
        d.text(name, Fonts.Kind.BOLD, right - Fonts.width(name, Fonts.Kind.BOLD), ty + 2, Colors.mulAlpha(0xFFE6E6F4, fade));
        d.textRight(subtitle, right, ty + 14, Colors.mulAlpha(0xFF8686A2, fade));
    }

    private static final String VERSION = net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("mraz1visual")
            .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("0.1.0");

    /** Подпись внизу слева: широкий логотип, версия, автор. */
    protected void renderBrand(int x) {
        int y = vh - 88;
        float sc = 1.1f;
        d.textScaled("MRAZ1VISUAL", Fonts.Kind.BRAND, x, y, sc, Colors.mulAlpha(ITEM_HOVER, fade));
        int bw = Math.round(Fonts.width("MRAZ1VISUAL", Fonts.Kind.BRAND) * sc);
        d.text(VERSION, x + bw + 2, y + 12, Colors.mulAlpha(0xFF8A8AA6, fade));
        d.text("by SpeedyEzKv", x, y + 30, Colors.mulAlpha(0xFF6E6E88, fade));
    }
}

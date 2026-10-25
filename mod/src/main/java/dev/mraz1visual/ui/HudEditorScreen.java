package dev.mraz1visual.ui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.mraz1visual.Mraz1Visual;
import dev.mraz1visual.render.HudPos;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Редактор HUD: включённые элементы видны прямо поверх игры — тяни мышью, колесо меняет масштаб,
 * у краёв и центра экрана элемент примагничивается (Shift — без привязки), ПКМ — вернуть на место по умолчанию.
 * Сам ничего не рисует за элементы: их рисует обычный HUD, редактор — только рамки и подсказки.
 */
public final class HudEditorScreen extends Screen {
    private static final int SNAP = 6;
    private final Screen parent;
    private final boolean grid;
    private HudPos drag;
    private double offX, offY;
    private int guideX = -1, guideY = -1;

    public HudEditorScreen(Screen parent, boolean grid) {
        super(Component.literal(Lang.get("mraz1visual.hud.editor.title")));
        this.parent = parent;
        this.grid = grid;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float pt) {
        // без размытия: элементы HUD должны быть видны как в игре
        g.fill(0, 0, width, height, 0x30000000);
        if (grid) {
            for (int x = 0; x < width; x += 16) g.fill(x, 0, x + 1, height, 0x14FFFFFF);
            for (int y = 0; y < height; y += 16) g.fill(0, y, width, y + 1, 0x14FFFFFF);
        }
        g.fill(width / 2, 0, width / 2 + 1, height, 0x18FFFFFF);
        g.fill(0, height / 2, width, height / 2 + 1, 0x18FFFFFF);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        super.render(g, mouseX, mouseY, pt);
        HudPos hover = pick(mouseX, mouseY);
        for (HudPos p : HudPos.all()) {
            if (!p.visible()) continue;
            boolean on = p == drag || p == hover && drag == null;
            int c = on ? Theme.accent() | 0xFF000000 : 0x90FFFFFF;
            outline(g, p.lastX - 1, p.lastY - 1, p.lastW + 2, p.lastH + 2, c);
            if (on) {
                String label = p.module.name + (p.scale.get() != 1 ? "  " + p.scale.display() : "");
                int ly = p.lastY > 12 ? p.lastY - 11 : p.lastY + p.lastH + 3;
                int lw = Fonts.width(label, Fonts.Kind.TEXT);
                g.fill(p.lastX - 1, ly - 2, p.lastX + lw + 5, ly + 9, 0xC00C0C12);
                Fonts.draw(g, label, Fonts.Kind.TEXT, p.lastX + 2, ly, 0xFFFFFFFF);
            }
        }
        if (drag != null) {
            if (guideX >= 0) g.fill(guideX, 0, guideX + 1, height, Theme.accent() | 0xFF000000);
            if (guideY >= 0) g.fill(0, guideY, width, guideY + 1, Theme.accent() | 0xFF000000);
        }
        String hint = Lang.get("mraz1visual.hud.editor.hint");
        int hw = Fonts.width(hint, Fonts.Kind.TEXT);
        g.fill(width / 2 - hw / 2 - 8, height - 22, width / 2 + hw / 2 + 8, height - 6, 0xC00C0C12);
        Fonts.draw(g, hint, Fonts.Kind.TEXT, width / 2f - hw / 2f, height - 18, 0xFFE9E9F1);
    }

    private static void outline(GuiGraphics g, int x, int y, int w, int h, int c) {
        g.fill(x, y, x + w, y + 1, c);
        g.fill(x, y + h - 1, x + w, y + h, c);
        g.fill(x, y, x + 1, y + h, c);
        g.fill(x + w - 1, y, x + w, y + h, c);
    }

    /** Верхний элемент под курсором (последний нарисованный — сверху). */
    private static HudPos pick(double mx, double my) {
        HudPos found = null;
        for (HudPos p : HudPos.all()) {
            if (p.visible() && mx >= p.lastX && mx < p.lastX + p.lastW && my >= p.lastY && my < p.lastY + p.lastH) found = p;
        }
        return found;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        HudPos p = pick(e.x(), e.y());
        if (p == null) return super.mouseClicked(e, doubleClick);
        if (e.button() == 1) {
            p.x.reset();
            p.y.reset();
            p.scale.reset();
            return true;
        }
        if (e.button() == 0) {
            drag = p;
            offX = e.x() - p.lastX;
            offY = e.y() - p.lastY;
            if (!p.active()) p.activate();
        }
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dragX, double dragY) {
        if (drag == null) return false;
        int w = drag.lastW, h = drag.lastH;
        int nx = (int) Math.round(e.x() - offX), ny = (int) Math.round(e.y() - offY);
        guideX = guideY = -1;
        if (!hasShift()) {
            if (grid) {
                nx = Math.round(nx / 8f) * 8;
                ny = Math.round(ny / 8f) * 8;
            }
            // края и центр экрана
            if (Math.abs(nx) < SNAP) { nx = 0; guideX = 0; }
            else if (Math.abs(nx + w - width) < SNAP) { nx = width - w; guideX = width - 1; }
            else if (Math.abs(nx + w / 2 - width / 2) < SNAP) { nx = width / 2 - w / 2; guideX = width / 2; }
            if (Math.abs(ny) < SNAP) { ny = 0; guideY = 0; }
            else if (Math.abs(ny + h - height) < SNAP) { ny = height - h; guideY = height - 1; }
            else if (Math.abs(ny + h / 2 - height / 2) < SNAP) { ny = height / 2 - h / 2; guideY = height / 2; }
        }
        nx = Math.max(0, Math.min(width - w, nx));
        ny = Math.max(0, Math.min(height - h, ny));
        drag.moveTo(nx, ny, width, height);
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        drag = null;
        guideX = guideY = -1;
        return super.mouseReleased(e);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        HudPos p = drag != null ? drag : pick(mouseX, mouseY);
        if (p == null || scrollY == 0) return false;
        if (!p.active()) p.activate();
        p.scale.set(p.scale.get() + (scrollY > 0 ? p.scale.step : -p.scale.step));
        return true;
    }

    private static boolean hasShift() {
        return dev.mraz1visual.input.Keys.isDown(InputConstants.KEY_LSHIFT) || dev.mraz1visual.input.Keys.isDown(InputConstants.KEY_RSHIFT);
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (e.key() == InputConstants.KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(e);
    }

    @Override
    public void onClose() {
        Mraz1Visual.CONFIG.saveIfDirty();
        minecraft.setScreen(parent);
    }
}

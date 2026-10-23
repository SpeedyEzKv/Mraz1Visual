package dev.mraz1visual.ui;

import dev.mraz1visual.input.Keys;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;

/**
 * Радиальное колесо: держишь клавишу — колесо открыто, мышь выбирает сектор, отпускаешь — выполняется выбранное.
 * Клик по сектору — то же самое. Ничего не делается само: только то, что игрок выбрал.
 */
public final class WheelScreen extends Screen {
    private final String title;
    private final List<String> items;
    private final int key;
    private final Consumer<String> action;
    private final Draw d = new Draw();
    private int selected = -1;
    private float openT;
    private long last;

    public WheelScreen(String title, List<String> items, int key, Consumer<String> action) {
        super(Component.literal(title));
        this.title = title;
        this.items = items;
        this.key = key;
        this.action = action;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0x40000000);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        long now = System.nanoTime();
        float dt = last == 0 ? 0 : Math.min(0.05f, (now - last) / 1e9f);
        last = now;
        openT = Math.min(1f, openT + dt / 0.18f);
        // отпустил клавишу — выполнить выбранное и закрыть
        if (key > 0 && !Keys.isDown(key)) {
            pick();
            return;
        }
        d.begin(g, font, 1f);
        float cx = width / 2f, cy = height / 2f, r = Math.min(width, height) * 0.30f * (0.85f + 0.15f * openT);
        float dx = mouseX - cx, dy = mouseY - cy;
        int n = Math.max(1, items.size());
        selected = dx * dx + dy * dy < 18 * 18 ? -1 : Math.floorMod((int) Math.round(Math.atan2(dy, dx) / (Math.PI * 2 / n)), n);
        d.shadow(Math.round(cx - r - 20), Math.round(cy - r - 20), Math.round(2 * r + 40), Math.round(2 * r + 40), Math.round(r + 20), 24f, 0x50000000);
        d.ring(cx, cy, r * 0.62f, r * 0.62f, 0x50101018);
        d.ring(cx, cy, r * 0.62f, 1f, 0x30FFFFFF);
        for (int i = 0; i < items.size(); i++) {
            double a = i * Math.PI * 2 / n;
            float ix = cx + (float) Math.cos(a) * r * 0.62f, iy = cy + (float) Math.sin(a) * r * 0.62f;
            String s = Draw.ellipsize(items.get(i), Math.round(r * 0.9f));
            int w = d.width(s) + 18, h = 22;
            boolean on = i == selected;
            d.glass(Math.round(ix - w / 2f), Math.round(iy - h / 2f), w, h, h / 2, on ? Colors.withAlpha(Theme.accent(), 0xA0) : 0xB0141418, on ? 1.4f : 1f);
            d.textCenter(s, Math.round(ix), Draw.textY(Math.round(iy - h / 2f), h), 0xFFFFFFFF);
        }
        d.textCenter(title, Math.round(cx), Math.round(cy - 4), Theme.TEXT_DIM);
        d.end();
    }

    private void pick() {
        int s = selected;
        onClose();
        if (s >= 0 && s < items.size()) action.accept(items.get(s));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        if (e.button() == 0) pick();
        else onClose();
        return true;
    }
}

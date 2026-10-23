package dev.mraz1visual.ui;

import dev.mraz1visual.cosmetics.Wardrobe;
import dev.mraz1visual.ui.widget.Widget;

import java.util.ArrayList;
import java.util.List;

/**
 * Вкладка «Cosmetics» — гардероб: слева категории со счётчиками, сверху фильтр по цвету, в центре сетка вещей,
 * справа 3D-витрина с твоей моделью. Наведение на вещь — примерка (после ухода мыши всё как было),
 * клик — надеть или снять. Витрину можно крутить мышью, кнопками и включить авто-поворот.
 */
public final class WardrobePanel implements Panel {
    private static final String[] TONES = {"Все", "Тёплые", "Холодные", "Светлые", "Тёмные"};
    private static final int CAT_W = 150, CELL_W = 118, CELL_H = 74, GAP = 8;

    private final Runnable showModules;
    private final PlayerPreview preview = new PlayerPreview();
    private String category; // null — все
    private int tone = -1;
    private boolean autoSpin;
    private float scroll, target;
    private int maxScroll;
    private boolean drag;
    private final List<Object[]> hits = new ArrayList<>();
    private int gridX, gridY, gridW, gridH, showX, showY, showW, showH;
    private Wardrobe.Item hovered;

    public WardrobePanel(Runnable showModules) {
        this.showModules = showModules;
    }

    @Override
    public void render(Draw d, double mx, double my, float dt, int x, int y, int w, int h) {
        hits.clear();
        // ── категории ──
        d.glass(x, y, CAT_W, h, 14, Theme.GLASS_PANEL, 0.9f);
        d.text("Гардероб", Fonts.Kind.BOLD, x + 12, y + 10, Theme.TEXT);
        int cy = y + 30;
        cy = catRow(d, mx, my, x, cy, null, "Всё", Wardrobe.ITEMS.size());
        for (String c : Wardrobe.CATEGORIES) cy = catRow(d, mx, my, x, cy, c, c, Wardrobe.count(c));
        int bx = x + 10, by = y + h - 32, bw = CAT_W - 20;
        boolean bh = Widget.hit(mx, my, bx, by, bw, 22);
        d.round(bx, by, bw, 22, 11, bh ? Theme.GLASS_ACTIVE : Theme.GLASS_CARD);
        d.textCenter("Настройки модулей ›", bx + bw / 2, Draw.textY(by, 22), Theme.TEXT_DIM);
        hits.add(new Object[]{bx, by, bw, 22, "modules"});

        // ── витрина справа ──
        showW = Math.max(170, Math.min(240, (w - CAT_W) / 3));
        showX = x + w - showW;
        showY = y;
        showH = h;
        d.glass(showX, showY, showW, showH, 14, Theme.GLASS_PANEL, 1f);
        d.shadow(showX + showW / 2 - 36, showY + showH - 70, 72, 10, 5, 10f, 0x50000000);
        Wardrobe.Item tr = Wardrobe.trying();
        d.textScaled(tr != null ? "ПРИМЕРКА" : "ВИТРИНА", Fonts.Kind.HEAD, showX + 12, showY + 11, 0.72f, tr != null ? Theme.accent() : Theme.TEXT_MUTED);
        if (tr != null) d.text(Draw.ellipsize(tr.name(), showW - 24), Fonts.Kind.BOLD, showX + 12, showY + 24, Theme.TEXT);
        if (autoSpin) preview.setYaw(preview.yaw() + dt * 40f);
        if (!preview.render(d.g, d.scale, showX + 4, showY + 34, showW - 8, showH - 76, true, dt)) {
            d.textCenter("Зайдите в мир —", showX + showW / 2, showY + showH / 2 - 10, Theme.TEXT_MUTED);
            d.textCenter("здесь будет ваша модель", showX + showW / 2, showY + showH / 2 + 2, Theme.TEXT_MUTED);
        }
        // кнопки: повернуть влево, авто, вправо
        String[] btn = {"‹", autoSpin ? "Стоп" : "Авто-поворот", "›"};
        int[] bwid = {26, 0, 26};
        bwid[1] = showW - 24 - 26 * 2 - 8;
        int bxx = showX + 12, byy = showY + showH - 32;
        for (int i = 0; i < 3; i++) {
            boolean hv = Widget.hit(mx, my, bxx, byy, bwid[i], 22);
            boolean on = i == 1 && autoSpin;
            d.round(bxx, byy, bwid[i], 22, 11, on ? Colors.withAlpha(Theme.accent(), 0x50) : hv ? Theme.GLASS_ACTIVE : Theme.GLASS_CARD);
            d.textCenter(btn[i], bxx + bwid[i] / 2, Draw.textY(byy, 22), Theme.TEXT);
            hits.add(new Object[]{bxx, byy, bwid[i], 22, "rot" + i});
            bxx += bwid[i] + 4;
        }

        // ── фильтр цвета и сетка ──
        gridX = x + CAT_W + 10;
        gridW = showX - 10 - gridX;
        int tx = gridX, ty = y + 2, th = 20;
        for (int i = -1; i < 4; i++) {
            String label = TONES[i + 1];
            int tw = d.width(label) + 18;
            boolean on = tone == i, hv = Widget.hit(mx, my, tx, ty, tw, th);
            d.round(tx, ty, tw, th, th / 2, on ? Colors.withAlpha(Theme.accent(), 0x48) : hv ? Theme.HOVER : Theme.GLASS_CARD);
            if (i >= 0) d.disc(tx + 9, ty + th / 2f, 3f, new int[]{0xFFFF9A6B, 0xFF8FD8FF, 0xFFF2F0FF, 0xFF2A2A30}[i]);
            d.text(label, tx + (i >= 0 ? 15 : 9), Draw.textY(ty, th), on ? Theme.TEXT : Theme.TEXT_DIM);
            hits.add(new Object[]{tx, ty, tw, th, "tone" + i});
            tx += tw + 4;
        }
        gridY = y + 30;
        gridH = y + h - gridY;
        List<Wardrobe.Item> items = Wardrobe.filter(category, tone);
        int cols = Math.max(1, (gridW + GAP) / (CELL_W + GAP));
        int cw = (gridW - (cols - 1) * GAP) / cols;
        int rows = (items.size() + cols - 1) / cols;
        maxScroll = Math.max(0, rows * (CELL_H + GAP) - GAP - gridH);
        target = Math.max(0, Math.min(target, maxScroll));
        scroll = Anim.to(scroll, target, dt);
        boolean in = Widget.hit(mx, my, gridX, gridY, gridW, gridH);
        Wardrobe.Item hov = null;
        d.clip(gridX - 2, gridY, gridW + 4, gridH);
        for (int i = 0; i < items.size(); i++) {
            Wardrobe.Item it = items.get(i);
            int c = i % cols, r = i / cols;
            int ix = gridX + c * (cw + GAP), iy = gridY + r * (CELL_H + GAP) - Math.round(scroll);
            if (iy + CELL_H < gridY || iy > gridY + gridH) continue;
            boolean hv = in && Widget.hit(mx, my, ix, iy, cw, CELL_H);
            if (hv) hov = it;
            boolean worn = Wardrobe.worn(it) && Wardrobe.trying() != it;
            d.glass(ix, iy, cw, CELL_H, 12, hv ? Theme.GLASS_ACTIVE : Theme.GLASS_CARD, hv ? 1.3f : 1f);
            if (worn) d.rim(ix, iy, cw, CELL_H, 12, Colors.withAlpha(Theme.accent(), 0xC0), Colors.withAlpha(Theme.accent(), 0x40));
            // образец: светящийся кружок цвета вещи
            d.shadow(ix + cw / 2 - 14, iy + 8, 28, 28, 14, 10f, Colors.withAlpha(it.color(), 0x70));
            d.disc(ix + cw / 2f, iy + 22, 11f, it.color());
            d.icon(icon(it), ix + cw / 2 - 5, iy + 17, 10, Wardrobe.tone(it.color()) == 2 ? 0xFF202028 : 0xFFFFFFFF);
            d.textCenter(Draw.ellipsize(it.name(), cw - 10), ix + cw / 2, iy + 42, Theme.TEXT);
            d.textCenter(worn ? "Надето" : it.category(), ix + cw / 2, iy + 56, worn ? Theme.accent() : Theme.TEXT_MUTED);
            hits.add(new Object[]{ix, iy, cw, CELL_H, it});
        }
        d.unclip();
        // примерка: наведение держит вещь, уход мыши — всё как было
        if (hov != hovered) {
            hovered = hov;
            Wardrobe.tryOn(hov);
        }
    }

    private static int icon(Wardrobe.Item it) {
        return switch (it.category()) {
            case "Крылья", "Плащи" -> Icons.CAPE;
            case "Головные уборы" -> Icons.HAT;
            case "Ауры" -> Icons.SPARKLE;
            default -> Icons.PAW;
        };
    }

    private int catRow(Draw d, double mx, double my, int x, int y, String cat, String label, int count) {
        boolean on = cat == null ? category == null : cat.equals(category);
        boolean hv = Widget.hit(mx, my, x + 6, y, CAT_W - 12, 24);
        if (on) d.glass(x + 6, y, CAT_W - 12, 24, 10, Colors.withAlpha(Theme.accent(), Theme.isLight() ? 0x50 : 0x30), 1.2f);
        else if (hv) d.round(x + 6, y, CAT_W - 12, 24, 10, Theme.HOVER);
        d.text(label, on ? Fonts.Kind.BOLD : Fonts.Kind.TEXT, x + 14, Draw.textY(y, 24), on ? Theme.TEXT : Theme.TEXT_DIM);
        d.textRight(Integer.toString(count), x + CAT_W - 14, Draw.textY(y, 24), Theme.TEXT_MUTED);
        hits.add(new Object[]{x + 6, y, CAT_W - 12, 24, cat == null ? "cat:" : "cat:" + cat});
        return y + 26;
    }

    @Override
    public boolean click(double mx, double my, int button) {
        if (button != 0) return true;
        for (Object[] h : hits) {
            if (!Widget.hit(mx, my, (int) h[0], (int) h[1], (int) h[2], (int) h[3])) continue;
            Object a = h[4];
            if (a instanceof Wardrobe.Item it) {
                Wardrobe.toggle(it);
                hovered = null; // следующая отрисовка снова начнёт примерку уже от нового состояния
            } else if (a.equals("modules")) {
                Wardrobe.endTryOn();
                showModules.run();
            } else if (a instanceof String s && s.startsWith("cat:")) {
                category = s.length() == 4 ? null : s.substring(4);
                target = 0;
            } else if (a instanceof String s && s.startsWith("tone")) {
                tone = Integer.parseInt(s.substring(4));
                target = 0;
            } else if (a.equals("rot0")) preview.setYaw(preview.yaw() - 45);
            else if (a.equals("rot2")) preview.setYaw(preview.yaw() + 45);
            else if (a.equals("rot1")) autoSpin = !autoSpin;
            return true;
        }
        if (Widget.hit(mx, my, showX, showY, showW, showH)) {
            drag = true;
            autoSpin = false;
            preview.dragStart(mx);
        }
        return true;
    }

    @Override
    public void drag(double mx, double my) {
        if (drag) preview.drag(mx);
    }

    @Override
    public void release(double mx, double my) {
        if (drag) {
            drag = false;
            preview.dragEnd();
        }
    }

    @Override
    public boolean scroll(double mx, double my, double amount) {
        target = Math.max(0, Math.min(maxScroll, target - (float) amount * 30));
        return true;
    }

    /** Окно закрыли или ушли с вкладки — снять примерку. */
    public void left() {
        hovered = null;
        Wardrobe.endTryOn();
    }
}

package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.HudLayer;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.render.HudKit;
import dev.mraz1visual.render.HudPos;
import dev.mraz1visual.setting.BoolSetting;
import dev.mraz1visual.setting.ColorSetting;
import dev.mraz1visual.ui.Fonts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Основа текстовых элементов HUD (FPS, пинг, часы…): строки «подпись значение», карточка, место из редактора HUD.
 *
 * Кэш: наследник в {@link #update} меняет строки только когда изменились данные (новый объект строки),
 * ширина карточки пересчитывается только тогда же — в обычном кадре ни аллокаций, ни замеров текста.
 */
public abstract class TextHud extends Module implements HudLayer {
    private static final int PAD = 5, ROW = 11, MAX = 4;

    protected final HudPos pos;
    final BoolSetting background = add(new BoolSetting("bg", "Фон-карточка", true).group("Вид"));
    final BoolSetting shadow = add(new BoolSetting("shadow", "Тень текста", true).group("Вид"));
    final BoolSetting showLabel = add(new BoolSetting("label", "Подпись", true).group("Вид"));
    final ColorSetting labelColor = add(new ColorSetting("label_color", "Цвет подписи", 0xB4A0A0B8).group("Вид"));
    final ColorSetting valueColor = add(new ColorSetting("value_color", "Цвет значения", 0xFFE9E9F1).group("Вид"));

    /** Строки элемента. Наследник пишет сюда в update(); null в labels — строка без подписи. */
    protected final String[] labels = new String[MAX], values = new String[MAX];
    protected int lines = 1;

    private final String[] drawnL = new String[MAX], drawnV = new String[MAX];
    private final int[] labelW = new int[MAX];
    private int drawnLines = -1, width, valueX;
    private boolean drawnShowLabel;

    protected TextHud(String id, String name, String description, double fx, double fy) {
        super(id, name, description, Category.HUD);
        pos = hudPos(fx, fy);
        java.util.Arrays.fill(labels, "");
        java.util.Arrays.fill(values, "");
    }

    /** Обновить строки. Зовётся каждый кадр — новые строки создавать только при изменении данных. */
    protected abstract void update(Minecraft mc);

    /** false — элемент сейчас нечего показывать (нет сервера, нет предметов…). */
    protected boolean visibleNow() { return true; }

    @Override
    public void renderHud(GuiGraphics g, float pt) {
        Minecraft mc = Minecraft.getInstance();
        update(mc);
        if (!visibleNow()) return;
        if (changed()) measure();
        int h = lines * ROW + PAD * 2 - 2;
        pos.begin(g, width, h);
        if (background.get()) HudKit.card(g, 0, 0, width, h, 1f);
        boolean sh = shadow.get(), lab = showLabel.get();
        for (int i = 0; i < lines; i++) {
            int y = PAD + i * ROW;
            if (lab && !labels[i].isEmpty()) draw(g, labels[i], Fonts.Kind.TEXT, PAD, y, labelColor.get(), sh);
            draw(g, values[i], Fonts.Kind.BOLD, lab && !labels[i].isEmpty() ? valueX : PAD, y, valueColor.get(), sh);
        }
        pos.end(g);
    }

    private static void draw(GuiGraphics g, String s, Fonts.Kind k, int x, int y, int c, boolean shadow) {
        if (shadow) Fonts.drawShadow(g, s, k, x, y, c);
        else Fonts.draw(g, s, k, x, y, c);
    }

    private boolean changed() {
        if (lines != drawnLines || showLabel.get() != drawnShowLabel) return true;
        for (int i = 0; i < lines; i++) if (labels[i] != drawnL[i] || values[i] != drawnV[i]) return true;
        return false;
    }

    private void measure() {
        drawnLines = lines;
        drawnShowLabel = showLabel.get();
        int lw = 0, vw = 0, plain = 0;
        for (int i = 0; i < lines; i++) {
            drawnL[i] = labels[i];
            drawnV[i] = values[i];
            int v = Fonts.width(values[i], Fonts.Kind.BOLD);
            if (drawnShowLabel && !labels[i].isEmpty()) {
                labelW[i] = Fonts.width(labels[i], Fonts.Kind.TEXT);
                lw = Math.max(lw, labelW[i]);
                vw = Math.max(vw, v);
            } else plain = Math.max(plain, v);
        }
        valueX = PAD + (lw > 0 ? lw + 5 : 0);
        width = Math.max(valueX + vw, PAD + plain) + PAD;
    }

    /** Кэш чисел → строк: одинаковое число не создаёт новую строку. */
    protected static final class IntText {
        private int last = Integer.MIN_VALUE;
        private String text = "";
        private final String suffix;

        public IntText(String suffix) { this.suffix = suffix; }

        public String of(int v) {
            if (v != last) {
                last = v;
                text = suffix.isEmpty() ? Integer.toString(v) : v + suffix;
            }
            return text;
        }
    }
}

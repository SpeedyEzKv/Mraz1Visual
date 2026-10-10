package dev.umbra.ui.menu;

import dev.umbra.ui.Anim;
import dev.umbra.ui.Colors;
import dev.umbra.ui.Draw;
import dev.umbra.ui.Fonts;
import dev.umbra.ui.Theme;
import dev.umbra.ui.widget.BindWidget;
import dev.umbra.ui.widget.Host;
import dev.umbra.ui.widget.Popup;
import dev.umbra.ui.widget.SettingWidget;
import dev.umbra.ui.widget.TextInput;
import dev.umbra.ui.widget.Widget;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Форма в стиле меню: подпись слева, поле/переключатель справа, кнопка-текст внизу справа.
 * Используется в «Добавлении сервера», «Мире в локальной сети».
 */
final class MenuForm implements Host {
    static final int ROW_H = 24;

    private final List<Object> rows = new ArrayList<>(); // SettingWidget или Field
    private record Field(String label, TextInput input) {}

    int x, y, w;
    private int labelW = 120;
    private String submit = "";
    private float submitHover;
    private int submitX, submitY, submitW;
    String error = "";

    void add(SettingWidget w) { rows.add(w); }

    TextInput field(String label, String placeholder, int max) {
        TextInput in = new TextInput(placeholder, max);
        in.underline = true;
        rows.add(new Field(label, in));
        return in;
    }

    void submit(String text) { this.submit = text; }

    void layout(int x, int y, int w) {
        this.x = x;
        this.y = y;
        this.w = w;
        labelW = Math.min(150, w / 2);
        int ry = y;
        for (Object r : rows) {
            if (r instanceof SettingWidget sw) sw.setBounds(x, ry, w, ROW_H);
            else if (r instanceof Field f) f.input.setBounds(x + labelW, ry + 3, w - labelW, ROW_H - 6);
            ry += ROW_H;
        }
    }

    void render(Draw d, double mx, double my, float dt, float fade, int submitRight, int submitBottom) {
        int ry = y;
        for (Object r : rows) {
            if (r instanceof SettingWidget sw) sw.render(d, mx, my, dt);
            else if (r instanceof Field f) {
                d.text(f.label, x, Draw.textY(ry, ROW_H), Colors.mulAlpha(Theme.TEXT_DIM, fade));
                f.input.render(d, mx, my, dt);
            }
            ry += ROW_H;
        }
        if (!submit.isEmpty()) {
            submitW = Fonts.width(submit, Fonts.Kind.HEAD);
            submitX = submitRight - submitW;
            submitY = submitBottom;
            boolean hov = Widget.hit(mx, my, submitX - 6, submitY - 5, submitW + 12, 18);
            submitHover = Anim.to(submitHover, hov ? 1 : 0, dt);
            d.text(submit, Fonts.Kind.HEAD, submitX, submitY, Colors.mulAlpha(Colors.lerp(Theme.TEXT_DIM, Theme.TEXT, submitHover), fade));
            if (!error.isEmpty()) d.textRight(error, submitX - 16, submitY, Colors.mulAlpha(0xFFE08A8A, fade));
        }
    }

    /** @return 1 — клик по кнопке отправки, 0 — обработан, -1 — мимо. */
    int click(double mx, double my, int button) {
        if (!submit.isEmpty() && Widget.hit(mx, my, submitX - 6, submitY - 5, submitW + 12, 18)) return 1;
        boolean any = false;
        for (Object r : rows) {
            if (r instanceof SettingWidget sw) {
                if (sw.mouseClicked(mx, my, button)) any = true;
            } else if (r instanceof Field f) {
                if (f.input.mouseClicked(mx, my, button)) any = true;
            }
        }
        return any ? 0 : -1;
    }

    boolean key(int key, boolean ctrl, Supplier<String> clip) {
        for (Object r : rows) if (r instanceof Field f && f.input.keyPressed(key, ctrl, clip)) return true;
        return false;
    }

    boolean chr(int cp) {
        for (Object r : rows) if (r instanceof Field f && f.input.charTyped(cp)) return true;
        return false;
    }

    /** Tab — к следующему полю. */
    void nextField() {
        List<TextInput> ins = new ArrayList<>();
        for (Object r : rows) if (r instanceof Field f) ins.add(f.input);
        if (ins.isEmpty()) return;
        int cur = -1;
        for (int i = 0; i < ins.size(); i++) if (ins.get(i).focused) cur = i;
        for (TextInput in : ins) in.focused = false;
        ins.get((cur + 1) % ins.size()).focused = true;
    }

    @Override public void openPopup(Popup popup) {}
    @Override public void startListening(BindWidget widget) {}
    @Override public float scrollOf(Widget w) { return 0; }
}

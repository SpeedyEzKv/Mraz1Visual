package dev.umbra.ui.widget;

import dev.umbra.ui.Anim;
import dev.umbra.ui.Colors;
import dev.umbra.ui.Draw;
import dev.umbra.ui.Fonts;
import dev.umbra.ui.Theme;
import org.lwjgl.glfw.GLFW;

/** Однострочное поле ввода. Ctrl+V — вставка, Backspace — удаление. */
public final class TextInput extends Widget {
    private final StringBuilder sb = new StringBuilder();
    private final String placeholder;
    private final int maxLen;
    private String view = "";
    public boolean focused;
    private float hover;

    public TextInput(String placeholder, int maxLen) {
        this.placeholder = placeholder;
        this.maxLen = maxLen;
    }

    public String value() { return view; }

    public void set(String s) {
        sb.setLength(0);
        sb.append(s, 0, Math.min(s.length(), maxLen));
        view = sb.toString();
    }

    @Override
    public void render(Draw d, double mx, double my, float dt) {
        hover = Anim.to(hover, hit(mx, my) || focused ? 1 : 0, dt);
        d.card(x, y, w, h, Theme.RADIUS_SM, Colors.lerp(Theme.FIELD, Theme.FIELD_HOVER, hover),
                focused ? Theme.accentSoft() : Theme.CARD_EDGE);
        int ty = Draw.textY(y, h);
        String shown = view.isEmpty() ? placeholder : view;
        // длинный текст показываем с конца
        int maxW = w - 14;
        if (!view.isEmpty() && d.width(shown) > maxW) {
            int start = 0;
            while (start < shown.length() && d.width(shown.substring(start)) > maxW) start++;
            shown = shown.substring(start);
        }
        d.text(shown, x + 7, ty, view.isEmpty() ? Theme.TEXT_MUTED : Theme.TEXT);
        if (focused && (System.currentTimeMillis() / 500 & 1) == 0) {
            int cx = x + 7 + (view.isEmpty() ? 0 : Fonts.width(shown)) + 1;
            d.rect(cx, y + (h - 9) / 2, 1, 9, Theme.TEXT);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        focused = hit(mx, my);
        return focused;
    }

    public boolean charTyped(int cp) {
        if (!focused || Character.isISOControl(cp)) return false;
        if (sb.length() < maxLen) {
            sb.appendCodePoint(cp);
            view = sb.toString();
        }
        return true;
    }

    /** @param clipboard текст буфера обмена (запрашивается только при Ctrl+V). */
    public boolean keyPressed(int key, boolean ctrl, java.util.function.Supplier<String> clipboard) {
        if (!focused) return false;
        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            int len = sb.length();
            if (len > 0) {
                if (ctrl) sb.setLength(0);
                else sb.setLength(len - Character.charCount(sb.codePointBefore(len)));
                view = sb.toString();
            }
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_V) {
            String clip = clipboard.get();
            if (clip != null) {
                for (int i = 0; i < clip.length() && sb.length() < maxLen; i++) {
                    char c = clip.charAt(i);
                    if (!Character.isISOControl(c)) sb.append(c);
                }
                view = sb.toString();
            }
            return true;
        }
        return false;
    }
}

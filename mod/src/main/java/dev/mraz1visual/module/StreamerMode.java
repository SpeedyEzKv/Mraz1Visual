package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.setting.BoolSetting;
import dev.mraz1visual.setting.EnumSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Режим стримера: твой ник на экране заменяется (чат, таб, ники, скорборд).
 * Подмена только в отрисовке текста у тебя — серверу ничего не уходит.
 */
public final class StreamerMode extends Module {
    private static StreamerMode instance;
    final EnumSetting alias = add(new EnumSetting("alias", "Вместо ника", 0, "player", "Игрок", "you", "Ты", "mraz1visual", "Mraz1Visual", "stars", "*****"));
    final BoolSetting hideCoords = add(new BoolSetting("hide_coords", "Скрыть координаты в Interface", true));

    private static volatile String name = "";
    private static volatile String replacement = "Игрок";

    public StreamerMode() {
        super("streamer_mode", "Streamer Mode", "Прячет твой ник на экране — для стримов и скриншотов.", Category.MISC);
        instance = this;
        bind.set(GLFW.GLFW_KEY_F8);
    }

    public static boolean hidesCoords() {
        return instance != null && instance.isEnabled() && instance.hideCoords.get();
    }

    @Override
    public void onTick(Minecraft mc) {
        name = mc.getUser() == null ? "" : mc.getUser().getName();
        replacement = alias.label();
    }

    @Override
    protected void onEnable() { onTick(Minecraft.getInstance()); }

    @Override
    protected void onDisable() { name = ""; }

    private static boolean active() {
        return instance != null && instance.isEnabled() && name.length() >= 3;
    }

    public static String filter(String s) {
        if (!active() || s == null || s.isEmpty()) return s;
        int at = indexOfIgnoreCase(s, name, 0);
        if (at < 0) return s;
        StringBuilder out = new StringBuilder();
        int from = 0;
        while (at >= 0) {
            out.append(s, from, at).append(replacement);
            from = at + name.length();
            at = indexOfIgnoreCase(s, name, from);
        }
        return out.append(s, from, s.length()).toString();
    }

    public static FormattedCharSequence filter(FormattedCharSequence seq) {
        if (!active() || seq == null) return seq;
        StringBuilder sb = new StringBuilder();
        List<Style> styles = new ArrayList<>();
        seq.accept((index, style, cp) -> {
            sb.appendCodePoint(cp);
            for (int i = Character.charCount(cp); i > 0; i--) styles.add(style);
            return true;
        });
        String s = sb.toString();
        if (indexOfIgnoreCase(s, name, 0) < 0) return seq;
        String rep = replacement, nm = name;
        return sink -> {
            int i = 0, out = 0;
            while (i < s.length()) {
                if (s.regionMatches(true, i, nm, 0, nm.length())) {
                    Style st = styles.get(i);
                    for (int j = 0; j < rep.length(); ) {
                        int cp = rep.codePointAt(j);
                        if (!sink.accept(out++, st, cp)) return false;
                        j += Character.charCount(cp);
                    }
                    i += nm.length();
                    continue;
                }
                int cp = s.codePointAt(i);
                if (!sink.accept(out++, styles.get(i), cp)) return false;
                i += Character.charCount(cp);
            }
            return true;
        };
    }

    private static int indexOfIgnoreCase(String s, String needle, int from) {
        int n = needle.length();
        for (int i = from; i + n <= s.length(); i++) {
            if (s.regionMatches(true, i, needle, 0, n)) return i;
        }
        return -1;
    }

    @SuppressWarnings("unused")
    private static String low(String s) { return s.toLowerCase(Locale.ROOT); }
}

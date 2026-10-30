package dev.mraz1visual.module;

import com.mojang.blaze3d.platform.InputConstants;
import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.setting.BoolSetting;
import dev.mraz1visual.setting.EnumSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Режим стримера: твой ник на экране заменяется (чат, таб, ники, скорборд).
 * Подмена только в отрисовке текста у тебя — серверу ничего не уходит.
 */
public final class StreamerMode extends Module {
    private static StreamerMode instance;
    final EnumSetting alias = add(new EnumSetting("alias", "Вместо ника", 0, "player", "Игрок", "you", "Ты", "mraz1visual", "Mraz1Visual", "stars", "*****", "custom", "Свой ник"));
    final dev.mraz1visual.setting.TextSetting customName = add(new dev.mraz1visual.setting.TextSetting("custom_name", "Свой ник", "Player", 16));
    final dev.mraz1visual.setting.ActionSetting editName = add(new dev.mraz1visual.setting.ActionSetting("edit_name", "Свой ник", "Изменить ›", this::editName));
    final BoolSetting hideCoords = add(new BoolSetting("hide_coords", "Скрыть координаты в Interface", true));

    private static volatile String name = "";
    private static volatile String replacement = "Игрок";

    public StreamerMode() {
        super("streamer_mode", "NameProtect", "Заменяет твой ник везде на экране: чат, таб, ники, скорборд. Можно свой ник.", Category.MISC);
        instance = this;
        bind.set(InputConstants.KEY_F8);
    }

    public static boolean hidesCoords() {
        return instance != null && instance.isEnabled() && instance.hideCoords.get();
    }

    @Override
    public void onTick(Minecraft mc) {
        name = mc.getUser() == null ? "" : mc.getUser().getName();
        replacement = alias.getId().equals("custom") ? (customName.get().isEmpty() ? "Игрок" : customName.get()) : alias.label();
    }

    private void editName() {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new dev.mraz1visual.ui.TextEditScreen(mc.screen, customName));
    }

    @Override
    protected void onEnable() { onTick(Minecraft.getInstance()); }

    @Override
    protected void onDisable() { name = ""; }

    private static boolean active() {
        return instance != null && instance.isEnabled() && name.length() >= 3;
    }

    public static String filter(String s) {
        if (s == null || s.isEmpty()) return s;
        if (active()) s = replaceName(s);
        if (HideAnarchy.active() && s.length() > 3) {
            java.util.regex.Matcher m = HideAnarchy.matcher(s);
            if (m.find()) s = m.replaceAll("$1$2•••");
        }
        return s;
    }

    private static String replaceName(String s) {
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

    /**
     * Подмена в стилизованном тексте (чат, таб, скорборд): ник → псевдоним, номер анархии → «•••».
     * Цвета и стили остаются как были — у каждого заменённого куска стиль его первой буквы.
     */
    public static FormattedCharSequence filter(FormattedCharSequence seq) {
        boolean nameOn = active(), anarchyOn = HideAnarchy.active();
        if (!nameOn && !anarchyOn || seq == null) return seq;
        StringBuilder sb = new StringBuilder();
        List<Style> styles = new ArrayList<>();
        seq.accept((index, style, cp) -> {
            sb.appendCodePoint(cp);
            for (int i = Character.charCount(cp); i > 0; i--) styles.add(style);
            return true;
        });
        String s = sb.toString();
        // куски на замену: [начало, конец) → строка
        List<int[]> ranges = new ArrayList<>();
        List<String> reps = new ArrayList<>();
        if (nameOn) {
            for (int at = indexOfIgnoreCase(s, name, 0); at >= 0; at = indexOfIgnoreCase(s, name, at + name.length())) {
                ranges.add(new int[]{at, at + name.length()});
                reps.add(replacement);
            }
        }
        if (anarchyOn) {
            java.util.regex.Matcher m = HideAnarchy.matcher(s);
            while (m.find()) {
                ranges.add(new int[]{m.start(3), m.end(3)});
                reps.add("•••");
            }
        }
        if (ranges.isEmpty()) return seq;
        Integer[] order = new Integer[ranges.size()];
        for (int i = 0; i < order.length; i++) order[i] = i;
        java.util.Arrays.sort(order, (a, b) -> Integer.compare(ranges.get(a)[0], ranges.get(b)[0]));
        return sink -> {
            int i = 0, out = 0, r = 0;
            while (i < s.length()) {
                while (r < order.length && ranges.get(order[r])[0] < i) r++;
                if (r < order.length && ranges.get(order[r])[0] == i) {
                    int[] rg = ranges.get(order[r]);
                    String rep = reps.get(order[r]);
                    Style st = styles.get(i);
                    for (int j = 0; j < rep.length(); ) {
                        int cp = rep.codePointAt(j);
                        if (!sink.accept(out++, st, cp)) return false;
                        j += Character.charCount(cp);
                    }
                    i = Math.max(i + 1, rg[1]);
                    r++;
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

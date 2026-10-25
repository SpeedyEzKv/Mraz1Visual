package dev.mraz1visual.ui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Строки интерфейса клиента: assets/mraz1visual/lang/&lt;язык&gt;.json (те же файлы, что читает игра).
 * Язык клиента свой — не зависит от языка игры; нет строки в выбранном языке — берётся русская, нет и её — ключ.
 * Новый язык = новый файл рядом с ru_ru.json и вариант в настройках клиента.
 */
public final class Lang {
    private Lang() {}

    public static final String DEFAULT = "ru_ru";
    private static final Map<String, Map<String, String>> cache = new HashMap<>();
    private static String current = DEFAULT;

    public static void setLanguage(String code) {
        current = code == null || code.isEmpty() ? DEFAULT : code;
    }

    public static String language() { return current; }

    public static String get(String key) {
        String v = table(current).get(key);
        if (v == null && !current.equals(DEFAULT)) v = table(DEFAULT).get(key);
        return v == null ? key : v;
    }

    /** Строка с подстановкой %s по порядку. */
    public static String get(String key, Object... args) {
        String s = get(key);
        StringBuilder sb = new StringBuilder(s.length() + 16);
        int a = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '%' && i + 1 < s.length() && s.charAt(i + 1) == 's' && a < args.length) {
                sb.append(args[a++]);
                i++;
            } else if (c == '%' && i + 1 < s.length() && s.charAt(i + 1) == '%') {
                sb.append('%');
                i++;
            } else sb.append(c);
        }
        return sb.toString();
    }

    /** Многострочная строка (переносы — \n в файле). */
    public static String[] lines(String key) {
        return get(key).split("\n");
    }

    private static Map<String, String> table(String code) {
        return cache.computeIfAbsent(code, Lang::load);
    }

    private static Map<String, String> load(String code) {
        Map<String, String> m = new HashMap<>();
        try (InputStream in = Lang.class.getResourceAsStream("/assets/mraz1visual/lang/" + code + ".json")) {
            if (in == null) return m;
            JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (var e : o.entrySet()) if (e.getValue().isJsonPrimitive()) m.put(e.getKey(), e.getValue().getAsString());
        } catch (Exception ignored) {
            // битый файл языка — интерфейс на ключах/русском, игра не падает
        }
        return m;
    }
}

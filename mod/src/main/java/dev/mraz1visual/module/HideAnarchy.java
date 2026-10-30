package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * FunPuk: номер анархии в табе и скорборде заменяется на «•••» — у тебя на экране (для скриншотов и стримов).
 * Подмена в отрисовке текста, на сервер ничего не уходит.
 */
public final class HideAnarchy extends Module {
    private static HideAnarchy instance;
    /** «Анархия #228», «анархии-228», «Anarchy: 228». */
    static final Pattern NUMBER = Pattern.compile("(?iu)(анархи[яиюейи]?|anarchy)(\\s*[#№:\\-]?\\s*)(\\d+)");

    public HideAnarchy() {
        super("hide_anarchy", "HideAnarchy", "Прячет номер анархии в табе и скорборде (FunTime).", Category.FUNPUK);
        instance = this;
        markNew();
    }

    public static boolean active() {
        return instance != null && instance.isEnabled();
    }

    /** Найти номер анархии в строке; m — переиспользуемый матчер. */
    static Matcher matcher(String s) {
        return NUMBER.matcher(s);
    }
}

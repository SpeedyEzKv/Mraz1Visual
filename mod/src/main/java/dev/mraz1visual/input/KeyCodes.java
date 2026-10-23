package dev.mraz1visual.input;

/**
 * Коды клавиш в конфиге — всегда GLFW (как в 1.21.11–26.2): конфиг общий для всех версий игры.
 * В 26.3 ввод перешёл на SDL3 и коды другие (сканкоды) — здесь перевод туда и обратно по именам клавиш Minecraft.
 */
public final class KeyCodes {
    private KeyCodes() {}

    /** GLFW-код → имя клавиши Minecraft (key.keyboard.*). */
    private static final Object[][] TABLE = buildTable();

    private static Object[][] buildTable() {
        java.util.List<Object[]> t = new java.util.ArrayList<>();
        for (int c = 'A'; c <= 'Z'; c++) t.add(new Object[]{c, String.valueOf((char) (c + 32))});
        for (int c = '0'; c <= '9'; c++) t.add(new Object[]{c, String.valueOf((char) c)});
        for (int i = 1; i <= 25; i++) t.add(new Object[]{289 + i, "f" + i});
        for (int i = 0; i <= 9; i++) t.add(new Object[]{320 + i, "keypad." + i});
        Object[][] named = {
                {330, "keypad.decimal"}, {331, "keypad.divide"}, {332, "keypad.multiply"}, {333, "keypad.subtract"},
                {334, "keypad.add"}, {335, "keypad.enter"}, {336, "keypad.equal"},
                {32, "space"}, {39, "apostrophe"}, {44, "comma"}, {45, "minus"}, {46, "period"}, {47, "slash"},
                {59, "semicolon"}, {61, "equal"}, {91, "left.bracket"}, {92, "backslash"}, {93, "right.bracket"},
                {96, "grave.accent"}, {161, "world.1"}, {162, "world.2"},
                {256, "escape"}, {257, "enter"}, {258, "tab"}, {259, "backspace"}, {260, "insert"}, {261, "delete"},
                {262, "right"}, {263, "left"}, {264, "down"}, {265, "up"}, {266, "page.up"}, {267, "page.down"},
                {268, "home"}, {269, "end"}, {280, "caps.lock"}, {281, "scroll.lock"}, {282, "num.lock"},
                {283, "print.screen"}, {284, "pause"}, {340, "left.shift"}, {341, "left.control"}, {342, "left.alt"},
                {343, "left.win"}, {344, "right.shift"}, {345, "right.control"}, {346, "right.alt"}, {347, "right.win"},
                {348, "menu"}};
        java.util.Collections.addAll(t, named);
        return t.toArray(new Object[0][]);
    }

    //? if >=26.3 {
    /*private static java.util.Map<Integer, Integer> toNative, toGlfw;

    private static void ensure() {
        if (toNative != null) return;
        java.util.Map<Integer, Integer> a = new java.util.HashMap<>(), b = new java.util.HashMap<>();
        for (Object[] e : TABLE) {
            try {
                var key = com.mojang.blaze3d.platform.InputConstants.getKey("key.keyboard." + e[1]);
                if (key == null || key == com.mojang.blaze3d.platform.InputConstants.UNKNOWN) continue;
                a.put((Integer) e[0], key.getValue());
                b.put(key.getValue(), (Integer) e[0]);
            } catch (Throwable ignored) {
                // клавиши нет в этой версии
            }
        }
        toGlfw = b;
        toNative = a;
    }
    *///?}

    /** Код из конфига (GLFW) → код этой версии игры. */
    public static int fromConfig(int glfw) {
        if (glfw < 0) return glfw;
        //? if >=26.3 {
        /*ensure();
        return toNative.getOrDefault(glfw, -1);
        *///?} else {
        return glfw;
        //?}
    }

    /** Код этой версии игры → код для конфига (GLFW). */
    public static int toConfig(int key) {
        if (key < 0) return key;
        //? if >=26.3 {
        /*ensure();
        return toGlfw.getOrDefault(key, -1);
        *///?} else {
        return key;
        //?}
    }

    /** Для тестов и отладки: таблица GLFW-кодов и имён. */
    static int size() {
        return TABLE.length;
    }
}

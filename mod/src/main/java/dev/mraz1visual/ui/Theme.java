package dev.mraz1visual.ui;

/**
 * Дизайн-токены. Все цвета, размеры и отступы интерфейса — только отсюда.
 * Цвета визуалов сюда не попадают: они живут в настройках модулей.
 */
public final class Theme {
    private Theme() {}

    // ── цвета (ARGB) ─────────────────────────────────────────
    // Нейтральная палитра (как Tailwind neutral): чёрный/белый, без цветного оттенка.
    // Два режима — тёмный и светлый; GUI включает светлый только на время своей отрисовки.
    public static final int DEFAULT_ACCENT = 0xFFFAFAFA;

    public static int WINDOW, WINDOW_EDGE, SHADOW, DIVIDER, HOVER, SELECTED, CARD, CARD_EDGE, FIELD, FIELD_HOVER,
            POPUP, TRACK, KNOB_OFF, KNOB_ON, TEXT, TEXT_DIM, TEXT_MUTED, SURFACE, ELEVATED,
            GLASS_WINDOW, GLASS_PANEL, GLASS_CARD, GLASS_ACTIVE;
    private static boolean light;

    static {
        dark();
    }

    public static boolean isLight() { return light; }

    public static void dark() {
        light = false;
        WINDOW = 0xF50A0A0A;
        SURFACE = 0xF2111111;
        ELEVATED = 0xFF262626;
        WINDOW_EDGE = 0xFF262626;
        SHADOW = 0x66000000;
        DIVIDER = 0xFF1F1F1F;
        HOVER = 0x0DFFFFFF;
        SELECTED = 0xFF222222;
        CARD = 0xF0141414;
        CARD_EDGE = 0xFF242424;
        FIELD = 0xFF1A1A1A;
        FIELD_HOVER = 0xFF262626;
        POPUP = 0xF2101012;
        TRACK = 0xFF2E2E2E;
        KNOB_OFF = 0xFFA1A1A1;
        KNOB_ON = 0xFF0A0A0A;
        TEXT = 0xFFFAFAFA;
        TEXT_DIM = 0xFFA1A1A1;
        TEXT_MUTED = 0xFF737373;
        GLASS_WINDOW = 0x8C0A0A0C;
        GLASS_PANEL = 0x8C0C0C0E;
        GLASS_CARD = 0x8C101012;
        GLASS_ACTIVE = 0x1FFFFFFF;
    }

    public static void light() {
        light = true;
        WINDOW = 0xF7FFFFFF;
        SURFACE = 0xF5FAFAFA;
        ELEVATED = 0xFFE5E5E5;
        WINDOW_EDGE = 0xFFE5E5E5;
        SHADOW = 0x30000000;
        DIVIDER = 0xFFEBEBEB;
        HOVER = 0x0A000000;
        SELECTED = 0xFFEDEDED;
        CARD = 0xF5FFFFFF;
        CARD_EDGE = 0xFFE5E5E5;
        FIELD = 0xFFF5F5F5;
        FIELD_HOVER = 0xFFE5E5E5;
        POPUP = 0xFCFFFFFF;
        TRACK = 0xFFCFCFCF;
        KNOB_OFF = 0xFFFFFFFF;
        KNOB_ON = 0xFFFFFFFF;
        TEXT = 0xFF0A0A0A;
        TEXT_DIM = 0xFF525252;
        TEXT_MUTED = 0xFF8A8A8A;
        GLASS_WINDOW = 0xB8F4F4F5;
        GLASS_PANEL = 0x99FFFFFF;
        GLASS_CARD = 0xA6FFFFFF;
        GLASS_ACTIVE = 0xCCFFFFFF;
    }

    private static int accent = DEFAULT_ACCENT;
    private static int accentSoft = Colors.withAlpha(DEFAULT_ACCENT, 0x55);

    /** Акцент. Белый акцент в светлом режиме становится почти чёрным — иначе его не видно. */
    public static int accent() {
        if (light && (accent & 0xFFFFFF) >= 0xE0E0E0 && Colors.luma(accent) > 0.85f) return 0xFF171717;
        return accent;
    }

    public static int accentSoft() {
        return light ? Colors.withAlpha(accent(), 0x40) : accentSoft;
    }

    public static void setAccent(int argb) {
        accent = argb | 0xFF000000;
        accentSoft = Colors.withAlpha(accent, 0x55);
    }

    // ── размеры (в единицах окна; 1 единица = 1–3 физических пикселя) ──
    public static final int WINDOW_W   = 760;
    public static final int WINDOW_H   = 448;
    public static final int WINDOW_MIN_W = 480;
    public static final int WINDOW_MIN_H = 280;
    public static final int RADIUS     = 10;
    public static final int RADIUS_SM  = 4;

    public static final int TOPBAR_H   = 42;
    public static final int SIDEBAR_W  = 154;
    public static final int HEADER_H   = 44;
    public static final int BANNER_H   = 150;
    public static final int ROW_H      = 22;
    public static final int PAD        = 12;
    public static final int GAP        = 8;
    public static final int TAB_GAP    = 12;

    public static final int CARD_PAD     = 8;
    public static final int CARD_TITLE_H = 18;
    public static final int CARD_MIN_W   = 190;

    public static final int SWITCH_W   = 20;
    public static final int SWITCH_H   = 11;
    public static final int SLIDER_W   = 84;
    public static final int FIELD_H    = 14;
    public static final int SEARCH_W   = 150;

    // ── шрифт ────────────────────────────────────────────────
    public static final int FONT_H     = 8; // высота строчных глифов ванильного шрифта

    // ── анимации ─────────────────────────────────────────────
    public static final float ANIM_SPEED = 18f; // больше — быстрее
}

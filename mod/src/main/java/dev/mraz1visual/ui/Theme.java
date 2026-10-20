package dev.mraz1visual.ui;

/**
 * Дизайн-токены «Liquid Glass». Все цвета, размеры, скругления и отступы интерфейса — только отсюда.
 * Стекло: размытый мир за окном (GlassBlur) + полупрозрачная тонировка + блик сверху + кромка со светом сверху.
 * Цвета визуалов сюда не попадают: они живут в настройках модулей.
 */
public final class Theme {
    private Theme() {}

    // ── цвета (ARGB) ─────────────────────────────────────────
    // Нейтральная основа и ОДИН акцент (пресеты — в настройках клиента). Тёмная тема по умолчанию.
    public static final int DEFAULT_ACCENT = 0xFFFAFAFA;

    public static int WINDOW, WINDOW_EDGE, SHADOW, DIVIDER, HOVER, SELECTED, CARD, CARD_EDGE, FIELD, FIELD_HOVER,
            POPUP, TRACK, KNOB_OFF, KNOB_ON, TEXT, TEXT_DIM, TEXT_MUTED, SURFACE, ELEVATED,
            GLASS_WINDOW, GLASS_PANEL, GLASS_CARD, GLASS_ACTIVE, GLASS_RAIL;
    private static boolean light;

    static {
        dark();
    }

    public static boolean isLight() { return light; }

    public static void dark() {
        light = false;
        WINDOW = 0xF20C0C10;
        SURFACE = 0xF2111114;
        ELEVATED = 0xFF2A2A30;
        WINDOW_EDGE = 0xFF26262C;
        SHADOW = 0x59000000;
        DIVIDER = 0x14FFFFFF;
        HOVER = 0x0FFFFFFF;
        SELECTED = 0x24FFFFFF;
        CARD = 0xEC141418;
        CARD_EDGE = 0x26FFFFFF;
        FIELD = 0x1AFFFFFF;
        FIELD_HOVER = 0x26FFFFFF;
        POPUP = 0xEE141418;
        TRACK = 0x33FFFFFF;
        KNOB_OFF = 0xFFFFFFFF;
        KNOB_ON = 0xFFFFFFFF;
        TEXT = 0xFFF5F5F7;
        TEXT_DIM = 0xFFA9A9B2;   // подписи ~60%
        TEXT_MUTED = 0xFF75757E;
        // стекло: тёмная тонировка, сквозь которую виден размытый мир
        GLASS_WINDOW = 0x9E0E0E13;
        GLASS_PANEL = 0x1AFFFFFF;
        GLASS_CARD = 0x17FFFFFF;
        GLASS_ACTIVE = 0x2EFFFFFF;
        GLASS_RAIL = 0x14FFFFFF;
    }

    public static void light() {
        light = true;
        WINDOW = 0xF5FAFAFC;
        SURFACE = 0xF5F2F2F5;
        ELEVATED = 0xFFE3E3E8;
        WINDOW_EDGE = 0xFFE3E3E8;
        SHADOW = 0x2E000000;
        DIVIDER = 0x14000000;
        HOVER = 0x0C000000;
        SELECTED = 0x18000000;
        CARD = 0xF7FFFFFF;
        CARD_EDGE = 0x1F000000;
        FIELD = 0x0F000000;
        FIELD_HOVER = 0x1A000000;
        POPUP = 0xF7FFFFFF;
        TRACK = 0x26000000;
        KNOB_OFF = 0xFFFFFFFF;
        KNOB_ON = 0xFFFFFFFF;
        TEXT = 0xFF111114;
        TEXT_DIM = 0xFF5A5A63;
        TEXT_MUTED = 0xFF8E8E96;
        GLASS_WINDOW = 0xB8F6F6F9;
        GLASS_PANEL = 0x8CFFFFFF;
        GLASS_CARD = 0x99FFFFFF;
        GLASS_ACTIVE = 0xE6FFFFFF;
        GLASS_RAIL = 0x80FFFFFF;
    }

    private static int accent = 0xFF5AC8FA;
    private static int accentSoft = Colors.withAlpha(accent, 0x55);

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

    // ── размеры (в единицах окна; 1 единица = 1–3 физических пикселя), сетка 4/8 ──
    public static final int WINDOW_W   = 780;
    public static final int WINDOW_H   = 460;
    public static final int WINDOW_MIN_W = 480;
    public static final int WINDOW_MIN_H = 280;
    /** Скругления: окно ~24 px, карточки ~16 px (в единицах окна), кнопки и тумблеры — капсулы. */
    public static final int RADIUS     = 18;
    public static final int RADIUS_CARD = 12;
    public static final int RADIUS_SM  = 6;

    public static final int TOPBAR_H   = 52;
    /** Ширина колонки категорий: капсула 48 + поля по 12. */
    public static final int SIDEBAR_W  = 72;
    public static final int RAIL_W     = 48;
    public static final int HEADER_H   = 44;
    public static final int BANNER_H   = 150;
    public static final int ROW_H      = 24;
    public static final int PAD        = 12;
    public static final int GAP        = 8;
    public static final int TAB_GAP    = 12;

    public static final int CARD_PAD     = 8;
    public static final int CARD_TITLE_H = 18;
    public static final int CARD_MIN_W   = 190;

    public static final int SWITCH_W   = 26;
    public static final int SWITCH_H   = 15;
    public static final int SLIDER_W   = 92;
    public static final int FIELD_H    = 16;
    public static final int SEARCH_W   = 176;

    // ── шрифт ────────────────────────────────────────────────
    public static final int FONT_H     = 8; // высота строчных глифов ванильного шрифта

    // ── анимации ─────────────────────────────────────────────
    public static final float ANIM_SPEED = 18f; // больше — быстрее
}

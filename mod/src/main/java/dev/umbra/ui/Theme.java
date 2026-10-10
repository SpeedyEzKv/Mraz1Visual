package dev.umbra.ui;

/**
 * Дизайн-токены. Все цвета, размеры и отступы интерфейса — только отсюда.
 * Цвета визуалов сюда не попадают: они живут в настройках модулей.
 */
public final class Theme {
    private Theme() {}

    // ── цвета (ARGB) ─────────────────────────────────────────
    public static final int DEFAULT_ACCENT = 0xFFB8B4F2; // лавандовый

    public static final int WINDOW      = 0xF00A0A0E;
    public static final int WINDOW_EDGE = 0x1AFFFFFF;
    public static final int SHADOW      = 0x40000000;
    public static final int DIVIDER     = 0x12FFFFFF;
    public static final int HOVER       = 0x0AFFFFFF;
    public static final int SELECTED    = 0x16FFFFFF;
    public static final int CARD        = 0xFF0F0F15;
    public static final int CARD_EDGE   = 0x10FFFFFF;
    public static final int FIELD       = 0xFF17171F;
    public static final int FIELD_HOVER = 0xFF1D1D27;
    public static final int POPUP       = 0xFA121219;
    public static final int TRACK       = 0xFF25252F;
    public static final int KNOB_OFF    = 0xFF676775;
    public static final int KNOB_ON     = 0xFFF2F2F8;

    public static final int TEXT        = 0xFFE9E9F1;
    public static final int TEXT_DIM    = 0xFF9B9BAB;
    public static final int TEXT_MUTED  = 0xFF5E5E6D;

    private static int accent = DEFAULT_ACCENT;
    private static int accentSoft = Colors.withAlpha(DEFAULT_ACCENT, 0x55);

    public static int accent() { return accent; }
    public static int accentSoft() { return accentSoft; }

    public static void setAccent(int argb) {
        accent = argb | 0xFF000000;
        accentSoft = Colors.withAlpha(accent, 0x55);
    }

    // ── размеры (в единицах окна; 1 единица = 1–3 физических пикселя) ──
    public static final int WINDOW_W   = 720;
    public static final int WINDOW_H   = 380;
    public static final int WINDOW_MIN_W = 480;
    public static final int WINDOW_MIN_H = 280;
    public static final int RADIUS     = 8;
    public static final int RADIUS_SM  = 4;

    public static final int TOPBAR_H   = 34;
    public static final int SIDEBAR_W  = 168;
    public static final int HEADER_H   = 48;
    public static final int BANNER_H   = 150;
    public static final int ROW_H      = 20;
    public static final int PAD        = 12;
    public static final int GAP        = 8;
    public static final int TAB_GAP    = 12;

    public static final int CARD_PAD     = 8;
    public static final int CARD_TITLE_H = 18;
    public static final int CARD_MIN_W   = 190;

    public static final int SWITCH_W   = 20;
    public static final int SWITCH_H   = 10;
    public static final int SLIDER_W   = 84;
    public static final int FIELD_H    = 14;
    public static final int SEARCH_W   = 96;

    // ── шрифт ────────────────────────────────────────────────
    public static final int FONT_H     = 8; // высота строчных глифов ванильного шрифта

    // ── анимации ─────────────────────────────────────────────
    public static final float ANIM_SPEED = 18f; // больше — быстрее
}

package dev.mraz1visual.ui;

/** Заглушка вкладки, которая ещё в работе: значок, заголовок и пара строк о том, что будет. */
public final class SoonPanel implements Panel {
    private final String title, text;
    private final int icon;
    private float t;

    public SoonPanel(String title, String text, int icon) {
        this.title = title;
        this.text = text;
        this.icon = icon;
    }

    @Override
    public void render(Draw d, double mx, double my, float dt, int x, int y, int w, int h) {
        t += dt;
        int cx = x + w / 2, cy = y + h / 2 - 24;
        float p = 0.5f + 0.5f * (float) Math.sin(t * 2f);
        d.shadow(cx - 26, cy - 26, 52, 52, 26, 20f, Colors.withAlpha(Theme.accent(), Math.round(50 + 50 * p)));
        d.glass(cx - 22, cy - 22, 44, 44, 16, Colors.withAlpha(Theme.accent(), 0x40), 1.2f);
        d.icon(icon, cx - 9, cy - 9, 18, Theme.isLight() ? 0xFF111114 : 0xFFFFFFFF);
        d.textScaled(title, Fonts.Kind.HEAD, cx - Math.round(d.width(title, Fonts.Kind.HEAD) * 1.3f / 2), cy + 34, 1.3f, Theme.TEXT);
        int ty = cy + 54;
        for (String line : text.split("\n")) {
            d.textCenter(line, cx, ty, Theme.TEXT_DIM);
            ty += 12;
        }
        String soon = "Скоро";
        int bw = d.width(soon) + 22;
        d.round(cx - bw / 2, ty + 8, bw, 18, 9, Colors.withAlpha(Theme.accent(), 0x40));
        d.textCenter(soon, cx, Draw.textY(ty + 8, 18), Theme.TEXT);
    }
}

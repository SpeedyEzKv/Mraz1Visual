package dev.mraz1visual.music;

import dev.mraz1visual.render.Ease;
import dev.mraz1visual.ui.Colors;
import dev.mraz1visual.ui.Fonts;
import dev.mraz1visual.ui.Sdf;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;

/**
 * «Сейчас играет» в стиле Live Activity: стеклянная карточка с обложкой, бегущей строкой, полосой прогресса и временем,
 * фон — приглушённый цвет обложки; компактный режим — «остров» сверху с мини-эквалайзером. Смена трека — плавная
 * (старый текст и обложка тают, новые проявляются).
 */
public final class MusicWidget {
    public static final int W = 220, H = 64, IW = 170, IH = 24;

    private String key = "", title = "", artist = "", oldTitle = "", oldArtist = "";
    private float swap = 1, eqPhase;
    private long last;
    private int tint = 0xFF2A2A3C;

    private void update(float dt) {
        String k = NowPlaying.key();
        if (!k.equals(key)) {
            key = k;
            oldTitle = title;
            oldArtist = artist;
            title = NowPlaying.title().isBlank() ? "Ничего не играет" : NowPlaying.title();
            String a = NowPlaying.artist(), src = NowPlaying.source();
            artist = a.isBlank() ? src : (src.isBlank() ? a : a + " · " + src);
            swap = 0;
        }
        swap = Math.min(1, swap + dt / 0.35f);
        tint = Colors.lerp(tint, NowPlaying.coverTint, Math.min(1, dt * 4));
        if (NowPlaying.playing()) eqPhase += dt;
    }

    private float dt() {
        long now = System.nanoTime();
        float dt = last == 0 ? 0 : Math.min(0.05f, (now - last) / 1e9f);
        last = now;
        return dt;
    }

    /** Развёрнутая карточка. */
    public void renderCard(GuiGraphics g, float x, float y, float alpha) {
        update(dt());
        int a = Math.round(255 * alpha);
        Sdf.shape(g, x, y + 3, W, H, 16, Sdf.SHADOW, 14, Colors.withAlpha(0x000000, a / 3), Colors.withAlpha(0x000000, a / 3));
        int top = Colors.withAlpha(Colors.lerp(tint, 0xFF101016, 0.25f), Math.round(0xE0 * alpha));
        int bottom = Colors.withAlpha(Colors.lerp(tint, 0xFF08080C, 0.6f), Math.round(0xE8 * alpha));
        Sdf.shape(g, x, y, W, H, 16, Sdf.FILL, 0, top, bottom);
        Sdf.shape(g, x, y, W, H, 16, Sdf.RING, 1f, Colors.withAlpha(0xFFFFFF, Math.round(60 * alpha)), Colors.withAlpha(0xFFFFFF, Math.round(14 * alpha)));
        cover(g, x + 8, y + 8, 48, alpha);
        float tx = x + 64, tw = W - 72;
        float e = Ease.smooth(swap);
        if (e < 1) {
            marquee(g, oldTitle, Fonts.Kind.BOLD, tx, y + 11, tw, Colors.withAlpha(0xFFFFFF, Math.round(255 * alpha * (1 - e))));
            marquee(g, oldArtist, Fonts.Kind.TEXT, tx, y + 23, tw, Colors.withAlpha(0xC8C8D2, Math.round(255 * alpha * (1 - e))));
        }
        marquee(g, title, Fonts.Kind.BOLD, tx, y + 11 + (1 - e) * 4, tw, Colors.withAlpha(0xFFFFFF, Math.round(255 * alpha * e)));
        marquee(g, artist, Fonts.Kind.TEXT, tx, y + 23 + (1 - e) * 4, tw, Colors.withAlpha(0xC8C8D2, Math.round(255 * alpha * e)));
        // прогресс и время
        long dur = NowPlaying.duration(), pos = NowPlaying.position();
        float f = dur > 0 ? Math.min(1, pos / (float) dur) : 0;
        Sdf.fill(g, tx, y + 42, tw, 3, 1.5f, Colors.withAlpha(0xFFFFFF, Math.round(50 * alpha)));
        if (f > 0) Sdf.fill(g, tx, y + 42, Math.max(3, tw * f), 3, 1.5f, Colors.withAlpha(0xFFFFFF, Math.round(220 * alpha)));
        if (dur > 0) {
            int c = Colors.withAlpha(0xA8A8B4, Math.round(255 * alpha));
            Fonts.draw(g, time(pos), Fonts.Kind.TEXT, tx, y + 49, c);
            String d = "-" + time(Math.max(0, dur - pos));
            Fonts.draw(g, d, Fonts.Kind.TEXT, tx + tw - Fonts.width(d, Fonts.Kind.TEXT), y + 49, c);
        }
        if (NowPlaying.playing()) bars(g, x + W - 18, y + 9, alpha);
    }

    /** Для меню и паузы (единицы окна Draw): карточка и, если interactive, кнопки ⏮ ⏯ ⏭ поверх. */
    public void render(dev.mraz1visual.ui.Draw d, int x, int y, float alpha, boolean interactive, double mx, double my) {
        renderCard(d.g, x, y, alpha);
        if (!interactive) return;
        int bx = x + W - 70, by = y + 8;
        button(d, dev.mraz1visual.ui.MediaIcons.PREV, bx, by, alpha, mx, my);
        button(d, NowPlaying.playing() ? dev.mraz1visual.ui.MediaIcons.PAUSE : dev.mraz1visual.ui.MediaIcons.PLAY, bx + 20, by, alpha, mx, my);
        button(d, dev.mraz1visual.ui.MediaIcons.NEXT, bx + 40, by, alpha, mx, my);
    }

    private void button(dev.mraz1visual.ui.Draw d, int kind, int x, int y, float a, double mx, double my) {
        boolean hov = dev.mraz1visual.ui.widget.Widget.hit(mx, my, x - 3, y - 3, 18, 18);
        if (hov) d.round(x - 3, y - 3, 18, 18, 9, Colors.withAlpha(0xFFFFFF, Math.round(40 * a)));
        dev.mraz1visual.ui.MediaIcons.draw(d, kind, x + 6, y + 6, 8, Colors.withAlpha(0xFFFFFF, Math.round((hov ? 255 : 190) * a)));
    }

    /** @return true, если клик попал в кнопку. */
    public boolean click(int x, int y, double mx, double my) {
        int bx = x + W - 70, by = y + 8;
        if (dev.mraz1visual.ui.widget.Widget.hit(mx, my, bx - 3, by - 3, 18, 18)) { NowPlaying.command("prev"); return true; }
        if (dev.mraz1visual.ui.widget.Widget.hit(mx, my, bx + 17, by - 3, 18, 18)) { NowPlaying.command("toggle"); return true; }
        if (dev.mraz1visual.ui.widget.Widget.hit(mx, my, bx + 37, by - 3, 18, 18)) { NowPlaying.command("next"); return true; }
        return false;
    }

    /** Компактный «остров». */
    public void renderIsland(GuiGraphics g, float x, float y, float alpha) {
        update(dt());
        int a = Math.round(255 * alpha);
        Sdf.shape(g, x, y + 2, IW, IH, IH / 2f, Sdf.SHADOW, 10, Colors.withAlpha(0x000000, a / 3), Colors.withAlpha(0x000000, a / 3));
        Sdf.shape(g, x, y, IW, IH, IH / 2f, Sdf.GLASS, 8f, Colors.withAlpha(0x0B0B10, Math.round(0xE0 * alpha)), Colors.withAlpha(0x0B0B10, Math.round(0xE0 * alpha)));
        cover(g, x + 4, y + 4, 16, alpha);
        float e = Ease.smooth(swap);
        marquee(g, title, Fonts.Kind.BOLD, x + 25, y + 8, IW - 50, Colors.withAlpha(0xFFFFFF, Math.round(255 * alpha * e)));
        if (NowPlaying.playing()) bars(g, x + IW - 18, y + 7, alpha);
    }

    private void cover(GuiGraphics g, float x, float y, int s, float alpha) {
        if (NowPlaying.hasCover()) {
            g.blit(RenderPipelines.GUI_TEXTURED, NowPlaying.cover(), Math.round(x), Math.round(y), 0f, 0f, s, s, 96, 96, 96, 96,
                    Colors.withAlpha(0xFFFFFF, Math.round(255 * alpha)));
            Sdf.shape(g, x, y, s, s, s / 6f, Sdf.RING, 1f, Colors.withAlpha(0xFFFFFF, Math.round(50 * alpha)), Colors.withAlpha(0xFFFFFF, Math.round(20 * alpha)));
        } else {
            Sdf.fill(g, x, y, s, s, s / 6f, Colors.withAlpha(0x2A2A3C, Math.round(255 * alpha)));
            String n = "♪";
            Fonts.draw(g, n, Fonts.Kind.BOLD, x + s / 2f - Fonts.width(n, Fonts.Kind.BOLD) / 2f, y + s / 2f - 4, Colors.withAlpha(0x9A9AB0, Math.round(255 * alpha)));
        }
    }

    /** Мини-эквалайзер: три столбика плавно «дышат». */
    private void bars(GuiGraphics g, float x, float y, float alpha) {
        for (int i = 0; i < 3; i++) {
            float h = 3 + 6 * (0.5f + 0.5f * (float) Math.sin(eqPhase * (5 + i * 1.7) + i * 1.3));
            Sdf.fill(g, x + i * 4, y + 9 - h, 2.4f, h, 1.2f, Colors.withAlpha(0xFFFFFF, Math.round(200 * alpha)));
        }
    }

    /** Бегущая строка, если текст не влезает: медленно уезжает, пауза, возвращается. */
    private void marquee(GuiGraphics g, String s, Fonts.Kind k, float x, float y, float w, int color) {
        if ((color >>> 24) < 3 || s.isEmpty()) return;
        float tw = Fonts.widthF(s, k);
        if (tw <= w) {
            Fonts.draw(g, s, k, x, y, color);
            return;
        }
        float over = tw - w + 8;
        float period = 4 + over / 18f;
        float t = (System.nanoTime() / 1e9f) % (period * 2);
        float p = t < 1.5f ? 0 : t < period ? (t - 1.5f) / (period - 1.5f) : t < period + 1.5f ? 1 : 1 - (t - period - 1.5f) / (period - 1.5f);
        g.enableScissor(Math.round(x), Math.round(y - 2), Math.round(x + w), Math.round(y + 10));
        Fonts.draw(g, s, k, x - over * Ease.inOutSine(p), y, color);
        g.disableScissor();
    }

    private static String time(long ms) {
        long s = ms / 1000;
        return s / 60 + ":" + (s % 60 < 10 ? "0" : "") + s % 60;
    }
}

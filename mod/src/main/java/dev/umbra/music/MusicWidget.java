package dev.umbra.music;

import dev.umbra.ui.Colors;
import dev.umbra.ui.Draw;
import dev.umbra.ui.Fonts;
import dev.umbra.ui.Theme;
import dev.umbra.ui.widget.Widget;
import net.minecraft.client.renderer.RenderPipelines;

/**
 * Виджет «Сейчас играет»: обложка, название, исполнитель, полоса, ⏮ ⏯ ⏭.
 * Один код для главного меню и HUD (рисует через Draw в его единицах).
 */
public final class MusicWidget {
    public static final int W = 196, H = 44;

    private String lastTitle = "", titleView = "", artistView = "", timeView = "";
    private long lastSec = -1;

    public void render(Draw d, int x, int y, float alpha, boolean interactive, double mx, double my) {
        String t = MediaClient.title;
        boolean empty = t.isEmpty();
        if (!t.equals(lastTitle)) {
            lastTitle = t;
            titleView = empty ? "Ничего не играет" : Draw.ellipsize(t, W - 54 - (interactive ? 52 : 0));
            String a = MediaClient.artist;
            String src = MediaClient.appName();
            artistView = Draw.ellipsize(a.isEmpty() ? src : (src.isEmpty() ? a : a + " · " + src), W - 54 - (interactive ? 52 : 0));
        }
        d.card(x, y, W, H, Theme.RADIUS, Colors.mulAlpha(0xD80C0C12, alpha), Colors.mulAlpha(Theme.CARD_EDGE, alpha));
        // обложка или диск
        int cx = x + 6, cy = y + 6;
        if (!empty && MediaClient.hasCover) {
            d.g.blit(RenderPipelines.GUI_TEXTURED, MediaClient.cover(), cx, cy, 0f, 0f, 32, 32, 64, 64, 64, 64,
                    Colors.withAlpha(0xFFFFFF, Math.round(255 * alpha)));
        } else {
            d.circle(cx + 16, cy + 16, 30, Colors.mulAlpha(0xFF2A2A3C, alpha));
            d.circle(cx + 16, cy + 16, 8, Colors.mulAlpha(0xFF0C0C12, alpha));
        }
        int tx = x + 46;
        d.text(titleView, Fonts.Kind.BOLD, tx, y + 9, Colors.mulAlpha(Theme.TEXT, alpha));
        d.text(artistView, tx, y + 20, Colors.mulAlpha(Theme.TEXT_MUTED, alpha));

        // полоса прогресса
        long dur = MediaClient.duration, pos = MediaClient.positionNow();
        int bw = W - 52;
        d.rect(tx, y + H - 8, bw, 1, Colors.mulAlpha(0x30FFFFFF, alpha));
        if (dur > 0) d.rect(tx, y + H - 8, (int) (bw * Math.min(1, pos / (double) dur)), 1, Colors.mulAlpha(Theme.TEXT_DIM, alpha));

        if (interactive) {
            int bx = x + W - 52;
            button(d, "⏮", bx, y + 10, alpha, mx, my);
            button(d, MediaClient.playing ? "⏸" : "▶", bx + 16, y + 10, alpha, mx, my);
            button(d, "⏭", bx + 32, y + 10, alpha, mx, my);
        }
    }

    private void button(Draw d, String s, int x, int y, float a, double mx, double my) {
        boolean hov = Widget.hit(mx, my, x - 2, y - 2, 16, 16);
        d.textCenter(s, x + 6, y + 2, Colors.mulAlpha(hov ? Theme.TEXT : Theme.TEXT_DIM, a));
    }

    /** @return true, если клик попал в кнопку. */
    public boolean click(int x, int y, double mx, double my) {
        int bx = x + W - 52, by = y + 10;
        if (Widget.hit(mx, my, bx - 2, by - 2, 16, 16)) { MediaClient.command("prev"); return true; }
        if (Widget.hit(mx, my, bx + 14, by - 2, 16, 16)) { MediaClient.command("toggle"); return true; }
        if (Widget.hit(mx, my, bx + 30, by - 2, 16, 16)) { MediaClient.command("next"); return true; }
        return false;
    }
}

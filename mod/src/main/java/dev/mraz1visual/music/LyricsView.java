package dev.mraz1visual.music;

import dev.mraz1visual.render.Ease;
import dev.mraz1visual.ui.Colors;
import dev.mraz1visual.ui.Fonts;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/**
 * Текст песни в стиле Apple Music: текущая строка крупно и ярко, соседние мельче и прозрачнее, при смене строки
 * колонка плавно доезжает (экспоненциальная «пружина»). Один компонент — для HUD, панели и экрана плеера.
 */
public final class LyricsView {
    private float scroll = -1;
    private long last;
    private List<Lyrics.Line> shownFor;

    /**
     * x, y, w, h — область; scale — размер шрифта; center — по центру (иначе по левому краю); alpha — общая прозрачность;
     * offsetMs — сдвиг синхронизации.
     */
    public void render(GuiGraphics g, float x, float y, float w, float h, float scale, boolean center, float alpha, long offsetMs) {
        long now = System.nanoTime();
        float dt = last == 0 ? 0 : Math.min(0.05f, (now - last) / 1e9f);
        last = now;
        List<Lyrics.Line> lines = Lyrics.lines;
        if (lines.isEmpty()) {
            String st = Lyrics.status;
            if (!st.isEmpty()) draw(g, st, x, y + h / 2 - 4 * scale, w, scale, center, Colors.withAlpha(0xFFFFFF, Math.round(150 * alpha)));
            return;
        }
        if (lines != shownFor) { shownFor = lines; scroll = -1; }
        int cur = Lyrics.synced ? Lyrics.index(NowPlaying.position() + offsetMs) : -1;
        float target = Math.max(0, cur);
        if (scroll < 0) scroll = target;
        scroll = Ease.approach(scroll, target, 9, dt);
        float lineH = 13 * scale;
        g.enableScissor((int) x, (int) y, (int) (x + w), (int) (y + h));
        float cy = y + h / 2 - lineH / 2;
        int from = Math.max(0, (int) scroll - 6), to = Math.min(lines.size() - 1, (int) scroll + 7);
        for (int i = from; i <= to; i++) {
            float d = i - scroll;
            float yy = cy + d * lineH * 1.15f;
            if (yy < y - lineH || yy > y + h) continue;
            float dist = Math.abs(d);
            boolean active = Lyrics.synced && i == cur;
            // ближе к текущей — крупнее и ярче
            float k = active ? 1.18f : Math.max(0.82f, 1f - dist * 0.08f);
            float a = active ? 1f : Math.max(0.12f, 0.55f - dist * 0.14f);
            if (!Lyrics.synced) { k = 1f; a = 0.85f; }
            int c = Colors.withAlpha(0xFFFFFF, Math.round(255 * a * alpha));
            draw(g, lines.get(i).text(), x, yy, w, scale * k, center, c);
        }
        g.disableScissor();
    }

    private static void draw(GuiGraphics g, String s, float x, float y, float w, float scale, boolean center, int color) {
        if (s.isEmpty()) s = "♪";
        float tw = Fonts.widthF(s, Fonts.Kind.BOLD) * scale;
        if (tw > w) {
            // длинная строка — уменьшаем под ширину (не ниже 55%), чтобы не резать края
            float k = Math.max(0.55f, w / tw);
            scale *= k;
            tw *= k;
        }
        float tx = center ? x + (w - tw) / 2 : x;
        Fonts.draw(g, s, Fonts.Kind.BOLD, tx, y, scale, color);
    }
}

package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.HudLayer;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.render.HudKit;
import dev.mraz1visual.setting.EnumSetting;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Отладка плавности: FPS, время кадра (мин / сред / макс за секунду) и график последних кадров.
 * Высокие столбики на графике — это и есть статтеры. Без аллокаций в кадре: строки обновляются 4 раза в секунду.
 */
public final class FrameStats extends Module implements HudLayer {
    private static final int N = 240;
    final EnumSetting corner = add(new EnumSetting("corner", "Угол", 0, "tl", "Слева сверху", "tr", "Справа сверху", "bl", "Слева снизу", "br", "Справа снизу"));
    final EnumSetting scale = add(new EnumSetting("scale", "Шкала графика", 1, "16", "16 мс", "33", "33 мс", "66", "66 мс"));

    private final float[] ms = new float[N], sorted = new float[N];
    private int head;
    private long last;
    private long textAt;
    private String line1 = "", line2 = "";

    public FrameStats() {
        super("frame_stats", "Frame Stats", "FPS, время кадра и график статтеров.", Category.PERFORMANCE);
    }

    @Override
    protected void onEnable() {
        last = 0;
        java.util.Arrays.fill(ms, 0);
    }

    @Override
    public void renderHud(GuiGraphics g, float partialTick) {
        long now = System.nanoTime();
        if (last != 0) {
            head = (head + 1) % N;
            ms[head] = (now - last) / 1e6f;
        }
        last = now;

        if (now - textAt > 250_000_000L) {
            textAt = now;
            // окно ~1 секунда: идём назад, пока не наберём 1000 мс
            float sum = 0, min = Float.MAX_VALUE, max = 0;
            int count = 0;
            for (int i = 0; i < N && sum < 1000f; i++) {
                float v = ms[(head - i + N) % N];
                if (v <= 0) break;
                sum += v;
                min = Math.min(min, v);
                max = Math.max(max, v);
                count++;
            }
            if (count > 0) {
                float avg = sum / count;
                // 1% low — FPS самых медленных кадров окна: статтеры видны цифрой, а не только на графике
                for (int i = 0; i < count; i++) sorted[i] = ms[(head - i + N) % N];
                java.util.Arrays.sort(sorted, 0, count);
                int lowN = Math.max(1, count / 100);
                float lowSum = 0;
                for (int i = count - lowN; i < count; i++) lowSum += sorted[i];
                line1 = Math.round(1000f / avg) + " FPS  ·  1% low " + Math.round(1000f / (lowSum / lowN));
                line2 = String.format(java.util.Locale.ROOT, "%.1f / %.1f / %.1f мс", min, avg, max);
            }
        }

        int w = 170, h = 74;
        int c = switch (corner.getId()) { case "tr" -> 1; case "bl" -> 2; case "br" -> 3; default -> 0; };
        int[] p = HudKit.anchor(g, c, w, h, 6);
        int x = p[0], y = p[1];
        HudKit.card(g, x, y, w, h, 1f);
        HudKit.bold(g, line1, x + 8, y + 7, 0xFFFFFFFF);
        HudKit.text(g, line2, x + 8, y + 19, 0xFF9A9AAE);

        float top = switch (scale.getId()) { case "16" -> 16.7f; case "66" -> 66.7f; default -> 33.3f; };
        int gx = x + 6, gy = y + 32, gw = w - 12, gh = h - 38;
        g.fill(gx, gy, gx + gw, gy + gh, 0x30000000);
        // линии 60 и 144 FPS
        int y60 = gy + gh - Math.round(Math.min(1f, 16.7f / top) * gh);
        int y144 = gy + gh - Math.round(Math.min(1f, 6.9f / top) * gh);
        g.fill(gx, y60, gx + gw, y60 + 1, 0x40FFD27A);
        g.fill(gx, y144, gx + gw, y144 + 1, 0x407FD8FF);
        int bars = Math.min(N, gw);
        for (int i = 0; i < bars; i++) {
            float v = ms[(head - i + N) % N];
            if (v <= 0) continue;
            int bh = Math.max(1, Math.round(Math.min(1f, v / top) * gh));
            int col = v > 33.4f ? 0xFFFF5A5A : v > 16.8f ? 0xFFFFC14E : 0xFF52E07A;
            int bx = gx + gw - 1 - i;
            g.fill(bx, gy + gh - bh, bx + 1, gy + gh, col);
        }
    }
}

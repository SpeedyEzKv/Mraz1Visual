package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.HudLayer;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.music.Lyrics;
import dev.mraz1visual.music.LyricsView;
import dev.mraz1visual.music.NowPlaying;
import dev.mraz1visual.render.Ease;
import dev.mraz1visual.setting.EnumSetting;
import dev.mraz1visual.setting.SliderSetting;
import dev.mraz1visual.ui.Colors;
import dev.mraz1visual.ui.Sdf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Текст песни в игре — синхронно с музыкой (встроенный плеер или то, что играет на компьютере). Строка над хотбаром
 * или стеклянная панель сбоку; сдвиг синхронизации — если текст спешит или отстаёт.
 */
public final class LyricsHud extends Module implements HudLayer {
    final EnumSetting mode = add(new EnumSetting("mode", "Вид", 0, "line", "Строка над хотбаром", "panel", "Панель сбоку"));
    final SliderSetting offset = add(new SliderSetting("offset", "Сдвиг синхронизации", 0, -3000, 3000, 50, " мс"));
    final SliderSetting size = add(new SliderSetting("size", "Размер", 1, 0.7, 1.6, 0.05, "×"));

    private final LyricsView view = new LyricsView();
    private float alpha;
    private long last;

    public LyricsHud() {
        super("lyrics", "Lyrics", "Синхронный текст песни в игре (LRCLIB и свои .lrc).", Category.MUSIC);
    }

    @Override
    public void onTick(Minecraft mc) {
        Lyrics.tick();
    }

    @Override
    public void renderHud(GuiGraphics g, float pt) {
        long now = System.nanoTime();
        float dt = last == 0 ? 0 : Math.min(0.05f, (now - last) / 1e9f);
        last = now;
        boolean show = NowPlaying.available() && NowPlaying.playing() && (!Lyrics.lines.isEmpty() || !Lyrics.status.isEmpty());
        alpha = Ease.approach(alpha, show ? 1 : 0, 6, dt);
        if (alpha < 0.02f) return;
        int sw = g.guiWidth(), sh = g.guiHeight();
        float k = size.getF();
        long off = Math.round(offset.get());
        if (mode.getId().equals("panel")) {
            float w = 170 * k, h = 120 * k, x = sw - w - 8, y = sh / 2f - h / 2;
            Sdf.shape(g, x, y, w, h, 14, Sdf.GLASS, 8f, Colors.mulAlpha(0xB00C0C12, alpha), Colors.mulAlpha(0xB00C0C12, alpha));
            view.render(g, x + 10, y + 6, w - 20, h - 12, 0.85f * k, false, alpha, off);
        } else {
            float w = Math.min(sw - 40, 360 * k), h = 30 * k, x = (sw - w) / 2, y = sh - 64 - h;
            // мягкая тёмная подложка — строка читается и на светлом небе, и на снегу
            Sdf.shape(g, x + w * 0.15f, y + 4, w * 0.7f, h - 8, (h - 8) / 2, Sdf.SHADOW, 18, Colors.mulAlpha(0x70000000, alpha), Colors.mulAlpha(0x70000000, alpha));
            view.render(g, x, y, w, h, 0.9f * k, true, alpha, off);
        }
    }
}

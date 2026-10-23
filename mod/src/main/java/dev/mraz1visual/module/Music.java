package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.HudLayer;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.input.Keys;
import dev.mraz1visual.music.MediaClient;
import dev.mraz1visual.music.MusicWidget;
import dev.mraz1visual.music.NowPlaying;
import dev.mraz1visual.render.Ease;
import dev.mraz1visual.setting.BoolSetting;
import dev.mraz1visual.setting.EnumSetting;
import dev.mraz1visual.setting.KeySetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * «Сейчас играет» в HUD: трек встроенного плеера или то, что звучит на компьютере (Spotify, Яндекс Музыка, браузер —
 * через лаунчер). Карточка в духе Live Activity или компактный «остров» сверху; клавиши управления.
 */
public final class Music extends Module implements HudLayer {
    final EnumSetting style = add(new EnumSetting("style", "Вид", 0, "card", "Карточка", "island", "Остров сверху"));
    final EnumSetting corner = add(new EnumSetting("corner", "Угол (карточка)", 0, "tl", "Слева сверху", "tr", "Справа сверху", "bl", "Слева снизу"));
    final BoolSetting hideIdle = add(new BoolSetting("hide_idle", "Прятать, когда ничего не играет", true));
    final KeySetting toggleKey = add(new KeySetting("toggle_key", "Пауза / играть", KeySetting.NONE).group("Клавиши"));
    final KeySetting nextKey = add(new KeySetting("next_key", "Следующий трек", KeySetting.NONE).group("Клавиши"));
    final KeySetting prevKey = add(new KeySetting("prev_key", "Предыдущий трек", KeySetting.NONE).group("Клавиши"));

    private final MusicWidget widget = new MusicWidget();
    private boolean tWas, nWas, pWas;
    private float alpha;
    private long last;

    public Music() {
        super("music", "Now Playing", "Что играет: встроенный плеер или музыка на компьютере (Spotify, Яндекс, браузер).", Category.MUSIC);
    }

    @Override
    protected void onEnable() {
        MediaClient.start();
    }

    @Override
    public void onTick(Minecraft mc) {
        boolean free = mc.screen == null;
        boolean t = free && Keys.isDown(toggleKey.get()), n = free && Keys.isDown(nextKey.get()), p = free && Keys.isDown(prevKey.get());
        if (t && !tWas) NowPlaying.command("toggle");
        if (n && !nWas) NowPlaying.command("next");
        if (p && !pWas) NowPlaying.command("prev");
        tWas = t; nWas = n; pWas = p;
    }

    @Override
    public void renderHud(GuiGraphics g, float pt) {
        long now = System.nanoTime();
        float dt = last == 0 ? 0 : Math.min(0.05f, (now - last) / 1e9f);
        last = now;
        boolean show = NowPlaying.available() || !hideIdle.get() && MediaClient.available();
        alpha = Ease.approach(alpha, show ? 1 : 0, 8, dt);
        if (alpha < 0.02f) return;
        int w = g.guiWidth(), h = g.guiHeight();
        if (style.getId().equals("island")) {
            widget.renderIsland(g, (w - MusicWidget.IW) / 2f, 6, alpha);
            return;
        }
        float x, y;
        switch (corner.getId()) {
            case "tr" -> { x = w - MusicWidget.W - 6; y = 34; }
            case "bl" -> { x = 6; y = h - MusicWidget.H - 6; }
            default -> { x = 6; y = 6; }
        }
        widget.renderCard(g, x, y, alpha);
    }
}

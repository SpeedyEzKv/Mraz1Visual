package dev.umbra.module;

import dev.umbra.core.Category;
import dev.umbra.core.HudLayer;
import dev.umbra.core.Module;
import dev.umbra.input.Keys;
import dev.umbra.music.MediaClient;
import dev.umbra.music.MusicWidget;
import dev.umbra.setting.BoolSetting;
import dev.umbra.setting.EnumSetting;
import dev.umbra.setting.KeySetting;
import dev.umbra.ui.Draw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * «Сейчас играет» в HUD: то, что звучит на компьютере (Spotify, Яндекс Музыка, браузер).
 * Управление — клавишами модуля или мультимедийными клавишами клавиатуры.
 * Работает при запуске через лаунчер Umbra.
 */
public final class Music extends Module implements HudLayer {
    final EnumSetting corner = add(new EnumSetting("corner", "Угол", 0, "tl", "Слева сверху", "tr", "Справа сверху", "bl", "Слева снизу"));
    final BoolSetting hideIdle = add(new BoolSetting("hide_idle", "Прятать, когда ничего не играет", true));
    final KeySetting toggleKey = add(new KeySetting("toggle_key", "Пауза / играть", KeySetting.NONE).group("Клавиши"));
    final KeySetting nextKey = add(new KeySetting("next_key", "Следующий трек", KeySetting.NONE).group("Клавиши"));
    final KeySetting prevKey = add(new KeySetting("prev_key", "Предыдущий трек", KeySetting.NONE).group("Клавиши"));

    private final MusicWidget widget = new MusicWidget();
    private final Draw d = new Draw();
    private boolean tWas, nWas, pWas;

    public Music() {
        super("music", "Music", "Что играет на компьютере: обложка, трек, управление.", Category.HUD);
    }

    @Override
    protected void onEnable() {
        MediaClient.start();
    }

    @Override
    public void onTick(Minecraft mc) {
        boolean free = mc.screen == null;
        boolean t = free && Keys.isDown(toggleKey.get()), n = free && Keys.isDown(nextKey.get()), p = free && Keys.isDown(prevKey.get());
        if (t && !tWas) MediaClient.command("toggle");
        if (n && !nWas) MediaClient.command("next");
        if (p && !pWas) MediaClient.command("prev");
        tWas = t; nWas = n; pWas = p;
    }

    @Override
    public void renderHud(GuiGraphics g, float pt) {
        if (!MediaClient.available()) return;
        if (hideIdle.get() && MediaClient.title.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        int w = g.guiWidth(), h = g.guiHeight();
        int x, y;
        switch (corner.getId()) {
            case "tr" -> { x = w - MusicWidget.W - 6; y = 34; }
            case "bl" -> { x = 6; y = h - MusicWidget.H - 6; }
            default -> { x = 6; y = 6; }
        }
        d.begin(g, mc.font, 1f);
        widget.render(d, x, y, 1f, false, -1, -1);
        d.end();
    }
}

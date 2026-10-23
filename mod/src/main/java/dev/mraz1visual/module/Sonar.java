package dev.mraz1visual.module;

import com.mojang.blaze3d.platform.InputConstants;
import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.core.WorldLayer;
import dev.mraz1visual.input.Keys;
import dev.mraz1visual.render.WorldDraw;
import dev.mraz1visual.setting.ColorSetting;
import dev.mraz1visual.setting.KeySetting;
import dev.mraz1visual.setting.SliderSetting;
import dev.mraz1visual.ui.Colors;
import net.minecraft.client.Minecraft;

/** Красивая волна по земле от тебя по клавише. Чистая косметика — ничего не подсвечивает. */
public final class Sonar extends Module implements WorldLayer {
    final KeySetting key = add(new KeySetting("key", "Клавиша", InputConstants.KEY_V));
    final SliderSetting radius = add(new SliderSetting("radius", "Радиус", 16, 4, 40, 1, " бл."));
    final ColorSetting color = add(new ColorSetting("color", "Цвет", 0xFFB8B4F2));

    private boolean was;
    private long start = -1;
    private double x, y, z;

    public Sonar() {
        super("sonar", "Sonar", "Волна-кольцо от тебя по клавише.", Category.VISUALS);
    }

    @Override
    public void onTick(Minecraft mc) {
        boolean down = mc.player != null && mc.screen == null && Keys.isDown(key.get());
        if (down && !was) {
            start = System.currentTimeMillis();
            x = mc.player.getX();
            y = mc.player.getY() + 0.05;
            z = mc.player.getZ();
        }
        was = down;
    }

    @Override
    public void renderWorld(WorldDraw d, float pt) {
        if (start < 0) return;
        float t = (System.currentTimeMillis() - start) / 1600f;
        if (t > 1) {
            start = -1;
            return;
        }
        double r = radius.get() * (1 - Math.pow(1 - t, 3));
        float a = 1f - t;
        int c = color.get();
        d.ring(x, y, z, Math.max(0, r - 0.6), r, 64, Colors.mulAlpha(c, 0f), Colors.mulAlpha(c, a * 0.8f));
        d.ring(x, y, z, Math.max(0, r * 0.75 - 0.2), r * 0.75, 64, Colors.mulAlpha(c, 0f), Colors.mulAlpha(c, a * 0.35f));
    }
}

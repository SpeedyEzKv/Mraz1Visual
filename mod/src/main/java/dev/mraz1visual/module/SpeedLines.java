package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.HudLayer;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.render.Ease;
import dev.mraz1visual.render.Fx;
import dev.mraz1visual.render.Palette;
import dev.mraz1visual.setting.EnumSetting;
import dev.mraz1visual.setting.SliderSetting;
import dev.mraz1visual.ui.Sdf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;

/**
 * Линии скорости: тонкие светлые штрихи у краёв экрана, летящие от центра, — на быстром беге и на элитрах.
 * Плотность и яркость растут со скоростью, центр экрана всегда чистый. Только картинка у тебя.
 */
public final class SpeedLines extends Module implements HudLayer {
    final EnumSetting preset = add(new EnumSetting("preset", "Цвет", 0, Palette.OPTIONS));
    final SliderSetting from = add(new SliderSetting("from", "С какой скорости", 6, 3, 20, 0.5, " бл/с"));
    final SliderSetting intensity = add(new SliderSetting("intensity", "Сила", 60, 10, 100, 5, " %"));

    private static final int MAX = 48;
    private final float[] ang = new float[MAX], dist = new float[MAX], vel = new float[MAX], len = new float[MAX];
    private float level;
    private long last;
    private double px, py, pz;
    private boolean has;

    public SpeedLines() {
        super("speed_lines", "Speed Lines", "Тонкие штрихи по краям экрана на быстром беге и на элитрах.", Category.VISUALS);
        for (int i = 0; i < MAX; i++) respawn(i, true);
    }

    private void respawn(int i, boolean anywhere) {
        ang[i] = (float) (Math.random() * Math.PI * 2);
        dist[i] = anywhere ? (float) (0.35 + Math.random() * 0.6) : 0.35f + (float) Math.random() * 0.1f;
        vel[i] = 0.9f + (float) Math.random() * 0.9f;
        len[i] = 0.06f + (float) Math.random() * 0.1f;
    }

    @Override
    public void renderHud(GuiGraphics g, float pt) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        long now = System.nanoTime();
        float dt = last == 0 ? 0 : Math.min(0.05f, (now - last) / 1e9f);
        last = now;
        if (p == null) return;
        // скорость — по реальному смещению за кадр (тики дают рывки)
        double x = p.xo + (p.getX() - p.xo) * pt, y = p.yo + (p.getY() - p.yo) * pt, z = p.zo + (p.getZ() - p.zo) * pt;
        double sp = 0;
        if (has && dt > 0) sp = Math.sqrt((x - px) * (x - px) + (y - py) * (y - py) * 0.3 + (z - pz) * (z - pz)) / dt;
        px = x; py = y; pz = z; has = true;
        float target = sp > 60 ? 0 : Ease.clamp((float) ((sp - from.get()) / (from.get() * 0.8)));
        level = Ease.approach(level, target, target > level ? 3 : 2, dt);
        if (level < 0.02f) return;
        int sw = g.guiWidth(), sh = g.guiHeight();
        float cx = sw / 2f, cy = sh / 2f, rad = (float) Math.hypot(cx, cy);
        int n = Math.min(MAX, Fx.count(Math.round(MAX * 0.75f)));
        float k = intensity.getF() / 100f * level * Fx.brightness();
        for (int i = 0; i < n; i++) {
            dist[i] += vel[i] * dt * (0.6f + level);
            if (dist[i] > 1.05f) respawn(i, false);
            float d0 = dist[i], d1 = d0 + len[i] * (0.6f + level);
            // ярче к середине пути, у центра экрана — прозрачно
            float a = k * Ease.smooth((d0 - 0.35f) / 0.2f) * (1 - Ease.smooth((d0 - 0.8f) / 0.25f));
            if (a < 0.01f) continue;
            int c = Fx.color(preset.getId(), 0.1f, Fx.time(), 0xFFFFFFFF, 0xFFFFFFFF, 0.55f * a);
            float lx = (float) Math.cos(ang[i]), ly = (float) Math.sin(ang[i]);
            float l = (d1 - d0) * rad;
            g.pose().pushMatrix();
            g.pose().translate(cx + lx * d0 * rad, cy + ly * d0 * rad);
            g.pose().rotate(ang[i] - (float) Math.PI / 2);
            Sdf.shape(g, -0.6f, 0, 1.2f, l, 0.6f, Sdf.FILL, 0, c & 0x00FFFFFF, c);
            g.pose().popMatrix();
        }
    }
}

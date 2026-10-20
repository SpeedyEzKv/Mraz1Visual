package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.core.WorldLayer;
import dev.mraz1visual.render.Palette;
import dev.mraz1visual.render.WorldDraw;
import dev.mraz1visual.setting.BoolSetting;
import dev.mraz1visual.setting.ColorSetting;
import dev.mraz1visual.setting.EnumSetting;
import dev.mraz1visual.setting.SliderSetting;
import dev.mraz1visual.ui.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * Аура: едва заметное мягкое свечение у ног, которое медленно «дышит», и несколько огоньков,
 * лениво поднимающихся вокруг. Без спама частиц: всё считается по времени, ничего не создаётся в кадре.
 */
public final class PlayerAura extends Module implements WorldLayer {
    final EnumSetting preset = add(new EnumSetting("preset", "Цвет", 0, Palette.OPTIONS));
    final ColorSetting color = add(new ColorSetting("color", "Центр (свой)", 0xFFEAF6FF));
    final ColorSetting color2 = add(new ColorSetting("color2", "Край (свой)", 0xFF7FD8FF));
    final SliderSetting size = add(new SliderSetting("size", "Размер", 1, 0.5, 2, 0.05, "×"));
    final SliderSetting intensity = add(new SliderSetting("intensity", "Яркость", 60, 10, 100, 5, " %"));
    final SliderSetting motes = add(new SliderSetting("motes", "Огоньки", 6, 0, 12, 1, ""));
    final BoolSetting firstPerson = add(new BoolSetting("first_person", "Видно и от 1-го лица", true));

    private static final long START = System.nanoTime();

    public PlayerAura() {
        super("player_aura", "Aura", "Мягкое свечение у ног и огоньки вокруг тебя.", Category.COSMETICS);
    }

    private int col(float t, float a) {
        return Colors.withAlpha(Palette.color(preset.getId(), t, (System.nanoTime() - START) / 3e9f, color.get(), color2.get()),
                Math.max(0, Math.min(255, Math.round(255 * a * intensity.getF() / 100f))));
    }

    @Override
    public void renderWorld(WorldDraw d, float pt) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || p.isInvisible() || p.isSpectator()) return;
        if (!firstPerson.get() && mc.options.getCameraType().isFirstPerson()) return;
        double x = p.xo + (p.getX() - p.xo) * pt, y = p.yo + (p.getY() - p.yo) * pt + 0.02, z = p.zo + (p.getZ() - p.zo) * pt;
        float time = (System.nanoTime() - START) / 1e9f;
        float s = size.getF();
        float breathe = 0.8f + 0.2f * (float) Math.sin(time * 1.4f);
        d.glowBegin();
        // свечение у ног: от центра к краю тает, второй слой — шире и слабее
        d.ring(x, y, z, 0, 0.55 * s, 40, col(0.1f, 0.38f * breathe), col(0.6f, 0f));
        d.ring(x, y + 0.002, z, 0.3 * s, 0.95 * s, 40, col(0.4f, 0.12f * breathe), col(0.8f, 0f));
        // огоньки: медленно поднимаются по спирали и растворяются
        int n = motes.getI();
        for (int i = 0; i < n; i++) {
            float ph = (time * 0.22f + i / (float) n) % 1f;
            double a = i * 2.399 + time * 0.35;
            double r = (0.35 + 0.25 * Math.sin(i * 1.7)) * s;
            double my = y + ph * 1.6;
            float f = (float) Math.sin(Math.PI * ph);
            d.billboardDot(x + Math.cos(a) * r, my, z + Math.sin(a) * r, 0.035 * s, col(0.05f + ph * 0.5f, 0.85f * f), 8);
            d.billboardDot(x + Math.cos(a) * r, my, z + Math.sin(a) * r, 0.09 * s, col(0.3f, 0.18f * f), 8);
        }
        d.glowEnd();
    }
}

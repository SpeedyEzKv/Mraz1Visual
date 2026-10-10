package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.HudLayer;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.render.HudKit;
import dev.mraz1visual.setting.EnumSetting;
import dev.mraz1visual.ui.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.effect.MobEffectInstance;

import java.util.ArrayList;
import java.util.List;

/** Твои эффекты: цветная метка, название с уровнем и таймер; кончается — мигает. */
public final class PotionsHud extends Module implements HudLayer {
    private static final String[] ROMAN = {"", " II", " III", " IV", " V", " VI", " VII", " VIII", " IX", " X"};
    final EnumSetting corner = add(new EnumSetting("corner", "Угол", 0, "tl", "Слева сверху", "tr", "Справа сверху", "bl", "Слева снизу", "br", "Справа снизу"));

    public PotionsHud() {
        super("potions_hud", "Potions", "Активные эффекты с таймером.", Category.VISUALS);
    }

    @Override
    public void renderHud(GuiGraphics g, float pt) {
        Minecraft mc = Minecraft.getInstance();
        List<MobEffectInstance> list = new ArrayList<>(mc.player.getActiveEffects());
        if (list.isEmpty()) return;
        List<String> names = new ArrayList<>(), times = new ArrayList<>();
        int w = 80;
        for (MobEffectInstance e : list) {
            int amp = e.getAmplifier();
            String n = e.getEffect().value().getDisplayName().getString() + (amp > 0 && amp < ROMAN.length ? ROMAN[amp] : amp > 0 ? " " + (amp + 1) : "");
            int ticks = e.getDuration();
            String t = e.isInfiniteDuration() ? "∞" : String.format("%d:%02d", ticks / 1200, ticks / 20 % 60);
            names.add(n);
            times.add(t);
            w = Math.max(w, HudKit.width(n) + HudKit.width(t) + 30);
        }
        int row = 12, h = 16 + list.size() * row;
        int[] at = HudKit.anchor(g, corner.get(), w, h, 4);
        if (corner.get() == 0) at[1] += 30; // не перекрывать надписи сверху слева
        int x = at[0], y = at[1];
        HudKit.card(g, x, y, w, h, 1f);
        HudKit.title(g, "ЭФФЕКТЫ", x + 6, y + 5, 1f);
        long now = System.currentTimeMillis();
        for (int i = 0; i < list.size(); i++) {
            MobEffectInstance e = list.get(i);
            int ry = y + 16 + i * row;
            int color = 0xFF000000 | e.getEffect().value().getColor();
            boolean ending = !e.isInfiniteDuration() && e.getDuration() < 200;
            float a = ending ? 0.55f + 0.45f * (float) Math.abs(Math.sin(now / 180.0)) : 1f;
            g.fill(x + 6, ry + 1, x + 8, ry + 8, Colors.mulAlpha(color, a));
            HudKit.text(g, names.get(i), x + 12, ry, Colors.mulAlpha(0xFFE9E9F1, a));
            String t = times.get(i);
            HudKit.text(g, t, x + w - 6 - HudKit.width(t), ry, Colors.mulAlpha(0xFF9B9BAB, a));
        }
    }
}

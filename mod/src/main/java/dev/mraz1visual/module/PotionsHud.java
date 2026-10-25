package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.HudLayer;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.render.HudKit;
import dev.mraz1visual.render.HudPos;
import dev.mraz1visual.setting.EnumSetting;
import dev.mraz1visual.ui.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * Твои эффекты: цветная метка, название с уровнем и таймер; кончается — мигает.
 * Строки собираются раз в тик (таймер всё равно идёт по тикам), кадр только рисует готовое.
 */
public final class PotionsHud extends Module implements HudLayer {
    private static final String[] ROMAN = {"", " II", " III", " IV", " V", " VI", " VII", " VIII", " IX", " X"};
    private static final int MAX = 24;
    final EnumSetting corner = add(new EnumSetting("corner", "Угол", 0, "tl", "Слева сверху", "tr", "Справа сверху", "bl", "Слева снизу",
            "br", "Справа снизу", "free", "Свободно (редактор HUD)"));
    final HudPos pos = hudPos(0.0, 0.3).activeWhen(() -> corner.getId().equals("free"), () -> corner.set(4));

    private final String[] names = new String[MAX], times = new String[MAX];
    private final int[] colors = new int[MAX];
    private final boolean[] ending = new boolean[MAX];
    private int count, width = 80;

    public PotionsHud() {
        super("potions_hud", "Potions", "Активные эффекты с таймером.", Category.HUD);
    }

    @Override
    public void onTick(Minecraft mc) {
        count = 0;
        if (mc.player == null) return;
        int w = 80;
        for (MobEffectInstance e : mc.player.getActiveEffects()) {
            if (count >= MAX) break;
            int amp = e.getAmplifier();
            names[count] = e.getEffect().value().getDisplayName().getString() + (amp > 0 && amp < ROMAN.length ? ROMAN[amp] : amp > 0 ? " " + (amp + 1) : "");
            int ticks = e.getDuration();
            int sec = ticks / 20 % 60;
            times[count] = e.isInfiniteDuration() ? "∞" : ticks / 1200 + (sec < 10 ? ":0" : ":") + sec;
            colors[count] = 0xFF000000 | e.getEffect().value().getColor();
            ending[count] = !e.isInfiniteDuration() && ticks < 200;
            w = Math.max(w, HudKit.width(names[count]) + HudKit.width(times[count]) + 30);
            count++;
        }
        width = w;
    }

    @Override
    public void renderHud(GuiGraphics g, float pt) {
        if (count == 0) return;
        int w = width, row = 12, h = 16 + count * row;
        boolean free = pos.active();
        int x, y;
        if (free) {
            pos.begin(g, w, h);
            x = 0;
            y = 0;
        } else {
            int[] at = HudKit.anchor(g, corner.get(), w, h, 4);
            if (corner.get() == 0) at[1] += 30; // не перекрывать надписи сверху слева
            x = at[0];
            y = at[1];
            pos.track(x, y, w, h);
        }
        HudKit.card(g, x, y, w, h, 1f);
        HudKit.title(g, "ЭФФЕКТЫ", x + 6, y + 5, 1f);
        long now = System.currentTimeMillis();
        for (int i = 0; i < count; i++) {
            int ry = y + 16 + i * row;
            float a = ending[i] ? 0.55f + 0.45f * (float) Math.abs(Math.sin(now / 180.0)) : 1f;
            g.fill(x + 6, ry + 1, x + 8, ry + 8, Colors.mulAlpha(colors[i], a));
            HudKit.text(g, names[i], x + 12, ry, Colors.mulAlpha(0xFFE9E9F1, a));
            HudKit.text(g, times[i], x + w - 6 - HudKit.width(times[i]), ry, Colors.mulAlpha(0xFF9B9BAB, a));
        }
        if (free) pos.end(g);
    }
}

package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.HudLayer;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.render.HudKit;
import dev.mraz1visual.render.HudPos;
import dev.mraz1visual.render.HudStyle;
import dev.mraz1visual.setting.BoolSetting;
import dev.mraz1visual.setting.EnumSetting;
import dev.mraz1visual.ui.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * Эффекты: иконка, название с уровнем и таймер; меньше 10 секунд — время жёлтое и мигает.
 * Строки собираются раз в тик (таймер всё равно идёт по тикам), кадр только рисует готовое.
 * Ванильные значки эффектов справа сверху можно скрыть.
 */
public final class PotionsHud extends Module implements HudLayer {
    private static PotionsHud instance;
    private static final String[] ROMAN = {"", " II", " III", " IV", " V", " VI", " VII", " VIII", " IX", " X"};
    private static final int MAX = 24, ROW = 13;
    final EnumSetting corner = add(new EnumSetting("corner", "Угол", 0, "tl", "Слева сверху", "tr", "Справа сверху", "bl", "Слева снизу",
            "br", "Справа снизу", "free", "Свободно (редактор HUD)"));
    final HudPos pos = hudPos(0.0, 0.3).activeWhen(() -> corner.getId().equals("free"), () -> corner.set(4));
    final EnumSetting sort = add(new EnumSetting("sort", "Сортировка", 0, "time", "По времени", "name", "По имени"));
    final BoolSetting icons = add(new BoolSetting("icons", "Показывать иконки", true));
    final BoolSetting hideVanilla = add(new BoolSetting("hide_vanilla", "Скрыть ванильные", true));

    private final String[] names = new String[MAX], times = new String[MAX];
    private final int[] colors = new int[MAX], ticks = new int[MAX];
    private final boolean[] ending = new boolean[MAX];
    @SuppressWarnings("unchecked")
    private final Holder<MobEffect>[] effects = new Holder[MAX];
    private int count, width = 80;

    public PotionsHud() {
        super("potions_hud", "Potion HUD", "Эффекты с иконкой, уровнем и таймером; ванильные значки можно скрыть.", Category.HUD);
        instance = this;
        markNew();
    }

    /** Из VanillaHudHooks: ванильные значки эффектов не рисовать. */
    public static boolean hidesVanilla() {
        return instance != null && instance.isEnabled() && instance.hideVanilla.get();
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
            int t = e.getDuration();
            int sec = t / 20 % 60;
            times[count] = e.isInfiniteDuration() ? "∞" : t / 1200 + (sec < 10 ? ":0" : ":") + sec;
            ticks[count] = e.isInfiniteDuration() ? Integer.MAX_VALUE : t;
            colors[count] = 0xFF000000 | e.getEffect().value().getColor();
            ending[count] = !e.isInfiniteDuration() && t < 200;
            effects[count] = e.getEffect();
            w = Math.max(w, HudKit.width(names[count]) + HudKit.width(times[count]) + (icons.get() ? 40 : 30));
            count++;
        }
        sortRows();
        width = w;
    }

    /** Вставками — эффектов единицы, без аллокаций. */
    private void sortRows() {
        boolean byName = sort.getId().equals("name");
        for (int i = 1; i < count; i++) {
            for (int j = i; j > 0; j--) {
                boolean swap = byName ? names[j].compareToIgnoreCase(names[j - 1]) < 0 : ticks[j] < ticks[j - 1];
                if (!swap) break;
                swapRow(j, j - 1);
            }
        }
    }

    private void swapRow(int a, int b) {
        String s = names[a]; names[a] = names[b]; names[b] = s;
        s = times[a]; times[a] = times[b]; times[b] = s;
        int c = colors[a]; colors[a] = colors[b]; colors[b] = c;
        c = ticks[a]; ticks[a] = ticks[b]; ticks[b] = c;
        boolean e = ending[a]; ending[a] = ending[b]; ending[b] = e;
        Holder<MobEffect> h = effects[a]; effects[a] = effects[b]; effects[b] = h;
    }

    @Override
    public void renderHud(GuiGraphics g, float pt) {
        if (count == 0) return;
        int w = width, h = 18 + count * ROW;
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
        HudStyle.panel(g, x, y, w, h, 8, 1f);
        HudKit.title(g, "ЭФФЕКТЫ", x + 6, y + 5, 1f);
        long now = System.currentTimeMillis();
        boolean ic = icons.get();
        for (int i = 0; i < count; i++) {
            int ry = y + 17 + i * ROW;
            int tx = x + 12;
            if (ic) {
                g.blitSprite(RenderPipelines.GUI_TEXTURED, dev.mraz1visual.compat.Mc.effectSprite(effects[i]), x + 6, ry - 1, 9, 9);
                tx = x + 19;
            } else g.fill(x + 6, ry + 1, x + 8, ry + 8, colors[i]);
            HudKit.text(g, names[i], tx, ry, 0xFFE9E9F1);
            // меньше 10 секунд — жёлтый и мигает
            int tc = ending[i] ? Colors.mulAlpha(HudStyle.YELLOW, 0.45f + 0.55f * (float) Math.abs(Math.sin(now / 160.0))) : 0xFF9B9BAB;
            HudKit.text(g, times[i], x + w - 6 - HudKit.width(times[i]), ry, tc);
        }
        if (free) pos.end(g);
    }
}

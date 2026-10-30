package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.HudLayer;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.setting.ColorSetting;
import dev.mraz1visual.setting.EnumSetting;
import dev.mraz1visual.ui.Fonts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Перезарядка предметов (жемчуг, хорус, щит, яблоки…) из ванильных кулдаунов игрока — ничего не выдумываем:
 * на слотах хотбара заливка снизу вверх и таймер «0.6с» над слотом; по желанию — ещё полоски под прицелом.
 * Секунды: ваниль отдаёт только долю, поэтому скорость убывания меряется по тикам (длина КД = доля / убыль за тик).
 */
public final class ItemCooldowns extends Module implements HudLayer {
    private static final String[] TENTHS = new String[600];
    final ColorSetting color = add(new ColorSetting("color", "Полоса", 0xFFB8B4F2));
    final EnumSetting place = add(new EnumSetting("place", "Где показывать", 0, "hotbar", "На хотбаре", "crosshair", "Под прицелом", "both", "И там, и там"));

    private final Item[] seen = new Item[10];
    // оценка оставшегося времени: предмет → доля в прошлом тике и убыль за тик
    private final Item[] trItem = new Item[10];
    private final float[] trLast = new float[10], trRate = new float[10];

    public ItemCooldowns() {
        super("item_cooldowns", "Item Cooldowns", "Заливка и таймер КД прямо на слотах хотбара.", Category.HUD);
        markNew();
    }

    @Override
    public void onTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null) return;
        for (int i = 0; i < 10; i++) {
            ItemStack s = i < 9 ? p.getInventory().getItem(i) : p.getOffhandItem();
            float f = s.isEmpty() ? 0 : p.getCooldowns().getCooldownPercent(s, 0f);
            Item it = s.isEmpty() ? null : s.getItem();
            if (it != trItem[i] || f <= 0) {
                trItem[i] = it;
                trLast[i] = f;
                trRate[i] = 0;
                continue;
            }
            float d = trLast[i] - f;
            if (d > 0) trRate[i] = trRate[i] == 0 ? d : trRate[i] * 0.6f + d * 0.4f;
            trLast[i] = f;
        }
    }

    @Override
    public void renderHud(GuiGraphics g, float pt) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        String pl = place.getId();
        if (!pl.equals("crosshair")) hotbar(g, p, pt);
        if (!pl.equals("hotbar")) crosshair(g, p, pt);
    }

    private void hotbar(GuiGraphics g, LocalPlayer p, float pt) {
        int x0 = g.guiWidth() / 2 - 91, y = g.guiHeight() - 22;
        for (int i = 0; i < 9; i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (s.isEmpty()) continue;
            float f = p.getCooldowns().getCooldownPercent(s, pt);
            if (f <= 0) continue;
            int ix = x0 + 3 + i * 20, iy = y + 3;
            int fh = Math.round(16 * f);
            // заливка снизу вверх: сколько осталось
            g.fill(ix, iy + 16 - fh, ix + 16, iy + 16, (color.get() & 0x00FFFFFF) | 0x70000000);
            String t = seconds(i, f);
            if (!t.isEmpty()) {
                int tw = Fonts.width(t, Fonts.Kind.BOLD);
                g.fill(ix + 8 - tw / 2 - 2, iy - 12, ix + 8 + tw / 2 + 2, iy - 2, 0xB00C0C12);
                Fonts.draw(g, t, Fonts.Kind.BOLD, ix + 8 - tw / 2f, iy - 11, 0xFFFFFFFF);
            }
        }
    }

    /** «0.6с» по измеренной скорости убывания; пока скорость не измерена — пусто. */
    private String seconds(int slot, float f) {
        float r = trRate[slot];
        if (r <= 0) return "";
        int tenths = Math.round(f / r / 2f); // тиков / 20 * 10
        if (tenths >= TENTHS.length) tenths = TENTHS.length - 1;
        String s = TENTHS[tenths];
        if (s == null) TENTHS[tenths] = s = tenths / 10 + "." + tenths % 10 + "с";
        return s;
    }

    private void crosshair(GuiGraphics g, LocalPlayer p, float pt) {
        int n = 0;
        int x = g.guiWidth() / 2 - 30, y = g.guiHeight() / 2 + 14;
        for (int i = 0; i <= 9; i++) {
            ItemStack s = i < 9 ? p.getInventory().getItem(i) : p.getOffhandItem();
            if (s.isEmpty()) continue;
            float f = p.getCooldowns().getCooldownPercent(s, pt);
            if (f <= 0) continue;
            boolean dup = false;
            for (int j = 0; j < n; j++) if (seen[j] == s.getItem()) dup = true;
            if (dup) continue;
            seen[n++] = s.getItem();
            int ry = y + (n - 1) * 18;
            g.renderItem(s, x, ry);
            g.fill(x + 20, ry + 7, x + 60, ry + 9, 0x60000000);
            g.fill(x + 20, ry + 7, x + 20 + Math.round(40 * f), ry + 9, color.get());
        }
        for (int j = 0; j < n; j++) seen[j] = null;
    }
}

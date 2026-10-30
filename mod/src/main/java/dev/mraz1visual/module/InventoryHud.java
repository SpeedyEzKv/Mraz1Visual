package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.HudLayer;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.render.HudKit;
import dev.mraz1visual.render.HudPos;
import dev.mraz1visual.render.HudStyle;
import dev.mraz1visual.setting.BoolSetting;
import dev.mraz1visual.setting.EnumSetting;
import dev.mraz1visual.setting.SliderSetting;
import dev.mraz1visual.ui.Fonts;
import dev.mraz1visual.ui.Sdf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

/** Мини-сетка 9×3 с содержимым инвентаря (без хотбара). Размер и прозрачность фона — в настройках. */
public final class InventoryHud extends Module implements HudLayer {
    private static final String[] NUM = new String[100];
    final EnumSetting corner = add(new EnumSetting("corner", "Угол", 4, "tl", "Слева сверху", "tr", "Справа сверху", "bl", "Слева снизу", "br", "Справа снизу",
            "free", "Свободно (редактор HUD)"));
    final HudPos pos = hudPos(1.0, 0.3).activeWhen(() -> corner.getId().equals("free"), () -> corner.set(4));
    final SliderSetting size = add(new SliderSetting("size", "Размер", 1.0, 0.5, 1.5, 0.05, "×"));
    final SliderSetting bg = add(new SliderSetting("bg", "Прозрачность фона", 85, 0, 100, 5, " %"));
    final BoolSetting counts = add(new BoolSetting("counts", "Показывать количество", true));

    public InventoryHud() {
        super("inventory_hud", "Inventory HUD", "Мини-сетка 9×3 с содержимым инвентаря.", Category.HUD);
        markNew();
    }

    @Override
    public void renderHud(GuiGraphics g, float pt) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        int w = 9 * 18 + 8, h = 3 * 18 + 8;
        float s = size.getF();
        boolean free = pos.active();
        if (free) {
            // масштаб в свободном режиме — общий ползунок «Размер» + колесо редактора
            pos.begin(g, Math.round(w * s), Math.round(h * s));
        } else {
            int sw = Math.round(w * s), sh = Math.round(h * s);
            int[] at = HudKit.anchor(g, corner.get(), sw, sh, 4);
            if (corner.get() == 1) at[1] += 64; // под Interface справа сверху
            pos.track(at[0], at[1], sw, sh);
            g.pose().pushMatrix();
            g.pose().translate(at[0], at[1]);
        }
        if (s != 1f) g.pose().scale(s, s);
        float a = bg.getF() / 100f;
        if (a > 0.01f) HudStyle.panel(g, 0, 0, w, h, 8, a);
        for (int i = 0; i < 27; i++) {
            int sx = 4 + (i % 9) * 18, sy = 4 + (i / 9) * 18;
            Sdf.fill(g, sx + 1, sy + 1, 16, 16, 3, 0x12FFFFFF);
            ItemStack st = p.getInventory().getItem(9 + i);
            if (st.isEmpty()) continue;
            g.renderItem(st, sx + 1, sy + 1);
            if (st.isDamageableItem() && st.isDamaged()) g.renderItemDecorations(mc.font, st, sx + 1, sy + 1);
            else if (counts.get() && st.getCount() > 1) {
                String n = num(st.getCount());
                Fonts.drawShadow(g, n, Fonts.Kind.BOLD, sx + 17 - Fonts.width(n, Fonts.Kind.BOLD), sy + 10, 0xFFFFFFFF);
            }
        }
        if (free) pos.end(g);
        else g.pose().popMatrix();
    }

    private static String num(int v) {
        if (v >= NUM.length) return Integer.toString(v);
        String s = NUM[v];
        if (s == null) NUM[v] = s = Integer.toString(v);
        return s;
    }
}

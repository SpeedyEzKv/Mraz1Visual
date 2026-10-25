package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.HudLayer;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.render.HudKit;
import dev.mraz1visual.setting.BoolSetting;
import dev.mraz1visual.setting.EnumSetting;
import dev.mraz1visual.ui.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/** Твоя броня и прочность рядом с хотбаром: иконка, полоска, проценты. */
public final class ArmorHud extends Module implements HudLayer {
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    final EnumSetting place = add(new EnumSetting("place", "Где", 0, "hotbar", "У хотбара", "left", "Слева сверху", "right", "Справа снизу", "free", "Свободно (редактор HUD)"));
    final dev.mraz1visual.render.HudPos pos = hudPos(0.5, 1.0).activeWhen(() -> place.getId().equals("free"), () -> place.set(3));
    private static final String[] NUM = new String[1000];
    private final ItemStack[] items = new ItemStack[6];
    final BoolSetting percent = add(new BoolSetting("percent", "Проценты прочности", true));
    final BoolSetting hands = add(new BoolSetting("hands", "Предметы в руках", false));

    public ArmorHud() {
        super("armor_hud", "Armor HUD", "Броня и её прочность на экране.", Category.HUD);
    }

    @Override
    public void renderHud(GuiGraphics g, float pt) {
        LocalPlayer p = Minecraft.getInstance().player;
        int n = 0;
        for (EquipmentSlot s : SLOTS) {
            ItemStack st = p.getItemBySlot(s);
            if (!st.isEmpty()) items[n++] = st;
        }
        if (hands.get()) {
            if (!p.getMainHandItem().isEmpty()) items[n++] = p.getMainHandItem();
            if (!p.getOffhandItem().isEmpty()) items[n++] = p.getOffhandItem();
        }
        if (n == 0) return;
        int cell = 22, w = n * cell + 6, h = percent.get() ? 32 : 24;
        int x, y;
        boolean free = pos.active();
        switch (place.getId()) {
            case "free" -> { pos.begin(g, w, h); x = 0; y = 0; }
            case "left" -> { x = 4; y = 4; }
            case "right" -> { x = g.guiWidth() - w - 4; y = g.guiHeight() - h - 4; }
            default -> { x = g.guiWidth() / 2 + 96; y = g.guiHeight() - h - 2; }
        }
        if (!free) pos.track(x, y, w, h);
        HudKit.card(g, x, y, w, h, 1f);
        for (int i = 0; i < n; i++) {
            ItemStack st = items[i];
            int ix = x + 4 + i * cell;
            g.renderItem(st, ix + 1, y + 3);
            if (st.isDamageableItem()) {
                float f = 1f - st.getDamageValue() / (float) Math.max(1, st.getMaxDamage());
                int c = Colors.lerp(0xFFE05A5A, 0xFF7AD67A, f);
                HudKit.bar(g, ix + 2, y + 20, 16, 2, f, c, 1f);
                if (percent.get()) {
                    String s = num(Math.round(f * 100));
                    HudKit.text(g, s, ix + 10 - HudKit.width(s) / 2, y + 24, 0xFFB4B4C4);
                }
            } else if (st.getCount() > 1 && percent.get()) {
                String s = num(st.getCount());
                HudKit.text(g, s, ix + 10 - HudKit.width(s) / 2, y + 24, 0xFFB4B4C4);
            }
        }
        if (free) pos.end(g);
    }

    private static String num(int v) {
        if (v < 0 || v >= NUM.length) return Integer.toString(v);
        String s = NUM[v];
        if (s == null) NUM[v] = s = Integer.toString(v);
        return s;
    }
}

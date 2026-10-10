package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.core.WorldLayer;
import dev.mraz1visual.render.WorldDraw;
import dev.mraz1visual.setting.BoolSetting;
import dev.mraz1visual.setting.ColorSetting;
import dev.mraz1visual.ui.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Подсветка ценных выброшенных предметов: светящийся столбик и кольцо. С проверкой глубины. */
public final class ItemGlow extends Module implements WorldLayer {
    final BoolSetting all = add(new BoolSetting("all", "Все предметы", false));
    final ColorSetting color = add(new ColorSetting("color", "Цвет ценных", 0xE0F2C94C));
    final ColorSetting other = add(new ColorSetting("other", "Цвет остальных", 0x80B8B4F2));
    final BoolSetting blink = add(new BoolSetting("blink", "Мигать", true));

    public ItemGlow() {
        super("item_glow", "Item Glow", "Подсветка ценных предметов на земле.", Category.VISUALS);
    }

    private static boolean valuable(ItemStack s) {
        Item i = s.getItem();
        return i == Items.TOTEM_OF_UNDYING || i == Items.ENCHANTED_GOLDEN_APPLE || i == Items.GOLDEN_APPLE || i == Items.ELYTRA
                || i == Items.NETHERITE_INGOT || i == Items.NETHERITE_HELMET || i == Items.NETHERITE_CHESTPLATE || i == Items.NETHERITE_LEGGINGS
                || i == Items.NETHERITE_BOOTS || i == Items.NETHERITE_SWORD || i == Items.ENDER_PEARL;
    }

    @Override
    public void renderWorld(WorldDraw d, float pt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        float t = System.currentTimeMillis() / 1000f;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof ItemEntity ie) || e.distanceToSqr(mc.player) > 48 * 48) continue;
            boolean v = valuable(ie.getItem());
            if (!v && !all.get()) continue;
            double x = e.xo + (e.getX() - e.xo) * pt, y = e.yo + (e.getY() - e.yo) * pt, z = e.zo + (e.getZ() - e.zo) * pt;
            float a = blink.get() ? 0.6f + 0.4f * (float) Math.abs(Math.sin(t * 3 + e.getId())) : 1f;
            int c = Colors.mulAlpha(v ? color.get() : other.get(), a);
            d.ring(x, y + 0.02, z, 0.2, 0.32, 32, Colors.mulAlpha(c, 0.2f), c);
            if (v) d.beam(x, y, z, 1.6, 0.12, Colors.mulAlpha(c, 0.7f));
        }
    }
}

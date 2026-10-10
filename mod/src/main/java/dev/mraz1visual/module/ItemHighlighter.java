package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.mixin.ContainerScreenAccessor;
import dev.mraz1visual.setting.BoolSetting;
import dev.mraz1visual.setting.ColorSetting;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Set;

/** Подсвечивает ценное в любом сундуке и инвентаре: тотемы, чарки, незерит, элитры, шалкеры. */
public final class ItemHighlighter extends Module {
    private static ItemHighlighter instance;
    final BoolSetting totems = add(new BoolSetting("totems", "Тотемы", true));
    final BoolSetting gapples = add(new BoolSetting("gapples", "Золотые яблоки", true));
    final BoolSetting netherite = add(new BoolSetting("netherite", "Незерит и элитры", true));
    final BoolSetting shulkers = add(new BoolSetting("shulkers", "Шалкеры", true));
    final BoolSetting pearls = add(new BoolSetting("pearls", "Жемчуг", false));
    final ColorSetting color = add(new ColorSetting("color", "Цвет", 0x50B8B4F2));

    private static Set<Item> netheriteSet;

    private static Set<Item> netherite() {
        if (netheriteSet == null) netheriteSet = Set.of(Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS,
            Items.NETHERITE_BOOTS, Items.NETHERITE_SWORD, Items.NETHERITE_AXE, Items.NETHERITE_PICKAXE, Items.NETHERITE_INGOT, Items.ELYTRA);
        return netheriteSet;
    }

    public ItemHighlighter() {
        super("item_highlighter", "Item Highlighter", "Подсветка ценных предметов в сундуках.", Category.VISUALS);
        instance = this;
    }

    private boolean matches(ItemStack s) {
        Item i = s.getItem();
        if (totems.get() && i == Items.TOTEM_OF_UNDYING) return true;
        if (gapples.get() && (i == Items.GOLDEN_APPLE || i == Items.ENCHANTED_GOLDEN_APPLE)) return true;
        if (netherite.get() && netherite().contains(i)) return true;
        if (pearls.get() && i == Items.ENDER_PEARL) return true;
        return shulkers.get() && net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(i).getPath().endsWith("shulker_box");
    }

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!(screen instanceof AbstractContainerScreen<?> cs)) return;
            ScreenEvents.afterRender(screen).register((s, g, mx, my, delta) -> {
                ItemHighlighter hl = instance;
                if (hl == null || !hl.isEnabled()) return;
                ContainerScreenAccessor acc = (ContainerScreenAccessor) cs;
                int c = hl.color.get(), edge = c | 0xFF000000;
                for (Slot slot : cs.getMenu().slots) {
                    ItemStack st = slot.getItem();
                    if (st.isEmpty() || !hl.matches(st)) continue;
                    int x = acc.mraz1visual$leftPos() + slot.x, y = acc.mraz1visual$topPos() + slot.y;
                    g.fill(x, y, x + 16, y + 16, c);
                    g.fill(x, y + 15, x + 16, y + 16, edge);
                }
            });
        });
    }
}

package dev.umbra.module;

import dev.umbra.core.Category;
import dev.umbra.core.Module;
import dev.umbra.mixin.ContainerScreenAccessor;
import dev.umbra.setting.BoolSetting;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ShulkerBoxBlock;

/** Наведи на шалкер в любом инвентаре — над курсором появится его содержимое. */
public final class ShulkerPreview extends Module {
    private static ShulkerPreview instance;

    final BoolSetting tint = add(new BoolSetting("tint", "Цвет шалкера", true));

    private final NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);

    public ShulkerPreview() {
        super("shulker_preview", "Shulker Preview", "Содержимое шалкера при наведении в инвентаре.", Category.VISUALS);
        instance = this;
    }

    /** Подключается один раз при старте. Пока модуль выключен — ранний выход. */
    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (screen instanceof AbstractContainerScreen<?> cs) {
                ScreenEvents.afterRender(screen).register((s, g, mx, my, delta) -> {
                    ShulkerPreview sp = instance;
                    if (sp != null && sp.isEnabled()) sp.render(cs, g, mx, my);
                });
            }
        });
    }

    private void render(AbstractContainerScreen<?> screen, GuiGraphics g, int mx, int my) {
        Slot slot = ((ContainerScreenAccessor) screen).umbra$hoveredSlot();
        if (slot == null || !slot.hasItem()) return;
        ItemStack stack = slot.getItem();
        if (!(Block.byItem(stack.getItem()) instanceof ShulkerBoxBlock box)) return;
        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        if (contents == null) return;
        for (int i = 0; i < items.size(); i++) items.set(i, ItemStack.EMPTY);
        contents.copyInto(items);

        int w = 9 * 18 + 8, h = 3 * 18 + 8;
        int x = Math.min(mx + 8, g.guiWidth() - w - 4);
        int y = Math.max(4, my - h - 12);

        int bg = 0xF00C0C12, edge = 0x30FFFFFF;
        if (tint.get()) {
            DyeColor dye = box.getColor();
            if (dye != null) edge = 0xC0000000 | (dye.getTextureDiffuseColor() & 0xFFFFFF);
        }
        g.nextStratum();
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, edge);
        g.fill(x, y, x + w, y + h, bg);
        Minecraft mc = Minecraft.getInstance();
        for (int i = 0; i < 27; i++) {
            int sx = x + 4 + (i % 9) * 18, sy = y + 4 + (i / 9) * 18;
            g.fill(sx, sy, sx + 16, sy + 16, 0x18FFFFFF);
            ItemStack it = items.get(i);
            if (it.isEmpty()) continue;
            g.renderItem(it, sx, sy);
            g.renderItemDecorations(mc.font, it, sx, sy);
        }
    }
}

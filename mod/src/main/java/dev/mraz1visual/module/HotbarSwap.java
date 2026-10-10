package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.input.Keys;
import dev.mraz1visual.setting.EnumSetting;
import dev.mraz1visual.setting.KeySetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.ClickType;

/** Меняет хотбар местами со строкой инвентаря по клавише. */
public final class HotbarSwap extends Module {
    final KeySetting key = add(new KeySetting("key", "Клавиша", KeySetting.NONE));
    final EnumSetting row = add(new EnumSetting("row", "Со строкой", 2, "1", "Верхней", "2", "Средней", "3", "Нижней"));
    private boolean was;

    public HotbarSwap() {
        super("hotbar_swap", "Hotbar Swap", "Поменять хотбар со строкой инвентаря по клавише.", Category.PLAYER);
    }

    @Override
    public void onTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        boolean down = p != null && mc.gameMode != null && mc.screen == null && Keys.isDown(key.get());
        if (down && !was) {
            int r = row.get();
            for (int i = 0; i < 9; i++) {
                mc.gameMode.handleInventoryMouseClick(p.inventoryMenu.containerId, 9 + r * 9 + i, i, ClickType.SWAP, p);
            }
        }
        was = down;
    }
}

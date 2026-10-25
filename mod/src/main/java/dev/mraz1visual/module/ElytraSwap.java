package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.input.Keys;
import dev.mraz1visual.setting.BoolSetting;
import dev.mraz1visual.setting.KeySetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** По клавише меняет нагрудник на элитры и обратно. */
public final class ElytraSwap extends Module {
    /** Слот нагрудника в меню инвентаря игрока. */
    private static final int CHEST_MENU_SLOT = 6;

    final KeySetting key = add(new KeySetting("key", "Клавиша", KeySetting.NONE));
    final BoolSetting firework = add(new BoolSetting("firework", "Фейерверк в руку после элитр", true));
    private boolean was;

    private static Item[] chestplates;

    public ElytraSwap() {
        super("elytra_swap", "Elytra Swap", "Нагрудник ⇄ элитры по клавише.", Category.PVP);
    }

    private static Item[] chestplates() {
        if (chestplates == null) chestplates = new Item[]{Items.NETHERITE_CHESTPLATE, Items.DIAMOND_CHESTPLATE,
            Items.IRON_CHESTPLATE, Items.CHAINMAIL_CHESTPLATE, Items.GOLDEN_CHESTPLATE, Items.LEATHER_CHESTPLATE};
        return chestplates;
    }

    @Override
    public void onTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        boolean down = p != null && mc.gameMode != null && mc.screen == null && Keys.isDown(key.get());
        if (down && !was) swap(mc, p);
        was = down;
    }

    private void swap(Minecraft mc, LocalPlayer p) {
        ItemStack chest = p.getItemBySlot(EquipmentSlot.CHEST);
        boolean wearingElytra = chest.is(Items.ELYTRA);
        int slot = -1;
        if (wearingElytra) {
            for (Item it : chestplates()) {
                slot = InvUtil.find(p, it, 0, 36);
                if (slot >= 0) break;
            }
        } else {
            slot = InvUtil.find(p, Items.ELYTRA, 0, 36);
        }
        if (slot < 0) return;

        int id = p.inventoryMenu.containerId;
        if (slot < 9) {
            // как клавиша 1–9 над слотом нагрудника
            mc.gameMode.handleInventoryMouseClick(id, CHEST_MENU_SLOT, slot, ClickType.SWAP, p);
        } else {
            int from = InvUtil.menuSlot(slot);
            mc.gameMode.handleInventoryMouseClick(id, from, 0, ClickType.PICKUP, p);
            mc.gameMode.handleInventoryMouseClick(id, CHEST_MENU_SLOT, 0, ClickType.PICKUP, p);
            mc.gameMode.handleInventoryMouseClick(id, from, 0, ClickType.PICKUP, p);
        }

        if (!wearingElytra && firework.get()) {
            int fw = InvUtil.find(p, Items.FIREWORK_ROCKET, 0, 9);
            if (fw >= 0) p.getInventory().setSelectedSlot(fw);
        }
    }
}

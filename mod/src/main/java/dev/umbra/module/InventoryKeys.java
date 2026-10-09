package dev.umbra.module;

import dev.umbra.core.Category;
import dev.umbra.core.Module;
import dev.umbra.input.Keys;
import dev.umbra.setting.BoolSetting;
import dev.umbra.setting.EnumSetting;
import dev.umbra.setting.KeySetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

/**
 * Клавиши-макросы инвентаря. Каждое действие — только по твоему нажатию:
 *  - Offhand Totem: тотем из инвентаря во вторую руку;
 *  - Click Pearl: бросить жемчуг из хотбара и вернуть слот;
 *  - Hotbar Swap: поменять хотбар со строкой инвентаря.
 */
public final class InventoryKeys extends Module {
    final KeySetting totemKey = add(new KeySetting("totem_key", "Тотем во вторую руку", GLFW.GLFW_KEY_R).group("Offhand Totem"));
    final KeySetting pearlKey = add(new KeySetting("pearl_key", "Бросить жемчуг", KeySetting.NONE).group("Click Pearl"));
    final BoolSetting pearlBack = add(new BoolSetting("pearl_back", "Вернуть слот после броска", true).group("Click Pearl"));
    final KeySetting swapKey = add(new KeySetting("swap_key", "Поменять хотбар", KeySetting.NONE).group("Hotbar Swap"));
    final EnumSetting swapRow = add(new EnumSetting("swap_row", "Со строкой", 2, "1", "Верхней", "2", "Средней", "3", "Нижней").group("Hotbar Swap"));

    private boolean totemWas, pearlWas, swapWas;

    public InventoryKeys() {
        super("inventory_keys", "Inventory Keys", "Offhand Totem, Click Pearl и Hotbar Swap — по твоим клавишам.", Category.PLAYER);
    }

    @Override
    public void onTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        boolean free = p != null && mc.gameMode != null && mc.screen == null;

        boolean t = free && Keys.isDown(totemKey.get());
        if (t && !totemWas) totem(mc, p);
        totemWas = t;

        boolean pe = free && Keys.isDown(pearlKey.get());
        if (pe && !pearlWas) pearl(mc, p);
        pearlWas = pe;

        boolean s = free && Keys.isDown(swapKey.get());
        if (s && !swapWas) swap(mc, p);
        swapWas = s;
    }

    /** Слот инвентаря (0–35) → номер слота в меню инвентаря игрока. */
    private static int menuSlot(int inv) {
        return inv < 9 ? 36 + inv : inv;
    }

    private static int find(LocalPlayer p, Item item, int from, int to) {
        for (int i = from; i < to; i++) if (p.getInventory().getItem(i).getItem() == item) return i;
        return -1;
    }

    private void totem(Minecraft mc, LocalPlayer p) {
        if (p.getOffhandItem().getItem() == Items.TOTEM_OF_UNDYING) return;
        int slot = find(p, Items.TOTEM_OF_UNDYING, 9, 36);
        if (slot < 0) slot = find(p, Items.TOTEM_OF_UNDYING, 0, 9);
        if (slot < 0) return;
        // кнопка 40 = вторая рука (как клавиша F в инвентаре)
        mc.gameMode.handleInventoryMouseClick(p.inventoryMenu.containerId, menuSlot(slot), 40, ClickType.SWAP, p);
    }

    private void pearl(Minecraft mc, LocalPlayer p) {
        int slot = find(p, Items.ENDER_PEARL, 0, 9);
        if (slot < 0 || p.getCooldowns().isOnCooldown(p.getInventory().getItem(slot))) return;
        int prev = p.getInventory().getSelectedSlot();
        p.getInventory().setSelectedSlot(slot);
        mc.gameMode.useItem(p, InteractionHand.MAIN_HAND);
        if (pearlBack.get()) p.getInventory().setSelectedSlot(prev);
    }

    private void swap(Minecraft mc, LocalPlayer p) {
        int row = swapRow.get(); // 0..2 → строки инвентаря 9..35
        for (int i = 0; i < 9; i++) {
            mc.gameMode.handleInventoryMouseClick(p.inventoryMenu.containerId, 9 + row * 9 + i, i, ClickType.SWAP, p);
        }
    }
}

package dev.umbra.module;

import dev.umbra.core.Category;
import dev.umbra.core.Module;
import dev.umbra.input.Keys;
import dev.umbra.setting.KeySetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

/** По нажатию клавиши кладёт тотем из инвентаря во вторую руку. Только по твоему нажатию. */
public final class OffhandTotem extends Module {
    final KeySetting key = add(new KeySetting("key", "Клавиша", GLFW.GLFW_KEY_R));
    private boolean was;

    public OffhandTotem() {
        super("offhand_totem", "Offhand Totem", "Тотем из инвентаря во вторую руку по клавише.", Category.PLAYER);
    }

    @Override
    public void onTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        boolean down = p != null && mc.gameMode != null && mc.screen == null && Keys.isDown(key.get());
        if (down && !was) run(mc, p);
        was = down;
    }

    private static void run(Minecraft mc, LocalPlayer p) {
        if (p.getOffhandItem().getItem() == Items.TOTEM_OF_UNDYING) return;
        int slot = InvUtil.find(p, Items.TOTEM_OF_UNDYING, 9, 36);
        if (slot < 0) slot = InvUtil.find(p, Items.TOTEM_OF_UNDYING, 0, 9);
        if (slot < 0) return;
        // кнопка 40 = вторая рука (как клавиша F в инвентаре)
        mc.gameMode.handleInventoryMouseClick(p.inventoryMenu.containerId, InvUtil.menuSlot(slot), 40, ClickType.SWAP, p);
    }
}

package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.input.Keys;
import dev.mraz1visual.setting.BoolSetting;
import dev.mraz1visual.setting.KeySetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;

/**
 * Эндер-жемчуг по клавише: одно нажатие — один бросок, как если бы ты сам выбрал слот и нажал ПКМ.
 * Жемчуг берётся из хотбара, потом слот возвращается. Удержание клавиши ничего не повторяет.
 */
public final class KeyPearl extends Module {
    final KeySetting key = add(new KeySetting("key", "Клавиша броска", KeySetting.NONE));
    final BoolSetting back = add(new BoolSetting("back", "Вернуть прежний слот", true));
    private boolean was;
    private int restore = -1;

    public KeyPearl() {
        super("key_pearl", "Key Pearl", "Бросок эндер-жемчуга по клавише из любого слота хотбара.", Category.PVP);
    }

    @Override
    protected void onDisable() {
        restore = -1;
    }

    @Override
    public void onTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (restore >= 0 && p != null) {
            // через тик: сервер уже получил бросок с выбранным слотом
            p.getInventory().setSelectedSlot(restore);
            restore = -1;
        }
        boolean down = p != null && mc.gameMode != null && mc.screen == null && Keys.isDown(key.get());
        if (down && !was) throwPearl(mc, p);
        was = down;
    }

    private void throwPearl(Minecraft mc, LocalPlayer p) {
        int slot = InvUtil.find(p, Items.ENDER_PEARL, 0, 9);
        if (slot < 0) return;
        int prev = p.getInventory().getSelectedSlot();
        p.getInventory().setSelectedSlot(slot);
        mc.gameMode.useItem(p, InteractionHand.MAIN_HAND);
        if (back.get() && prev != slot) restore = prev;
    }
}

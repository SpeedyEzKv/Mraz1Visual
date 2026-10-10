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

/** Бросок жемчуга из хотбара по клавише, слот возвращается обратно. */
public final class ClickPearl extends Module {
    final KeySetting key = add(new KeySetting("key", "Клавиша", KeySetting.NONE));
    final BoolSetting back = add(new BoolSetting("back", "Вернуть слот после броска", true));
    private boolean was;

    public ClickPearl() {
        super("click_pearl", "Click Pearl", "Бросить жемчуг из хотбара по клавише.", Category.PLAYER);
    }

    @Override
    public void onTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        boolean down = p != null && mc.gameMode != null && mc.screen == null && Keys.isDown(key.get());
        if (down && !was) {
            int slot = InvUtil.find(p, Items.ENDER_PEARL, 0, 9);
            if (slot >= 0 && !p.getCooldowns().isOnCooldown(p.getInventory().getItem(slot))) {
                int prev = p.getInventory().getSelectedSlot();
                p.getInventory().setSelectedSlot(slot);
                mc.gameMode.useItem(p, InteractionHand.MAIN_HAND);
                if (back.get()) p.getInventory().setSelectedSlot(prev);
            }
        }
        was = down;
    }
}

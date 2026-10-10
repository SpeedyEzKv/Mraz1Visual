package dev.umbra.module;

import dev.umbra.core.Category;
import dev.umbra.core.Module;
import dev.umbra.setting.BoolSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/** Бег включается сам, когда идёшь вперёд, — как игровое «переключение бега». */
public final class AutoSprint extends Module {
    final BoolSetting swim = add(new BoolSetting("swim", "Плыть в воде", true));

    public AutoSprint() {
        super("auto_sprint", "Auto Sprint", "Бег включается сам, когда вы идёте вперёд, — как игровое переключение бега.", Category.PLAYER);
    }

    @Override
    public void onTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.screen != null && !mc.options.keyUp.isDown()) return;
        if (!mc.options.keyUp.isDown() || p.isShiftKeyDown() || p.horizontalCollision || p.isUsingItem()) return;
        if (p.getFoodData().getFoodLevel() <= 6 && !p.getAbilities().mayfly) return;
        if (p.isInWater() && !swim.get()) return;
        if (!p.isSprinting()) p.setSprinting(true);
    }
}

package dev.umbra.module;

import dev.umbra.core.Category;
import dev.umbra.core.Module;
import dev.umbra.setting.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;

/** Возрождение сразу после смерти, без экрана смерти. */
public final class AutoRespawn extends Module {
    final SliderSetting delay = add(new SliderSetting("delay", "Задержка", 0, 0, 3, 0.1, " с"));
    private long deadAt = -1;

    public AutoRespawn() {
        super("auto_respawn", "Auto Respawn", "Сразу возрождает после смерти.", Category.PLAYER);
    }

    @Override
    public void onTick(Minecraft mc) {
        if (mc.player == null || !(mc.screen instanceof DeathScreen)) {
            deadAt = -1;
            return;
        }
        long now = System.currentTimeMillis();
        if (deadAt < 0) deadAt = now;
        if (now - deadAt >= delay.get() * 1000) {
            mc.player.respawn();
            mc.setScreen(null);
            deadAt = -1;
        }
    }
}

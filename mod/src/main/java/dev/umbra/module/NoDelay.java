package dev.umbra.module;

import dev.umbra.core.Category;
import dev.umbra.core.Module;
import dev.umbra.mixin.MinecraftAccessor;
import dev.umbra.setting.EnumSetting;
import dev.umbra.setting.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

/**
 * No Delay: убирает паузу ПКМ (в ванилле 4 тика) — пока держишь ПКМ, блоки ставятся быстрее.
 * Работает только пока ты сам держишь кнопку.
 */
public final class NoDelay extends Module {
    final SliderSetting delay = add(new SliderSetting("delay", "Задержка", 1, 0, 3, 1, " тик.")
            .hint("0 — каждый тик (20 в секунду). В ванилле — 4. Античит может ругаться на 0, начни с 1–2."));
    final EnumSetting items = add(new EnumSetting("items", "Для чего", 0,
            "blocks", "Только блоки", "all", "Всё (блоки, бутылки опыта, снежки…)"));

    public NoDelay() {
        super("no_delay", "No Delay", "Быстрая установка блоков, пока держишь ПКМ.", Category.PLAYER);
    }

    @Override
    public void onTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.screen != null || !mc.options.keyUse.isDown()) return;
        if (items.getId().equals("blocks") && !isBlock(p.getMainHandItem()) && !isBlock(p.getOffhandItem())) return;
        MinecraftAccessor acc = (MinecraftAccessor) mc;
        // Счётчик уменьшается в начале следующего тика, поэтому +1.
        int target = (int) Math.round(delay.get()) + 1;
        if (acc.umbra$getRightClickDelay() > target) acc.umbra$setRightClickDelay(target);
    }

    private static boolean isBlock(ItemStack s) {
        return !s.isEmpty() && s.getItem() instanceof BlockItem;
    }
}

package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.setting.BoolSetting;
import dev.mraz1visual.setting.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Set;

/**
 * Ест сам, когда голод падает ниже порога: берёт лучшую еду из хотбара,
 * держит ПКМ, пока не наестся, и возвращает слот.
 */
public final class AutoEat extends Module {
    final SliderSetting hunger = add(new SliderSetting("hunger", "Есть, когда голод ≤", 14, 1, 19, 1, ""));
    final BoolSetting gapples = add(new BoolSetting("gapples", "Можно есть золотые яблоки", false));
    final BoolSetting back = add(new BoolSetting("back", "Вернуть слот после еды", true));

    private static Set<Item> bad;
    private boolean eating;
    private int prevSlot = -1;

    public AutoEat() {
        super("auto_eat", "Auto Eat", "Сам ест еду из хотбара, когда проголодался.", Category.PLAYER);
    }

    private static Set<Item> bad() {
        if (bad == null) bad = Set.of(Items.ROTTEN_FLESH, Items.SPIDER_EYE, Items.POISONOUS_POTATO, Items.PUFFERFISH,
            Items.CHORUS_FRUIT, Items.SUSPICIOUS_STEW, Items.CHICKEN);
        return bad;
    }

    private boolean edible(ItemStack s) {
        if (s.isEmpty() || bad().contains(s.getItem())) return false;
        if (!gapples.get() && (s.is(Items.GOLDEN_APPLE) || s.is(Items.ENCHANTED_GOLDEN_APPLE))) return false;
        return s.get(DataComponents.FOOD) != null;
    }

    private int bestFood(LocalPlayer p) {
        int best = -1, bestScore = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (!edible(s)) continue;
            FoodProperties f = s.get(DataComponents.FOOD);
            int score = f == null ? 0 : f.nutrition();
            if (score > bestScore) { bestScore = score; best = i; }
        }
        return best;
    }

    @Override
    public void onTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.gameMode == null) { stop(mc); return; }
        int food = p.getFoodData().getFoodLevel();

        if (eating) {
            ItemStack held = p.getMainHandItem();
            if (mc.screen != null || food >= 20 || !edible(held)) {
                stop(mc);
                return;
            }
            mc.options.keyUse.setDown(true);
            return;
        }

        if (mc.screen != null || p.isUsingItem() || food > hunger.getI()) return;
        int slot = bestFood(p);
        if (slot < 0) return;
        prevSlot = p.getInventory().getSelectedSlot();
        p.getInventory().setSelectedSlot(slot);
        mc.options.keyUse.setDown(true);
        eating = true;
    }

    private void stop(Minecraft mc) {
        if (!eating) return;
        eating = false;
        mc.options.keyUse.setDown(false);
        if (back.get() && prevSlot >= 0 && mc.player != null) mc.player.getInventory().setSelectedSlot(prevSlot);
        prevSlot = -1;
    }

    @Override
    protected void onDisable() { stop(Minecraft.getInstance()); }
}

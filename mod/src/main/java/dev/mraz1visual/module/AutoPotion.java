package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.setting.BoolSetting;
import dev.mraz1visual.setting.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;

/**
 * Бросает взрывное зелье себе под ноги: лечение при низком здоровье
 * и (по желанию) силу / скорость / огнестойкость, когда эффекта нет.
 * Зелья берутся из хотбара, слот и взгляд возвращаются в том же тике.
 */
public final class AutoPotion extends Module {
    final SliderSetting health = add(new SliderSetting("health", "Лечиться при HP ≤", 8, 1, 19, 1, " HP"));
    final BoolSetting strength = add(new BoolSetting("strength", "Сила, если нет", false));
    final BoolSetting speed = add(new BoolSetting("speed", "Скорость, если нет", false));
    final BoolSetting fireRes = add(new BoolSetting("fire_res", "Огнестойкость, если нет", false));
    final BoolSetting onGround = add(new BoolSetting("on_ground", "Только стоя на земле", true));
    final SliderSetting delay = add(new SliderSetting("delay", "Пауза между бросками", 1, 0.3, 5, 0.1, " с"));

    private long lastThrow;

    public AutoPotion() {
        super("auto_potion", "Auto Potion", "Сам бросает взрывные зелья лечения и баффов из хотбара.", Category.PLAYER);
    }

    @Override
    public void onTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.gameMode == null || mc.screen != null || !p.isAlive() || p.isUsingItem()) return;
        if (onGround.get() && !p.onGround()) return;
        long now = System.currentTimeMillis();
        if (now - lastThrow < delay.get() * 1000) return;

        int slot = -1;
        if (p.getHealth() <= health.get()) slot = find(p, MobEffects.INSTANT_HEALTH);
        if (slot < 0 && p.getHealth() <= health.get()) slot = find(p, MobEffects.REGENERATION);
        if (slot < 0 && strength.get() && !p.hasEffect(MobEffects.STRENGTH)) slot = find(p, MobEffects.STRENGTH);
        if (slot < 0 && speed.get() && !p.hasEffect(MobEffects.SPEED)) slot = find(p, MobEffects.SPEED);
        if (slot < 0 && fireRes.get() && !p.hasEffect(MobEffects.FIRE_RESISTANCE)) slot = find(p, MobEffects.FIRE_RESISTANCE);
        if (slot < 0) return;

        int prevSlot = p.getInventory().getSelectedSlot();
        float prevPitch = p.getXRot();
        p.setXRot(90f);
        p.getInventory().setSelectedSlot(slot);
        mc.gameMode.useItem(p, InteractionHand.MAIN_HAND);
        p.getInventory().setSelectedSlot(prevSlot);
        p.setXRot(prevPitch);
        lastThrow = now;
    }

    /** Слот хотбара со взрывным зельем, в котором есть нужный эффект. */
    private static int find(LocalPlayer p, Holder<MobEffect> effect) {
        for (int i = 0; i < 9; i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (!s.is(Items.SPLASH_POTION)) continue;
            PotionContents pc = s.get(DataComponents.POTION_CONTENTS);
            if (pc == null) continue;
            for (MobEffectInstance e : pc.getAllEffects()) {
                if (e.getEffect().value() == effect.value()) return i;
            }
        }
        return -1;
    }
}

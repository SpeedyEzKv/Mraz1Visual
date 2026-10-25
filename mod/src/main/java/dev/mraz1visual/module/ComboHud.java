package dev.mraz1visual.module;

import dev.mraz1visual.core.AttackListener;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/** Комбо: удары по одной цели подряд, пока тебя не ударили. Сбрасывается через 3 секунды без ударов. */
public final class ComboHud extends TextHud implements AttackListener {
    private final IntText text = new IntText("");
    private int combo, lastHurt;
    private long lastHit;
    private Entity target;

    public ComboHud() {
        super("hud_combo", "Combo Counter", "Удары подряд по цели, пока тебя не ударили.", 1.0, 0.22);
        labels[0] = "Комбо";
    }

    @Override
    public void onAttack(Entity e) {
        if (!(e instanceof LivingEntity le) || le.hurtTime > 0) return; // в неуязвимости удар не засчитан
        combo = e == target ? combo + 1 : 1;
        target = e;
        lastHit = System.currentTimeMillis();
    }

    @Override
    protected void update(Minecraft mc) {
        if (mc.player != null) {
            int hurt = mc.player.hurtTime;
            if (hurt > lastHurt) combo = 0; // нас ударили
            lastHurt = hurt;
        }
        if (combo > 0 && System.currentTimeMillis() - lastHit > 3000) combo = 0;
        values[0] = text.of(combo);
    }
}

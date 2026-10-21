package dev.mraz1visual.mixin;

import dev.mraz1visual.module.TotemFx;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Totem FX: вместо ванильного облака зелёно-жёлтых частиц тотема — свой эффект (только картинка у тебя). */
@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin {
    @Inject(method = "createTrackingEmitter(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/particles/ParticleOptions;I)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void mraz1visual$totem(Entity e, ParticleOptions p, int lifetime, CallbackInfo ci) {
        if (p != ParticleTypes.TOTEM_OF_UNDYING) return;
        // тотем сработал (у своего игрока событие идёт мимо LivingEntity.handleEntityEvent — ловим здесь)
        if (e instanceof net.minecraft.world.entity.LivingEntity le) TotemFx.onPop(le);
        if (TotemFx.replacesVanilla()) ci.cancel();
    }
}

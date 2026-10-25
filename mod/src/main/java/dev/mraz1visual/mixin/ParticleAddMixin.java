package dev.mraz1visual.mixin;

import dev.mraz1visual.module.ParticleMultiplier;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Particle Multiplier: часть частиц не добавляется — меньше работы рендеру. */
@Mixin(ParticleEngine.class)
public abstract class ParticleAddMixin {
    @Inject(method = "add", at = @At("HEAD"), cancellable = true, require = 0)
    private void mraz1visual$limit(Particle p, CallbackInfo ci) {
        if (ParticleMultiplier.drop()) ci.cancel();
    }
}

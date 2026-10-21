package dev.mraz1visual.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.mraz1visual.module.Animations;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Animations: длительность взмаха только для нашего игрока и только на клиенте. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @ModifyReturnValue(method = "getCurrentSwingDuration", at = @At("RETURN"), require = 0)
    private int mraz1visual$swingDuration(int original) {
        if ((Object) this != Minecraft.getInstance().player) return original;
        return Animations.swingDuration(original);
    }

    /** Totem Pops: событие 35 — сработал тотем. */
    @org.spongepowered.asm.mixin.injection.Inject(method = "handleEntityEvent", at = @At("HEAD"), require = 0)
    private void mraz1visual$totem(byte id, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (id == 35) {
            dev.mraz1visual.module.TotemPops.onPop((LivingEntity) (Object) this);
            dev.mraz1visual.module.TotemFx.onPop((LivingEntity) (Object) this);
        }
        if (id == 3) dev.mraz1visual.module.KillEffect.onDeath((LivingEntity) (Object) this);
    }
}

package dev.umbra.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.umbra.module.Animations;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Animations: длительность взмаха только для нашего игрока и только на клиенте. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @ModifyReturnValue(method = "getCurrentSwingDuration", at = @At("RETURN"), require = 0)
    private int umbra$swingDuration(int original) {
        if ((Object) this != Minecraft.getInstance().player) return original;
        return Animations.swingDuration(original);
    }
}

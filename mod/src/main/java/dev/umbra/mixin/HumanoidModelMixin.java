package dev.umbra.mixin;

import dev.umbra.module.Emotes;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Emotes: поза модели твоего игрока (видна только тебе). */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"), require = 0)
    private void umbra$emote(HumanoidRenderState state, CallbackInfo ci) {
        HumanoidModel<?> m = (HumanoidModel<?>) (Object) this;
        Emotes.apply(state.x, state.y, state.z, m.head, m.body, m.rightArm, m.leftArm, m.rightLeg, m.leftLeg);
    }
}

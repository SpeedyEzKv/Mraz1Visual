package dev.mraz1visual.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.mraz1visual.module.Hands;
import dev.mraz1visual.module.SwingAnimations;
import dev.mraz1visual.module.ViewModel;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** View Model, Hands, Swing Animations. Если метод в игре переименуют — миксин просто не применится. */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
    @Inject(method = "renderHandsWithItems", at = @At("HEAD"), require = 0)
    private void mraz1visual$viewModelPush(CallbackInfo ci, @Local(argsOnly = true) PoseStack poseStack) {
        ViewModel.apply(poseStack);
    }

    @Inject(method = "renderHandsWithItems", at = @At("RETURN"), require = 0)
    private void mraz1visual$viewModelPop(CallbackInfo ci, @Local(argsOnly = true) PoseStack poseStack) {
        ViewModel.restore(poseStack);
    }

    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true, require = 0)
    private void mraz1visual$hands(CallbackInfo ci, @Local(argsOnly = true) InteractionHand hand, @Local(argsOnly = true) ItemStack stack) {
        if (Hands.hide(hand, stack)) ci.cancel();
    }

    @Inject(method = "applyItemArmAttackTransform", at = @At("HEAD"), cancellable = true, require = 0)
    private void mraz1visual$swing(PoseStack poseStack, HumanoidArm arm, float progress, CallbackInfo ci) {
        if (SwingAnimations.apply(poseStack, arm, progress)) ci.cancel();
    }
}

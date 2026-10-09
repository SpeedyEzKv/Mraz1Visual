package dev.umbra.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.umbra.module.ViewModel;
import net.minecraft.client.renderer.ItemInHandRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** View Model: сдвиг/масштаб рук. Если метод в игре переименуют — миксин просто не применится. */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
    @Inject(method = "renderHandsWithItems", at = @At("HEAD"), require = 0)
    private void umbra$viewModelPush(CallbackInfo ci, @Local(argsOnly = true) PoseStack poseStack) {
        ViewModel.apply(poseStack);
    }

    @Inject(method = "renderHandsWithItems", at = @At("RETURN"), require = 0)
    private void umbra$viewModelPop(CallbackInfo ci, @Local(argsOnly = true) PoseStack poseStack) {
        ViewModel.restore(poseStack);
    }
}

package dev.mraz1visual.mixin;

import dev.mraz1visual.module.PostFxModule;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Mraz1 PostFX: мир (с рукой) уже в кадре, HUD и меню — ещё нет. */
@Mixin(GameRenderer.class)
public abstract class GameRendererPostMixin {
    @Inject(method = "render(Lnet/minecraft/client/DeltaTracker;Z)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;renderLevel(Lnet/minecraft/client/DeltaTracker;)V", shift = At.Shift.AFTER), require = 0)
    private void mraz1visual$postFx(DeltaTracker delta, boolean tick, CallbackInfo ci) {
        PostFxModule.afterLevel(delta.getGameTimeDeltaPartialTick(false));
    }

    /** Глубина мира — до того, как игра очистит её под руку. */
    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/CommandEncoder;clearDepthTexture(Lcom/mojang/blaze3d/textures/GpuTexture;D)V"), require = 0)
    private void mraz1visual$depth(CallbackInfo ci) {
        PostFxModule.beforeHand();
    }
}

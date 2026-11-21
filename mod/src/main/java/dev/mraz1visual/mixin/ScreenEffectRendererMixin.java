package dev.mraz1visual.mixin;

import dev.mraz1visual.module.NoRender;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No Render: огонь на экране и анимация тотема. */
@Mixin(ScreenEffectRenderer.class)
public abstract class ScreenEffectRendererMixin {
    //? if >=26.2 {
    /*@Inject(method = "submitFire", at = @At("HEAD"), cancellable = true)
    *///?} else {
    @Inject(method = "renderFire", at = @At("HEAD"), cancellable = true)
    //?}
    private static void mraz1visual$noFire(CallbackInfo ci) {
        if (NoRender.hideFire()) ci.cancel();
    }

    //? if >=26.3 {
    /*@Inject(method = "renderItemActivationAnimation", at = @At("HEAD"), cancellable = true)
    *///?} else {
    @Inject(method = "displayItemActivation", at = @At("HEAD"), cancellable = true)
    //?}
    private void mraz1visual$noTotem(CallbackInfo ci) {
        if (NoRender.hideTotem()) ci.cancel();
    }
}

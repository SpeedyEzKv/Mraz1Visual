package dev.mraz1visual.mixin;

import dev.mraz1visual.module.NoRender;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No Render: анимация тотема. Если метод переименуют — просто не применится. */
@Mixin(GameRenderer.class)
public abstract class NoRenderMixins {
    @Inject(method = "displayItemActivation", at = @At("HEAD"), cancellable = true, require = 0)
    private void mraz1visual$noTotem(CallbackInfo ci) {
        if (NoRender.hideTotem()) ci.cancel();
    }
}

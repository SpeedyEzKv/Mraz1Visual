package dev.umbra.mixin;

import dev.umbra.module.NoRender;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No Render: огонь на экране и анимация тотема (в 1.21.6+ она живёт здесь). Нет метода — не применится. */
@Mixin(ScreenEffectRenderer.class)
public abstract class ScreenEffectRendererMixin {
    @Inject(method = "renderFire", at = @At("HEAD"), cancellable = true, require = 0)
    private static void umbra$noFire(CallbackInfo ci) {
        if (NoRender.hideFire()) ci.cancel();
    }

    @Inject(method = "displayItemActivation", at = @At("HEAD"), cancellable = true, require = 0)
    private void umbra$noTotem(CallbackInfo ci) {
        if (NoRender.hideTotem()) ci.cancel();
    }
}

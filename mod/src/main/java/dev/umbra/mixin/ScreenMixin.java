package dev.umbra.mixin;

import dev.umbra.module.BetterMinecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Better Minecraft: без размытия фона в меню. */
@Mixin(Screen.class)
public abstract class ScreenMixin {
    @Inject(method = "renderBlurredBackground", at = @At("HEAD"), cancellable = true, require = 0)
    private void umbra$noBlur(CallbackInfo ci) {
        if (BetterMinecraft.noMenuBlur()) ci.cancel();
    }
}

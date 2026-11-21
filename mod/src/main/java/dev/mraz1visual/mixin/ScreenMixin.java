package dev.mraz1visual.mixin;

import dev.mraz1visual.module.BetterMinecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Better Minecraft: без размытия фона в меню. */
@Mixin(Screen.class)
public abstract class ScreenMixin {
    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
    private void mraz$integrationBackground(net.minecraft.client.gui.GuiGraphics g, int mx, int my, float delta, CallbackInfo ci) {
        var screen = (Screen) (Object) this;
        if (!dev.mraz1visual.integration.IntegrationScreens.themed(screen)) return;
        dev.mraz1visual.integration.IntegrationTheme.background(g, screen.width, screen.height, mx, my);
        ci.cancel();
    }

    @Inject(method = "renderBlurredBackground", at = @At("HEAD"), cancellable = true, require = 0)
    private void mraz1visual$noBlur(CallbackInfo ci) {
        if (BetterMinecraft.noMenuBlur()) ci.cancel();
    }
}

package dev.mraz1visual.mixin;

import dev.mraz1visual.integration.IntegrationTheme;
import dev.mraz1visual.ui.menu.LoadingScreens;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({LevelLoadingScreen.class, GenericMessageScreen.class})
public abstract class LoadingBackgroundMixin {
    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
    private void mraz$loadingBackground(GuiGraphics g, int mx, int my, float delta, CallbackInfo ci) {
        var screen = (net.minecraft.client.gui.screens.Screen)(Object)this;
        if (!LoadingScreens.themed(screen)) return;
        IntegrationTheme.background(g, screen.width, screen.height, mx, my);
        ci.cancel();
    }
}

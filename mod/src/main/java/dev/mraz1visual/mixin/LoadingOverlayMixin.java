package dev.mraz1visual.mixin;

import dev.mraz1visual.ui.menu.LoadingView;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LoadingOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Свой экран загрузки вместо красной заставки Mojang (при запуске и смене ресурс-паков). */
@Mixin(LoadingOverlay.class)
public abstract class LoadingOverlayMixin {
    @Inject(method = "render", at = @At("TAIL"), require = 0)
    private void mraz1visual$loading(GuiGraphics g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        LoadingView.overlay(this, g);
    }
}

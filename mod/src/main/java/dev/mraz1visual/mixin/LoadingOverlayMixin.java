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
    @org.spongepowered.asm.mixin.Shadow private float currentProgress;
    @org.spongepowered.asm.mixin.Shadow private long fadeOutStart;
    @org.spongepowered.asm.mixin.Shadow private long fadeInStart;
    @org.spongepowered.asm.mixin.Shadow @org.spongepowered.asm.mixin.Final private boolean fadeIn;

    @Inject(method = "render", at = @At("TAIL"), require = 0)
    private void mraz1visual$loading(GuiGraphics g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        LoadingView.overlay(g, currentProgress, fadeOutStart, fadeInStart, fadeIn);
    }
}

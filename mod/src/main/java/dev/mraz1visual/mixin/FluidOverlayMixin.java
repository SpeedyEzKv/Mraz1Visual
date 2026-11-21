package dev.mraz1visual.mixin;

import dev.mraz1visual.module.NoFluids;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenEffectRenderer.class)
public abstract class FluidOverlayMixin {
    //? if >=26.2 {
    /*@Inject(method = "submitWater", at = @At("HEAD"), cancellable = true)
    *///?} else {
    @Inject(method = "renderWater", at = @At("HEAD"), cancellable = true)
    //?}
    private static void mraz$water(CallbackInfo ci){if(NoFluids.hide(FogType.WATER))ci.cancel();}

    //? if >=26.2 {
    /*@Inject(method = "submitBlockSprite", at = @At("HEAD"), cancellable = true)
    *///?} else {
    @Inject(method = "renderTex", at = @At("HEAD"), cancellable = true)
    //?}
    private static void mraz$lava(CallbackInfo ci){if(NoFluids.inHiddenLava())ci.cancel();}
}

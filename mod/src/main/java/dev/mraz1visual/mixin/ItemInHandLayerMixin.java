package dev.mraz1visual.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.mraz1visual.render.ItemFx;
import dev.mraz1visual.render.PlayerLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Свои мечи и аура от 3-го лица: предмет в руке модели рисуется в меню/инвентаре (ортографическая камера)
 * или в мире — от этого зависит, как разворачивать к камере спрайты свечения. Сама подмена — в ItemFx.
 */
@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerMixin {
    @Inject(method = "submitArmWithItem", at = @At("HEAD"), require = 0)
    private void mraz1visual$begin(CallbackInfo ci, @Local(argsOnly = true) ArmedEntityRenderState state) {
        ItemFx.guiPass = Boolean.TRUE.equals(state.getData(PlayerLayer.GUI));
    }

    @Inject(method = "submitArmWithItem", at = @At("RETURN"), require = 0)
    private void mraz1visual$end(CallbackInfo ci) {
        ItemFx.guiPass = false;
    }
}

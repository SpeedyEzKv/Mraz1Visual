package dev.mraz1visual.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.mraz1visual.render.ItemFx;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Свои мечи и аура: какой предмет сейчас отправляет слои модели. */
@Mixin(ItemStackRenderState.class)
public abstract class ItemStackRenderStateMixin {
    @WrapOperation(method = "submit", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState$LayerRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"),
            require = 0)
    private void mraz1visual$layer(ItemStackRenderState.LayerRenderState layer, PoseStack ps, SubmitNodeCollector c, int light, int overlay, int outline,
                                   Operation<Void> original) {
        ItemFx.begin((ItemStackRenderState) (Object) this);
        try {
            original.call(layer, ps, c, light, overlay, outline);
        } finally {
            ItemFx.end();
        }
    }
}

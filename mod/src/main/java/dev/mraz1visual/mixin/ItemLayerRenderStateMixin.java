package dev.mraz1visual.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.mraz1visual.render.ItemFx;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

/**
 * Свои мечи и аура: в момент отправки квадов модели предмета (поза уже в пространстве модели —
 * после раскладки в руке, взмаха и покачивания) подставить свой меч и нарисовать ауру по силуэту.
 */
@Mixin(ItemStackRenderState.LayerRenderState.class)
public abstract class ItemLayerRenderStateMixin {
    @WrapOperation(method = "submit", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitItem(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemDisplayContext;III[ILjava/util/List;Lnet/minecraft/client/renderer/rendertype/RenderType;Lnet/minecraft/client/renderer/item/ItemStackRenderState$FoilType;)V"),
            require = 0)
    private void mraz1visual$item(SubmitNodeCollector c, PoseStack ps, ItemDisplayContext ctx, int light, int overlay, int outline, int[] tints,
                                  List<BakedQuad> quads, RenderType type, ItemStackRenderState.FoilType foil, Operation<Void> original) {
        if (ItemFx.submitLayer(ps, c, ctx, quads)) return;
        original.call(c, ps, ctx, light, overlay, outline, tints, quads, type, foil);
    }
}

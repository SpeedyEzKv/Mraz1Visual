package dev.mraz1visual.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.mraz1visual.module.CustomSwords;
import dev.mraz1visual.module.FireHand;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fire Hand и Custom Swords: эффекты на предмете от 1-го лица и замена модели меча (только картинка). */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandFxMixin {
    @Inject(method = "renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void mraz1visual$itemFx(LivingEntity entity, ItemStack stack, ItemDisplayContext ctx, PoseStack ps,
                                    SubmitNodeCollector collector, int light, CallbackInfo ci) {
        FireHand.renderItem(entity, stack, ctx, ps, collector);
        if (CustomSwords.render(entity, stack, ctx, ps, collector)) ci.cancel();
    }
}

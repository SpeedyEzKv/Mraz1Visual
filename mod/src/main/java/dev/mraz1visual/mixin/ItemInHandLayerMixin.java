package dev.mraz1visual.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.mraz1visual.module.CustomSwords;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Custom Swords от 3-го лица: вместо модели меча в руке своего игрока — свой клинок (только картинка). */
@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerMixin {
    @WrapOperation(method = "submitArmWithItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"),
            require = 0)
    private void mraz1visual$sword(ItemStackRenderState item, PoseStack ps, SubmitNodeCollector c, int light, int overlay, int outline,
                                   Operation<Void> original, @Local(argsOnly = true) ArmedEntityRenderState state,
                                   @Local(argsOnly = true) ItemStack stack, @Local(argsOnly = true) HumanoidArm arm) {
        if (state instanceof AvatarRenderState a && CustomSwords.renderThird(a.id, stack, arm == HumanoidArm.LEFT, ps, c)) return;
        original.call(item, ps, c, light, overlay, outline);
    }
}

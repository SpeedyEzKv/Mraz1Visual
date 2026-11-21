package dev.mraz1visual.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.mraz1visual.module.ItemPhysic;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import org.joml.Quaternionfc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Item Physic: вместо подпрыгивания и вращения — предмет лежит плашмя. */
@Mixin(ItemEntityRenderer.class)
public abstract class ItemEntityRendererMixin {
    @WrapOperation(method = "submit", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V", ordinal = 0))
    private void mraz1visual$bob(PoseStack ps, float x, float y, float z, Operation<Void> original) {
        if (ItemPhysic.on()) original.call(ps, x, 0.06f, z);
        else original.call(ps, x, y, z);
    }

    //? if >=26.3 {
    /*@WrapOperation(method = "submit", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;rotate(Lcom/mojang/math/Axis;F)V"))
    private void mraz1visual$spin(PoseStack ps, Axis axis, float angle, Operation<Void> original) {
        if (ItemPhysic.on()) original.call(ps, Axis.XP, 90f);
        else original.call(ps, axis, angle);
    }
    *///?} else {
    @WrapOperation(method = "submit", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;mulPose(Lorg/joml/Quaternionfc;)V"))
    private void mraz1visual$spin(PoseStack ps, Quaternionfc q, Operation<Void> original) {
        if (ItemPhysic.on()) original.call(ps, (Quaternionfc) Axis.XP.rotationDegrees(90f));
        else original.call(ps, q);
    }

    //?}
}

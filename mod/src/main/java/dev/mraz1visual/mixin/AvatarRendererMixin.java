package dev.mraz1visual.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.mraz1visual.module.Hands;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hands → «Стеклянная рука»: рука от первого лица рисуется полупрозрачной.
 * Перехватываем отправку модели руки и подставляем прозрачный тип рендера + альфу.
 * Ванилла может вызывать любую из двух перегрузок — ловим обе; не нашли — миксин молча не применится.
 */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
    @WrapOperation(method = "renderHand", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModelPart(Lnet/minecraft/client/model/geom/ModelPart;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IILnet/minecraft/client/renderer/texture/TextureAtlasSprite;)V"),
        require = 0)
    private void mraz1visual$glassHand(SubmitNodeCollector collector, ModelPart part, PoseStack pose, RenderType type, int light, int overlay,
                                       TextureAtlasSprite sprite, Operation<Void> original, @Local(argsOnly = true) Identifier skin) {
        int tint = Hands.glassTint();
        if (tint == -1) {
            original.call(collector, part, pose, type, light, overlay, sprite);
            return;
        }
        //? if >=26.2 {
        /*collector.submitModelPart(part, pose, RenderTypes.entityTranslucent(skin), light, overlay, sprite, tint, null, 0);
        *///?} else {
        collector.submitModelPart(part, pose, RenderTypes.entityTranslucent(skin), light, overlay, sprite, false, false, tint, null, 0);
        //?}
    }

    @WrapOperation(method = "renderHand", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModelPart(Lnet/minecraft/client/model/geom/ModelPart;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IILnet/minecraft/client/renderer/texture/TextureAtlasSprite;ZZILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;I)V"),
        require = 0)
    private void mraz1visual$glassHandFull(SubmitNodeCollector collector, ModelPart part, PoseStack pose, RenderType type, int light, int overlay,
                                           TextureAtlasSprite sprite, boolean sheeted, boolean foil, int color,
                                           ModelFeatureRenderer.CrumblingOverlay crumbling, int outline,
                                           Operation<Void> original, @Local(argsOnly = true) Identifier skin) {
        int tint = Hands.glassTint();
        if (tint == -1) {
            original.call(collector, part, pose, type, light, overlay, sprite, sheeted, foil, color, crumbling, outline);
            return;
        }
        original.call(collector, part, pose, RenderTypes.entityTranslucent(skin), light, overlay, sprite, sheeted, foil, tint, crumbling, outline);
    }

}

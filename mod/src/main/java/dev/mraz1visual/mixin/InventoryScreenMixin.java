package dev.mraz1visual.mixin;

import dev.mraz1visual.ui.PlayerPreview;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Модель в инвентаре и в превью меню: метка «это меню» для слоя косметики (питомец рядом с моделью)
 * и поворот модели мышкой в превью. Состояние создаётся заново на каждую отрисовку — метка не утекает в мир.
 */
@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin {
    @ModifyArg(method = "renderEntityInInventoryFollowsMouse", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;submitEntityRenderState(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;FLorg/joml/Vector3f;Lorg/joml/Quaternionf;Lorg/joml/Quaternionf;IIII)V"),
            index = 0, require = 0)
    private static EntityRenderState mraz1visual$preview(EntityRenderState state) {
        return PlayerPreview.tag(state);
    }
}

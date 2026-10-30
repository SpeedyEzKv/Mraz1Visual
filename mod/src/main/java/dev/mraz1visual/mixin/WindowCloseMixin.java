package dev.mraz1visual.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.blaze3d.platform.Window;
import dev.mraz1visual.module.PvpSafe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** PvP Safe: закрытие окна в бою — сначала вопрос «Остаться / Всё равно выйти». */
@Mixin(Window.class)
public abstract class WindowCloseMixin {
    //? if >=26.3 {
    /*@org.spongepowered.asm.mixin.Shadow private boolean shouldClose;
    *///?}

    @ModifyReturnValue(method = "shouldClose", at = @At("RETURN"), require = 0)
    private boolean mraz1visual$pvpSafe(boolean close) {
        if (!close || !PvpSafe.blocksWindowClose()) return close;
        // снять флаг закрытия, иначе игра спросит снова в следующем кадре
        //? if >=26.3 {
        /*shouldClose = false;
        *///?} else {
        org.lwjgl.glfw.GLFW.glfwSetWindowShouldClose(((Window) (Object) this).handle(), false);
        //?}
        return false;
    }
}

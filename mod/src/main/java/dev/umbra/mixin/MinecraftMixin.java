package dev.umbra.mixin;

import dev.umbra.ui.menu.MenuRouter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Подменяет ванильное главное меню и список серверов на свои. Больше ничего. */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private Screen umbra$route(Screen screen) {
        return MenuRouter.route((Minecraft) (Object) this, screen);
    }

    /** Заголовок окна: «Umbra 0.1.x» вместо «Minecraft* 1.21.11 — Одиночная игра». */
    @Inject(method = "createTitle", at = @At("HEAD"), cancellable = true, require = 0)
    private void umbra$title(CallbackInfoReturnable<String> cir) {
        cir.setReturnValue(dev.umbra.WindowBranding.title());
    }
}

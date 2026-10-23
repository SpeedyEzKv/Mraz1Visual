package dev.mraz1visual.mixin;

import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;

/** 26.2+: свои меню вместо ванильных — экраны теперь ставит Gui.setScreen (до 26.2 — MinecraftMixin). */
@Mixin(Gui.class)
public abstract class GuiScreenMixin {
    //? if >=26.2 {
    /*@org.spongepowered.asm.mixin.injection.ModifyVariable(method = "setScreen", at = @org.spongepowered.asm.mixin.injection.At("HEAD"), argsOnly = true)
    private net.minecraft.client.gui.screens.Screen mraz1visual$route(net.minecraft.client.gui.screens.Screen screen) {
        return dev.mraz1visual.ui.menu.MenuRouter.route(net.minecraft.client.Minecraft.getInstance(), screen);
    }
    *///?}
}

package dev.mraz1visual.mixin;

import dev.mraz1visual.integration.IntegrationScreens;
import dev.mraz1visual.integration.IntegrationTheme;
import dev.mraz1visual.ui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Skin the list background, retaining native rows, selection, scrolling and input. */
@Mixin(AbstractSelectionList.class)
public abstract class IntegrationListMixin {
    //? if >=26.1 {
    /*@Inject(method = "extractListBackground", at = @At("HEAD"), cancellable = true)
    *///?} else {
    @Inject(method = "renderListBackground", at = @At("HEAD"), cancellable = true)
    //?}
    private void mraz$background(GuiGraphics g,CallbackInfo ci){
        if(!IntegrationScreens.themed(Minecraft.getInstance().screen))return;
        var list=(AbstractSelectionList<?>)(Object)this;boolean previous=IntegrationTheme.begin();
        try{g.fill(list.getX(),list.getY(),list.getRight(),list.getBottom(),Theme.GLASS_PANEL);}finally{IntegrationTheme.end(previous);}
        ci.cancel();
    }
}

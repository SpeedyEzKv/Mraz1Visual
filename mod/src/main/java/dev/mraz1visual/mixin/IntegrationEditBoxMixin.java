package dev.mraz1visual.mixin;

import dev.mraz1visual.integration.IntegrationScreens;
import dev.mraz1visual.ui.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EditBox.class)
public abstract class IntegrationEditBoxMixin {
    @Unique private final Draw mraz$fieldDraw = new Draw();

    // Only the first bordered check selects the native background sprite. Keep
    // isBordered() unchanged for text insets, selection, caret and hit testing.
    @Redirect(method = "renderWidget", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/EditBox;isBordered()Z", ordinal = 0))
    private boolean mraz$nativeBackground(EditBox box) {
        return !IntegrationScreens.themed(Minecraft.getInstance().screen) && box.isBordered();
    }

    @Inject(method = "renderWidget", at = @At("HEAD"))
    private void mraz$field(GuiGraphics g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (!IntegrationScreens.themed(mc.screen)) return;
        EditBox box = (EditBox) (Object) this;
        boolean previous = dev.mraz1visual.integration.IntegrationTheme.begin();
        try {
        box.setTextColor(Theme.TEXT);
        box.setTextColorUneditable(Theme.TEXT_MUTED);
        // Keep the native border flag: it also controls the caret and text insets.
        Draw d = mraz$fieldDraw;
        d.begin(g, mc.font, 1);
        d.card(box.getX()-2, box.getY()-2, box.getWidth()+4, box.getHeight()+4, 7, Theme.FIELD,
                box.isFocused() ? Theme.accent() : Theme.CARD_EDGE);
        d.end();
        } finally { dev.mraz1visual.integration.IntegrationTheme.end(previous); }
    }
}

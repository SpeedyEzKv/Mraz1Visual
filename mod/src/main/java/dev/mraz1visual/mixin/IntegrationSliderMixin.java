package dev.mraz1visual.mixin;

import dev.mraz1visual.integration.IntegrationScreens;
import dev.mraz1visual.integration.IntegrationTheme;
import dev.mraz1visual.ui.Draw;
import dev.mraz1visual.ui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractSliderButton.class)
public abstract class IntegrationSliderMixin {
    @Shadow protected double value;
    @Unique private final Draw mraz$draw = new Draw();

    @Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true)
    private void mraz$slider(GuiGraphics g, int mx, int my, float delta, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (!IntegrationScreens.themed(mc.screen)) return;
        AbstractSliderButton slider = (AbstractSliderButton)(Object)this;
        int x = slider.getX(), y = slider.getY(), w = slider.getWidth(), h = slider.getHeight();
        boolean previous = IntegrationTheme.begin();
        try {
            Draw d = mraz$draw;
            d.begin(g, mc.font, 1);
            d.card(x, y, w, h, Math.min(8, h / 2), slider.isHoveredOrFocused() ? Theme.FIELD_HOVER : Theme.CARD,
                    slider.isFocused() ? Theme.accent() : Theme.CARD_EDGE);
            int span = Math.max(0, w - 12), thumb = (int)Math.round(span * value);
            int color = slider.active ? Theme.accent() : Theme.TEXT_MUTED;
            d.round(x + 6, y + h - 5, span, 2, 1, Theme.ELEVATED);
            if (thumb > 0) d.round(x + 6, y + h - 5, thumb, 2, 1, color);
            d.round(x + 4 + thumb, y + h - 6, 4, 4, 2, color);
            IntegrationTheme.label(g, mc.font, slider.getMessage(), x + w / 2, y + (h - 12) / 2,
                    w - 8, slider.active ? Theme.TEXT : Theme.TEXT_MUTED);
            d.end();
        } finally { IntegrationTheme.end(previous); }
        ci.cancel();
    }
}

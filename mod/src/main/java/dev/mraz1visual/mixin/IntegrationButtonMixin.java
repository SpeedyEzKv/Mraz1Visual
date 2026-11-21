package dev.mraz1visual.mixin;

import dev.mraz1visual.integration.IntegrationScreens;
import dev.mraz1visual.ui.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractButton.class)
public abstract class IntegrationButtonMixin {
    @Unique private float mraz$hover;
    @Unique private long mraz$last;
    @Unique private final Draw mraz$draw = new Draw();

    @Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true)
    private void mraz$skin(GuiGraphics g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (!IntegrationScreens.themed(mc.screen)) return;
        AbstractButton b = (AbstractButton) (Object) this;
        String label = b.getMessage().getString();
        if (label.isBlank() || b.getWidth() < 36 || b.getWidth() < b.getHeight() * 1.5f) return; // keep upstream icon and toggle renderers
        long now = System.nanoTime();
        float dt = mraz$last == 0 ? 0 : Math.min(.1f, (now-mraz$last)/1e9f);
        mraz$last = now;
        float target = b.isHoveredOrFocused() && b.active ? 1 : 0;
        mraz$hover = dev.mraz1visual.integration.IntegrationTheme.animated() ? Anim.to(mraz$hover, target, dt) : target;
        boolean previous = dev.mraz1visual.integration.IntegrationTheme.begin();
        try {
        Draw d = mraz$draw;
        d.begin(g, mc.font, 1);
        d.card(b.getX(), b.getY(), b.getWidth(), b.getHeight(), Math.min(8,b.getHeight()/2),
                Colors.lerp(Theme.CARD, Theme.FIELD_HOVER, mraz$hover), b.isFocused() ? Theme.accent() : Theme.CARD_EDGE);
        d.clip(b.getX()+3, b.getY(), b.getWidth()-6, b.getHeight());
        dev.mraz1visual.integration.IntegrationTheme.label(g, mc.font, b.getMessage(), b.getX()+b.getWidth()/2, b.getY()+(b.getHeight()-mc.font.lineHeight)/2, b.getWidth()-10, b.active ? Theme.TEXT : Theme.TEXT_MUTED);
        d.unclip();
        d.end();
        } finally { dev.mraz1visual.integration.IntegrationTheme.end(previous); }
        ci.cancel();
    }
}

package dev.mraz1visual.render;

import dev.mraz1visual.Mraz1Visual;
import dev.mraz1visual.core.HudLayer;
import dev.mraz1visual.core.ModuleManager;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;

/** Один HUD-слой мода, внутри — все включённые HUD-модули. */
public final class HudDraw {
    private HudDraw() {}

    public static void register() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("mraz1visual", "hud"), HudDraw::render);
    }

    private static void render(GuiGraphics g, DeltaTracker delta) {
        HudLayer[] layers = Mraz1Visual.MODULES.hudLayers();
        if (layers.length == 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null) return;
        float pt = delta.getGameTimeDeltaPartialTick(true);
        dev.mraz1visual.render.HudPos.nextFrame();
        float hs = dev.mraz1visual.module.UiAnimations.hudScale(mc);
        boolean anim = hs < 0.999f;
        if (anim) {
            float cx = g.guiWidth() / 2f, cy = g.guiHeight() / 2f;
            g.pose().pushMatrix();
            g.pose().translate(cx, cy);
            g.pose().scale(hs, hs);
            g.pose().translate(-cx, -cy);
        }
        for (HudLayer l : layers) {
            try {
                l.renderHud(g, pt);
            } catch (Throwable t) {
                ModuleManager.LOG.error("HUD layer {} failed", l.getClass().getSimpleName(), t);
            }
        }
        if (anim) g.pose().popMatrix();
    }
}

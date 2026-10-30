package dev.mraz1visual.render;

import dev.mraz1visual.module.HealthBars;
import dev.mraz1visual.module.PotionsHud;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

/** Скрытие ванильных частей HUD, которые заменяют наши модули (сердца и еда, значки эффектов). */
public final class VanillaHudHooks {
    private VanillaHudHooks() {}

    public static void register() {
        HudElementRegistry.replaceElement(VanillaHudElements.HEALTH_BAR, original -> (g, delta) -> {
            if (!HealthBars.replacesVanilla()) original.render(g, delta);
        });
        HudElementRegistry.replaceElement(VanillaHudElements.FOOD_BAR, original -> (g, delta) -> {
            if (!HealthBars.replacesVanilla()) original.render(g, delta);
        });
        //? if >=26.1 {
        /*HudElementRegistry.replaceElement(VanillaHudElements.MOB_EFFECTS, original -> (g, delta) -> {
        *///?} else {
        HudElementRegistry.replaceElement(VanillaHudElements.STATUS_EFFECTS, original -> (g, delta) -> {
        //?}
            if (!PotionsHud.hidesVanilla()) original.render(g, delta);
        });
    }
}

package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.setting.BoolSetting;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

/**
 * Убрать мешающие эффекты: огонь на экране, анимацию тотема, оверлеи (тыква, снег, виньетка),
 * боссбар и табло. Всё — только картинка; то, что важно для игры (здоровье, эффекты), не трогаем.
 */
public final class NoRender extends Module {
    private static NoRender instance;

    final BoolSetting fire = add(new BoolSetting("fire", "Огонь на экране", true));
    final BoolSetting totem = add(new BoolSetting("totem", "Анимация тотема", false));
    final BoolSetting overlays = add(new BoolSetting("overlays", "Тыква, снег, виньетка", true));
    final BoolSetting bossBar = add(new BoolSetting("boss_bar", "Полоса босса", false));
    final BoolSetting scoreboard = add(new BoolSetting("scoreboard", "Табло справа", false));

    public NoRender() {
        super("no_render", "No Render", "Убирает огонь на экране, анимацию тотема и другие помехи.", Category.VISUALS);
        instance = this;
    }

    private static boolean on(BoolSetting s) {
        return instance != null && instance.isEnabled() && s.get();
    }

    public static boolean hideFire() { return instance != null && on(instance.fire); }
    public static boolean hideTotem() { return instance != null && on(instance.totem); }
    public static boolean hideScoreboard() { return instance != null && on(instance.scoreboard); }

    public static void register() {
        HudElementRegistry.replaceElement(VanillaHudElements.MISC_OVERLAYS, original -> (g, delta) -> {
            if (instance == null || !on(instance.overlays)) original.render(g, delta);
        });
        HudElementRegistry.replaceElement(VanillaHudElements.BOSS_BAR, original -> (g, delta) -> {
            if (instance == null || !on(instance.bossBar)) original.render(g, delta);
        });
        HudElementRegistry.replaceElement(VanillaHudElements.SCOREBOARD, original -> (g, delta) -> {
            if (instance == null || !on(instance.scoreboard)) original.render(g, delta);
        });
    }
}

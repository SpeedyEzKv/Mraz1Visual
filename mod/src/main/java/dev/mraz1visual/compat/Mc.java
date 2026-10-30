package dev.mraz1visual.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * Тонкий слой совместимости 1.21.11 ↔ 26.x: то, что нельзя выразить переименованием (VERSIONS.md).
 * Блоки {@code //? if} разворачивает Stonecutter при сборке каждой версии; в git файл лежит в состоянии 1.21.11.
 */
public final class Mc {
    private Mc() {}

    /** Полная яркость в упакованных координатах света (блок 15, небо 15). */
    public static final int FULL_BRIGHT = 0xF000F0;

    /** Формат RGBA8 для своих текстур (в 26.2 — GpuFormat, в 26.3 — пакет renderpearl). */
    //? if >=26.2 {
    /*public static final com.mojang.blaze3d.GpuFormat RGBA8 = com.mojang.blaze3d.GpuFormat.RGBA8_UNORM;
    *///?} else {
    public static final com.mojang.blaze3d.textures.TextureFormat RGBA8 = com.mojang.blaze3d.textures.TextureFormat.RGBA8;
    //?}

    /** Буфер глубины перевёрнут (reversed-Z: дальше = 0) — так с 26.2. */
    //? if >=26.2 {
    /*public static final boolean REVERSED_Z = true;
    *///?} else {
    public static final boolean REVERSED_Z = false;
    //?}

    /** Сообщение игроку: в чат или над хотбаром. Только локально — на сервер ничего не уходит. */
    public static void message(Player p, Component text, boolean overlay) {
        if (p == null) return;
        //? if >=26.1 {
        /*if (overlay) p.sendOverlayMessage(text);
        else p.sendSystemMessage(text);
        *///?} else {
        p.displayClientMessage(text, overlay);
        //?}
    }

    public static void message(Component text, boolean overlay) {
        message(Minecraft.getInstance().player, text, overlay);
    }

    /** Применить новый масштаб интерфейса. */
    public static void resizeGui(Minecraft mc) {
        //? if >=26.1 {
        /*mc.resizeGui();
        *///?} else {
        mc.resizeDisplay();
        //?}
    }

    /** Упакованный свет в точке мира (блок + небо). */
    public static int lightCoords(net.minecraft.client.multiplayer.ClientLevel level, net.minecraft.core.BlockPos pos) {
        //? if >=26.2 {
        /*return net.minecraft.util.LightCoordsUtil.getLightCoords(level, pos);
        *///?} else {
        return net.minecraft.client.renderer.LevelRenderer.getLightColor(level, pos);
        //?}
    }

    /** Взмах основной рукой (только анимация у себя). */
    public static void swing(net.minecraft.world.entity.player.Player p) {
        //? if >=26.3 {
        /*p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false);
        *///?} else {
        p.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        //?}
    }

    /** Системное сообщение в чат (только у себя). */
    public static void chat(Minecraft mc, Component text) {
        //? if >=26.2 {
        /*mc.gui.hud.getChat().addClientSystemMessage(text);
        *///?} elif >=26.1 {
        /*mc.gui.getChat().addClientSystemMessage(text);
        *///?} else {
        mc.gui.getChat().addMessage(text);
        //?}
    }

    /** Дальняя плоскость проекции мира (для линейной глубины в пост-обработке). */
    public static float depthFar(Minecraft mc) {
        //? if >=26.1 {
        /*return Math.max(64f, mc.options.getEffectiveRenderDistance() * 64f);
        *///?} else {
        return mc.gameRenderer.getDepthFar();
        //?}
    }

    /** Полосы боссов (в 26.2+ HUD — mc.gui.hud). */
    public static net.minecraft.client.gui.components.BossHealthOverlay bossOverlay(Minecraft mc) {
        //? if >=26.2 {
        /*return mc.gui.hud.getBossOverlay();
        *///?} else {
        return mc.gui.getBossOverlay();
        //?}
    }

    /** Значок эффекта для HUD (в 26.2+ HUD — класс Hud). */
    public static net.minecraft.resources.Identifier effectSprite(net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect) {
        //? if >=26.2 {
        /*return net.minecraft.client.gui.Hud.getMobEffectSprite(effect);
        *///?} else {
        return net.minecraft.client.gui.Gui.getMobEffectSprite(effect);
        //?}
    }

    /** Время мира в тиках с начала (для счётчика дней). В 26.x — игровое время. */
    public static long totalDayTime(net.minecraft.client.multiplayer.ClientLevel level) {
        //? if >=26.1 {
        /*return level.getGameTime();
        *///?} else {
        return level.getDayTime();
        //?}
    }

    /** Время суток в тиках (0 — рассвет, 6000 — полдень). В 26.x время идёт через «часы мира» — берём игровое. */
    public static long dayTime(net.minecraft.client.multiplayer.ClientLevel level) {
        //? if >=26.1 {
        /*return level.getGameTime() % 24000L;
        *///?} else {
        return level.getDayTime();
        //?}
    }
}

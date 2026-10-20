package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.setting.BoolSetting;
import dev.mraz1visual.setting.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;

/** Больше кадров: не рисует далёкие сущности, выброшенные предметы и стойки для брони. */
public final class Optimization extends Module {
    private static Optimization instance;
    final SliderSetting entities = add(new SliderSetting("entities", "Мобы дальше", 64, 16, 128, 4, " бл."));
    final SliderSetting items = add(new SliderSetting("items", "Предметы на земле дальше", 24, 4, 64, 2, " бл."));
    final BoolSetting stands = add(new BoolSetting("stands", "Прятать далёкие стойки для брони", true));
    final BoolSetting keepPlayers = add(new BoolSetting("keep_players", "Игроков рисовать всегда", true));
    /** Глубокая оптимизация — мод ZakoOpt (LGPL), лаунчер ставит его вместе с клиентом. */
    final dev.mraz1visual.setting.ActionSetting zako = add(new dev.mraz1visual.setting.ActionSetting("zakoopt",
            "Расширенная оптимизация (ZakoOpt)", "Настроить ›", Optimization::openZako));

    public Optimization() {
        super("optimization", "Optimization", "Не рисует далёкие мобы и предметы — больше FPS.", Category.MISC);
        instance = this;
    }

    /** Вызывается из миксина EntityRenderer.shouldRender. */
    public static boolean cull(Entity e) {
        Optimization o = instance;
        if (o == null || !o.isEnabled()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || e == mc.player || e == mc.getCameraEntity()) return false;
        if (e instanceof Player) return !o.keepPlayers.get() && far(mc, e, o.entities.get());
        if (e instanceof ItemEntity) return far(mc, e, o.items.get());
        if (e instanceof ArmorStand) return o.stands.get() && far(mc, e, 16);
        return far(mc, e, o.entities.get());
    }

    private static boolean far(Minecraft mc, Entity e, double dist) {
        return mc.player.distanceToSqr(e) > dist * dist;
    }

    /** Открыть настройки ZakoOpt, если мод установлен; иначе — подсказка в чат. */
    private static void openZako() {
        Minecraft mc = Minecraft.getInstance();
        if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("zakoopt")) {
            if (mc.player != null) mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "ZakoOpt не установлен — обнови клиент через лаунчер Mraz1Visual."), false);
            return;
        }
        try {
            Object screen = Class.forName("zako.opt.ZakoOptScreen")
                    .getConstructor(net.minecraft.client.gui.screens.Screen.class).newInstance(mc.screen);
            mc.setScreen((net.minecraft.client.gui.screens.Screen) screen);
        } catch (Throwable t) {
            dev.mraz1visual.core.ModuleManager.LOG.warn("Can't open ZakoOpt settings", t);
        }
    }
}

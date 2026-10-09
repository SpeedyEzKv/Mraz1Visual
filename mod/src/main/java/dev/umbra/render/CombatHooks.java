package dev.umbra.render;

import dev.umbra.Umbra;
import dev.umbra.core.AttackListener;
import dev.umbra.core.ModuleManager;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionResult;

/** Слушает наши удары. Ничего не меняет в ударе — только сообщает модулям. */
public final class CombatHooks {
    private CombatHooks() {}

    public static void register() {
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            AttackListener[] ls = Umbra.MODULES.attackListeners();
            if (ls.length == 0 || player != Minecraft.getInstance().player) return InteractionResult.PASS;
            for (AttackListener l : ls) {
                try {
                    l.onAttack(entity);
                } catch (Throwable t) {
                    ModuleManager.LOG.error("Attack listener failed", t);
                }
            }
            return InteractionResult.PASS;
        });
    }
}

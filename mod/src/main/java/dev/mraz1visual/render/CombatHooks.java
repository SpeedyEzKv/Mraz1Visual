package dev.mraz1visual.render;

import dev.mraz1visual.Mraz1Visual;
import dev.mraz1visual.core.AttackListener;
import dev.mraz1visual.core.ModuleManager;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionResult;

/** Слушает наши удары. Ничего не меняет в ударе — только сообщает модулям. */
public final class CombatHooks {
    private CombatHooks() {}

    public static void register() {
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            AttackListener[] ls = Mraz1Visual.MODULES.attackListeners();
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

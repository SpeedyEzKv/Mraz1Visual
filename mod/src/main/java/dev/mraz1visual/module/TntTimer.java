package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.core.WorldLayer;
import dev.mraz1visual.render.WorldDraw;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.PrimedTnt;

import java.util.Locale;

/** Сколько секунд осталось до взрыва подожжённого динамита — над ним. */
public final class TntTimer extends Module implements WorldLayer {
    public TntTimer() {
        super("tnt_timer", "TNT Timer", "Таймер над подожжённым динамитом.", Category.VISUALS);
    }

    @Override
    public void renderWorld(WorldDraw d, float pt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof PrimedTnt tnt) || e.distanceToSqr(mc.player) > 48 * 48) continue;
            float sec = Math.max(0, (tnt.getFuse() - pt) / 20f);
            ChatFormatting c = sec > 2 ? ChatFormatting.GREEN : sec > 1 ? ChatFormatting.YELLOW : ChatFormatting.RED;
            double x = e.xo + (e.getX() - e.xo) * pt, y = e.yo + (e.getY() - e.yo) * pt + 1.3, z = e.zo + (e.getZ() - e.zo) * pt;
            d.label(Component.literal(String.format(Locale.ROOT, "%.1f", sec)).withStyle(c), x, y, z, 1.2f, 0xFFFFFFFF, 0x70000000);
        }
    }
}

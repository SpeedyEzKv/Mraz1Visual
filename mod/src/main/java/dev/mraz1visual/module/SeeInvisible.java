package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/** Only changes the native local visibility test, never the actual invisibility effect. */
public final class SeeInvisible extends Module {
    private static SeeInvisible instance;
    public SeeInvisible(){super("see_invisible","See Invisible","Показывает невидимых игроков полупрозрачными (AuraVisual).",Category.VISUALS);instance=this;markNew();}
    public static boolean reveal(Entity entity){return instance!=null&&instance.isEnabled()&&entity instanceof Player player&&player.hasEffect(MobEffects.INVISIBILITY);}
}

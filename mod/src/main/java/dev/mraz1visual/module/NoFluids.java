package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.setting.BoolSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.material.FogType;

/** AuraVisual fluid fog/overlay controls. Fluid states and game physics are untouched. */
public final class NoFluids extends Module {
    private static NoFluids instance;
    private final BoolSetting water=add(new BoolSetting("water","Вода",true));
    private final BoolSetting lava=add(new BoolSetting("lava","Лава",true));
    public NoFluids(){super("no_fluids","No Fluids","Убирает туман и пелену воды/лавы без изменения блоков и физики (AuraVisual).",Category.VISUALS);instance=this;markNew();}
    public static boolean hide(FogType type){return instance!=null&&instance.isEnabled()&&
            (type==FogType.WATER&&instance.water.get()||type==FogType.LAVA&&instance.lava.get());}
    public static boolean inHiddenLava(){return hide(FogType.LAVA)&&Minecraft.getInstance().gameRenderer.getMainCamera().getFluidInCamera()==FogType.LAVA;}
}

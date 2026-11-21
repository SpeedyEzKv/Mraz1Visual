package dev.mraz1visual.module;

import dev.mraz1visual.core.*;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.render.WorldDraw;
import dev.mraz1visual.setting.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;

/** Actual worn armor values above the pointed/last attacked player (AuraVisual). */
public final class ArmorDurabilityTag extends Module implements WorldLayer,AttackListener {
    private static final EquipmentSlot[] SLOTS={EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET};
    private static final String[] NAMES={"Ш","Н","П","Б"};
    private final SliderSetting scale=add(new SliderSetting("scale","Масштаб",1,.6,1.8,.05,"×"));
    private final SliderSetting height=add(new SliderSetting("height","Высота",.55,.2,1.4,.05," бл."));
    private final SliderSetting range=add(new SliderSetting("range","Дистанция",8,3,16,.5," бл."));
    private Player target;private Component label;
    public ArmorDurabilityTag(){super("armor_durability_tag","Armor Durability","Прочность надетой брони над текущей целью (AuraVisual).",Category.VISUALS);markNew();}
    @Override protected void onDisable(){target=null;label=null;}
    @Override public void onAttack(Entity entity){if(entity instanceof Player player)target=player;}
    @Override public void onTick(Minecraft mc){
        if(mc.player==null||mc.level==null){onDisable();return;}
        if(mc.crosshairPickEntity instanceof Player pointed)target=pointed;
        if(target==null||target==mc.player||target.level()!=mc.level||!target.isAlive()||target.distanceToSqr(mc.player)>range.get()*range.get()){onDisable();return;}
        StringBuilder text=new StringBuilder();
        for(int i=0;i<SLOTS.length;i++){var item=target.getItemBySlot(SLOTS[i]);if(!item.isDamageableItem())continue;
            if(!text.isEmpty())text.append("  ");text.append(NAMES[i]).append(' ').append(Math.round(100f*(item.getMaxDamage()-item.getDamageValue())/item.getMaxDamage())).append('%');}
        label=text.isEmpty()?null:Component.literal(text.toString());
    }
    @Override public void renderWorld(WorldDraw d,float pt){if(target==null||label==null)return;
        d.label(label,target.xo+(target.getX()-target.xo)*pt,target.yo+(target.getY()-target.yo)*pt+target.getBbHeight()+height.get(),target.zo+(target.getZ()-target.zo)*pt,scale.getF(),-1,0xA00C0C12);
    }
}

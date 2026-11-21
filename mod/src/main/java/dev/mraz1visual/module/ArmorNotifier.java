package dev.mraz1visual.module;

import dev.mraz1visual.core.*;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.render.HudKit;
import dev.mraz1visual.setting.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.EquipmentSlot;

public final class ArmorNotifier extends Module implements HudLayer {
    private final SliderSetting threshold=add(new SliderSetting("threshold","Порог прочности",15,1,50,1," %"));
    private String warning="";
    public ArmorNotifier(){super("armor_notifier","Armor Notifier","Предупреждение о низкой прочности надетой брони (AuraVisual).",Category.HUD);}
    @Override public void onTick(Minecraft mc){
        warning="";if(mc.player==null)return;
        for(var slot:new EquipmentSlot[]{EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET}){
            var stack=mc.player.getItemBySlot(slot);if(!stack.isDamageableItem())continue;
            int percent=Math.round(100f*(stack.getMaxDamage()-stack.getDamageValue())/stack.getMaxDamage());
            if(percent<=threshold.getI()){warning=stack.getHoverName().getString()+": "+percent+"%";break;}
        }
    }
    @Override public void renderHud(GuiGraphics g,float pt){if(warning.isEmpty())return;int w=HudKit.width(warning)+20,x=(g.guiWidth()-w)/2,y=g.guiHeight()-90;HudKit.card(g,x,y,w,25,1);HudKit.bold(g,warning,x+10,y+8,0xFFFF6B7D);}
}

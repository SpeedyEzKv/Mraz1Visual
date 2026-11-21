package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.mixin.ContainerScreenAccessor;
import dev.mraz1visual.setting.*;
import dev.mraz1visual.ui.Draw;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** AuraVisual potion classification, integrated into actual native container slots. */
public final class PotionHighlight extends Module {
    private static PotionHighlight instance;
    private final EnumSetting mode=add(new EnumSetting("mode","Режим цвета",0,"category","Бафф / дебафф","effect","Цвет эффекта"));
    private final EnumSetting shape=add(new EnumSetting("shape","Форма",0,"square","Квадрат","circle","Круг"));
    private final ColorSetting buff=add(new ColorSetting("buff","Положительные эффекты",1146486664));
    private final ColorSetting debuff=add(new ColorSetting("debuff","Отрицательные эффекты",1442788181));
    private final SliderSetting alpha=add(new SliderSetting("alpha","Непрозрачность",110,0,255,5,""));
    private final BoolSetting outline=add(new BoolSetting("outline","Обводка",true));
    private final BoolSetting arrows=add(new BoolSetting("arrows","Стрелы с эффектами",true));
    private final Draw draw=new Draw();
    public PotionHighlight(){super("potion_highlight","Potion Highlight","Цветная подсветка зелий и стрел по реальным эффектам (AuraVisual).",Category.VISUALS);instance=this;markNew();}
    public static int highlightColor(ItemStack stack){
        PotionHighlight m=instance;if(m==null||!m.isEnabled()||stack==null||stack.isEmpty())return 0;
        if(!stack.is(Items.POTION)&&!stack.is(Items.SPLASH_POTION)&&!stack.is(Items.LINGERING_POTION)&&!(m.arrows.get()&&stack.is(Items.TIPPED_ARROW)))return 0;
        var contents=stack.get(DataComponents.POTION_CONTENTS);if(contents==null)return 0;
        int rgb=contents.getColor();
        if(m.mode.getId().equals("category")){
            boolean good=false,bad=false;
            for(var effect:contents.getAllEffects()){var category=effect.getEffect().value().getCategory();good|=category==MobEffectCategory.BENEFICIAL;bad|=category==MobEffectCategory.HARMFUL;}
            if(bad)rgb=m.debuff.get();else if(good)rgb=m.buff.get();
        }return (m.alpha.getI()<<24)|(rgb&0xFFFFFF);
    }
    public static void register(){
        ScreenEvents.AFTER_INIT.register((mc,screen,w,h)->{
            if(!(screen instanceof AbstractContainerScreen<?> container))return;
            ScreenEvents.afterRender(screen).register((s,g,mx,my,pt)->{
                PotionHighlight m=instance;if(m==null||!m.isEnabled())return;
                var access=(ContainerScreenAccessor)container;m.draw.begin(g,Minecraft.getInstance().font,1);
                for(var slot:container.getMenu().slots){int c=highlightColor(slot.getItem());if(c==0)continue;
                    int x=access.mraz1visual$leftPos()+slot.x,y=access.mraz1visual$topPos()+slot.y,r=m.shape.getId().equals("circle")?8:2;
                    if(m.outline.get())m.draw.card(x,y,16,16,r,c,c|0xFF000000);else m.draw.round(x,y,16,16,r,c);
                }m.draw.end();
            });
        });
    }
}

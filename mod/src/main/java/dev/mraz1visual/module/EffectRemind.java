package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.core.HudLayer;
import dev.mraz1visual.render.HudKit;
import dev.mraz1visual.render.HudPos;
import dev.mraz1visual.setting.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.*;
import java.util.*;

/** AuraVisual expiry reminders with the existing client HUD and editor. */
public final class EffectRemind extends Module implements HudLayer {
    private final SliderSetting trigger=add(new SliderSetting("trigger","За сколько предупредить",5,1,60,.5," с"));
    private final SliderSetting duration=add(new SliderSetting("duration","Время показа",5,1,30,.5," с"));
    private final SliderSetting iconSize=add(new SliderSetting("icon_size","Размер иконки",13,8,32,1," px"));
    private final SliderSetting gap=add(new SliderSetting("gap","Интервал",4,0,16,1," px"));
    private final BoolSetting timer=add(new BoolSetting("timer","Показывать время",true));
    private final BoolSetting pulse=add(new BoolSetting("pulse","Пульсация",true));
    private final BoolSetting beneficial=add(new BoolSetting("beneficial","Только положительные",false));
    private final HudPos position=hudPos(.5,.55);
    private final Map<Holder<MobEffect>,Long> triggered=new HashMap<>();
    private final ArrayList<MobEffectInstance> visible=new ArrayList<>();
    public EffectRemind(){super("effect_remind","Effect Remind","Иконки и таймеры эффектов, которые скоро закончатся (AuraVisual).",Category.HUD);markNew();}
    @Override protected void onDisable(){triggered.clear();visible.clear();}
    @Override public void onTick(Minecraft mc){
        visible.clear();if(mc.player==null){triggered.clear();return;}long now=System.nanoTime();Set<Holder<MobEffect>> current=new HashSet<>();
        for(var effect:mc.player.getActiveEffects()){
            current.add(effect.getEffect());
            if(effect.isInfiniteDuration()||effect.getDuration()<=0||effect.getDuration()>trigger.get()*20
                    ||beneficial.get()&&effect.getEffect().value().getCategory()!=MobEffectCategory.BENEFICIAL){triggered.remove(effect.getEffect());continue;}
            long born=triggered.computeIfAbsent(effect.getEffect(),ignored->now);
            if(now-born<=duration.get()*1e9&&visible.size()<24)visible.add(effect);
        }
        triggered.keySet().retainAll(current);
    }
    @Override public void renderHud(GuiGraphics g,float pt){
        if(visible.isEmpty())return;int icon=iconSize.getI(),cell=icon+10,h=icon+(timer.get()?22:10);
        int available=Math.max(cell,(int)(g.guiWidth()/position.scale.get())-12);
        int columns=Math.max(1,Math.min(visible.size(),(available+gap.getI())/(cell+gap.getI()))),rows=(visible.size()+columns-1)/columns;
        int w=columns*cell+(columns-1)*gap.getI();position.begin(g,w,rows*h+(rows-1)*gap.getI());
        for(int i=0;i<visible.size();i++){
            var effect=visible.get(i);int x=i%columns*(cell+gap.getI()),y=i/columns*(h+gap.getI());HudKit.card(g,x,y,cell,h,1);
            float scale=pulse.get()?1+.1f*(float)Math.sin(System.nanoTime()/120_000_000.):1;
            g.pose().pushMatrix();g.pose().translate(x+cell/2f,y+icon/2f+4);g.pose().scale(scale,scale);
            g.blitSprite(RenderPipelines.GUI_TEXTURED,dev.mraz1visual.compat.Mc.effectSprite(effect.getEffect()),-icon/2,-icon/2,icon,icon);g.pose().popMatrix();
            if(timer.get()){String text=(int)Math.ceil(effect.getDuration()/20.)+"с";HudKit.text(g,text,x+(cell-HudKit.width(text))/2,y+icon+8,0xFFFFD34D);}
        }position.end(g);
    }
}

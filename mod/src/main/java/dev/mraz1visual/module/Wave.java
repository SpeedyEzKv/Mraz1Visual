package dev.mraz1visual.module;

import dev.mraz1visual.core.*;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.render.*;
import dev.mraz1visual.setting.*;
import dev.mraz1visual.ui.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/** Local expanding shock waves on jump/attack, adapted to the common world renderer. */
public final class Wave extends Module implements dev.mraz1visual.core.DecorativeLayer,AttackListener {
    private final BoolSetting jump=add(new BoolSetting("jump","При прыжке",true));
    private final BoolSetting hit=add(new BoolSetting("hit","При ударе",true));
    private final SliderSetting range=add(new SliderSetting("range","Радиус",10,1,50,.5," бл."));
    private final SliderSetting lifetime=add(new SliderSetting("lifetime","Длительность",1.2,.3,4,.1," с"));
    private final ColorSetting color=add(new ColorSetting("color","Цвет",0xFFE4274E));
    private final BoolSetting fill=add(new BoolSetting("fill","Заполнение",true));
    private final java.util.List<double[]> waves=new java.util.ArrayList<>();
    private boolean grounded=true;
    public Wave(){super("aura_wave","Wave","Расходящаяся волна от прыжка и удара (AuraVisual).",Category.COMBAT);}
    private void spawn(Entity e){if(waves.size()>=16)waves.removeFirst();waves.add(new double[]{e.getX(),e.getY()+.025,e.getZ(),System.nanoTime()});}
    @Override public void onTick(Minecraft mc){if(mc.player==null)return;boolean on=mc.player.onGround();if(jump.get()&&grounded&&!on&&mc.player.getDeltaMovement().y>.1)spawn(mc.player);grounded=on;}
    @Override public void onAttack(Entity e){if(hit.get())spawn(e);}
    @Override protected void onDisable(){waves.clear();}
    @Override public void renderWorld(WorldDraw d,float pt){
        long now=System.nanoTime();waves.removeIf(v->now-v[3]>lifetime.get()*1e9);
        for(double[] v:waves){float t=(float)((now-v[3])/(lifetime.get()*1e9));double r=range.get()*Ease.outExpo(t);int c=Colors.mulAlpha(color.get(),1-t);
            FxDraw.softRing(d,v[0],v[1],v[2],r,.04,c,64);
            if(fill.get())d.ring(v[0],v[1],v[2],0,r,64,Colors.mulAlpha(c,.08f),Colors.mulAlpha(c,.25f));
        }
    }
}

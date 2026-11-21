package dev.mraz1visual.module;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.core.Category;
import dev.mraz1visual.models.*;
import dev.mraz1visual.render.*;
import dev.mraz1visual.setting.*;
import dev.mraz1visual.ui.Colors;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import java.util.*;

public final class SkinTransition extends Module {
    private static SkinTransition instance;
    private final SliderSetting duration=add(new SliderSetting("duration","Длительность",.45,.15,1,.05," с")),particles=add(new SliderSetting("particles","Осколков",10,0,24,1,""));
    private final ColorSetting color=add(new ColorSetting("color","Свечение",0xFFD9DDF8));
    private final Map<String,String> selected=new HashMap<>();
    private record Change(ModelEntry before,ModelEntry after,long start){}
    private final Map<String,Change> changes=new HashMap<>();
    private Object world;
    public SkinTransition(){super("skin_transition","Skin Transition","Старый скин распадается на осколки, новый собирается в руке; предмет не изменяется.",Category.SWORDS);instance=this;markNew();}
    @Override protected void onEnable(){selected.clear();selected.putAll(ModelPrefs.weapons);changes.clear();}
    @Override protected void onDisable(){changes.clear();selected.clear();}
    @Override public void onTick(Minecraft mc){
        if(mc.level!=world){world=mc.level;changes.clear();selected.clear();selected.putAll(ModelPrefs.weapons);}
        for(var entry:ModelPrefs.weapons.entrySet())if(!Objects.equals(selected.get(entry.getKey()),entry.getValue())){
            var next=ModelRegistry.get(entry.getValue());var previous=ModelRegistry.get(selected.get(entry.getKey()));
            if(next!=null&&!next.broken()){next.mesh();if(previous!=null)previous.mesh();changes.put(entry.getKey(),new Change(previous,next,System.nanoTime()));}
        }
        selected.clear();selected.putAll(ModelPrefs.weapons);changes.values().removeIf(c->System.nanoTime()-c.start>duration.get()*1e9);
    }
    public static boolean submit(PoseStack ps,SubmitNodeCollector collector,String slot,ModelEntry current,String state,int overlay,int light,boolean gui){
        var module=instance;if(module==null||!module.isEnabled()||gui)return false;
        Change change=module.changes.get(slot);
        if(change==null || change.after!=current)return false;float t=(float)((System.nanoTime()-change.start)/(module.duration.get()*1e9));if(t>=1)return false;
        int accent=Fx.color("custom",.2f,Fx.time(),module.color.get(),module.color.get());
        if(change.before!=null)ModelDraw.submitSweep(collector,ps,change.before.mesh(state),t,false,accent,overlay,light);
        Mesh next=current.mesh(state);
        ModelDraw.submitSweep(collector,ps,next,t,true,accent,overlay,light);
        int axis=next.sizeX()>next.sizeY()?0:1;
        float[] b=next.bounds;
        if(next.sizeZ()>b[axis+3]-b[axis])axis=2;
        final int major=axis;
        float fraction=Math.abs(b[axis+3])<Math.abs(b[axis])?1-t:t;
        final double center=b[axis]+fraction*(b[axis+3]-b[axis]);
        final int amount=Math.round(module.particles.getI()*Fx.quality());
        int tint=Colors.mulAlpha(accent,(float)Math.sin(t*Math.PI)*.65f);
        if(amount>0)WorldDraw.submitLocal(ps,collector,false,d->{
            if(!d.isGlowPass())return;
            d.glowBegin();
            for(int i=0;i<amount;i++){
                double angle=i*2.39996+t*2, r=.045+.04*Math.sin(i);
                double x=(b[0]+b[3])*.5,y=(b[1]+b[4])*.5,z=(b[2]+b[5])*.5;
                if(major==0)x=center;else x+=Math.cos(angle)*r;
                if(major==1)y=center;else y+=Math.sin(angle)*r;
                if(major==2)z=center;else z+=Math.sin(angle)*r;
                d.billboardDot(x,y,z,.008,tint,4);
            }
            d.glowEnd();
        });
        return true;
    }
    public static void musicalTrim(PoseStack ps,SubmitNodeCollector collector,Mesh mesh){float pulse=MusicCosmetics.pulse("weapon");if(pulse<.005f)return;float[] b=mesh.bounds;int tint=Colors.mulAlpha(Fx.color("eclipse",.15f,Fx.time(),-1,-1),pulse*.5f);WorldDraw.submitLocal(ps,collector,false,d->{d.glowBegin();d.line(b[0],b[1],b[2],b[0],b[4],b[2],.008,tint);d.glowEnd();});}
}

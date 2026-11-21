package dev.mraz1visual.module;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.core.Category;
import dev.mraz1visual.setting.*;
import dev.mraz1visual.render.*;
import net.minecraft.client.Minecraft;

/** Local inventory/movement events only; never searches for ores or changes entities. */
public final class PetReactions extends Module {
    private static PetReactions instance;
    private final BoolSetting diamonds=add(new BoolSetting("diamonds","Радоваться алмазам",true)),danger=add(new BoolSetting("danger","Прятаться при низком здоровье",true)),sleep=add(new BoolSetting("sleep","Засыпать без движения",true));
    private final SliderSetting health=add(new SliderSetting("health","Порог здоровья",6,2,14,1," HP")),idle=add(new SliderSetting("idle","До сна",20,5,120,5," с")),joyTime=add(new SliderSetting("joy_time","Длительность радости",2,.5,5,.25," с")),cooldown=add(new SliderSetting("cooldown","Между реакциями",5,1,30,1," с"));
    private Object world;
    private int still,joy,wake,cooldownTicks;
    private String state="follow";
    private float smoothScale=1, restAngle;
    public PetReactions(){super("pet_reactions","Pet Reactions","Радость от полученных алмазов, укрытие при опасности, сон и пробуждение питомца.",Category.PETS);instance=this;markNew();}
    @Override public void onTick(Minecraft mc){
        if(PhotoMode.frozen())return;
        if(mc.player==null||mc.level==null){reset();return;}var p=mc.player;
        if(world!=mc.level){reset();world=mc.level;}
        if(cooldownTicks>0)cooldownTicks--;if(joy>0)joy--;if(wake>0)wake--;
        boolean moving=p.getDeltaMovement().horizontalDistanceSqr()>.001||!p.onGround();
        if(moving){if(state.equals("sleep"))wake=15;still=0;}else still++;
        state=danger.get()&&p.getHealth()<=health.get()?"hide":joy>0?"joy":wake>0?"wake":sleep.get()&&still>=idle.get()*20?"sleep":"follow";
        restAngle+=((state.equals("sleep")?-16:state.equals("hide")?8:0)-restAngle)*.13f;
        smoothScale+=((state.equals("hide")?.55f:state.equals("sleep")?.85f:1)-smoothScale)*.2f;
    }
    public static void pickup(net.minecraft.world.item.ItemStack stack) {
        var m=instance;
        if(m==null || !m.isEnabled() || !m.diamonds.get() || m.cooldownTicks>0 || !stack.is(net.minecraft.world.item.Items.DIAMOND))return;
        m.joy=(int)(m.joyTime.get()*20);
        m.cooldownTicks=(int)(m.cooldown.get()*20);
    }

    public static String state(){return instance!=null&&instance.isEnabled()?instance.state:"follow";}
    public static float scale(){return instance!=null&&instance.isEnabled()?instance.smoothScale:1;}
    public static float restAngle(){return instance!=null && instance.isEnabled()?instance.restAngle:0;}
    public static float bounce(){
        var m=instance;
        if(m==null || !m.isEnabled())return 0;
        if(m.state.equals("wake"))return .04f*(float)Math.sin(Math.PI*m.wake/15);
        if(!m.state.equals("joy"))return 0;
        float phase=1-Math.min(1,m.joy/(20*m.joyTime.getF()));
        return .30f*(1-phase)*(float)Math.abs(Math.sin(phase*3*Math.PI));
    }
    private void reset(){world=null;still=joy=wake=cooldownTicks=0;state="follow";smoothScale=1;restAngle=0;}
    @Override protected void onDisable(){reset();}
}

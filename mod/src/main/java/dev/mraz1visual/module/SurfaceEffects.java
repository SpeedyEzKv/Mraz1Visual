package dev.mraz1visual.module;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.core.*;
import dev.mraz1visual.render.*;
import dev.mraz1visual.setting.*;
import dev.mraz1visual.ui.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import java.util.ArrayList;

public final class SurfaceEffects extends Module implements DecorativeLayer {
    private final BoolSetting ripples=add(new BoolSetting("ripples","Круги на воде",true)),drops=add(new BoolSetting("drops","Капли после выхода",true)),snow=add(new BoolSetting("snow","Снежная пыль",true));
    private final SliderSetting amount=add(new SliderSetting("amount","Интенсивность",60,10,100,5," %"));
    private record Ripple(double x,double y,double z,long born){}
    private final ArrayList<Ripple> rings=new ArrayList<>();
    private Object world;private boolean wasWater;private int dripTicks;private double px,py,pz,stepDistance;
    private int foot = 1;
    public SurfaceEffects(){super("surface_effects","Surface Effects","Водные круги, капли после воды и снежная пыль при движении.",Category.VISUALS);markNew();}
    @Override public void onTick(Minecraft mc){
        if(mc.player==null||mc.level==null){reset();return;}var p=mc.player;
        if(world!=mc.level||p.distanceToSqr(px,py,pz)>64){reset();world=mc.level;wasWater=p.isInWater();px=p.getX();pz=p.getZ();}
        double dx=p.getX()-px, dz=p.getZ()-pz, distance=Math.hypot(dx,dz);
        px=p.getX();py=p.getY();pz=p.getZ();var pos=p.blockPosition();var fluid=mc.level.getFluidState(pos);boolean water=fluid.is(FluidTags.WATER);
        stepDistance += distance;
        boolean step = distance > .001 && stepDistance >= .68;
        if (step) { stepDistance %= .68; foot = -foot; }
        double forwardX=distance>.001?dx/distance:0, forwardZ=distance>.001?dz/distance:0;
        double footX=px+foot*forwardZ*.13, footZ=pz-foot*forwardX*.13;
        if(water&&!p.isUnderWater()&&step&&ripples.get()){if(rings.size()>=24)rings.remove(0);var contact=net.minecraft.core.BlockPos.containing(footX,py,footZ);
            var surface=mc.level.getFluidState(contact);
            if(surface.is(FluidTags.WATER))rings.add(new Ripple(footX,contact.getY()+surface.getHeight(mc.level,contact)+.015,footZ,System.nanoTime()));}
        if(wasWater&&!water)dripTicks=20;wasWater=water;
        if(dripTicks>0){dripTicks--;if(drops.get()&&!water&&p.tickCount%3==0)for(int i=0;i<3;i++)FxParticles.spawn(px+(Math.random()-.5)*.5,py+.3+Math.random(),pz+(Math.random()-.5)*.5,dx*9+(Math.random()-.5)*.5,.35+Math.random()*.35,dz*9+(Math.random()-.5)*.5,.45f,.018f,Fx.color("custom",.55f,Fx.time(),0xFF7CB9D7,0xFF7CB9D7,amount.getF()/160),2,false);}
        var under=mc.level.getBlockState(pos.below());var at=mc.level.getBlockState(pos);
        if(snow.get()&&p.onGround()&&step&&(under.is(Blocks.SNOW_BLOCK)||at.is(Blocks.SNOW)||at.is(Blocks.POWDER_SNOW))){for(int i=0;i<3;i++)FxParticles.spawn(footX,py+.045,footZ,-forwardX*.65+(Math.random()-.5)*.6,.24+Math.random()*.25,-forwardZ*.65+(Math.random()-.5)*.6,.5f,.04f,Fx.color("custom",.3f,Fx.time(),0xFFECF4FF,0xFFECF4FF,amount.getF()/115),.2f,false);}
        rings.removeIf(r->System.nanoTime()-r.born>750_000_000L);
    }
    @Override public void renderWorld(WorldDraw d,float pt){for(var r:rings){float t=(System.nanoTime()-r.born)/750_000_000f;int c=Colors.mulAlpha(Fx.color("custom",.2f,Fx.time(),0xFFCFECFF,0xFFCFECFF),(1-t)*amount.getF()/130);d.ring(r.x,r.y,r.z,.12+t*.6,.135+t*.6,Fx.segments(40),c,c);}}
    private void reset(){world=null;rings.clear();wasWater=false;dripTicks=0;stepDistance=0;foot=1;}
    @Override protected void onDisable(){reset();}
}

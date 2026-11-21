package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.render.Fx;
import dev.mraz1visual.render.FxParticles;
import dev.mraz1visual.setting.BoolSetting;
import dev.mraz1visual.setting.SliderSetting;
import dev.mraz1visual.ui.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Мягкая пыль цвета блока под ногами: облачко при приземлении (сильнее — с высоты) и редкие клубы на бегу.
 * Вместо ванильных квадратных крошек — круглые полупрозрачные клубы, которые медленно расползаются и тают.
 */
public final class LandingDust extends Module implements dev.mraz1visual.core.DecorativeLayer {
    final BoolSetting sprint = add(new BoolSetting("sprint", "Клубы на бегу", true));
    final SliderSetting amount = add(new SliderSetting("amount", "Количество", 100, 30, 200, 10, " %"));
    final SliderSetting minimum=add(new SliderSetting("minimum","Минимальная высота",.65,.4,3,.05," бл.")),radius=add(new SliderSetting("radius","Радиус волны",1,.4,2,.1,"×")),lifetime=add(new SliderSetting("lifetime","Длительность волны",.65,.3,1.5,.05," с"));
    final BoolSetting wave=add(new BoolSetting("wave","Волна приземления",true)),cracks=add(new BoolSetting("cracks","Световые трещины с высоты",true)),surface=add(new BoolSetting("surface","Цвет поверхности",true));
    final dev.mraz1visual.setting.ColorSetting color=add(new dev.mraz1visual.setting.ColorSetting("color","Свой цвет",0xFFC9D3F0));
    private record Landing(double x,double y,double z,double power,int color,long born,double[] reach){}
    private final java.util.ArrayList<Landing> landings=new java.util.ArrayList<>();
    private Object world;private double lastX,lastY,lastZ;private int airTicks;

    private boolean wasOnGround = true;
    private double fallFrom;
    private int stepTick;

    public LandingDust() {
        super("landing_dust", "Landing Dust", "Мягкая пыль цвета блока при приземлении и на бегу.", Category.VISUALS);
    }

    @Override
    public void onTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) {onDisable();return;}
        if(world!=mc.level||p.distanceToSqr(lastX,lastY,lastZ)>64){landings.clear();world=mc.level;wasOnGround=p.onGround();fallFrom=p.getY();airTicks=0;}
        lastX=p.getX();lastY=p.getY();lastZ=p.getZ();
        boolean on = p.onGround();
        if(!on)airTicks++;
        if (!on && wasOnGround) fallFrom = p.getY();
        if (!on) fallFrom = Math.max(fallFrom, p.getY());
        if (on && !wasOnGround) {
            double h = fallFrom - p.getY();
            if (h >= minimum.get()&&airTicks>=3&&!p.isInWater()) {
                puff(mc,p,(int)Math.min(26,6+h*4),.6+Math.min(1.6,h*.25));
                var pos=BlockPos.containing(p.getX(),p.getY()-.04,p.getZ());var block=mc.level.getBlockState(pos);
                if(h>1.65&&!block.isAir()&&(wave.get()||cracks.get())){if(landings.size()>=12)landings.remove(0);landings.add(new Landing(p.getX(),p.getY()+.025,p.getZ(),Math.min(3,h),surface.get()?0xFF000000|block.getMapColor(mc.level,pos).col:color.get(),System.nanoTime(),groundReach(mc,p,Math.min(3,h))));}
            }
            airTicks=0;
        }
        if (on && sprint.get() && p.isSprinting() && ++stepTick % 6 == 0) puff(mc, p, 2, 0.25);
        wasOnGround = on;
        landings.removeIf(e->System.nanoTime()-e.born>lifetime.get()*1e9);
    }

    // Sample the actual collision surface once at impact, including partial stair treads.
    private double[] groundReach(Minecraft mc, LocalPlayer p, double power) {
        int segments = Math.min(64,Fx.segments(40));
        double[] reach = new double[segments];
        double limit = (.2+power*.55)*radius.get();
        var pos = new BlockPos.MutableBlockPos();
        for (int i=0;i<segments;i++) {
            double dx=dev.mraz1visual.render.WorldDraw.cos(i,segments), dz=dev.mraz1visual.render.WorldDraw.sin(i,segments);
            for (double r=.07;r<limit+.07;r+=.07) {
                double x=p.getX()+dx*r,z=p.getZ()+dz*r;
                pos.set(Math.floor(x),Math.floor(p.getY()-.04),Math.floor(z));
                var shape=mc.level.getBlockState(pos).getCollisionShape(mc.level,pos);
                if (shape.isEmpty()) break;
                double top=pos.getY()+shape.max(net.minecraft.core.Direction.Axis.Y,z-pos.getZ(),x-pos.getX());
                if (Math.abs(top-p.getY())>.055) break;
                reach[i]=r;
            }
        }
        return reach;
    }

    @Override public void renderWorld(dev.mraz1visual.render.WorldDraw d,float pt){
        for(var e:landings){float t=(float)((System.nanoTime()-e.born)/(lifetime.get()*1e9)),a=Math.max(0,1-t);double r=(.2+dev.mraz1visual.render.Ease.outCubic(t)*e.power*.55)*radius.get();int c=Colors.mulAlpha(Fx.color("custom",.25f,Fx.time(),Colors.lerp(e.color,0xFFE4ECFF,.55f),e.color),a*.55f);
            int n=e.reach.length;
            d.glowBegin();
            for(int i=0;i<n;i++){
                int j=(i+1)%n;
                double dx=dev.mraz1visual.render.WorldDraw.cos(i,n),dz=dev.mraz1visual.render.WorldDraw.sin(i,n);
                if(wave.get() && Math.min(e.reach[i],e.reach[j])>=r){
                    double nx=dev.mraz1visual.render.WorldDraw.cos(j,n),nz=dev.mraz1visual.render.WorldDraw.sin(j,n);
                    d.quad(e.x+dx*(r-.022),e.y,e.z+dz*(r-.022),e.x+dx*r,e.y,e.z+dz*r,e.x+nx*r,e.y,e.z+nz*r,e.x+nx*(r-.022),e.y,e.z+nz*(r-.022),c&0xffffff,c,c,c&0xffffff);
                }
                if(cracks.get() && i%5==0){
                    double len=Math.min(r,Math.min(e.reach[i],e.reach[j]));
                    if(len<=.23)continue;
                    double mid=len*.57,bend=mid*.06*Math.sin(i*7.);
                    d.line(e.x+dx*.16,e.y,e.z+dz*.16,e.x+dx*mid-dz*bend,e.y,e.z+dz*mid+dx*bend,.012,c);
                    d.line(e.x+dx*mid-dz*bend,e.y,e.z+dz*mid+dx*bend,e.x+dx*len,e.y,e.z+dz*len,.008,c);
                }
            }
            d.glowEnd();
        }
    }
    @Override protected void onDisable(){world=null;landings.clear();airTicks=0;wasOnGround=true;}

    private void puff(Minecraft mc, LocalPlayer p, int n, double power) {
        BlockPos below = BlockPos.containing(p.getX(),p.getY()-.04,p.getZ());
        BlockState st = mc.level.getBlockState(below);
        if (st.isAir()) return;
        int base = surface.get()?0xFF000000 | st.getMapColor(mc.level, below).col:color.get();
        int c = Colors.withAlpha(Fx.color("custom",.25f,Fx.time(),Colors.lerp(base,0xFFEDEDED,.35f),base), 0x70);
        int count = Math.max(1, Math.round(n * amount.getF() / 100f * Fx.quality()));
        for (int i = 0; i < count; i++) {
            double a = Math.random() * Math.PI * 2, v = power * (0.6 + Math.random() * 0.6);
            FxParticles.spawn(p.getX() + Math.cos(a) * 0.2, p.getY() + 0.05, p.getZ() + Math.sin(a) * 0.2,
                    Math.cos(a) * v, 0.15 + Math.random() * 0.25, Math.sin(a) * v, 0.7f + (float) Math.random() * 0.6f,
                    0.1f + (float) Math.random() * 0.08f, c, 0.4f, false, FxParticles.DOT, false, 0.08f, 0.3f);
        }
    }
}

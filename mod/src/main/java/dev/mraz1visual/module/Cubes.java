package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.core.WorldLayer;
import dev.mraz1visual.render.WorldDraw;
import dev.mraz1visual.setting.*;
import dev.mraz1visual.ui.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3d;
import org.joml.Vector3d;
import java.util.ArrayList;
import java.util.Random;

/** AuraVisual Cubes simulation, adapted to native geometry. Attribution: AURAVISUAL-NOTICE.txt. */
public final class Cubes extends Module implements WorldLayer {
    private static final Identifier GLOW=Identifier.fromNamespaceAndPath("auravisuals","textures/glow.png");
    private static final int[][] CUBE={{0,1},{1,3},{3,2},{2,0},{4,5},{5,7},{7,6},{6,4},{0,4},{1,5},{2,6},{3,7}};
    private static final int[][] PYRAMID={{0,1},{0,2},{0,3},{0,4},{1,2},{2,3},{3,4},{4,1}};
    private final EnumSetting animation=add(new EnumSetting("animation","Анимация",0,"scatter","Разлёт","fall","Падение"));
    private final EnumSetting shape=add(new EnumSetting("shape","Форма",0,"cube","Кубы","triangle","Треугольники"));
    private final SliderSetting count=add(new SliderSetting("count","Количество",30,5,100,1,""));
    private final SliderSetting size=add(new SliderSetting("size","Размер",1,.1,3,.1,"×"));
    private final SliderSetting speed=add(new SliderSetting("speed","Скорость",1,.1,5,.1,"×"));
    private final SliderSetting distance=add(new SliderSetting("distance","Дальность",12,5,24,.5," бл."));
    private final SliderSetting glow=add(new SliderSetting("glow","Сила свечения",1,0,1.5,.05,"×"));
    private final ColorSetting color=add(new ColorSetting("color","Цвет",0xFF78C8FF));
    private static final class Particle { double x,y,z,px,py,pz,vx,vy,vz,rx,ry,rz,vrx,vry,vrz,phase;int life,max; }
    private final ArrayList<Particle> particles=new ArrayList<>();
    private final Random random=new Random();
    private final Matrix3d rotation=new Matrix3d();
    private final Vector3d point=new Vector3d();
    private final double[][] vertices=new double[8][3];
    private Object level;
    private boolean pressed;
    public Cubes(){super("aura_cubes","Cubes","Вращающиеся кубы и пирамиды; разлёт, падение и реакция на удар (AuraVisual).",Category.VISUALS);markNew();}
    @Override protected void onDisable(){particles.clear();level=null;pressed=false;}
    private Particle spawn(Minecraft mc){
        Particle p=new Particle();double r=distance.get();boolean fall=animation.getId().equals("fall");double s=speed.get();
        p.x=mc.player.getX()+(random.nextDouble()*2-1)*r;p.z=mc.player.getZ()+(random.nextDouble()*2-1)*r;
        p.y=mc.player.getY()+(fall?4:2)+random.nextDouble()*r*(fall?.55:.8);p.px=p.x;p.py=p.y;p.pz=p.z;
        double a=random.nextDouble()*Math.PI*2,v=(.01+random.nextDouble()*.02)*s;
        p.vx=fall?(random.nextDouble()-.5)*.008*s:-Math.sin(a)*v;p.vz=fall?(random.nextDouble()-.5)*.008*s:Math.cos(a)*v;
        p.vy=fall?(-.012-random.nextDouble()*.012)*s:(random.nextDouble()-.5)*.01*s;
        p.rx=random.nextDouble()*360;p.ry=random.nextDouble()*360;p.rz=random.nextDouble()*360;
        p.vrx=(random.nextDouble()-.5)*1.5;p.vry=(random.nextDouble()-.5)*1.5;p.vrz=(random.nextDouble()-.5)*1.5;
        p.life=p.max=fall?260+random.nextInt(220):420+random.nextInt(420);p.phase=random.nextDouble()*Math.PI*2;return p;
    }
    @Override public void onTick(Minecraft mc){
        if(mc.player==null||mc.level==null){onDisable();return;}if(level!=mc.level){particles.clear();level=mc.level;}
        while(particles.size()>count.getI())particles.removeLast();
        for(int i=0,n=Math.min(10,count.getI()-particles.size());i<n;i++)particles.add(spawn(mc));
        double s=.75*speed.get(),r=distance.get()*1.75;boolean fall=animation.getId().equals("fall");
        for(int i=0;i<particles.size();i++){
            Particle p=particles.get(i);p.px=p.x;p.py=p.y;p.pz=p.z;p.phase+=.06*s;
            p.x+=p.vx*s+(fall?Math.sin(p.phase)*.0024*s:0);p.y+=p.vy*s;p.z+=p.vz*s+(fall?Math.cos(p.phase*.8)*.002*s:0);
            if(fall)p.vy=Math.max(p.vy-.00008*s,-.032);else{p.vx*=.995;p.vy*=.995;p.vz*=.995;}
            double spin=s*(fall?.2:1);p.rx+=p.vrx*spin;p.ry+=p.vry*spin;p.rz+=p.vrz*spin;
            if(--p.life<=0||mc.player.distanceToSqr(p.x,p.y,p.z)>r*r||fall&&p.y<mc.player.getY()-2.5)particles.set(i,spawn(mc));
        }
        boolean down=mc.options.keyAttack.isDown();if(down&&!pressed)impulse(mc);pressed=down;
    }
    private void impulse(Minecraft mc){
        var origin=mc.player.getEyePosition();var direction=mc.player.getLookAngle();Particle best=null;double nearest=128;
        for(Particle p:particles){double dx=p.x-origin.x,dy=p.y-origin.y,dz=p.z-origin.z,t=dx*direction.x+dy*direction.y+dz*direction.z;
            double ox=dx-direction.x*t,oy=dy-direction.y*t,oz=dz-direction.z*t;
            if(t>=0&&t<nearest&&ox*ox+oy*oy+oz*oz<=1.32){best=p;nearest=t;}}
        if(best!=null){double f=.08*speed.get();best.vx+=direction.x*f;best.vy+=direction.y*f+.02;best.vz+=direction.z*f;}
    }
    @Override public void renderWorld(WorldDraw d,float pt){
        double s=.18*size.get();boolean triangle=shape.getId().equals("triangle");
        for(Particle p:particles){
            double x=p.px+(p.x-p.px)*pt,y=p.py+(p.y-p.py)*pt,z=p.pz+(p.z-p.pz)*pt;
            float alpha=Math.min(1,(p.max-p.life)/10f)*p.life/p.max;int c=Colors.mulAlpha(color.get(),alpha);
            if(glow.get()>0 && LocalArtwork.AURA)d.sprite(GLOW,x,y,z,s*6,Colors.mulAlpha(c,.14f*glow.getF()),false);
            rotation.rotationZYX(Math.toRadians(p.rz),Math.toRadians(p.ry),Math.toRadians(p.rx));
            for(int i=0,n=triangle?5:8;i<n;i++){
                if(triangle)point.set(i==0?0:(i==1||i==4?-s:s),i==0?s:-s,i==0?0:(i<=2?-s:s));
                else point.set((i&1)==0?-s:s,(i&2)==0?-s:s,(i&4)==0?-s:s);
                rotation.transform(point);vertices[i][0]=x+point.x;vertices[i][1]=y+point.y;vertices[i][2]=z+point.z;
            }
            for(int[] edge:triangle?PYRAMID:CUBE){double[] a=vertices[edge[0]],b=vertices[edge[1]];d.line(a[0],a[1],a[2],b[0],b[1],b[2],.012*size.get(),c);}
        }
    }
}

package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.core.WorldLayer;
import dev.mraz1visual.render.WorldDraw;
import dev.mraz1visual.setting.*;
import dev.mraz1visual.ui.Colors;
import net.minecraft.client.Minecraft;

/** AuraVisual-inspired falling meteors; entirely local cosmetic geometry. */
public final class MeteorFly extends Module implements WorldLayer {
    private final SliderSetting count = add(new SliderSetting("count","Количество",20,1,50,1,""));
    private final SliderSetting radius = add(new SliderSetting("radius","Радиус",35,8,64,1," бл."));
    private final SliderSetting speed = add(new SliderSetting("speed","Скорость",1,.25,3,.05,"×"));
    private final ColorSetting color = add(new ColorSetting("color","Цвет",0xFFFF6E50));
    private final BoolSetting glow = add(new BoolSetting("glow","Свечение",true));
    private final double[][] particles = new double[50][5];
    private final java.util.Random random = new java.util.Random();
    public MeteorFly() { super("meteor_fly","Meteor Fly","Метеоры с затухающим светящимся следом (AuraVisual).",Category.SKY); }
    @Override public void onTick(Minecraft mc) {
        if(mc.player==null) return;
        for(int i=0;i<count.getI();i++) {
            double[] p=particles[i];
            if(p[4]<=0 || Math.abs(p[0]-mc.player.getX())>80 || Math.abs(p[2]-mc.player.getZ())>80) {
                double a=random.nextDouble()*Math.PI*2,r=random.nextDouble()*radius.get();
                p[0]=mc.player.getX()+Math.cos(a)*r; p[1]=mc.player.getY()+10+random.nextDouble()*25;
                p[2]=mc.player.getZ()+Math.sin(a)*r;p[3]=80+random.nextInt(80);p[4]=p[3];
            }
            p[0]+=.35*speed.get();p[1]-=.18*speed.get();p[2]+=.12*speed.get();p[4]--;
        }
    }
    @Override public void renderWorld(WorldDraw d,float pt) {
        if(glow.get())d.glowBegin();
        for(int i=0;i<count.getI();i++) {
            double[] p=particles[i]; if(p[4]<=0)continue;
            float fade=(float)Math.min(1,Math.min((p[3]-p[4])/18,p[4]/36));
            for(int k=0;k<18;k++) {
                double t=k*.16, next=(k+1)*.16;
                d.line(p[0]-t,p[1]+t*.51,p[2]-t*.34,p[0]-next,p[1]+next*.51,p[2]-next*.34,.035*(1-k/20.),Colors.mulAlpha(color.get(),fade*(1-k/18f)));
            }
        }
        if(glow.get())d.glowEnd();
    }
}

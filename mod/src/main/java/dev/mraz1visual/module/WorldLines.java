package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.core.WorldLayer;
import dev.mraz1visual.render.WorldDraw;
import dev.mraz1visual.setting.*;
import dev.mraz1visual.ui.Colors;
import java.util.ArrayList;
import java.util.Random;
import net.minecraft.client.Minecraft;

/** AuraVisual's axis-aligned growing paths, rendered through Mraz1Visual's native pipeline. */
public final class WorldLines extends Module implements WorldLayer {
    private static final int[][] AXES={{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
    private final SliderSetting quantity=add(new SliderSetting("quantity","Количество",45,20,80,1,""));
    private final SliderSetting speed=add(new SliderSetting("speed","Скорость",1,.5,2,.1,"×"));
    private final ColorSetting color=add(new ColorSetting("color","Цвет",0xFF8FD8FF));
    private final BoolSetting glow=add(new BoolSetting("glow","Свечение",true));
    private final Random random=new Random(93882);
    private final ArrayList<Path> paths=new ArrayList<>();
    private final double[][] points=new double[21][3];
    private Object level;
    private static final class Path {final double[][] nodes=new double[20][3];int count,head=1,tail;double progress,tailProgress,seed;long fade;}
    public WorldLines(){super("world_lines","World Lines","Бегущие угловатые линии с растущим следом и затуханием (AuraVisual).",Category.VISUALS);markNew();}
    @Override protected void onDisable(){paths.clear();level=null;}
    private Path spawn(Minecraft mc){
        Path p=new Path();double fov=mc.options.fov().get(),angle=Math.toRadians(mc.player.getYRot()+(random.nextDouble()*1.5-.75)*fov),distance=6+random.nextInt(19);
        var eye=mc.player.getEyePosition();p.nodes[0]=new double[]{eye.x-Math.sin(angle)*distance,eye.y+random.nextInt(25)-12,eye.z+Math.cos(angle)*distance};
        p.count=1;p.seed=random.nextDouble()*Math.PI*2;int previous=random.nextInt(6);
        for(int i=0,n=7+random.nextInt(6);i<n;i++){
            int next;do{next=random.nextInt(6);}while((next^1)==previous);previous=next;int length=1+random.nextInt(3);
            for(int axis=0;axis<3;axis++)p.nodes[p.count][axis]=p.nodes[p.count-1][axis]+AXES[next][axis]*length;p.count++;
        }return p;
    }
    @Override public void onTick(Minecraft mc){
        if(mc.player==null||mc.level==null){onDisable();return;}if(level!=mc.level){paths.clear();level=mc.level;}
        long now=System.nanoTime();paths.removeIf(p->p.fade!=0&&now-p.fade>700_000_000L||mc.player.distanceToSqr(p.nodes[0][0],p.nodes[0][1],p.nodes[0][2])>10000);
        while(paths.size()>quantity.getI())paths.removeLast();while(paths.size()<quantity.getI())paths.add(spawn(mc));
        double advance=.06*speed.get();
        for(Path p:paths)if(p.fade==0){
            if(p.head<p.count){p.progress+=advance;if(p.progress>=1){p.progress--;p.head++;}}else p.fade=now;
            if(p.head-p.tail>14){p.tailProgress+=advance;if(p.tailProgress>=1){p.tailProgress--;p.tail++;}}
        }
    }
    private int sample(Path p,float pt){
        int tail=Math.min(p.tail,p.count-1),head=Math.min(p.head,p.count-1),n=0;
        double t=p.head-p.tail>14&&p.fade==0?Math.min(1,p.tailProgress+.06*speed.get()*pt):p.tailProgress;
        for(int k=0;k<3;k++)points[0][k]=p.nodes[tail][k]+(p.nodes[Math.min(tail+1,p.count-1)][k]-p.nodes[tail][k])*t;n++;
        for(int i=tail+1;i<=head;i++){System.arraycopy(p.nodes[i],0,points[n++],0,3);}
        if(p.head<p.count&&n>1){double progress=Math.min(1,p.progress+.06*speed.get()*pt);for(int k=0;k<3;k++)points[n-1][k]=p.nodes[head-1][k]+(p.nodes[head][k]-p.nodes[head-1][k])*progress;}
        return n;
    }
    @Override public void renderWorld(WorldDraw d,float pt){
        long now=System.nanoTime();if(glow.get())d.glowBegin();
        for(Path p:paths){int n=sample(p,pt);float fade=p.fade==0?1:Math.max(0,1-(float)Math.pow((now-p.fade)/700_000_000.,3));
            float pulse=.7f+.3f*(float)(.5+.5*Math.sin(now/1e9*2+p.seed));
            for(int i=0;i<n-1;i++){double[] a=points[i],b=points[i+1];float alpha=fade*pulse*(.22f+.78f*(i+1)*(i+1)/(n*n));int c=Colors.mulAlpha(color.get(),alpha);
                if(glow.get())d.line(a[0],a[1],a[2],b[0],b[1],b[2],.07,Colors.mulAlpha(c,.15f));
                d.line(a[0],a[1],a[2],b[0],b[1],b[2],.015,c);}
            double[] head=points[n-1];d.billboardDot(head[0],head[1],head[2],.035,Colors.mulAlpha(color.get(),fade));
        }if(glow.get())d.glowEnd();
    }
}

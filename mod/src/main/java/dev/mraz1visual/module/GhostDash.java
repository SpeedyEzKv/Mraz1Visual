package dev.mraz1visual.module;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.core.*;
import dev.mraz1visual.render.*;
import dev.mraz1visual.setting.*;
import dev.mraz1visual.ui.Colors;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/** Captures actual posed vanilla skin vertices, without rendering entities or their feature layers again. */
public final class GhostDash extends Module implements DecorativeLayer {
    private static GhostDash instance;
    private final SliderSetting frequency=add(new SliderSetting("frequency","Интервал следа",3,2,10,1," × 0,24 бл.")),duration=add(new SliderSetting("duration","Время жизни",.4,.15,.8,.05," с")),count=add(new SliderSetting("count","Копий",3,1,3,1,"")),speed=add(new SliderSetting("speed","Минимальная скорость",3.5,1,8,.5," бл./с")),opacity=add(new SliderSetting("opacity","Непрозрачность",35,10,70,5," %"));
    private final BoolSetting sprint=add(new BoolSetting("sprint","Только на беге",true));
    private final ColorSetting color=add(new ColorSetting("color","Оттенок скина",0xFF89DDFF));
    private static final class Ghost extends Recorder {double x,y,z;float yaw;long born;Identifier skin;}
    private final Ghost[] ghosts={new Ghost(),new Ghost(),new Ghost()};
    private static final Identifier FROST = Identifier.fromNamespaceAndPath("mraz1visual", "textures/fx/ghost_frost.png");
    private final EnumSetting surface = add(new EnumSetting("surface", "Материал копий", 0,
            "frost", "Матовое стекло", "skin", "Скин игрока"));
    private int activeCount;
    private double travel;
    private int next,lastTick=-1;private Object world;
    private Object player;private double previousX,previousY,previousZ;
    private final PoseStack ps=new PoseStack();
    public GhostDash(){super("ghost_dash","Ghost Dash","Короткие объёмные копии настоящей позы и скина игрока при ускорении.",Category.VISUALS);instance=this;markNew();}
    public static void capture(PlayerModel model,boolean gui){
        var g=instance;var mc=Minecraft.getInstance();var p=mc.player;
        if(gui||g==null||!g.isEnabled()||p==null||p.tickCount==g.lastTick||g.sprint.get()&&!p.isSprinting()||Math.hypot(p.getX()-p.xo,p.getZ()-p.zo)*20<g.speed.get())return;
        if(g.world!=mc.level){g.clear();g.world=mc.level;}
        int limit = g.count.getI();
        if (g.activeCount != limit) {
            for (int i = limit; i < g.ghosts.length; i++) g.ghosts[i].n = 0;
            g.next %= limit;
            g.activeCount = limit;
        }
        double spacing = g.frequency.getI()*.24;
        if (g.lastTick >= 0 && g.travel < spacing) return;
        g.travel %= spacing;
        g.lastTick=p.tickCount;Ghost snap=g.ghosts[g.next];g.next=(g.next+1)%g.count.getI();snap.n=0;
        var local=new PoseStack();
        for(var part:new net.minecraft.client.model.geom.ModelPart[]{model.head,model.body,model.leftArm,model.rightArm,model.leftLeg,model.rightLeg})part.render(local,snap,dev.mraz1visual.compat.Mc.FULL_BRIGHT,OverlayTexture.NO_OVERLAY,-1);
        snap.x=p.getX();snap.y=p.getY();snap.z=p.getZ();snap.yaw=p.yBodyRot;snap.born=System.nanoTime();snap.skin=p.getSkin().body().texturePath();
    }
    @Override public void onTick(Minecraft mc){var p=mc.player;if(mc.level!=world||p!=player||p!=null&&p.distanceToSqr(previousX,previousY,previousZ)>64){clear();world=mc.level;player=p;}if(p!=null){if(lastTick>=0)travel+=Math.hypot(p.getX()-previousX,p.getZ()-previousZ);previousX=p.getX();previousY=p.getY();previousZ=p.getZ();}}
    @Override public void renderWorld(WorldDraw d,float pt){
        if(d.source()==null)return;
        for(int index=0;index<count.getI();index++){var g=ghosts[index];float t=(System.nanoTime()-g.born)/(duration.getF()*1e9f);if(g.n==0||t>=1||g.skin==null)continue;
            ps.setIdentity();ps.last().pose().set(d.framePose());ps.translate(g.x-d.camX(),g.y+1.4072-d.camY(),g.z-d.camZ());ps.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180-g.yaw));ps.scale(.9375f,-.9375f,.9375f);
            var vc=d.source().getBuffer(RenderTypes.entityTranslucent(surface.getId().equals("frost") ? FROST : g.skin));int tint=Colors.mulAlpha(Fx.color("custom",.4f,Fx.time(),color.get(),color.get()),opacity.getF()/100*(1-t)*Fx.decorativeAlpha(g.x,g.y+1,g.z,"trails"));
            double theta=Math.toRadians(180-g.yaw), cs=Math.cos(theta), sn=Math.sin(theta);
            double dx=d.camX()-g.x,dy=d.camY()-g.y-.9,dz=d.camZ()-g.z;
            double distance=Math.max(.01,Math.sqrt(dx*dx+dy*dy+dz*dz));
            double vx=(cs*dx-sn*dz)/distance,vy=-dy/distance,vz=(sn*dx+cs*dz)/distance;
            int faceTint=tint;
            for(int i=0;i<g.n;i++){int o=i*8;
                if ((i&3)==0) {
                    double rim=1-Math.min(1,Math.abs(vx*g.values[o+5]+vy*g.values[o+6]+vz*g.values[o+7]));
                    faceTint=Colors.mulAlpha(tint,(float)(1-t+t*rim*rim));
                }
                vc.addVertex(ps.last().pose(),g.values[o],g.values[o+1],g.values[o+2]).setColor(faceTint).setUv(g.values[o+3],g.values[o+4]).setOverlay(OverlayTexture.NO_OVERLAY).setUv2(240,240).setNormal(g.values[o+5],g.values[o+6],g.values[o+7]);}
        }d.restore();
    }
    private void clear(){for(var g:ghosts)g.n=0;lastTick=-1;next=0;travel=0;}
    @Override protected void onDisable(){clear();world=player=null;}
    private static class Recorder implements VertexConsumer {
        final float[] values=new float[4096*8];int n;
        @Override public VertexConsumer addVertex(float x,float y,float z){if(n>=4096)throw new IllegalStateException("Ghost mesh exceeds bounded pool");int o=n++*8;values[o]=x;values[o+1]=y;values[o+2]=z;return this;}
        @Override public VertexConsumer setColor(int r,int g,int b,int a){return this;}
        @Override public VertexConsumer setColor(int argb){return this;}
        @Override public VertexConsumer setUv(float u,float v){int o=(n-1)*8;values[o+3]=u;values[o+4]=v;return this;}
        @Override public VertexConsumer setUv1(int u,int v){return this;}
        @Override public VertexConsumer setUv2(int u,int v){return this;}
        @Override public VertexConsumer setNormal(float x,float y,float z){int o=(n-1)*8;values[o+5]=x;values[o+6]=y;values[o+7]=z;return this;}
        public VertexConsumer setLineWidth(float value){return this;}
        public VertexConsumer setUv3(float u,float v){return this;}
    }
}

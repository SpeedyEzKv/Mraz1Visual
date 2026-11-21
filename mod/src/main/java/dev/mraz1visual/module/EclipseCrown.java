package dev.mraz1visual.module;
import dev.mraz1visual.core.*;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.render.*;
import dev.mraz1visual.setting.*;
import dev.mraz1visual.ui.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;

public final class EclipseCrown extends Module implements AttackListener {
    private static EclipseCrown instance;
    private final SliderSetting size=add(new SliderSetting("size","Размер",.38,.18,.8,.02," бл.")),offset=add(new SliderSetting("offset","За головой",.32,.2,.65,.02," бл.")),edge=add(new SliderSetting("edge","Толщина каймы",.018,.006,.06,.002," бл."));
    private final SliderSetting inertia=add(new SliderSetting("inertia","Инерция",.22,.05,.8,.02," с")),duration=add(new SliderSetting("duration","Вспышка",.55,.15,1.5,.05," с")),brightness=add(new SliderSetting("brightness","Яркость",70,15,100,5," %")),particles=add(new SliderSetting("particles","Частиц при ударе",6,0,18,1,""));
    private final EnumSetting preset=add(new EnumSetting("preset","Палитра",9,Palette.OPTIONS));
    private final ColorSetting color=add(new ColorSetting("color","Свой цвет",0xFFE6EDFF));
    private final BoolSetting outgoing=add(new BoolSetting("outgoing","При нанесении удара",true)),incoming=add(new BoolSetting("incoming","При получении урона",true));
    private float yaw,lag,flash,previousHurt;
    private double lastX,lastY,lastZ;
    private Object world;
    private net.minecraft.world.entity.LivingEntity attacked;
    private float attackedHealth;private int attackedHurt,attackTicks;
    public EclipseCrown(){super("eclipse_crown","Eclipse Crown","Объёмное затмение за головой с инерцией и реакцией на удары.",Category.COSMETICS);instance=this;markNew();}
    public static boolean active(){return instance!=null&&instance.isEnabled();}
    @Override public void onTick(Minecraft mc){
        if(PhotoMode.frozen())return;
        if(mc.player==null||mc.level==null){world=null;flash=0;attacked=null;return;}var p=mc.player;
        if(world!=mc.level||p.distanceToSqr(lastX,lastY,lastZ)>64){yaw=p.getYHeadRot();lag=0;flash=0;previousHurt=p.hurtTime;world=mc.level;attacked=null;}
        lastX=p.getX();lastY=p.getY();lastZ=p.getZ();
        float delta=net.minecraft.util.Mth.wrapDegrees(p.getYHeadRot()-yaw);yaw+=delta*Math.min(1,.05f/inertia.getF());lag=Math.max(-24,Math.min(24,net.minecraft.util.Mth.wrapDegrees(yaw-p.getYHeadRot())));
        flash=Math.max(0,flash-.05f/duration.getF());
        if(incoming.get()&&p.hurtTime>previousHurt)burst();previousHurt=p.hurtTime;
        if(attacked!=null){if(attacked.level()!=mc.level||attackTicks--<=0)attacked=null;else if(attacked.hurtTime>attackedHurt||attacked.getHealth()<attackedHealth){var damage=attacked.getLastDamageSource();if(damage!=null&&damage.getEntity()==p)burst();attacked=null;}}
    }
    @Override public void onAttack(Entity target){if(outgoing.get()&&target instanceof net.minecraft.world.entity.LivingEntity living){attacked=living;attackedHealth=living.getHealth();attackedHurt=living.hurtTime;attackTicks=12;}}
    private void burst(){flash=1;var p=Minecraft.getInstance().player;if(p==null)return;int amount=Math.max(0,Math.round(particles.getI()*Fx.quality()));for(int i=0;i<amount;i++){double a=Math.PI*2*i/Math.max(1,amount);FxParticles.spawn(p.getX()+Math.cos(a)*.25,p.getEyeY()+Math.sin(a)*.25,p.getZ(),Math.cos(a)*.45,.2,Math.sin(a)*.45,.45f,.025f,Fx.color(preset.getId(),.2f,Fx.time(),color.get(),color.get()),.1f,false,FxParticles.SPARK,true,.2f,0);}}
    public static void submit(PoseStack ps,SubmitNodeCollector collector,boolean gui){var c=instance;if(c==null||!c.isEnabled())return;ps.pushPose();ps.mulPose(com.mojang.math.Axis.YP.rotationDegrees(gui?0:c.lag));WorldDraw.submitLocal(ps,collector,gui,c::paint);ps.popPose();}
    private void paint(WorldDraw d) {
        double radius = size.get() * .88, z = offset.get() + .06, cy = .62;
        float time = Fx.time(), pulse = MusicCosmetics.pulse("crown");
        int n = Math.max(12,Math.round(Fx.segments(40)*d.localDetail()));
        float strength = brightness.getF() / 100 * Fx.brightness();
        int silver = Fx.color(preset.getId(), .13f, time, color.get(), color.get());
        double sweep = (1 - flash) * Math.PI * 2;
        double sweepX=Math.cos(sweep), sweepY=Math.sin(sweep);
        boolean glow = d.isGlowPass();
        if (glow) d.glowBegin();
        for (int i = 0; i < n; i++) {
            double x = WorldDraw.cos(i,n), y = WorldDraw.sin(i,n);
            double nx = WorldDraw.cos(i+1,n), ny = WorldDraw.sin(i+1,n);
            double facing = (.5 + .5 * (.8*x + .6*y));
            double crescent = facing * facing * facing;
            double impact = flash > 0 ? Math.max(0, (x*sweepX+y*sweepY-.78)/.22) * Fx.flash(Math.min(1,flash*5)) : 0;
            double lip = radius + edge.get() * (.25 + 2.15*crescent + impact);
            int rim = Colors.mulAlpha(silver, strength * (float)(.22 + .78*crescent + impact));
            if (glow) {
                double halo = lip + .034 * (pulse + crescent + impact);
                int hot = Colors.mulAlpha(rim, (float)(.35*crescent + impact*.5));
                d.quad(x*lip,cy+y*lip,z,nx*lip,cy+ny*lip,z,nx*halo,cy+ny*halo,z,x*halo,cy+y*halo,z,hot,hot,hot&0xffffff,hot&0xffffff);
                continue;
            }
            int graphite = Colors.lerp(0xFF10111B, 0xFF343747, (float)facing);
            d.tri(-radius*.15,cy+radius*.18,z+.032,x*radius,cy+y*radius,z,nx*radius,cy+ny*radius,z,0xFF101019,graphite,graphite);
            d.tri(0,cy,z-.025,nx*radius,cy+ny*radius,z,x*radius,cy+y*radius,z,0xFF181925,graphite,graphite);
            // The second broken arc follows the illuminated quadrant of the bevel.
            if (facing > .71 && i%9 != 0) {
                double inner = radius*.90, outer = inner+.006;
                d.quad(x*inner,cy+y*inner,z+.012,nx*inner,cy+ny*inner,z+.012,nx*outer,cy+ny*outer,z+.012,x*outer,cy+y*outer,z+.012,Colors.mulAlpha(rim,.52f));
            }
            d.quad(x*radius,cy+y*radius,z,nx*radius,cy+ny*radius,z,nx*lip,cy+ny*lip,z,x*lip,cy+y*lip,z,rim);
        }
        if (glow) {
            double x = radius*.8, y = cy+radius*.6;
            int hot = Colors.mulAlpha(silver, strength*(.68f+pulse));
            double spark=.025+.012*pulse;
            d.billboardDot(x,y,z+.012,spark,hot,6);
            d.line(x-spark*1.4,y,z+.018,x+spark*1.4,y,z+.018,.007,hot);
            d.line(x,y-spark,z+.018,x,y+spark,z+.018,.007,hot);
            d.glowEnd();
        }
    }
    @Override protected void onDisable(){flash=lag=0;world=null;attacked=null;}
}

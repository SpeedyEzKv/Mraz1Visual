package dev.mraz1visual.module;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.core.Category;
import dev.mraz1visual.render.*;
import dev.mraz1visual.setting.*;
import dev.mraz1visual.ui.Colors;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;

public final class ShardWings extends Module {
    private static ShardWings instance;
    private final EnumSetting material=add(new EnumSetting("material","Материал",0,"glass","Стекло","obsidian","Обсидиан","stardust","Звёздная пыль"));
    private final EnumSetting preset=add(new EnumSetting("preset","Палитра",9,Palette.OPTIONS));
    private final SliderSetting size=add(new SliderSetting("size","Размер",1,.5,1.7,.05,"×")),count=add(new SliderSetting("count","Осколков на крыло",10,4,18,1,"")),speed=add(new SliderSetting("speed","Скорость складывания",1,.3,2,.1,"×")),opacity=add(new SliderSetting("opacity","Непрозрачность",65,15,100,5," %"));
    private final BoolSetting trail=add(new BoolSetting("trail","След на бегу",true)),elytra=add(new BoolSetting("elytra","Показывать с элитрами",false));
    private float open, previousOpen, tip, previousTip;
    private static final int[] SIDES = {-1, 1};
    private Object world;
    public ShardWings(){super("shard_wings","Shard Wings","Осколочные крылья: складываются в покое, раскрываются в прыжке, оставляют короткий след.",Category.WINGS);instance=this;markNew();}
    public static boolean active(){return instance!=null&&instance.isEnabled();}
    @Override public void onTick(Minecraft mc){
        if(PhotoMode.frozen())return;
        if(mc.player==null||mc.level==null){world=null;open=previousOpen=tip=previousTip=0;return;}var p=mc.player;
        if(world!=mc.level){world=mc.level;open=previousOpen=tip=previousTip=0;}
        float target=p.isFallFlying()?1:!p.onGround()?.95f:p.isSprinting()?.65f:p.getDeltaMovement().horizontalDistanceSqr()>.002?.3f:0;
        previousOpen = open;
        previousTip = tip;
        open += (target-open)*Math.min(1, .32f*speed.getF());
        tip += (open-tip)*Math.min(1, .19f*speed.getF());
        if(trail.get()&&p.isSprinting()&&p.tickCount%3==0){double yaw=Math.toRadians(p.yBodyRot);for(int side:SIDES){
            double dx=side*(.35+open*.6)*Math.cos(yaw),dz=side*(.35+open*.6)*Math.sin(yaw);
            FxParticles.spawn(p.getX()+dx,p.getY()+1.1,p.getZ()+dz,0,.06,0,.35f,.025f,Fx.color(preset.getId(),.3f,Fx.time(),-1,-1),.1f,false,material.getId().equals("stardust")?FxParticles.STAR:FxParticles.DIAMOND,true,.2f,0);
        }}
    }
    public static void submit(PoseStack ps, SubmitNodeCollector collector, boolean gui) {
        var w=instance;
        var player=Minecraft.getInstance().player;
        if(w==null || !w.isEnabled() || player==null)return;
        boolean equipped=player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).is(net.minecraft.world.item.Items.ELYTRA);
        if(equipped && !w.elytra.get())return;
        ps.pushPose();
        if(equipped)ps.translate(0, .07, .27);
        WorldDraw.submitLocal(ps,collector,gui,w::paint);
        ps.popPose();
    }
    private void paint(WorldDraw d) {
        float scale = size.getF(), time = Fx.time(), pulse = MusicCosmetics.pulse("wings");
        float partial = PhotoMode.frozen() ? 1 : Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
        float shoulder = previousOpen + partial*(open-previousOpen);
        float fingertip = previousTip + partial*(tip-previousTip);
        boolean obsidian = material.getId().equals("obsidian");
        boolean stars = material.getId().equals("stardust");
        float opacityScale = opacity.getF()/100;
        boolean glow = d.isGlowPass();
        if (glow) d.glowBegin();
        String theme = VisualFx.get() != null && VisualFx.get().sync.get() ? VisualFx.get().preset.getId() : preset.getId();
        int panels = Math.max(4,Math.round(count.getI()*d.localDetail()));
        int socket = Fx.color(preset.getId(),.72f,time,0xFF8895B2,0xFF8895B2);
        for(int side:SIDES){
            double hingeX=side*.19*scale, hingeY=-.17*scale, hingeZ=.29*scale;
            if(glow) continue;
            d.crystal(hingeX,hingeY,hingeZ,.065*scale,.105*scale,.78,
                    Colors.lerp(socket,-1,.55f),Colors.shade(socket,.42f));
        }
        for (int side : SIDES) for (int i = 0; i < panels; i++) {
            double t = (double)i/(panels-1);
            double spread = shoulder*(1-t) + fingertip*t;
            double rootX = side*scale*(.11 + (.12 + shoulder*.23)*(1-t));
            double rootY = scale*(-.08 - .37*t + shoulder*.12*(1-t));
            double rootZ = scale*(.255 + .08*t);
            double angle = .19 + .57*(1-t) + spread*(.64 + .66*(1-t));
            double length = scale*(.46 + .36*(1-t))*(1 + .29*spread);
            double dx = side*Math.sin(angle), dy = -Math.cos(angle);
            double tipX = rootX + dx*length, tipY = rootY + dy*length;
            double tipZ = rootZ + scale*(.14 + .14*t);
            double width = scale*(.066 + .044*(1-t))*(stars ? .72 : 1);
            double mx = rootX + dx*length*.40, my = rootY + dy*length*.40;
            double mz = rootZ + scale*.11, ridge = mz + scale*.038;
            double lx = mx+dy*width, ly = my-dx*width, rx = mx-dy*width, ry = my+dx*width;
            int accent = Fx.color(preset.getId(), (float)(.2+.5*t), time+(float)t*4, 0xFFE7F1FF, 0xFF849ABE);
            float glint = (float)Math.max(0, 1-Math.abs((time*.25+t+1)%1-.5)*9);
            if (theme.equals("amethyst")) glint = (int)(time*3)%panels == i ? .9f : .06f;
            if (theme.equals("ember")) glint *= .3f + .7f*shoulder;
            if (theme.equals("aurora")) glint = .35f+.3f*(float)Math.sin(t*4+time*.65);
            int line = Colors.mulAlpha(accent, (.55f + .25f*glint + .20f*pulse)*opacityScale*Fx.brightness());
            if (glow) {
                int cut = Colors.mulAlpha(Colors.lerp(accent,-1,.44f),opacityScale*(.45f+.34f*glint+.2f*pulse)*Fx.brightness());
                d.line(mx,my,ridge+.003,tipX,tipY,tipZ+.003,.010*scale,cut);
                if (stars) d.billboardDot(tipX,tipY,tipZ,.014*scale,line,6);
                if (pulse > .015f || glint > .1f) d.line(mx,my,ridge,tipX,tipY,tipZ,.008*scale,Colors.mulAlpha(line,.2f+glint*.3f+pulse));
                continue;
            }
            int face = obsidian ? Colors.lerp(0xFF171423,accent,.13f) : Colors.lerp(0xFF8298BA,accent,.68f);
            int shadow = Colors.mulAlpha(Colors.shade(face,.52f),opacityScale*(obsidian?1:.72f));
            int bright = Colors.mulAlpha(face,opacityScale*(obsidian?1:stars?.32f:.69f));
            d.quad(rootX,rootY,rootZ,lx,ly,mz,tipX,tipY,tipZ,mx,my,ridge,shadow,bright,shadow,bright);
            d.quad(rootX,rootY,rootZ,mx,my,ridge,tipX,tipY,tipZ,rx,ry,mz,bright,bright,shadow,shadow);
            d.line(lx,ly,mz,tipX,tipY,tipZ,.012*scale,line);
            d.line(rootX,rootY,rootZ,lx,ly,mz,.009*scale,line);
        }
        if (glow) d.glowEnd();
    }
    @Override protected void onDisable(){open=previousOpen=tip=previousTip=0;world=null;}
}

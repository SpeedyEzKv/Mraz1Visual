package dev.mraz1visual.module;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.core.Category;
import dev.mraz1visual.setting.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.Screen;
import dev.mraz1visual.mixin.OptionInstanceAccessor;

/** Local vanilla third-person orbit with wall collisions retained; never moves the server player. */
public final class PhotoMode extends Module {
    private static PhotoMode instance;
    private final SliderSetting zoom=add(new SliderSetting("zoom","Расстояние камеры",4,1.5,10,.25," бл.")),fov=add(new SliderSetting("fov","Поле зрения",60,30,110,1,"°")),brightness=add(new SliderSetting("brightness","Яркость кадра",.7,0,2,.1,"×"));
    private final BoolSetting freeze=add(new BoolSetting("freeze","Заморозить косметику",true)),effects=add(new BoolSetting("effects","Декоративные частицы",true));
    private Screen parent;private CameraType camera;private int previousFov;private double previousGamma;private Object world;private float yaw,pitch,timeOffset;private boolean entered;private int shotTicks;private double frozenAt=-1;
    public PhotoMode(){super("photo_mode","Photo Mode","Локальный фоторежим: орбита, FOV, яркость, косметика и снимок без HUD. Мир продолжает работать.",Category.VISUALS);instance=this;bind.set(com.mojang.blaze3d.platform.InputConstants.KEY_F7);markNew();}
    public static boolean active(){return instance!=null&&instance.isEnabled()&&instance.entered;}
    public static boolean frozen(){return active()&&instance.freeze.get();}
    public static boolean effectsVisible(){return !active()||instance.effects.get();}
    public static float animationTime(float normal){var p=instance;if(p==null)return normal;if(!frozen()){if(p.frozenAt>=0){p.timeOffset+=normal-p.frozenAt;p.frozenAt=-1;}return normal-p.timeOffset;}if(p.frozenAt<0)p.frozenAt=normal;return (float)p.frozenAt-p.timeOffset;}
    public static float yaw(){return active()?instance.yaw:0;}
    public static float pitch(){return active()?instance.pitch:0;}
    public static float distance(){return instance.zoom.getF();}
    @SuppressWarnings("unchecked") private static OptionInstanceAccessor<Double> gamma(){return (OptionInstanceAccessor<Double>)(Object)Minecraft.getInstance().options.gamma();}
    @Override protected void onEnable(){if(entered)return;var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null){setEnabled(false);return;}
        parent=mc.screen;camera=mc.options.getCameraType();previousFov=mc.options.fov().get();previousGamma=gamma().mraz1visual$getValue();world=mc.level;entered=true;yaw=pitch=0;shotTicks=0;frozenAt=-1;
        mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);mc.setScreen(new dev.mraz1visual.ui.PhotoScreen());
    }
    @Override protected void onDisable(){if(!entered)return;entered=false;animationTime(dev.mraz1visual.render.Fx.rawTime());var mc=Minecraft.getInstance();mc.options.setCameraType(camera);mc.options.fov().set(previousFov);gamma().mraz1visual$setValue(previousGamma);if(mc.screen instanceof dev.mraz1visual.ui.PhotoScreen)mc.setScreen(world==mc.level?parent:null);world=null;shotTicks=0;}
    @Override public void onTick(Minecraft mc){if(!entered)return;if(mc.player==null||!mc.player.isAlive()||world!=mc.level||!(mc.screen instanceof dev.mraz1visual.ui.PhotoScreen)){setEnabled(false);return;}
        mc.options.fov().set(fov.getI());gamma().mraz1visual$setValue(brightness.get());
        if(shotTicks>0&&--shotTicks==0)net.minecraft.client.Screenshot.grab(mc.gameDirectory,"Mraz1Visual-photo-"+System.currentTimeMillis()+".png",mc.getMainRenderTarget(),1,message->mc.execute(()->{if(mc.player!=null)dev.mraz1visual.compat.Mc.message(mc.player,message,false);}));
    }
    public static boolean hidingControls(){return active()&&instance.shotTicks>0;}
    public static void takeShot(){if(active())instance.shotTicks=3;}
    public static void exit(){if(instance!=null)instance.setEnabled(false);}
    public static void orbit(double dx,double dy){if(active()){instance.yaw+=(float)dx*.35f;instance.pitch=Math.max(-70,Math.min(70,instance.pitch+(float)dy*.35f));}}
    public static void zoom(double delta){if(active())instance.zoom.set(Math.max(1.5,Math.min(10,instance.zoom.get()-delta*.25)));}
    public static void fov(double delta){if(active())instance.fov.set(Math.max(30,Math.min(110,instance.fov.get()+delta)));}
    public static void light(double delta){if(active())instance.brightness.set(Math.max(0,Math.min(2,instance.brightness.get()+delta)));}
    public static void toggleFreeze(){if(active()){animationTime(dev.mraz1visual.render.Fx.rawTime());instance.freeze.set(!instance.freeze.get());animationTime(dev.mraz1visual.render.Fx.rawTime());}}
    public static void toggleEffects(){if(active())instance.effects.set(!instance.effects.get());}
}

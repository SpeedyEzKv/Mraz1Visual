package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.render.Palette;
import dev.mraz1visual.setting.BoolSetting;
import dev.mraz1visual.setting.ColorSetting;
import dev.mraz1visual.setting.EnumSetting;
import dev.mraz1visual.setting.SliderSetting;

/** Общие настройки всех визуалов: качество, яркость, «Без вспышек» и один пресет на всё. Всегда включён. */
public final class VisualFx extends Module {
    public final EnumSetting quality = add(new EnumSetting("quality", "Качество эффектов", 2,
            "low", "Низкое", "medium", "Среднее", "high", "Высокое", "ultra", "Ультра")
            .hint("Сколько частиц, сегментов у колец и лент. На слабом ПК — «Среднее»."));
    public final SliderSetting brightness = add(new SliderSetting("brightness", "Яркость эффектов", 100, 30, 130, 5, " %"));
    public final BoolSetting noFlash = add(new BoolSetting("no_flash", "Без вспышек", false)
            .hint("Убирает резкие вспышки и мерцание: удары, убийства и тотемы светятся мягко."));
    public final BoolSetting sync = add(new BoolSetting("sync", "Один пресет на все визуалы", false).group("Палитра"));
    public final EnumSetting preset = add(new EnumSetting("preset", "Пресет", 0, Palette.OPTIONS).group("Палитра"));
    public final ColorSetting color1 = add(new ColorSetting("color1", "Свой: центр", 0xFFB8E6FF).group("Палитра"));
    public final ColorSetting color2 = add(new ColorSetting("color2", "Свой: край", 0xFF7A6BFF).group("Палитра"));
    public final BoolSetting combatClarity = add(new BoolSetting("combat_clarity", "Приглушать украшения около цели", true).group("Обзор"));
    public final SliderSetting clarityStrength = add(new SliderSetting("clarity_strength", "Сила приглушения", 65,0,95,5," %").group("Обзор"));
    public final SliderSetting clarityAngle = add(new SliderSetting("clarity_angle", "Область около прицела", 12,3,30,1,"°").group("Обзор"));
    public final SliderSetting claritySpeed = add(new SliderSetting("clarity_speed","Скорость перехода",6,2,15,1,"×").group("Обзор"));
    public final BoolSetting clarityParticles=add(new BoolSetting("clarity_particles","Приглушать частицы",true).group("Обзор")),clarityTrails=add(new BoolSetting("clarity_trails","Приглушать следы и волны",true).group("Обзор")),clarityCosmetics=add(new BoolSetting("clarity_cosmetics","Приглушать украшения у камеры",true).group("Обзор"));
    private float focus;
    private double eyeX,eyeY,eyeZ,aimX,aimY,aimZ;

    @Override public void onTick(net.minecraft.client.Minecraft mc) {
        boolean target=mc.screen==null&&mc.player!=null&&mc.level!=null&&mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult hit&&hit.getEntity() instanceof net.minecraft.world.entity.LivingEntity living&&living.isAlive();
        focus+=((combatClarity.get()&&target?1f:0f)-focus)*Math.min(1,claritySpeed.getF()*.05f);
        if(mc.player!=null){var eye=mc.player.getEyePosition();var aim=mc.player.getViewVector(1);eyeX=eye.x;eyeY=eye.y;eyeZ=eye.z;aimX=aim.x;aimY=aim.y;aimZ=aim.z;}else focus=0;
    }
    public float decorativeAlpha(double x,double y,double z){
        return decorativeAlpha(x,y,z,"particles");
    }
    public float decorativeAlpha(double x,double y,double z,String kind){
        if(kind.equals("particles")&&!clarityParticles.get()||kind.equals("trails")&&!clarityTrails.get()||kind.equals("cosmetics")&&!clarityCosmetics.get())return 1;
        if(focus<.001f)return 1;
        double dx=x-eyeX,dy=y-eyeY,dz=z-eyeZ,len=Math.sqrt(dx*dx+dy*dy+dz*dz);
        if(len<.05)return 1;
        double dot=(dx*aimX+dy*aimY+dz*aimZ)/len,edge=Math.cos(Math.toRadians(clarityAngle.get()));
        float weight=(float)Math.max(0,Math.min(1,(dot-edge)/(1-edge)));
        return 1-focus*clarityStrength.getF()/100*weight;
    }
    public float cosmeticNearCameraAlpha(double distance){
        if(!clarityCosmetics.get())return 1;
        return 1-focus*clarityStrength.getF()/100*(float)Math.max(0,Math.min(1,(1.8-distance)/1.8));
    }

    private static VisualFx instance;

    public VisualFx() {
        super("visual_fx", "Эффекты: общее", "Качество, яркость, «Без вспышек» и один цвет на все визуалы.", Category.VISUALS);
        instance = this;
    }

    public static VisualFx get() {
        return instance;
    }

    @Override
    public boolean alwaysOn() {
        return true;
    }
}

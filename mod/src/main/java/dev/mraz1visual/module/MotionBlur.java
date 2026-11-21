package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.render.post.WorldMotionBlur;
import dev.mraz1visual.setting.SliderSetting;

/** World-only temporal blur. Never submits a fullscreen image into the HUD. */
public final class MotionBlur extends Module {
    private static MotionBlur instance;
    final SliderSetting strength=add(new SliderSetting("strength","Сила",.45,0,1,.05,""));
    public MotionBlur(){super("motion_blur","Motion Blur","Плавный шлейф при повороте камеры, без размытия HUD.",Category.SKY);instance=this;markNew();}
    @Override protected void onDisable(){WorldMotionBlur.reset();}
    public static void afterLevel(){
        if(instance==null||!instance.isEnabled()||instance.strength.getF()<=0){WorldMotionBlur.reset();return;}
        WorldMotionBlur.render(instance.strength.getF());
    }
}

package dev.mraz1visual.module;
import dev.mraz1visual.core.*;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.setting.*;
import net.minecraft.client.Minecraft;

/** Playback breathing is deliberately distinct from actual local PCM amplitude. */
public final class MusicCosmetics extends Module {
    private static MusicCosmetics instance;
    private final EnumSetting mode=add(new EnumSetting("mode","Реакция",1,"playback","Плавная анимация воспроизведения","pcm","Реальный сигнал встроенного плеера"));
    private final SliderSetting amount=add(new SliderSetting("amount","Интенсивность",35,0,100,5," %"));
    private final BoolSetting crown=add(new BoolSetting("crown","Корона",true)),wings=add(new BoolSetting("wings","Крылья",true)),pet=add(new BoolSetting("pet","Питомец",true)),weapon=add(new BoolSetting("weapon","Оружие",true));
    private float value;
    public MusicCosmetics(){super("music_cosmetics","Music Cosmetics","Мягкое дыхание при воспроизведении или настоящий PCM встроенного плеера; внешний звук не записывается.",Category.COSMETICS);instance=this;markNew();}
    @Override protected void onEnable(){dev.mraz1visual.music.MediaClient.start();}
    @Override public void onTick(Minecraft mc){
        if(PhotoMode.frozen())return;
        float target=0;
        if(dev.mraz1visual.music.NowPlaying.available()&&dev.mraz1visual.music.NowPlaying.local()&&dev.mraz1visual.music.NowPlaying.playing()){
            if(mode.getId().equals("pcm")){if(dev.mraz1visual.music.NowPlaying.local())target=Math.min(1,dev.mraz1visual.music.player.MusicPlayer.signalLevel*4);}
            else target=.45f+.15f*(float)Math.sin(dev.mraz1visual.render.Fx.time()*1.8);
        }
        value+=(target-value)*(target>value?.35f:.14f);
    }
    public static float pulse(String part){var m=instance;if(m==null||!m.isEnabled())return 0;boolean on=switch(part){case "crown"->m.crown.get();case "wings"->m.wings.get();case "pet"->m.pet.get();default->m.weapon.get();};return on?m.value*m.amount.getF()/100:0;}
    @Override protected void onDisable(){value=0;}
}

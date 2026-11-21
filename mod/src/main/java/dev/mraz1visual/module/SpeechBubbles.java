package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.core.WorldLayer;
import dev.mraz1visual.render.WorldDraw;
import dev.mraz1visual.setting.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Chat bubbles attached only to players whose names occur in received chat. */
public final class SpeechBubbles extends Module implements WorldLayer {
    private static SpeechBubbles instance;
    private final SliderSetting duration=add(new SliderSetting("duration","Длительность",5,2,15,.5," с"));
    private final SliderSetting size=add(new SliderSetting("size","Размер",1,.5,2.5,.05,"×"));
    private final SliderSetting distance=add(new SliderSetting("distance","Дистанция",25,4,48,1," бл."));
    private final BoolSetting own=add(new BoolSetting("own","Своё сообщение",true));
    private final ColorSetting color=add(new ColorSetting("color","Текст",0xFFFFFFFF));
    private static final class Bubble {
        final String text; final long born; Component display;
        Bubble(String text,long born){this.text=text;this.born=born;}
    }
    private final java.util.Map<java.util.UUID,Bubble> bubbles=new java.util.HashMap<>();
    public SpeechBubbles(){super("speech_bubbles","Speech Bubbles","Сообщения чата над игроками (AuraVisual).",Category.VISUALS);instance=this;}
    public static void message(Component message){
        SpeechBubbles m=instance;Minecraft mc=Minecraft.getInstance();
        if(m==null||!m.isEnabled()||mc.level==null||message==null)return;
        String raw=message.getString();
        for(var player:mc.level.players()) {
            if(player==mc.player&&!m.own.get())continue;
            String name=player.getGameProfile().name();
            var matcher=java.util.regex.Pattern.compile("(?:^|[<\\s\\[|])"+java.util.regex.Pattern.quote(name)+"(?:>|:|\\s*»|\\s*→)\\s*(.+)").matcher(raw);
            if(matcher.find()) {String body=matcher.group(1);m.bubbles.put(player.getUUID(),new Bubble(body.substring(0,Math.min(220,body.length())),System.nanoTime()));break;}
        }
    }
    @Override protected void onDisable(){bubbles.clear();}
    @Override public void onTick(Minecraft mc){
        long now=System.nanoTime();bubbles.values().removeIf(b->now-b.born>duration.get()*1e9);
        for(Bubble b:bubbles.values())b.display=Component.literal(mc.font.plainSubstrByWidth(StreamerMode.filter(b.text),180));
    }
    @Override public void renderWorld(WorldDraw d,float pt){
        Minecraft mc=Minecraft.getInstance();if(mc.player==null||mc.level==null)return;long now=System.nanoTime();
        bubbles.values().removeIf(b->now-b.born>duration.get()*1e9);
        for(var p:mc.level.players()) {
            Bubble b=bubbles.get(p.getUUID());if(b==null||b.display==null||p.distanceToSqr(mc.player)>distance.get()*distance.get())continue;
            d.label(b.display,p.xo+(p.getX()-p.xo)*pt,p.yo+(p.getY()-p.yo)*pt+p.getBbHeight()+.55,p.zo+(p.getZ()-p.zo)*pt,size.getF(),color.get(),0xC0181822);
        }
    }
}

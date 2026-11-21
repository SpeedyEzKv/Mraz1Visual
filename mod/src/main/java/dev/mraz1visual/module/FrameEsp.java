package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.core.WorldLayer;
import dev.mraz1visual.render.WorldDraw;
import dev.mraz1visual.setting.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.decoration.ItemFrame;

public final class FrameEsp extends Module implements WorldLayer {
    private final ColorSetting color=add(new ColorSetting("color","Цвет",0xFFE4274E));
    private final SliderSetting range=add(new SliderSetting("range","Дистанция",24,4,64,1," бл."));
    private final BoolSetting itemName=add(new BoolSetting("item_name","Название предмета",true));
    public FrameEsp(){super("frame_esp","Frame ESP","Рамка и подпись предметов в видимых рамках (AuraVisual).",Category.VISUALS);}
    @Override public void renderWorld(WorldDraw d,float pt){
        Minecraft mc=Minecraft.getInstance();if(mc.level==null||mc.player==null)return;
        for(var entity:mc.level.entitiesForRendering())if(entity instanceof ItemFrame frame&&frame.distanceToSqr(mc.player)<range.get()*range.get()){
            var box=frame.getBoundingBox().inflate(.015);d.edges(box.minX,box.minY,box.minZ,box.maxX,box.maxY,box.maxZ,.012,color.get());
            if(itemName.get()&&!frame.getItem().isEmpty())d.label(frame.getItem().getHoverName(),frame.getX(),box.maxY+.15,frame.getZ(),.7f,0xFFFFFFFF,0xA0111118);
        }
    }
}

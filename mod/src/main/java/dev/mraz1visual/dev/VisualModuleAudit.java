package dev.mraz1visual.dev;

import dev.mraz1visual.Mraz1Visual;
import dev.mraz1visual.core.*;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.render.*;
import net.minecraft.client.Minecraft;
import java.util.*;
import java.util.function.BiConsumer;

/** Opt-in lifecycle/render audit, run only after the isolated world fixtures. */
final class VisualModuleAudit {
    private static List<Module> modules;
    private static final Map<Module,Boolean> previous=new LinkedHashMap<>();
    private static Module current,sounds;private static boolean soundState;
    private static int index,frames,moduleErrors,worldErrors,hudErrors;
    static boolean tick(Minecraft mc,BiConsumer<Boolean,String> check){
        if(modules==null){
            var categories=EnumSet.of(Category.HUD,Category.VISUALS,Category.COSMETICS,Category.SWORDS,Category.WINGS,Category.PETS,Category.SKY,Category.COMBAT,Category.SHADERS);
            modules=Mraz1Visual.MODULES.all().stream().filter(m->categories.contains(m.category)&&!m.alwaysOn()&&!Set.of("photo_mode","world_music").contains(m.id)).toList();
            sounds=Mraz1Visual.MODULES.find("ui_sounds");if(sounds!=null){soundState=sounds.isEnabled();sounds.setEnabled(false);}
            for(var m:modules){previous.put(m,m.isEnabled());m.setEnabled(false);}mc.setScreen(null);
        }
        if(current!=null){if(++frames<6)return false;
            current.setEnabled(false);
            check.accept(ModuleManager.failureCount()==moduleErrors&&WorldDraw.errorCount()==worldErrors&&HudDraw.errorCount()==hudErrors,"visual module enable/tick/render/disable: "+current.id);
            current=null;
        }
        if(index>=modules.size()){
            for(var entry:previous.entrySet())entry.getKey().setEnabled(entry.getValue());if(sounds!=null)sounds.setEnabled(soundState);
            check.accept(true,"isolated visual lifecycle audit completed for "+modules.size()+" modules; initial states restored");return true;
        }
        moduleErrors=ModuleManager.failureCount();worldErrors=WorldDraw.errorCount();hudErrors=HudDraw.errorCount();frames=0;
        current=modules.get(index++);current.setEnabled(true);return false;
    }
}

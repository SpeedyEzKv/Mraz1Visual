package dev.mraz1visual.dev;
import dev.mraz1visual.Mraz1Visual;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.models.*;
import dev.mraz1visual.module.*;
import dev.mraz1visual.setting.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.CameraType;
import net.minecraft.world.item.*;
import java.util.*;
import java.util.function.BiConsumer;

/** Opt-in fixtures for the isolated world only. Never loaded in normal gameplay. */
final class EclipseSmoke {
    private static int phase;private static CameraType camera;private static int fov;private static double gamma;
    private static Module module(String id){return Objects.requireNonNull(Mraz1Visual.MODULES.find(id),id);}
    private static Object field(Object o,String name)throws Exception{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    private static void set(Object o,String name,Object value)throws Exception{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,value);}
    static void frame(Minecraft mc){if(mc.player!=null&&phase==3){mc.player.setSprinting(true);mc.player.setDeltaMovement(.23,0,0);}}
    static boolean run(Minecraft mc,BiConsumer<Boolean,String> check)throws Exception{
        var p=mc.player;
        switch(phase++){
            case 0->{
                mc.setScreen(null);mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                VisualRegression.modelPreferences(check);
                VisualRegression.beginCoverRace();
                for(String id:new String[]{"eclipse_crown","shard_wings","ghost_dash","pet_reactions","surface_effects","landing_dust","skin_transition","music"})module(id).setEnabled(true);
                MediaIntegrationSmoke.begin(mc.gameDirectory.toPath());
                var size=(SliderSetting)module("eclipse_crown").settings().stream().filter(s->s.id.equals("size")).findFirst().orElseThrow();double old=size.get();size.set(.5);Mraz1Visual.CONFIG.save();size.setSilently(.2);Mraz1Visual.CONFIG.load();Mraz1Visual.MODULES.afterConfigLoad();
                check.accept(size.get()==.5&&module("shard_wings").isEnabled(),"Eclipse settings and module toggles survive actual config save/load");size.set(old);
                var v=VisualFx.get();int oldPreset=v.preset.get();boolean oldSync=v.sync.get();v.sync.set(true);v.preset.set(9);int c=dev.mraz1visual.render.Fx.color("custom",.4f,0,0xFFFF0000,0xFFFF0000);v.preset.set(11);int d=dev.mraz1visual.render.Fx.color("custom",.4f,0,0xFFFF0000,0xFFFF0000);v.sync.set(false);
                check.accept(c!=d&&dev.mraz1visual.render.Fx.color("custom",.4f,0,0xFFFF0000,0xFFFF0000)==0xFFFF0000,"shared palettes change effects without overwriting individual colors");v.preset.set(oldPreset);v.sync.set(oldSync);
            }
            case 1->{
                VisualRegression.verifyCoverRace(check);
                var pet=module("pet_reactions");var before=p.getInventory().getItem(8).copy();float hp=p.getHealth();var motion=p.getDeltaMovement();p.getInventory().setItem(8,ItemStack.EMPTY);pet.onTick(mc);p.getInventory().setItem(8,new ItemStack(Items.DIAMOND,3));pet.onTick(mc);
                check.accept(!PetReactions.state().equals("joy"),"inventory edits do not impersonate a diamond pickup");
                var drop=new net.minecraft.world.entity.item.ItemEntity(mc.level,p.getX(),p.getY(),p.getZ(),new ItemStack(Items.DIAMOND));
                drop.setId(-800904);mc.level.addEntity(drop);
                p.connection.handleTakeItemEntity(new net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket(drop.getId(),p.getId(),1));
                pet.onTick(mc);
                check.accept(PetReactions.state().equals("joy"),"server pickup packet for the local owner's diamond triggers pet celebration");p.setHealth(2);pet.onTick(mc);check.accept(PetReactions.state().equals("hide"),"low health overrides pet celebration");
                p.setHealth(20);p.setDeltaMovement(0,0,0);p.setOnGround(true);for(int i=0;i<2500;i++)pet.onTick(mc);check.accept(PetReactions.state().equals("sleep"),"pet sleeps only after sustained inactivity");p.setDeltaMovement(.2,0,0);pet.onTick(mc);check.accept(PetReactions.state().equals("wake"),"moving wakes the sleeping pet");p.getInventory().setItem(8,before);p.setHealth(hp);p.setDeltaMovement(motion);
            }
            case 2->{mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);p.setSprinting(true);}
            case 3->{
                Object[] ghosts=(Object[])field(module("ghost_dash"),"ghosts");int populated=0,max=0;for(Object g:ghosts){var n=g.getClass().getSuperclass().getDeclaredField("n");n.setAccessible(true);int count=n.getInt(g);if(count>0)populated++;max=Math.max(max,count);}
                check.accept(populated>0&&populated<=3&&max<=4096,"Ghost Dash captures bounded real posed skin vertices in the player render layer");net.minecraft.client.Screenshot.grab(mc.gameDirectory,"eclipse-ghost-wings.png",mc.getMainRenderTarget(),1,ignored->{});p.setSprinting(false);p.setDeltaMovement(0,0,0);
                double beforeX=p.getX(),beforeY=p.getY(),beforeZ=p.getZ();p.setPos(beforeX+20,beforeY,beforeZ);module("ghost_dash").onTick(mc);p.setPos(beforeX,beforeY,beforeZ);module("ghost_dash").onTick(mc);
                boolean cleared=true;for(Object g:ghosts){var n=g.getClass().getSuperclass().getDeclaredField("n");n.setAccessible(true);cleared&=n.getInt(g)==0;}check.accept(cleared,"teleporting clears every old Ghost Dash copy");
                var endpoint=dev.mraz1visual.music.MediaClient.class.getDeclaredField("endpoint");endpoint.setAccessible(true);check.accept(endpoint.get(null)!=null&&dev.mraz1visual.music.MediaClient.error.isEmpty(),"bundled native music helper serves the game without a launcher connection");
                var crown=module("eclipse_crown");crown.onTick(mc);p.hurtTime=10;crown.onTick(mc);check.accept((Float)field(crown,"flash")>0,"Eclipse Crown reacts to actual damage state");
                var kill=module("kill_effect");kill.setEnabled(true);((EnumSetting)kill.settings().stream().filter(s->s.id.equals("style")).findFirst().orElseThrow()).set(8);
                var victim=new net.minecraft.client.player.RemotePlayer(mc.level,new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"EclipseFixture"));victim.setId(-800903);victim.setPos(p.getX()+2,p.getY(),p.getZ());victim.setHealth(20);mc.level.addEntity(victim);kill.onTick(mc);((dev.mraz1visual.core.AttackListener)kill).onAttack(victim);kill.onTick(mc);
                check.accept(((List<?>)field(kill,"fx")).isEmpty(),"Eclipse kill effect never triggers on attack intent alone");victim.handleDamageEvent(p.damageSources().playerAttack(p));victim.setHealth(0);kill.onTick(mc);check.accept(((List<?>)field(kill,"fx")).size()==1,"Eclipse kill effect triggers once after confirmed tracked-target death");
                kill.setEnabled(false);check.accept(((List<?>)field(kill,"fx")).isEmpty()&&field(kill,"last")==null,"disabling kill effects clears geometry and tracked targets");kill.setEnabled(true);kill.onTick(mc);check.accept(((List<?>)field(kill,"fx")).isEmpty(),"re-enabling kill effects never replays an old death");
                VisualRegression.beginAspect();
            }
            case 4->{
                VisualRegression.verifyAspect(mc,check);
                var landing=module("landing_dust");landing.onTick(mc);set(landing,"wasOnGround",false);set(landing,"fallFrom",p.getY()+5);set(landing,"airTicks",8);p.setOnGround(true);landing.onTick(mc);check.accept(!((List<?>)field(landing,"landings")).isEmpty(),"high landing creates a real height-dependent wave");
                var surface=module("surface_effects");surface.onTick(mc);set(surface,"wasWater",true);surface.onTick(mc);check.accept((Integer)field(surface,"dripTicks")>0,"leaving water arms a bounded droplet effect");
                var skin=module("skin_transition");var items=ModelRegistry.all().stream().filter(e->e.kind==ModelEntry.Kind.WEAPON&&e.collection.equals("aura-items")&&!e.broken()).limit(2).toList();if(items.size()<2)throw new IllegalStateException("Missing real weapon fixtures");
                module("weapon_skins").setEnabled(true);var held=new ItemStack(Items.DIAMOND_SWORD);p.getInventory().setItem(0,held);p.getInventory().setSelectedSlot(0);ModelPrefs.weapons.put("diamond_sword",items.get(0).id);skin.onTick(mc);ModelPrefs.weapons.put("diamond_sword",items.get(1).id);skin.onTick(mc);
                check.accept(!((Map<?,?>)field(skin,"changes")).isEmpty()&&WeaponSkins.skinFor(p.getMainHandItem())==items.get(1)&&p.getMainHandItem()==held,"real equipped sword uses selected model and a transition without changing its ItemStack");
            }
            case 5->{
                camera=mc.options.getCameraType();fov=mc.options.fov().get();gamma=mc.options.gamma().get();module("photo_mode").setEnabled(true);PhotoMode.orbit(120,-30);((SliderSetting)module("photo_mode").settings().stream().filter(s->s.id.equals("fov")).findFirst().orElseThrow()).set(fov==60?75:60);PhotoMode.light(.1);
                check.accept(PhotoMode.active()&&mc.screen instanceof dev.mraz1visual.ui.PhotoScreen&&!mc.mouseHandler.isMouseGrabbed(),"photo mode opens a real local camera screen with the cursor released");
            }
            case 6->{
                check.accept(mc.options.fov().get()!=fov&&PhotoMode.frozen(),"photo settings control actual FOV and pause cosmetic animation");
                Object parent=field(module("photo_mode"),"parent");Mraz1Visual.CONFIG.save();Mraz1Visual.CONFIG.load();Mraz1Visual.MODULES.afterConfigLoad();
                check.accept(PhotoMode.active()&&field(module("photo_mode"),"parent")==parent&&((Integer)field(module("photo_mode"),"previousFov"))==fov,"reloading config in photo mode preserves the original return screen and FOV");
                net.minecraft.client.Screenshot.grab(mc.gameDirectory,"eclipse-photo-ui.png",mc.getMainRenderTarget(),1,ignored->{});PhotoMode.takeShot();
            }
            case 7->{
                if(MediaIntegrationSmoke.result==null||!MediaIntegrationSmoke.result.isDone()){phase--;return false;}String media=MediaIntegrationSmoke.result.join();check.accept(media.startsWith("PASS "),media);
                try(var files=java.nio.file.Files.list(mc.gameDirectory.toPath().resolve("screenshots"))){check.accept(files.anyMatch(x->x.getFileName().toString().startsWith("Mraz1Visual-photo-")),"photo screenshot is saved through Minecraft with controls hidden");}
                mc.screen.onClose();check.accept(!PhotoMode.active()&&!(mc.screen instanceof dev.mraz1visual.ui.PhotoScreen)&&mc.options.getCameraType()==camera&&mc.options.fov().get()==fov&&mc.options.gamma().get().equals(gamma),"Escape restores camera, FOV, brightness and photo state even after config reload");
                check.accept(dev.mraz1visual.render.WorldDraw.errorCount()==0&&dev.mraz1visual.render.HudDraw.errorCount()==0,"new Eclipse effects render without world/HUD exceptions");return true;
            }
        }return false;
    }
}

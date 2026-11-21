package dev.mraz1visual.module;

import com.mojang.blaze3d.platform.NativeImage;
import dev.mraz1visual.core.AttackListener;
import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.core.WorldLayer;
import dev.mraz1visual.render.WorldDraw;
import dev.mraz1visual.setting.*;
import dev.mraz1visual.ui.Colors;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Random;

/** AuraVisual sprite assets/triggers with a bounded native simulation; no Aura UI is used. */
public final class AuraParticles extends Module implements WorldLayer,AttackListener {
    private static final int LIMIT=240;
    private static final Identifier CUSTOM=Identifier.fromNamespaceAndPath("mraz1visual","cosmetics/custom_particle");
    private static final Identifier GLOW=Identifier.fromNamespaceAndPath("auravisuals","textures/glow.png");
    private static final String[] FILES={"particles/star","particles/heart","particles/snowflake","particles/dollar","particles/crown","particles/ded","particles/crest","particles/light","particles/cube","bloom"};
    private static final Identifier[] TEXTURES=java.util.Arrays.stream(FILES).map(s->Identifier.fromNamespaceAndPath("auravisuals","textures/"+s+".png")).toArray(Identifier[]::new);
    public static final Path CUSTOM_DIR=FabricLoader.getInstance().getConfigDir().resolve("mraz1visual/particles");
    private static AuraParticles instance;
    private final EnumSetting shape=add(new EnumSetting("shape","Частицы",0,"star","Звёзды","heart","Сердечки","snow","Снежинки","dollar","Доллар","crown","Корона","skull","Череп","cross","Крест","lightning","Молния","cube","Куб","bloom","Блум","random","Случайные","custom","Свой PNG"));
    private final EnumSetting flight=add(new EnumSetting("flight","Полёт",0,"free","Свободный","up","Взлёт","down","Падение"));
    private final BoolSetting rainbow=add(new BoolSetting("rainbow","Разноцветные",true));
    private final ColorSetting color=add(new ColorSetting("color","Свой цвет",0xFFFFFFFF));
    private final SliderSetting speed=add(new SliderSetting("speed","Скорость",1,.1,5,.1,"×"));
    private final SliderSetting lifetime=add(new SliderSetting("lifetime","Время жизни",20,1,100,1," т."));
    private final SliderSetting size=add(new SliderSetting("size","Размер",1,.1,3,.1,"×"));
    private final SliderSetting amount=add(new SliderSetting("amount","Частиц при событии",5,1,50,1,""));
    private final SliderSetting worldAmount=add(new SliderSetting("world_amount","Частиц в мире",1,0,10,1,""));
    private final SliderSetting totemAmount=add(new SliderSetting("totem_amount","Частиц при тотеме",15,1,50,1,""));
    private final SliderSetting glow=add(new SliderSetting("glow","Свечение",.35,0,1,.05,"×"));
    private final BoolSetting hit=add(new BoolSetting("hit","При ударе",true).group("События"));
    private final BoolSetting jump=add(new BoolSetting("jump","При прыжке",true).group("События"));
    private final BoolSetting sprint=add(new BoolSetting("sprint","При беге",true).group("События"));
    private final BoolSetting pearls=add(new BoolSetting("pearls","След жемчуга",true).group("События"));
    private final BoolSetting world=add(new BoolSetting("world","Вокруг игрока",true).group("События"));
    private final BoolSetting totem=add(new BoolSetting("totem","При тотеме",true).group("События"));
    private final TextSetting customFile=add(new TextSetting("custom_file","Имя файла PNG","particle.png",128).group("Свой PNG"));
    private final ActionSetting editFile=add(new ActionSetting("edit_file","Имя файла PNG","Изменить ›",()->{
        var mc=Minecraft.getInstance();mc.setScreen(new dev.mraz1visual.ui.TextEditScreen(mc.screen,customFile));
    }).group("Свой PNG"));
    private final ActionSetting openFolder=add(new ActionSetting("open_folder","Папка PNG","Открыть ›",()->{
        try{Files.createDirectories(CUSTOM_DIR);dev.mraz1visual.compat.Platform.openPath(CUSTOM_DIR);}catch(Exception e){report("Не удалось открыть папку PNG");}
    }).group("Свой PNG"));
    private static final class Particle{double x,y,z,px,py,pz,vx,vy,vz,spin;int age,life,color;Identifier texture;}
    private final ArrayList<Particle> particles=new ArrayList<>();
    private final Random random=new Random();
    private Object level;private int ticks,lastTotemTick=-100,lastTotemEntity=Integer.MIN_VALUE;private boolean grounded,warmed,customReady;
    private long customStamp=Long.MIN_VALUE;private String loadedName="",customError="";
    public AuraParticles(){super("aura_particles","Aura Particles","Исходные текстуры частиц, шесть игровых событий и свой PNG: config/mraz1visual/particles (AuraVisual).",Category.VISUALS);instance=this;markNew();}
    public boolean customLoaded(){return customReady;}
    @Override protected void onDisable(){particles.clear();level=null;grounded=false;ticks=0;lastTotemTick=-100;}
    private void report(String message){Notifications.message("Частицы: свой PNG",message);}
    private void loadCustom(Minecraft mc){
        String name=customFile.get().trim();
        try{
            Path dir=CUSTOM_DIR.toAbsolutePath().normalize(),file=dir.resolve(name).normalize();Files.createDirectories(dir);
            if(!dir.equals(file.getParent())||!name.toLowerCase(java.util.Locale.ROOT).endsWith(".png"))throw new java.io.IOException("Укажи имя PNG из папки частиц");
            long stamp=Files.exists(file)?Files.getLastModifiedTime(file).toMillis():-1;
            if(name.equals(loadedName)&&stamp==customStamp)return;
            loadedName=name;customStamp=stamp;customReady=false;
            if(stamp<0){report("Файл не найден: "+name);return;}
            NativeImage image;
            try(var input=Files.newInputStream(file)){image=NativeImage.read(input);}
            if(image.getWidth()>2048||image.getHeight()>2048){image.close();throw new java.io.IOException("PNG должен быть не больше 2048×2048");}
            mc.getTextureManager().register(CUSTOM,new DynamicTexture(()->"Mraz1Visual custom particle",image));customReady=true;
        }catch(Exception e){customReady=false;String key=name+"#"+customStamp;if(!key.equals(customError)){customError=key;report("Не удалось загрузить "+name);}loadedName=name;}
    }
    private void emit(double x,double y,double z,int number){
        if(shape.getId().equals("custom")&&!customReady)return;
        for(int i=0;i<number&&particles.size()<LIMIT;i++){
            Particle p=new Particle();p.x=p.px=x+(random.nextDouble()-.5)*.35;p.y=p.py=y+(random.nextDouble()-.5)*.35;p.z=p.pz=z+(random.nextDouble()-.5)*.35;
            double velocity=.04*speed.get();p.vx=random.nextGaussian()*velocity;p.vy=(random.nextDouble()-.25)*velocity;p.vz=random.nextGaussian()*velocity;
            p.life=lifetime.getI();p.spin=random.nextDouble()*Math.PI*2;
            p.color=rainbow.get()?0xFF000000|java.awt.Color.HSBtoRGB(random.nextFloat(),.6f,1):color.get();
            int index=shape.get();p.texture=index==11?CUSTOM:TEXTURES[index==10?random.nextInt(TEXTURES.length):index];particles.add(p);
        }
    }
    @Override public void onAttack(Entity entity){if(hit.get())emit(entity.getX(),entity.getY()+entity.getBbHeight()*.6,entity.getZ(),amount.getI());}
    public static void totem(Entity entity){
        AuraParticles m=instance;if(m==null||!m.isEnabled()||!m.totem.get()||entity==null||entity.level()!=Minecraft.getInstance().level)return;
        if(m.lastTotemEntity==entity.getId()&&m.ticks-m.lastTotemTick<4)return;m.lastTotemEntity=entity.getId();m.lastTotemTick=m.ticks;
        m.emit(entity.getX(),entity.getY()+entity.getBbHeight()*.6,entity.getZ(),m.totemAmount.getI());
    }
    @Override public void onTick(Minecraft mc){
        if(mc.player==null||mc.level==null){onDisable();return;}if(level!=mc.level){particles.clear();level=mc.level;grounded=mc.player.onGround();}
        if(!warmed){for(Identifier texture:TEXTURES)mc.getTextureManager().getTexture(texture);mc.getTextureManager().getTexture(GLOW);warmed=true;}
        if(shape.getId().equals("custom")&&ticks%20==0)loadCustom(mc);ticks++;
        particles.removeIf(p->++p.age>=p.life);
        for(Particle p:particles){p.px=p.x;p.py=p.y;p.pz=p.z;
            p.vy+=flight.getId().equals("down")?-.003*speed.get():flight.getId().equals("up")?.002*speed.get():0;
            p.vx*=.99;p.vy*=.99;p.vz*=.99;p.x+=p.vx;p.y+=p.vy;p.z+=p.vz;p.spin+=.04;
        }
        if(jump.get()&&grounded&&!mc.player.onGround()&&mc.player.getDeltaMovement().y>.05)emit(mc.player.getX(),mc.player.getY()+.1,mc.player.getZ(),amount.getI());grounded=mc.player.onGround();
        if(sprint.get()&&mc.player.isSprinting()&&ticks%2==0)emit(mc.player.getX(),mc.player.getY()+.15,mc.player.getZ(),Math.min(20,amount.getI()));
        if(world.get()&&ticks%4==0)emit(mc.player.getX()+(random.nextDouble()-.5)*12,mc.player.getY()+random.nextDouble()*4,mc.player.getZ()+(random.nextDouble()-.5)*12,worldAmount.getI());
        if(pearls.get()&&ticks%2==0)for(Entity entity:mc.level.entitiesForRendering())if(entity.distanceToSqr(mc.player)<4096&&BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath().equals("ender_pearl"))emit(entity.getX(),entity.getY(),entity.getZ(),1);
    }
    @Override public void renderWorld(WorldDraw d,float pt){
        for(Particle p:particles){double x=p.px+(p.x-p.px)*pt,y=p.py+(p.y-p.py)*pt,z=p.pz+(p.z-p.pz)*pt;
            float age=p.age+pt,fade=Math.min(1,age/3)*Math.max(0,1-age/p.life);int c=Colors.mulAlpha(p.color,fade);double radius=.13*size.get();
            if(glow.get()>0)d.sprite(GLOW,x,y,z,radius*3,Colors.mulAlpha(c,glow.getF()*.3f),false);
            d.sprite(p.texture,x,y,z,radius,c,false,p.spin);
        }
    }
}

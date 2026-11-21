package dev.mraz1visual.ui;

import dev.mraz1visual.Mraz1Visual;
import dev.mraz1visual.models.*;
import dev.mraz1visual.module.ModelPets;
import dev.mraz1visual.ui.widget.Widget;
import java.util.*;

/** Our wardrobe, backed by the built-in wearable collections. */
public final class WardrobePanel implements Panel {
    private static final ModelEntry.Kind[] KINDS={ModelEntry.Kind.WING,ModelEntry.Kind.HAT,ModelEntry.Kind.BACK,ModelEntry.Kind.PET};
    private static final String[] LABELS={"Крылья","Головные уборы","На спину","Питомцы"};
    private record Hit(int x,int y,int w,int h,Runnable action){}
    private final Runnable showModules;
    private final List<Hit> hits=new ArrayList<>();
    private final Map<String,AnimPlayer> animations=new HashMap<>();
    private int category=-1,maxScroll,gx,gy,gw,gh;
    private float scroll,target,spin;
    private String query="";
    public WardrobePanel(Runnable showModules){this.showModules=showModules;}
    @Override public void opened(){ModelRegistry.ensureLoaded();}
    public void search(String value){String next=value.toLowerCase(Locale.ROOT);if(!query.equals(next)){query=next;scroll=target=0;}}
    public static List<ModelEntry> catalog(){return ModelRegistry.all().stream().filter(e->(e.collection.equals("aura") || e.collection.equals("pulse"))&&Arrays.asList(KINDS).contains(e.kind)).toList();}
    public static String selected(ModelEntry.Kind kind){return switch(kind){case HAT->ModelPrefs.hat;case WING->ModelPrefs.wing;case BACK->ModelPrefs.back;case PET->ModelPrefs.pet;default->"";};}
    public static void toggle(ModelEntry e){
        if(e==null||e.broken()||!(e.collection.equals("aura") || e.collection.equals("pulse")))return;
        if(e.kind==ModelEntry.Kind.WING){var shards=Mraz1Visual.MODULES.find("shard_wings");if(shards!=null)shards.setEnabled(false);}
        String id=e.id.equals(selected(e.kind))?"":e.id;
        switch(e.kind){
            case HAT->ModelPrefs.hat=id;
            case WING->ModelPrefs.wing=id;
            case BACK->ModelPrefs.back=id;
            case PET->{var pets=ModelPets.get();if(pets!=null)pets.select(id.isEmpty()?null:e);else ModelPrefs.pet=id;}
            default->{return;}
        }
        if(!id.isEmpty()){
            var module=Mraz1Visual.MODULES.find(e.kind==ModelEntry.Kind.PET?"model_pets":"model_cosmetics");
            if(module!=null){
                if(e.kind!=ModelEntry.Kind.PET)for(var setting:module.settings())if(setting.id.equals(switch(e.kind){case HAT->"hat";case BACK->"back";default->"wing";}))setting.fromJson(new com.google.gson.JsonPrimitive(true));
                module.setEnabled(true);
            }
        }
        ModelPrefs.save();Mraz1Visual.CONFIG.markDirty();
    }
    public static void clear(){ModelPrefs.hat=ModelPrefs.wing=ModelPrefs.back=ModelPrefs.pet="";if(ModelPets.get()!=null)ModelPets.get().select(null);for(String id:new String[]{"eclipse_crown","shard_wings"}){var m=Mraz1Visual.MODULES.find(id);if(m!=null)m.setEnabled(false);}ModelPrefs.save();}
    @Override public void render(Draw d,double mx,double my,float dt,int x,int y,int w,int h){
        hits.clear();spin+=dt*35;var all=catalog();int side=Math.min(150,Math.max(112,w/4));
        d.glass(x,y,side,h,14,Theme.GLASS_PANEL,.9f);d.text("Косметика",Fonts.Kind.BOLD,x+10,y+12,Theme.TEXT);
        int cy=y+36;category(d,mx,my,x,cy,side,-1,"Всё",all.size());cy+=29;
        for(int i=0;i<KINDS.length;i++){final var kind=KINDS[i];category(d,mx,my,x,cy,side,i,LABELS[i],(int)all.stream().filter(e->e.kind==kind).count());cy+=29;}
        if(h>310){for(String[] entry:new String[][]{{"eclipse_crown","Корона Eclipse"},{"shard_wings","Крылья-осколки"}}){var module=Mraz1Visual.MODULES.find(entry[0]);if(module!=null){button(d,mx,my,x+8,cy,side-16,22,(module.isEnabled()?"● ":"")+entry[1],module::toggle);cy+=27;}}}
        button(d,mx,my,x+8,y+h-56,side-16,22,"Снять всё",WardrobePanel::clear);
        button(d,mx,my,x+8,y+h-29,side-16,22,"Настройки ›",showModules);
        gx=x+side+10;gw=w-side-10;gy=y+30;gh=h-30;
        d.text("Каталог · " + all.size(),Fonts.Kind.BOLD,gx+2,y+2,Theme.TEXT);
        d.text("Клик — надеть / снять",gx+2,y+16,Theme.TEXT_MUTED);
        var list=all.stream().filter(e->(category<0||e.kind==KINDS[category])&&(query.isEmpty()||(e.name+" "+e.author+" "+e.id).toLowerCase(Locale.ROOT).contains(query))).toList();
        int cols=Math.max(1,(gw+8)/106),cw=(gw-(cols-1)*8)/cols,ch=116;
        maxScroll=Math.max(0,((list.size()+cols-1)/cols)*(ch+8)-8-gh);target=Math.max(0,Math.min(maxScroll,target));scroll=Anim.to(scroll,target,dt);
        d.clip(gx,gy,gw,gh);
        if(list.isEmpty() && !ModelRegistry.loading()) {
            String message = all.isEmpty() ? "Косметические эффекты доступны в настройках." : "Ничего не найдено";
            d.text(Draw.ellipsize(message, gw - 12), gx + 6, gy + 12, Theme.TEXT_DIM);
        }
        for(int i=0;i<list.size();i++){
            var e=list.get(i);int cx=gx+i%cols*(cw+8),yy=gy+i/cols*(ch+8)-Math.round(scroll);
            if(yy+ch<gy||yy>=gy+gh)continue;
            boolean hov=Widget.hit(mx,my,cx,Math.max(yy,gy),cw,Math.min(yy+ch,gy+gh)-Math.max(yy,gy)),on=e.id.equals(selected(e.kind));
            d.glass(cx,yy,cw,ch,12,on?Theme.GLASS_ACTIVE:hov?Theme.GLASS_PANEL:Theme.GLASS_CARD,1);
            if(on)d.rim(cx,yy,cw,ch,12,Theme.accent(),Colors.mulAlpha(Theme.accent(),.4f));
            if(!e.broken()){
                var player=animations.computeIfAbsent(e.id,key->{var a=new AnimPlayer();a.play(e.anim("idle")!=null?e.anim("idle"):e.anims.isEmpty()?null:e.anims.get(0),0,true);return a;});player.update(dt);
                ModelPreview.draw(d.g,e.mesh(),player.pose(e.mesh()),(e.kind==ModelEntry.Kind.WING||e.kind==ModelEntry.Kind.BACK?180:200)+(hov?spin:0),-15,0,.8f,true,Math.round((cx+4)*d.scale),Math.round((yy+4)*d.scale),Math.round((cw-8)*d.scale),Math.round(76*d.scale));
            }else d.textCenter("Ошибка модели",cx+cw/2,yy+36,0xFFFF6B6B);
            d.text(Draw.ellipsize(e.name,cw-12),cx+6,yy+84,Theme.TEXT);
            d.text(Draw.ellipsize(on?"Надето · снять":e.author.isEmpty()?e.sourceTitle:e.author,cw-12),cx+6,yy+99,on?Theme.accent():Theme.TEXT_MUTED);
            hits.add(new Hit(cx,Math.max(yy,gy),cw,Math.max(0,Math.min(yy+ch,gy+gh)-Math.max(yy,gy)),()->toggle(e)));
        }
        d.unclip();
        if(list.isEmpty())d.text(ModelRegistry.loading()?"Загрузка моделей…":"Ничего не найдено",gx+12,gy+20,Theme.TEXT_MUTED);
        if(maxScroll>0){int th=Math.max(18,gh*gh/(gh+maxScroll));d.round(gx+gw-3,gy+Math.round((gh-th)*scroll/maxScroll),3,th,1,Theme.ELEVATED);}
    }
    private void category(Draw d,double mx,double my,int x,int y,int w,int index,String label,int count){
        button(d,mx,my,x+6,y,w-12,24,Draw.ellipsize(label,w-40)+"  "+count,()->{category=index;scroll=target=0;});
        if(category==index)d.rim(x+6,y,w-12,24,10,Theme.accent(),Colors.mulAlpha(Theme.accent(),.3f));
    }
    private void button(Draw d,double mx,double my,int x,int y,int w,int h,String text,Runnable action){d.round(x,y,w,h,10,Widget.hit(mx,my,x,y,w,h)?Theme.GLASS_ACTIVE:Theme.GLASS_CARD);d.textCenter(text,x+w/2,Draw.textY(y,h),Theme.TEXT);hits.add(new Hit(x,y,w,h,action));}
    @Override public boolean click(double mx,double my,int button){if(button==0)for(var hit:hits)if(Widget.hit(mx,my,hit.x,hit.y,hit.w,hit.h)){hit.action.run();break;}return true;}
    @Override public boolean scroll(double mx,double my,double amount){if(Widget.hit(mx,my,gx,gy,gw,gh))target=Math.max(0,Math.min(maxScroll,target-(float)amount*40));return true;}
    public void left(){hits.clear();}
}

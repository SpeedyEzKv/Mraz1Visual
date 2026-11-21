package dev.mraz1visual.ui;
import dev.mraz1visual.module.PhotoMode;
import dev.mraz1visual.ui.widget.Widget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.*;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;

public final class PhotoScreen extends Screen {
    private final Draw draw=new Draw();
    private record Hit(int x,int y,int w,int h,Runnable action){}
    private final ArrayList<Hit> hits=new ArrayList<>();private boolean dragging;
    public PhotoScreen(){super(Component.literal("Фоторежим"));}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void renderBackground(GuiGraphics g, int mouseX,int mouseY,float pt){}
    @Override public void render(GuiGraphics g, int mouseX,int mouseY,float pt){
        int mx=mouseX,my=mouseY;
        hits.clear();if(PhotoMode.hidingControls())return;
        int w=Math.min(440,width-16),x=(width-w)/2,y=height-112;draw.begin(g,font,1);
        draw.glass(x,y,w,104,14,0xE610111B,1);draw.text("ФОТОРЕЖИМ",Fonts.Kind.HEAD,x+12,y+10,Theme.TEXT);
        draw.text(w>=400?"Перетаскивание — орбита · колесо — расстояние · Esc — выйти":"Мышь — орбита · колесо — зум · Esc — выйти",x+12,y+25,Theme.TEXT_DIM);
        int bw=(w-28)/4;
        button(mx,my,x+8,y+43,bw,"FOV −",()->PhotoMode.fov(-5));button(mx,my,x+12+bw,y+43,bw,"FOV +",()->PhotoMode.fov(5));
        button(mx,my,x+16+bw*2,y+43,bw,"Свет −",()->PhotoMode.light(-.1));button(mx,my,x+20+bw*3,y+43,bw,"Свет +",()->PhotoMode.light(.1));
        button(mx,my,x+8,y+72,bw,PhotoMode.frozen()?"Поза: стоп":"Поза: живая",PhotoMode::toggleFreeze);
        button(mx,my,x+12+bw,y+72,bw,PhotoMode.effectsVisible()?"Эффекты: да":"Эффекты: нет",PhotoMode::toggleEffects);button(mx,my,x+16+bw*2,y+72,bw,"Снимок",PhotoMode::takeShot);button(mx,my,x+20+bw*3,y+72,bw,"Готово",PhotoMode::exit);
        draw.end();
    }
    private void button(int mx,int my,int x,int y,int w,String label,Runnable action){draw.round(x,y,w,22,8,Widget.hit(mx,my,x,y,w,22)?Theme.GLASS_ACTIVE:Theme.GLASS_CARD);draw.textCenter(label,x+w/2,Draw.textY(y,22),Theme.TEXT);hits.add(new Hit(x,y,w,22,action));}
    @Override public boolean mouseClicked(MouseButtonEvent e,boolean twice){for(var hit:hits)if(Widget.hit(e.x(),e.y(),hit.x,hit.y,hit.w,hit.h)){if(e.button()==0)hit.action.run();return true;}dragging=e.button()==0;return true;}
    @Override public boolean mouseReleased(MouseButtonEvent e){dragging=false;return true;}
    @Override public boolean mouseDragged(MouseButtonEvent e,double dx,double dy){if(dragging)PhotoMode.orbit(dx,dy);return true;}
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){PhotoMode.zoom(dy);return true;}
    @Override public void onClose(){PhotoMode.exit();}
}

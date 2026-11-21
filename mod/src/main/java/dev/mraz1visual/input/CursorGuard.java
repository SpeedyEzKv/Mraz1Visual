package dev.mraz1visual.input;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;

/** Reconcile native mouse mode after the OS recreates/resizes a fullscreen window. */
public final class CursorGuard {
    private static Object screen;
    private static int width,height,settle;
    private static double guiScale;
    private static boolean fullscreen,focused;
    private CursorGuard(){}
    public static void tick(Minecraft mc){
        var window=mc.getWindow();
        boolean full=mc.options.fullscreen().get(),focus=mc.isWindowActive();
        double scale=window.getGuiScale();
        if(screen!=mc.screen||width!=window.getScreenWidth()||height!=window.getScreenHeight()||guiScale!=scale||fullscreen!=full||focused!=focus)settle=5;
        screen=mc.screen;width=window.getScreenWidth();height=window.getScreenHeight();guiScale=scale;fullscreen=full;focused=focus;
        if(mc.screen==null||!focus){settle=0;return;}
        if(settle<=0&&!mc.mouseHandler.isMouseGrabbed())return;
        if(settle>0)settle--;
        mc.mouseHandler.releaseMouse();
        double x=Math.max(0,Math.min(Math.max(0,width-1),mc.mouseHandler.xpos()));
        double y=Math.max(0,Math.min(Math.max(0,height-1),mc.mouseHandler.ypos()));
        // releaseMouse() returns early if its Java flag already says released;
        // the native cursor can still be hidden after a fullscreen transition.
        //? if >=26.3 {
        /*InputConstants.releaseMouse(window,x,y);
        mc.mouseHandler.resyncMousePosition();
        *///?} else {
        InputConstants.grabOrReleaseMouse(window,InputConstants.CURSOR_NORMAL,x,y);
        mc.mouseHandler.setIgnoreFirstMove();
        //?}
    }
}

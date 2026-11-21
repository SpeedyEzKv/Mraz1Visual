package dev.mraz1visual.integration;

import dev.mraz1visual.core.Modules;
import dev.mraz1visual.ui.Theme;
import dev.mraz1visual.ui.menu.EclipseBackground;
import dev.mraz1visual.wallpaper.WallpaperPlayer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphics;

/** Uses the client's existing design tokens and background for upstream screens. */
public final class IntegrationTheme {
    private static final EclipseBackground BACKGROUND = new EclipseBackground();
    private IntegrationTheme() {}
    public static boolean light() { return Modules.client != null && Modules.client.theme.getId().equals("light"); }
    public static boolean animated() { return Modules.client != null && Modules.client.animations.get() && !Modules.client.reduceMotion.get(); }
    public static boolean begin() {
        boolean previous = Theme.isLight();
        if (light() != previous) { if (light()) Theme.light(); else Theme.dark(); }
        return previous;
    }
    public static void end(boolean previous) {
        if (Theme.isLight() != previous) { if (previous) Theme.light(); else Theme.dark(); }
    }
    public static void background(GuiGraphics g, int width, int height, int mx, int my) {
        boolean previous = begin();
        try {
            if (!Modules.client.wallMenus.get() || !WallpaperPlayer.render(g, 0, 0, width, height))
                BACKGROUND.render(g, width, height, .66f, .364f, 1, mx, my, animated());
            g.fill(0, 0, width, height, light() ? 0xE6FAFAFC : 0x99030308);
        } finally { end(previous); }
    }
    /** Keep styled runs, explicit icon fonts and bidi shaping when a native control is too narrow. */
    public static void label(GuiGraphics g, net.minecraft.client.gui.Font font, net.minecraft.network.chat.Component label,
                             int center, int top, int available, int color) {
        if (available < 1) return;
        var line = label.getVisualOrderText();
        if (font.width(line) > available) {
            var ellipsis = net.minecraft.network.chat.Component.literal("…");
            int remaining = available - font.width(ellipsis);
            if (remaining < 0) return;
            var clipped = net.minecraft.network.chat.FormattedText.composite(font.substrByWidth(label, remaining), ellipsis);
            line = net.minecraft.locale.Language.getInstance().getVisualOrder(clipped);
        }
        g.drawString(font, line, center - font.width(line) / 2, top, color, false);
    }

    public static int nativeTextColor(int original) {
        if (!light()) return original;
        int r=original>>16&255,g=original>>8&255,b=original&255;
        if ((original & 0xFFFFFF)==0xCACACA) return original; // upstream Mod Menu badge text on colored fills
        if(r!=g || g!=b || r<120) return original; // retain semantic/link/tag colors
        return (original & 0xFF000000) | (r>=220 ? 0x111114 : r>=170 ? 0x5A5A63 : 0x75757E);
    }
    /** Only undo the old integration's generated GIF selection; custom wallpapers stay. */
    public static void removeImportedShellWallpaper() {
        var wallpaper = WallpaperPlayer.current();
        var imported = FabricLoader.getInstance().getConfigDir().resolve("mraz1visual/aura-media").toAbsolutePath().normalize();
        var names = java.util.Set.of("boy-kiss.gif","bro.gif","cat.gif","girlokak.gif","ishowspeed1.gif","ishowspeed3.gif","ivanzolo.gif",
                "mao-mao1.gif","meow-kitty.gif","speed-thung.gif","wasper.gif","winnerdog1.gif");
        if (wallpaper != null && wallpaper.media() != null && names.contains(wallpaper.media().getFileName().toString())
                && imported.equals(wallpaper.media().toAbsolutePath().normalize().getParent())) {
            WallpaperPlayer.select(null);
        }
    }
}

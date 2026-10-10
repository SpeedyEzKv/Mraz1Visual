package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.HudLayer;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.render.HudKit;
import dev.mraz1visual.setting.BoolSetting;
import dev.mraz1visual.ui.Colors;
import dev.mraz1visual.ui.Fonts;
import dev.mraz1visual.ui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** Плашка слева сверху: MRAZ1VISUAL · FPS · пинг · время. */
public final class Watermark extends Module implements HudLayer {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    final BoolSetting fps = add(new BoolSetting("fps", "FPS", true));
    final BoolSetting ping = add(new BoolSetting("ping", "Пинг", true));
    final BoolSetting time = add(new BoolSetting("time", "Время", true));
    final BoolSetting nick = add(new BoolSetting("nick", "Ник", false));

    public Watermark() {
        super("watermark", "Watermark", "Плашка клиента с FPS, пингом и временем.", Category.VISUALS);
    }

    @Override
    public void renderHud(GuiGraphics g, float pt) {
        Minecraft mc = Minecraft.getInstance();
        StringBuilder sb = new StringBuilder();
        if (fps.get()) sb.append("  ").append(mc.getFps()).append(" fps");
        if (ping.get() && mc.getConnection() != null && mc.player != null) {
            PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
            if (info != null) sb.append("  ").append(info.getLatency()).append(" ms");
        }
        if (time.get()) sb.append("  ").append(LocalTime.now().format(TIME));
        if (nick.get()) sb.append("  ").append(StreamerMode.filter(mc.getUser().getName()));
        String info = sb.toString();
        int bw = Fonts.width("MRAZ1VISUAL", Fonts.Kind.HEAD);
        int w = 10 + bw + HudKit.width(info) + 8, h = 16;
        int x = 4, y = 4;
        HudKit.card(g, x, y, w, h, 1f);
        g.fill(x + 1, y + 3, x + 3, y + h - 3, Theme.accent());
        g.drawString(mc.font, Fonts.of("MRAZ1VISUAL", Fonts.Kind.HEAD), x + 7, y + 4, 0xFFE9E9F1, false);
        HudKit.text(g, info, x + 7 + bw, y + 4, Colors.withAlpha(0x9B9BAB, 0xFF));
    }
}

package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.HudLayer;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.render.HudPos;
import dev.mraz1visual.render.HudStyle;
import dev.mraz1visual.setting.BoolSetting;
import dev.mraz1visual.ui.Colors;
import dev.mraz1visual.ui.Fonts;
import dev.mraz1visual.ui.Sdf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;

import java.time.LocalTime;

/**
 * Водяной знак: логотип + «Mraz1Visual» | FPS | пинг | время | сервер. Ширина — под текст.
 * Части пересобираются 4 раза в секунду и только если поменялись числа — в кадре нет новых строк.
 */
public final class Watermark extends Module implements HudLayer {
    private static final int MAX = 5, PAD = 7, SEP = 13, H = 18;
    final HudPos pos = hudPos(0.0, 0.0);
    final BoolSetting fps = add(new BoolSetting("fps", "FPS", true));
    final BoolSetting ping = add(new BoolSetting("ping", "Пинг", true));
    final BoolSetting time = add(new BoolSetting("time", "Время", true));
    final BoolSetting server = add(new BoolSetting("server", "Сервер", false));
    final BoolSetting nick = add(new BoolSetting("nick", "Ник", false));

    private final String[] parts = new String[MAX];
    private final int[] partW = new int[MAX];
    private int count, width, lastFps = -1, lastPing = -2, lastMin = -1;
    private String lastServer = "", lastNick = "", pingStr = "", fpsStr = "", timeStr = "";
    private long at;
    private static final String BRAND = "Mraz1Visual";

    public Watermark() {
        super("watermark", "Watermark", "Плашка: логотип, FPS, пинг, время и сервер. Ширина — под текст.", Category.HUD);
        markNew();
    }

    @Override
    public void renderHud(GuiGraphics g, float pt) {
        Minecraft mc = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        if (now - at > 250 || count == 0) {
            at = now;
            rebuild(mc);
        }
        pos.begin(g, width, H);
        HudStyle.panel(g, 0, 0, width, H, 7, 1f);
        // логотип: кружок с градиентом и буквой
        Sdf.fill(g, 4, 3, 12, 12, 6, HudStyle.GRAD_A);
        Sdf.fill(g, 7, 3, 9, 12, 6, Colors.withAlpha(HudStyle.GRAD_B, 0xB0));
        Fonts.draw(g, "M", Fonts.Kind.BOLD, 7.5f, 5, 0xFFFFFFFF);
        int x = 20;
        Fonts.draw(g, BRAND, Fonts.Kind.BOLD, x, 5, HudStyle.TEXT);
        x += Fonts.width(BRAND, Fonts.Kind.BOLD);
        for (int i = 0; i < count; i++) {
            g.fill(x + SEP / 2, 5, x + SEP / 2 + 1, H - 5, 0x40FFFFFF);
            x += SEP;
            Fonts.draw(g, parts[i], Fonts.Kind.TEXT, x, 5, HudStyle.TEXT_DIM);
            x += partW[i];
        }
        pos.end(g);
    }

    private void rebuild(Minecraft mc) {
        int n = 0;
        if (fps.get()) {
            int f = mc.getFps();
            if (f != lastFps) { lastFps = f; fpsStr = f + " fps"; }
            parts[n++] = fpsStr;
        }
        if (ping.get() && mc.getConnection() != null && mc.player != null && !mc.hasSingleplayerServer()) {
            PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
            int pv = info == null ? -1 : info.getLatency();
            if (pv != lastPing) { lastPing = pv; pingStr = pv < 0 ? "— ms" : pv + " ms"; }
            parts[n++] = pingStr;
        }
        if (time.get()) {
            LocalTime t = LocalTime.now();
            int m = t.getHour() * 60 + t.getMinute();
            if (m != lastMin) { lastMin = m; timeStr = t.getHour() + (t.getMinute() < 10 ? ":0" : ":") + t.getMinute(); }
            parts[n++] = timeStr;
        }
        if (server.get() && !StreamerMode.hidesCoords()) {
            ServerData sd = mc.getCurrentServer();
            String s = sd == null || mc.hasSingleplayerServer() ? "Одиночная" : sd.ip;
            if (!s.equals(lastServer)) lastServer = s;
            parts[n++] = lastServer;
        }
        if (nick.get() && mc.getUser() != null) {
            String s = StreamerMode.filter(mc.getUser().getName());
            if (!s.equals(lastNick)) lastNick = s;
            parts[n++] = lastNick;
        }
        int w = 20 + Fonts.width(BRAND, Fonts.Kind.BOLD) + PAD;
        for (int i = 0; i < n; i++) {
            partW[i] = Fonts.width(parts[i], Fonts.Kind.TEXT);
            w += SEP + partW[i];
        }
        count = n;
        width = w;
    }
}

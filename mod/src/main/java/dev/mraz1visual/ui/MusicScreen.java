package dev.mraz1visual.ui;

import dev.mraz1visual.module.MusicPlayerModule;
import dev.mraz1visual.music.Lyrics;
import dev.mraz1visual.music.LyricsView;
import dev.mraz1visual.music.NowPlaying;
import dev.mraz1visual.music.player.Library;
import dev.mraz1visual.music.player.MusicPlayer;
import dev.mraz1visual.music.player.Track;
import dev.mraz1visual.ui.widget.Widget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Экран встроенного плеера в стиле Liquid Glass: библиотека (все треки, исполнители, альбомы, плейлисты, очередь),
 * поиск, панель «Сейчас играет» с синхронным текстом песни, нижняя панель управления (прогресс, громкость,
 * перемешивание, повтор, эквалайзер).
 */
public final class MusicScreen extends Screen {
    private static final String[] VIEWS = {"tracks", "Все треки", "artists", "Исполнители", "albums", "Альбомы", "playlists", "Плейлисты", "queue", "Очередь"};
    private static final int SIDE = 120, NOW = 190, BAR = 50, ROW = 18;

    private final Screen parent;
    private final Draw d = new Draw();
    private final LyricsView lyrics = new LyricsView();
    private float k = 1;
    private int wx, wy, ww, wh;
    private String view = "tracks";
    private String group;            // открытый исполнитель/альбом/плейлист
    private final StringBuilder search = new StringBuilder();
    private float scroll, scrollTarget;
    private int scrollMax;
    private long last;
    private float openT;
    private boolean seekDrag, volDrag;
    private final List<Hit> hits = new ArrayList<>();
    private int barX, barW, volX, volW, barY;

    private record Hit(int x, int y, int w, int h, Runnable r) {}

    public MusicScreen(Screen parent) {
        super(Component.literal("Музыка"));
        this.parent = parent;
        MusicPlayerModule.init();
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override public void onClose() { minecraft.setScreen(parent); }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (minecraft.level != null && GlassBlur.update(4)) GlassBlur.draw(g, 0, 0, width, height, 0xFFFFFFFF);
        else super.renderBackground(g, mouseX, mouseY, partialTick);
        g.fill(0, 0, width, height, 0x55000000);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        boolean light = dev.mraz1visual.core.Modules.client.theme.getId().equals("light");
        if (light) Theme.light();
        try {
            draw(g, mouseX, mouseY);
        } finally {
            if (light) Theme.dark();
        }
    }

    private void draw(GuiGraphics g, int gmx, int gmy) {
        long now = System.nanoTime();
        float dt = last == 0 ? 0 : Math.min(0.1f, (now - last) / 1e9f);
        last = now;
        openT = Math.min(1, openT + dt / 0.25f);
        scroll = Anim.to(scroll, scrollTarget, dt);
        Lyrics.tick();
        k = Math.min(1f, Math.min(width / 720f, height / 440f));
        int vw = Math.round(width / k), vh = Math.round(height / k);
        int mx = Math.round(gmx / k), my = Math.round(gmy / k);
        ww = Math.min(vw - 24, 820);
        wh = Math.min(vh - 16, 480);
        wx = (vw - ww) / 2;
        wy = (vh - wh) / 2;
        hits.clear();
        d.begin(g, font, k);
        d.shadow(wx, wy + 8, ww, wh, Theme.RADIUS, 30f, Theme.SHADOW);
        d.glass(wx, wy, ww, wh, Theme.RADIUS, Theme.GLASS_WINDOW, 1.1f);
        drawSide(mx, my);
        drawList(mx, my);
        drawNow(g);
        drawBar(g, mx, my);
        if (openT < 1) d.round(wx, wy, ww, wh, Theme.RADIUS, Colors.withAlpha(Theme.isLight() ? 0xF4F4F5 : 0x0A0A0C, Math.round(255 * (1 - openT))));
        d.end();
    }

    // ── левая колонка ──

    private void drawSide(int mx, int my) {
        int x = wx + 12, y = wy + 14;
        d.text("Музыка", Fonts.Kind.HEAD, x + 4, y, Theme.TEXT);
        y += 20;
        for (int i = 0; i < VIEWS.length; i += 2) {
            String id = VIEWS[i];
            boolean on = id.equals(view) && group == null, hov = Widget.hit(mx, my, x, y, SIDE, 18);
            if (on || id.equals(view)) d.round(x, y, SIDE, 18, 9, on ? Theme.GLASS_ACTIVE : Theme.HOVER);
            else if (hov) d.round(x, y, SIDE, 18, 9, Theme.HOVER);
            d.text(VIEWS[i + 1], x + 10, Draw.textY(y, 18), id.equals(view) ? Theme.TEXT : Theme.TEXT_DIM);
            hits.add(new Hit(x, y, SIDE, 18, () -> { view = id; group = null; scrollTarget = scroll = 0; }));
            y += 20;
        }
        y += 8;
        pill(mx, my, x, y, SIDE, "+ Папка с музыкой", MusicPlayerModule::pickFolderFromUi);
        y += 22;
        pill(mx, my, x, y, SIDE, Library.scanning() ? "Сканирую…" : "Обновить", () -> Library.rescan(new ArrayList<>(MusicPlayer.folders)));
        y += 22;
        pill(mx, my, x, y, SIDE, "Открыть папку", () -> ModelGallery.openFolder(Library.DEFAULT));
        y += 28;
        d.text(Library.tracks().size() + " треков", x + 4, y, Theme.TEXT_MUTED);
        d.text("mp3 · ogg · flac · wav", x + 4, y + 12, Theme.TEXT_MUTED);
    }

    private void pill(int mx, int my, int x, int y, int w, String label, Runnable r) {
        boolean hov = Widget.hit(mx, my, x, y, w, 18);
        d.round(x, y, w, 18, 9, hov ? Theme.GLASS_ACTIVE : Theme.GLASS_CARD);
        d.textCenter(label, x + w / 2, Draw.textY(y, 18), Theme.TEXT);
        hits.add(new Hit(x, y, w, 18, r));
    }

    // ── список ──

    private void drawList(int mx, int my) {
        int x = wx + 12 + SIDE + 12, y = wy + 12, w = ww - (x - wx) - NOW - 24, h = wh - BAR - 24;
        // поиск
        d.round(x, y, w, 20, 10, Theme.FIELD);
        String q = search.toString();
        d.text(q.isEmpty() ? "Поиск по библиотеке…" : Draw.ellipsize(q, w - 20), x + 10, Draw.textY(y, 20), q.isEmpty() ? Theme.TEXT_MUTED : Theme.TEXT);
        y += 26;
        h -= 26;
        if (group != null) {
            boolean hov = Widget.hit(mx, my, x, y, w, 16);
            d.text("‹ " + Draw.ellipsize(group, w - 20), Fonts.Kind.BOLD, x + 4, y + 3, hov ? Theme.TEXT : Theme.TEXT_DIM);
            hits.add(new Hit(x, y, w, 16, () -> { group = null; scrollTarget = scroll = 0; }));
            y += 20;
            h -= 20;
        }
        String ql = q.toLowerCase(Locale.ROOT);
        List<Row> rows = rows(ql);
        scrollMax = Math.max(0, rows.size() * ROW - h);
        scrollTarget = Math.max(0, Math.min(scrollMax, scrollTarget));
        if (rows.isEmpty()) {
            String empty = Library.scanning() ? "Сканирую папки…" : view.equals("playlists") && group == null
                    ? "Плейлистов пока нет — нажмите ♥ у трека, он попадёт в «Избранное»."
                    : "Положите музыку в mraz1visual/music или добавьте свою папку.";
            d.text(Draw.ellipsize(empty, w - 10), x + 4, y + 8, Theme.TEXT_MUTED);
            return;
        }
        d.clip(x, y, w, h);
        Track cur = MusicPlayer.current();
        for (int i = 0; i < rows.size(); i++) {
            int ry = y + i * ROW - Math.round(scroll);
            if (ry + ROW < y || ry > y + h) continue;
            Row r = rows.get(i);
            boolean hov = Widget.hit(mx, my, x, ry, w, ROW) && my >= y && my < y + h;
            boolean playingRow = r.track != null && cur != null && r.track.path.equals(cur.path);
            if (playingRow) d.round(x, ry + 1, w, ROW - 2, 8, Theme.GLASS_ACTIVE);
            else if (hov) d.round(x, ry + 1, w, ROW - 2, 8, Theme.HOVER);
            d.text(Draw.ellipsize(r.title, w * 6 / 10), playingRow ? Fonts.Kind.BOLD : Fonts.Kind.TEXT, x + 8, Draw.textY(ry, ROW), Theme.TEXT);
            d.textRight(Draw.ellipsize(r.sub, w * 3 / 10), x + w - 26, Draw.textY(ry, ROW), Theme.TEXT_MUTED);
            if (r.track != null) {
                boolean fav = inFavorites(r.track);
                boolean sh = Widget.hit(mx, my, x + w - 20, ry, 16, ROW);
                if (fav || hov) d.text(fav ? "♥" : "♡", x + w - 16, Draw.textY(ry, ROW), fav ? Theme.accent() : sh ? Theme.TEXT : Theme.TEXT_MUTED);
                Track t = r.track;
                if (my >= y && my < y + h) hits.add(new Hit(x + w - 20, ry, 16, ROW, () -> toggleFavorite(t)));
            }
            if (my >= y && my < y + h) hits.add(new Hit(x, ry, w - 22, ROW, r.action));
        }
        d.unclip();
    }

    private record Row(String title, String sub, Track track, Runnable action) {}

    private List<Row> rows(String q) {
        List<Row> out = new ArrayList<>();
        List<Track> lib = Library.tracks();
        switch (view) {
            case "artists", "albums" -> {
                Map<String, List<Track>> m = view.equals("artists") ? Library.byArtist() : Library.byAlbum();
                if (group == null) {
                    for (var en : m.entrySet()) {
                        if (!q.isEmpty() && !en.getKey().toLowerCase(Locale.ROOT).contains(q)) continue;
                        String name = en.getKey();
                        out.add(new Row(name, en.getValue().size() + " тр.", null, () -> { group = name; scrollTarget = scroll = 0; }));
                    }
                } else trackRows(out, m.getOrDefault(group, List.of()), q);
            }
            case "playlists" -> {
                if (group == null) {
                    synchronized (MusicPlayer.playlists) {
                        for (var en : MusicPlayer.playlists.entrySet()) {
                            String name = en.getKey();
                            out.add(new Row(name, en.getValue().size() + " тр.", null, () -> { group = name; scrollTarget = scroll = 0; }));
                        }
                    }
                } else {
                    List<Track> list = new ArrayList<>();
                    List<Path> paths = MusicPlayer.playlists.getOrDefault(group, List.of());
                    for (Path p : paths) {
                        Track t = Library.find(p);
                        if (t != null) list.add(t);
                    }
                    trackRows(out, list, q);
                }
            }
            case "queue" -> trackRows(out, MusicPlayer.queue(), q);
            default -> trackRows(out, lib, q);
        }
        return out;
    }

    private void trackRows(List<Row> out, List<Track> list, String q) {
        List<Track> filtered = new ArrayList<>();
        for (Track t : list) if (t.matches(q)) filtered.add(t);
        for (int i = 0; i < filtered.size(); i++) {
            Track t = filtered.get(i);
            int idx = i;
            String sub = t.artist + (t.lengthMs > 0 ? " · " + time(t.lengthMs) : "");
            out.add(new Row(t.title, sub, t, () -> MusicPlayer.playList(filtered, idx)));
        }
    }

    private static boolean inFavorites(Track t) {
        List<Path> fav = MusicPlayer.playlists.get("Избранное");
        return fav != null && fav.contains(t.path);
    }

    private static void toggleFavorite(Track t) {
        List<Path> fav = MusicPlayer.playlists.computeIfAbsent("Избранное", k -> new ArrayList<>());
        if (!fav.remove(t.path)) fav.add(t.path);
        MusicPlayer.save();
    }

    // ── «Сейчас играет» + текст песни ──

    private void drawNow(GuiGraphics g) {
        int x = wx + ww - NOW - 12, y = wy + 12, w = NOW, h = wh - BAR - 24;
        int tint = NowPlaying.coverTint;
        Sdf.shape(g, x, y, w, h, Theme.RADIUS_CARD, Sdf.FILL, 0, Colors.withAlpha(Colors.lerp(tint, 0xFF101016, 0.2f), 0xC8),
                Colors.withAlpha(Colors.lerp(tint, 0xFF08080C, 0.7f), 0xD8));
        d.rim(x, y, w, h, Theme.RADIUS_CARD, Colors.withAlpha(0xFFFFFF, 50), Colors.withAlpha(0xFFFFFF, 12));
        int cs = 96, cx = x + (w - cs) / 2, cy = y + 12;
        if (NowPlaying.available() && NowPlaying.hasCover()) {
            g.blit(RenderPipelines.GUI_TEXTURED, NowPlaying.cover(), cx, cy, 0f, 0f, cs, cs, 96, 96, 96, 96);
        } else {
            d.round(cx, cy, cs, cs, 14, 0x40FFFFFF);
            d.textCenter("♪", cx + cs / 2, cy + cs / 2 - 4, Theme.TEXT_DIM);
        }
        String title = NowPlaying.available() ? NowPlaying.title() : "Ничего не играет";
        d.textCenter(Draw.ellipsize(title, w - 16), x + w / 2, cy + cs + 8, Theme.TEXT);
        if (NowPlaying.available()) d.textCenter(Draw.ellipsize(NowPlaying.artist() + " · " + NowPlaying.source(), w - 16), x + w / 2, cy + cs + 20, Theme.TEXT_MUTED);
        // текст песни — в координатах GUI (вне масштаба окна)
        int ly = cy + cs + 34;
        float gx = x * k, gy = ly * k, gw = w * k, gh = (y + h - ly - 8) * k;
        d.end();
        lyrics.render(g, gx + 8 * k, gy, gw - 16 * k, gh, 0.8f, true, 1f, 0);
        d.begin(g, font, k);
    }

    // ── нижняя панель ──

    private void drawBar(GuiGraphics g, int mx, int my) {
        int x = wx + 12, y = wy + wh - BAR - 6, w = ww - 24;
        barY = y;
        d.glass(x, y, w, BAR, Theme.RADIUS_CARD, Theme.GLASS_PANEL, 0.9f);
        int cx = x + 16, cyB = y + 10;
        boolean playing = NowPlaying.playing();
        icon(mx, my, cx, cyB, MediaIcons.PREV, () -> NowPlaying.command("prev"));
        icon(mx, my, cx + 26, cyB, playing ? MediaIcons.PAUSE : MediaIcons.PLAY, () -> NowPlaying.command("toggle"));
        icon(mx, my, cx + 52, cyB, MediaIcons.NEXT, () -> NowPlaying.command("next"));
        // прогресс
        barX = cx + 90;
        volW = 80;
        barW = w - (barX - x) - volW - 200;
        long dur = NowPlaying.duration(), pos = NowPlaying.position();
        float f = dur > 0 ? Math.min(1, pos / (float) dur) : 0;
        d.round(barX, cyB + 7, barW, 4, 2, Theme.TRACK);
        d.round(barX, cyB + 7, Math.max(4, Math.round(barW * f)), 4, 2, Theme.TEXT);
        d.text(time(pos), barX, cyB + 16, Theme.TEXT_MUTED);
        if (dur > 0) d.textRight(time(dur), barX + barW, cyB + 16, Theme.TEXT_MUTED);
        // громкость
        volX = barX + barW + 18;
        MusicPlayerModule m = MusicPlayerModule.get();
        float vol = MusicPlayer.volume;
        d.text("♫", volX - 2, cyB + 4, Theme.TEXT_DIM);
        d.round(volX + 12, cyB + 7, volW, 4, 2, Theme.TRACK);
        d.round(volX + 12, cyB + 7, Math.max(4, Math.round(volW * vol)), 4, 2, Theme.accent());
        // режимы
        int rx = volX + 12 + volW + 12;
        int tw1 = toggle(mx, my, rx, cyB + 2, "Микс", MusicPlayer.shuffle, () -> { if (m != null) m.toggleShuffle(); });
        int tw2 = toggle(mx, my, rx + tw1 + 4, cyB + 2, MusicPlayer.repeat == 2 ? "Повтор 1" : "Повтор", MusicPlayer.repeat > 0, () -> { if (m != null) m.cycleRepeat(); });
        if (m != null) pill(mx, my, rx + tw1 + tw2 + 8, cyB + 2, 58, m.eqLabel(), m::cycleEq);
    }

    private void icon(int mx, int my, int x, int y, int kind, Runnable r) {
        boolean hov = Widget.hit(mx, my, x - 4, y - 2, 22, 22);
        if (hov) d.round(x - 4, y - 2, 22, 22, 11, Theme.HOVER);
        MediaIcons.draw(d, kind, x + 7, y + 9, kind == MediaIcons.PLAY || kind == MediaIcons.PAUSE ? 11 : 9, hov ? Theme.TEXT : Theme.TEXT_DIM);
        hits.add(new Hit(x - 4, y - 2, 22, 22, r));
    }

    /** @return ширина кнопки. */
    private int toggle(int mx, int my, int x, int y, String s, boolean on, Runnable r) {
        int w = d.width(s) + 14;
        boolean hov = Widget.hit(mx, my, x, y, w, 18);
        d.round(x, y, w, 18, 9, on ? Theme.GLASS_ACTIVE : hov ? Theme.HOVER : Theme.GLASS_CARD);
        d.textCenter(s, x + w / 2, Draw.textY(y, 18), on ? Theme.accent() : Theme.TEXT_DIM);
        hits.add(new Hit(x, y, w, 18, r));
        return w;
    }

    private static String time(long ms) {
        long s = ms / 1000;
        return s / 60 + ":" + (s % 60 < 10 ? "0" : "") + s % 60;
    }

    // ── ввод ──

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        double mx = e.x() / k, my = e.y() / k;
        if (!Widget.hit(mx, my, wx, wy, ww, wh)) { onClose(); return true; }
        int cyB = barY + 10;
        if (Widget.hit(mx, my, barX, cyB, barW, 14)) { seekDrag = true; seekTo(mx); return true; }
        if (Widget.hit(mx, my, volX + 12, cyB, volW, 14)) { volDrag = true; volTo(mx); return true; }
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (Widget.hit(mx, my, h.x, h.y, h.w, h.h)) { h.r.run(); return true; }
        }
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (seekDrag) seekTo(e.x() / k);
        if (volDrag) volTo(e.x() / k);
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        seekDrag = volDrag = false;
        return true;
    }

    private void seekTo(double mx) {
        if (!NowPlaying.local()) return;
        float f = (float) Math.max(0, Math.min(1, (mx - barX) / barW));
        MusicPlayer.seek((long) (f * MusicPlayer.length()));
    }

    private void volTo(double mx) {
        float f = (float) Math.max(0, Math.min(1, (mx - volX - 12) / volW));
        MusicPlayer.volume = f;
        MusicPlayerModule m = MusicPlayerModule.get();
        if (m != null) m.setVolume(f);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double sx, double sy) {
        scrollTarget = Math.max(0, Math.min(scrollMax, scrollTarget - (float) sy * 40));
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        int key = e.key();
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (!search.isEmpty()) search.setLength(0);
            else onClose();
            return true;
        }
        if (key == GLFW.GLFW_KEY_BACKSPACE && !search.isEmpty()) {
            search.setLength(search.length() - Character.charCount(search.codePointBefore(search.length())));
            return true;
        }
        if (key == GLFW.GLFW_KEY_SPACE && search.isEmpty()) {
            NowPlaying.command("toggle");
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(CharacterEvent e) {
        int cp = e.codepoint();
        if (Character.isISOControl(cp) || search.length() > 40 || (cp == ' ' && search.isEmpty())) return false;
        search.appendCodePoint(cp);
        scrollTarget = scroll = 0;
        return true;
    }
}

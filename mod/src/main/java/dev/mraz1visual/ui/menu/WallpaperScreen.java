package dev.mraz1visual.ui.menu;

import com.mojang.blaze3d.platform.NativeImage;
import dev.mraz1visual.core.ModuleManager;
import dev.mraz1visual.ui.Anim;
import dev.mraz1visual.ui.Colors;
import dev.mraz1visual.ui.Draw;
import dev.mraz1visual.ui.Fonts;
import dev.mraz1visual.ui.Theme;
import dev.mraz1visual.ui.widget.Widget;
import dev.mraz1visual.wallpaper.Ffmpeg;
import dev.mraz1visual.wallpaper.Wallpaper;
import dev.mraz1visual.wallpaper.WallpaperPlayer;
import dev.mraz1visual.wallpaper.WallpaperScanner;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Выбор обоев: все обои Wallpaper Engine с компьютера плиткой с превью.
 * Клик — обои сразу ставятся на фон (этот экран тоже на нём, видно результат).
 */
public final class WallpaperScreen extends MenuBase {
    private enum Filter { ALL, VIDEO, OWN }

    private static final int TW = 320, TH = 180;
    private static final int TITLE_H = 30, GAP = 12;

    private final Screen parent;
    private final Item[] filterItems = new Item[3];
    private Filter filter = Filter.ALL;
    private String query = "";

    private volatile List<Wallpaper> all;
    private final List<Wallpaper> shown = new ArrayList<>();
    private int left, px, py, pw, ph, cols, cw, chh;
    private float scroll, scrollTarget;
    private final Map<String, Float> hover = new HashMap<>();

    // превью
    private record Thumb(Identifier id, DynamicTexture tex) {}
    private final Map<String, Thumb> thumbs = new HashMap<>();
    private final java.util.Set<String> requested = new java.util.HashSet<>();
    private final ConcurrentLinkedQueue<Object[]> ready = new ConcurrentLinkedQueue<>();
    private final ExecutorService loader = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "Mraz1Visual wallpaper previews");
        t.setDaemon(true);
        return t;
    });
    private static int idCounter;

    public WallpaperScreen(Screen parent) {
        super("Обои");
        this.parent = parent;
        filterItems[0] = item("ВСЕ", () -> setFilter(Filter.ALL));
        filterItems[1] = item("ВИДЕО", () -> setFilter(Filter.VIDEO));
        filterItems[2] = item("СВОИ", () -> setFilter(Filter.OWN));
        itemAfterGap("ФАЙЛ…", this::pickFile);
        item("ПАПКА", this::openFolder);
        item("ОБНОВИТЬ", this::rescan);
        itemAfterGap("НАЗАД", this::onClose);
        filterItems[0].selected = true;
    }

    @Override protected float sunScreenX() { return 0.66f; }
    @Override protected float sunScreenY() { return 0.02f; }
    @Override protected float itemScale() { return 1.15f; }
    @Override protected int itemLine() { return 30; }

    @Override
    protected void init() {
        super.init();
        if (all == null) rescan();
    }

    private void rescan() {
        all = null;
        Thread t = new Thread(() -> {
            List<Wallpaper> list;
            try {
                list = WallpaperScanner.scan();
            } catch (Throwable e) {
                ModuleManager.LOG.warn("Wallpaper scan failed", e);
                list = List.of();
            }
            all = list;
        }, "Mraz1Visual wallpaper scan");
        t.setDaemon(true);
        t.start();
    }

    private void setFilter(Filter f) {
        filter = f;
        for (int i = 0; i < 3; i++) filterItems[i].selected = i == f.ordinal();
        scroll = scrollTarget = 0;
    }

    @Override
    protected void onLayout() {
        left = Math.max(24, Math.round(vw * 0.056f));
        layoutItems(left, Math.round(vh * 0.2f));
        int itemsRight = left;
        for (Item it : items) itemsRight = Math.max(itemsRight, it.x + it.w);
        px = Math.max(itemsRight + 40, Math.round(vw * 0.24f));
        py = 92;
        pw = vw - px - Math.max(24, Math.round(vw * 0.04f));
        ph = vh - py - 20;
        cols = Math.max(1, (pw + GAP) / (130 + GAP));
        cw = (pw - (cols - 1) * GAP) / cols;
        chh = Math.round(cw * 9f / 16f) + TITLE_H;
    }

    private void refilter() {
        shown.clear();
        List<Wallpaper> src = all;
        if (src == null) return;
        String q = query.toLowerCase(Locale.ROOT);
        for (Wallpaper w : src) {
            if (filter == Filter.VIDEO && w.kind() != Wallpaper.Kind.VIDEO) continue;
            if (filter == Filter.OWN && !w.source().equals("Свои")) continue;
            if (!q.isEmpty() && !w.title().toLowerCase(Locale.ROOT).contains(q)) continue;
            shown.add(w);
        }
    }

    // ── превью ───────────────────────────────────────────────

    private void requestThumb(Wallpaper w) {
        if (!requested.add(w.key())) return;
        loader.execute(() -> {
            BufferedImage img = null;
            try {
                if (w.preview() != null) img = ImageIO.read(w.preview().toFile());
            } catch (Throwable ignored) {
            }
            if (img == null) img = ffmpegFrame(w.media());
            if (img == null) return;
            BufferedImage out = new BufferedImage(TW, TH, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = out.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            float s = Math.max(TW / (float) img.getWidth(), TH / (float) img.getHeight());
            int dw = Math.round(img.getWidth() * s), dh = Math.round(img.getHeight() * s);
            g.drawImage(img, (TW - dw) / 2, (TH - dh) / 2, dw, dh, null);
            g.dispose();
            ready.add(new Object[]{w.key(), out.getRGB(0, 0, TW, TH, null, 0, TW)});
        });
    }

    /** Кадр из видео (или webp) через ffmpeg, если он уже есть. */
    private static BufferedImage ffmpegFrame(Path media) {
        String ff = Ffmpeg.find();
        if (ff == null) return null;
        try {
            Process p = new ProcessBuilder(ff, "-hide_banner", "-loglevel", "error", "-nostdin", "-ss", "1", "-i", media.toString(),
                    "-frames:v", "1", "-vf", "scale=" + TW + ":-2", "-f", "image2pipe", "-vcodec", "png", "pipe:1")
                    .redirectError(ProcessBuilder.Redirect.DISCARD).start();
            BufferedImage img = ImageIO.read(p.getInputStream());
            p.destroy();
            return img;
        } catch (Throwable t) {
            return null;
        }
    }

    private void uploadThumbs() {
        for (int n = 0; n < 6; n++) {
            Object[] r = ready.poll();
            if (r == null) return;
            int[] px = (int[]) r[1];
            NativeImage ni = new NativeImage(TW, TH, false);
            for (int y = 0, i = 0; y < TH; y++) for (int x = 0; x < TW; x++, i++) ni.setPixel(x, y, px[i] | 0xFF000000);
            DynamicTexture t = new DynamicTexture(() -> "mraz1visual wallpaper preview", ni);
            Identifier id = Identifier.fromNamespaceAndPath("mraz1visual", "wallpaper/thumb_" + (idCounter++));
            minecraft.getTextureManager().register(id, t);
            thumbs.put((String) r[0], new Thumb(id, t));
        }
    }

    // ── действия ─────────────────────────────────────────────

    private void pickFile() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            String[] ext = {"*.mp4", "*.webm", "*.mkv", "*.mov", "*.avi", "*.gif", "*.jpg", "*.jpeg", "*.png", "*.webp", "project.json"};
            PointerBuffer filters = stack.mallocPointer(ext.length);
            for (String e : ext) filters.put(stack.UTF8(e));
            filters.flip();
            String res = TinyFileDialogs.tinyfd_openFileDialog("Обои", "", filters, "Видео, gif, картинка или project.json", false);
            if (res == null) return;
            Wallpaper w = WallpaperScanner.fromFile(Path.of(res), "Файл");
            if (w != null) WallpaperPlayer.select(w);
        } catch (Throwable t) {
            ModuleManager.LOG.warn("Wallpaper file pick failed", t);
        }
    }

    private void openFolder() {
        try {
            java.nio.file.Files.createDirectories(WallpaperScanner.OWN);
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            String dir = WallpaperScanner.OWN.toAbsolutePath().toString();
            if (os.contains("win")) new ProcessBuilder("explorer", dir).start();
            else if (os.contains("mac")) new ProcessBuilder("open", dir).start();
            else new ProcessBuilder("xdg-open", dir).start();
        } catch (Throwable t) {
            ModuleManager.LOG.warn("Can't open wallpaper folder", t);
        }
    }

    // ── рисование ────────────────────────────────────────────

    @Override
    protected void renderMenu(double mx, double my, float dt) {
        refilter();
        uploadThumbs();
        renderItems(mx, my, dt);
        renderBrand(left);

        // заголовок и состояние
        d.textScaled("ОБОИ", Fonts.Kind.TITLE, px, 30, 1.2f, Colors.mulAlpha(Theme.TEXT, fade));
        String count = all == null ? "Ищу обои Wallpaper Engine…" : "Найдено: " + shown.size();
        d.text(count, Fonts.Kind.HEAD, px + Math.round(Fonts.width("ОБОИ", Fonts.Kind.TITLE) * 1.2f) + 12, 40, Colors.mulAlpha(Theme.TEXT_DIM, fade));
        String q = query.isEmpty() ? "Печатайте, чтобы искать" : "Поиск: " + query + "_";
        d.textRight(q, px + pw, 40, Colors.mulAlpha(query.isEmpty() ? Theme.TEXT_MUTED : Theme.TEXT, fade));
        d.text(WallpaperPlayer.status(), px, 66, Colors.mulAlpha(Theme.TEXT_DIM, fade));
        d.rect(px, py - 8, pw, 1, Colors.mulAlpha(0x22FFFFFF, fade));

        scroll = Anim.to(scroll, scrollTarget, dt);
        d.clip(px - 4, py, pw + 8, ph);
        int sc = Math.round(scroll);
        int total = shown.size() + 1; // первая плитка — стандартный фон
        Wallpaper cur = WallpaperPlayer.current();
        for (int i = 0; i < total; i++) {
            int x = px + (i % cols) * (cw + GAP), y = py + (i / cols) * (chh + GAP) - sc;
            if (y + chh < py || y > py + ph) continue;
            Wallpaper w = i == 0 ? null : shown.get(i - 1);
            String key = w == null ? "" : w.key();
            boolean hov = Widget.hit(mx, my, x, y, cw, chh) && my >= py && my <= py + ph;
            float hv = Anim.to(hover.getOrDefault(key, 0f), hov ? 1 : 0, dt);
            hover.put(key, hv);
            boolean sel = w == null ? cur == null : cur != null && cur.key().equals(key);
            int imgH = chh - TITLE_H;

            d.round(x - 1, y - 1, cw + 2, chh + 2, 5, Colors.mulAlpha(sel ? Theme.accent() : Colors.lerp(0x22FFFFFF, 0x66FFFFFF, hv), fade));
            d.round(x, y, cw, chh, 4, Colors.mulAlpha(0xF0101016, fade));
            if (w == null) {
                d.gradientV(x, y, cw, imgH, 0xFF1A1A2A, 0xFF050508);
                d.circle(x + cw / 2, y + imgH / 2, Math.round(imgH * 0.5f), 0xFFE8E8F4);
                d.circle(x + cw / 2, y + imgH / 2, Math.round(imgH * 0.46f), 0xFF050508);
            } else {
                Thumb t = thumbs.get(key);
                if (t != null) {
                    WallpaperPlayer.blitLinear(d.g, t.tex(), x, y, cw, imgH, 0, 1, 0, 1);
                } else {
                    requestThumb(w);
                    d.rect(x, y, cw, imgH, 0xFF15151E);
                    d.textCenter(w.kind() == Wallpaper.Kind.VIDEO ? "▶" : "…", x + cw / 2, y + imgH / 2 - 4, Theme.TEXT_MUTED);
                }
                if (hv > 0.01f) d.rect(x, y, cw, imgH, Math.round(40 * hv) << 24 | 0xFFFFFF);
            }
            String title = w == null ? "Затмение (стандарт)" : w.title();
            String sub = w == null ? "Встроенный фон Mraz1Visual" : w.source().isEmpty() ? w.label() : w.label() + " · " + w.source();
            d.text(Draw.ellipsize(title, cw - 10), Fonts.Kind.BOLD, x + 6, y + imgH + 5, Colors.mulAlpha(sel ? Theme.TEXT : 0xFFD4D4E2, fade));
            d.text(Draw.ellipsize(sub, cw - 10), x + 6, y + imgH + 17, Colors.mulAlpha(Theme.TEXT_MUTED, fade));
            if (sel) d.round(x + cw - 18, y + 6, 12, 12, 6, Theme.accent());
        }
        if (all != null && shown.isEmpty()) {
            int y = py + chh + GAP + 10 - sc;
            d.text(query.isEmpty() ? "Обоев Wallpaper Engine не найдено." : "Ничего не найдено.", px, y, Colors.mulAlpha(Theme.TEXT_DIM, fade));
            if (query.isEmpty()) {
                d.text("Подпишитесь на обои в Мастерской Steam, нажмите «ФАЙЛ…» или положите видео/gif/картинки в «ПАПКУ».", px, y + 14, Colors.mulAlpha(Theme.TEXT_MUTED, fade));
            }
        }
        d.unclip();
    }

    private int contentH() {
        int total = shown.size() + 1;
        return ((total + cols - 1) / cols) * (chh + GAP);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double sx, double sy) {
        int max = Math.max(0, contentH() - ph);
        scrollTarget = (float) Math.max(0, Math.min(max, scrollTarget - sy * (chh + GAP) * 0.6));
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        if (e.button() != 0) return true;
        double mx = e.x() / k, my = e.y() / k;
        if (clickItems(mx, my)) return true;
        if (my < py || my > py + ph) return true;
        int sc = Math.round(scroll);
        int total = shown.size() + 1;
        for (int i = 0; i < total; i++) {
            int x = px + (i % cols) * (cw + GAP), y = py + (i / cols) * (chh + GAP) - sc;
            if (Widget.hit(mx, my, x, y, cw, chh)) {
                WallpaperPlayer.select(i == 0 ? null : shown.get(i - 1));
                return true;
            }
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (e.key() == GLFW.GLFW_KEY_ESCAPE) {
            if (!query.isEmpty()) query = "";
            else onClose();
            return true;
        }
        if (e.key() == GLFW.GLFW_KEY_BACKSPACE && !query.isEmpty()) {
            query = query.substring(0, query.length() - 1);
            scroll = scrollTarget = 0;
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(CharacterEvent e) {
        int cp = e.codepoint();
        if (cp >= 32 && query.length() < 40) {
            query += new String(Character.toChars(cp));
            scroll = scrollTarget = 0;
        }
        return true;
    }

    @Override
    public void removed() {
        super.removed();
        loader.shutdownNow();
        for (Thumb t : thumbs.values()) minecraft.getTextureManager().release(t.id());
        thumbs.clear();
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // здесь обои показываем всегда (даже если в меню они выключены) — чтобы был виден выбор
        if (!WallpaperPlayer.render(g, 0, 0, width, height)) super.renderBackground(g, mouseX, mouseY, partialTick);
        g.fill(0, 0, width, height, 0x66000000);
    }
}

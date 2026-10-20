package dev.mraz1visual.ui;

import com.mojang.blaze3d.platform.Window;
import dev.mraz1visual.Mraz1Visual;
import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.core.ModuleManager;
import dev.mraz1visual.core.Modules;
import dev.mraz1visual.module.ClientSettings;
import dev.mraz1visual.setting.BoolSetting;
import dev.mraz1visual.setting.ColorSetting;
import dev.mraz1visual.setting.EnumSetting;
import dev.mraz1visual.setting.KeySetting;
import dev.mraz1visual.setting.Setting;
import dev.mraz1visual.setting.SliderSetting;
import dev.mraz1visual.ui.widget.BindWidget;
import dev.mraz1visual.ui.widget.ColorWidget;
import dev.mraz1visual.ui.widget.EnumWidget;
import dev.mraz1visual.ui.widget.Host;
import dev.mraz1visual.ui.widget.Popup;
import dev.mraz1visual.ui.widget.SettingWidget;
import dev.mraz1visual.ui.widget.SliderWidget;
import dev.mraz1visual.ui.widget.ToggleWidget;
import dev.mraz1visual.ui.widget.Widget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Окно настроек в «нейтральном» стиле: слева боковая панель (категории, разделы клиента, профиль),
 * справа заголовок с поиском и карточки модулей. Клик по карточке раскрывает её настройки прямо на месте,
 * ПКМ — вкл/выкл, СКМ или звезда — в избранное. Тёмная и светлая тема, обои — внутри окна.
 */
public final class Mraz1VisualScreen extends Screen implements Host {
    private static final String BRAND = "MRAZ1VISUAL";
    private static final int SEARCH_MAX = 32;
    private static final Category[] MODULE_TABS = {Category.PLAYER, Category.VISUALS, Category.COSMETICS, Category.SWORDS, Category.WINGS, Category.PETS, Category.SKY, Category.PERFORMANCE, Category.MISC};
    private static final int NAV_H = 19, HINT_H = 20, CARD_HEAD = 38, CARD_R = 7, COL_GAP = 8, CARD_GAP = 8;
    private static final int TILE = 20, ICON = 9;
    private static final Path FAV_FILE = dev.mraz1visual.util.TextFiles.DIR.resolve("favorites.txt");

    private final ModuleManager mm = Mraz1Visual.MODULES;
    private final ClientSettings cs = Modules.client;
    private final Draw d = new Draw();

    // геометрия (единицы окна)
    private float k = 1f;
    private int vw, vh, wx, wy, ww, wh;
    private int layoutW = -1, layoutH = -1, layoutSize = -1;

    // навигация
    private enum NavKind { CATEGORY, FAVORITES, FRIENDS, WALLPAPER }

    private static final class Nav {
        final NavKind kind;
        final Category category;
        final String label;
        final int icon;
        int y;
        float hover;
        /** Масштаб подписи: 1 — влезает, меньше — сжать (считается в relayout, не в кадре). */
        float labelScale = 1f;

        Nav(NavKind kind, Category category, String label, int icon) {
            this.kind = kind;
            this.category = category;
            this.label = label;
            this.icon = icon;
        }
    }

    private final List<Nav> nav = new ArrayList<>();
    private int sectionModulesY, sectionClientY;
    private float themeHover;

    // состояние
    private Category category = Category.VISUALS;
    private boolean favorites;
    private boolean restored;
    private final List<Module> list = new ArrayList<>();
    private final java.util.Set<String> favs = new java.util.LinkedHashSet<>();

    /** Состояние карточки модуля: раскрытие, наведение, анимации. */
    private static final class Card {
        final Module m;
        boolean open;
        float openT, hover, tog, star;
        int x, y, w, h, fullH;
        final List<SettingWidget> rows = new ArrayList<>();
        final List<String> groups = new ArrayList<>(); // подпись группы перед строкой (или null)
        final List<Integer> rowY = new ArrayList<>();
        String desc = "";
        int descW = -1;

        Card(Module m) {
            this.m = m;
            this.tog = m.isEnabled() ? 1 : 0;
        }
    }

    private final Map<Module, Card> cardState = new IdentityHashMap<>();
    private final List<Card> cards = new ArrayList<>();
    private float contentScroll, contentTarget;
    private int contentMax;

    private Popup popup;
    private BindWidget listening;
    private Widget active;

    private final StringBuilder search = new StringBuilder();
    private String searchView = "";
    private boolean searchFocused;

    private long lastNanos;
    private final ProfilesPanel profiles = new ProfilesPanel();
    private float openT, contentT = 1f;
    /** Окно закрывается: проигрываем анимацию и только потом уходим с экрана. */
    private boolean closing;
    /** Плашка активного пункта меню — плавно переезжает между пунктами. */
    private float selY = -1;
    private String subtitle = "";

    /** Куда вернуться при закрытии: null — в игру, иначе в меню, откуда открыли. */
    private Screen parent;

    public void setParent(Screen parent) {
        this.parent = parent;
    }

    public Mraz1VisualScreen() {
        super(Component.literal("Mraz1Visual"));
        loadFavorites();
    }

    private boolean configsMode() {
        return category == Category.CONFIGS && !favorites && search.isEmpty();
    }

    // ── жизненный цикл ───────────────────────────────────────

    @Override
    protected void init() {
        d.font = this.font;
        if (!restored) {
            restored = true;
            restoreState();
        }
        openT = cs.animations.get() ? 0f : 1f;
        closing = false;
        profiles.refresh();
        relayout();
    }

    private void restoreState() {
        Category c = Category.VISUALS;
        try {
            if (!Mraz1Visual.CONFIG.uiCategory.isEmpty()) c = Category.valueOf(Mraz1Visual.CONFIG.uiCategory);
        } catch (IllegalArgumentException ignored) {
        }
        // вкладку могли убрать (старый конфиг) — тогда открываем Визуалы
        if (c != Category.CONFIGS && c != Category.CLIENT && !java.util.Arrays.asList(MODULE_TABS).contains(c)) c = Category.VISUALS;
        category = c;
        Module m = mm.find(Mraz1Visual.CONFIG.uiModule);
        rebuildList();
        if (m != null && list.contains(m)) card(m).open = true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        // сначала красиво сворачиваем окно, закрытие — в конце анимации (см. renderWindow)
        if (cs.animations.get() && !closing && openT > 0.05f) {
            closing = true;
            popup = null;
            return;
        }
        finishClose();
    }

    private void finishClose() {
        closing = false;
        popup = null;
        if (listening != null) listening.cancel();
        listening = null;
        searchFocused = false;
        String cat = category.name();
        String mod = "";
        for (Card c : cards) if (c.open) mod = c.m.id;
        if (!cat.equals(Mraz1Visual.CONFIG.uiCategory) || !mod.equals(Mraz1Visual.CONFIG.uiModule)) {
            Mraz1Visual.CONFIG.uiCategory = cat;
            Mraz1Visual.CONFIG.uiModule = mod;
            Mraz1Visual.CONFIG.markDirty();
        }
        Mraz1Visual.CONFIG.saveIfDirty();
        Screen back = parent;
        parent = null;
        minecraft.setScreen(back);
    }

    @Override
    public void removed() {
        Mraz1Visual.CONFIG.saveIfDirty();
        lastNanos = 0;
        active = null;
    }

    // ── избранное ────────────────────────────────────────────

    private void loadFavorites() {
        try {
            if (java.nio.file.Files.exists(FAV_FILE)) {
                for (String l : java.nio.file.Files.readAllLines(FAV_FILE)) if (!l.isBlank()) favs.add(l.trim());
            }
        } catch (Throwable ignored) {
        }
    }

    private void toggleFavorite(Module m) {
        if (!favs.remove(m.id)) favs.add(m.id);
        try {
            java.nio.file.Files.createDirectories(FAV_FILE.getParent());
            java.nio.file.Files.write(FAV_FILE, favs);
        } catch (Throwable ignored) {
        }
        if (favorites) rebuildList();
    }

    // ── раскладка ────────────────────────────────────────────

    private void relayout() {
        layoutW = width;
        layoutH = height;
        layoutSize = cs.size.get();

        Window win = minecraft.getWindow();
        double gui = win.getGuiScale();
        int pw = win.getWidth(), ph = win.getHeight();
        double base = Math.max(0.75, ph / 1080.0) * switch (cs.size.getId()) {
            case "small" -> 1.1;
            case "large" -> 1.5;
            default -> 1.3;
        };
        double fit = Math.min(pw / (double) (Theme.WINDOW_W + 24), ph / (double) (Theme.WINDOW_H + 24));
        double unit = Math.max(0.6, Math.min(base, fit));
        k = (float) (unit / gui);
        d.scale = k;
        vw = (int) (width / k);
        vh = (int) (height / k);

        ww = Math.min(Theme.WINDOW_W, vw - 16);
        wh = Math.min(Theme.WINDOW_H, vh - 16);
        wx = (vw - ww) / 2;
        wy = (vh - wh) / 2;

        nav.clear();
        int y = wy + 50;
        sectionModulesY = y;
        y += 16;
        for (Category c : MODULE_TABS) {
            Nav n = new Nav(NavKind.CATEGORY, c, c.title, Icons.forCategory(c));
            n.y = y;
            nav.add(n);
            y += NAV_H + 2;
        }
        Nav fav = new Nav(NavKind.FAVORITES, null, "Избранное", Icons.STAR);
        fav.y = y;
        nav.add(fav);
        y += NAV_H + 12;
        sectionClientY = y;
        y += 16;
        Object[][] client = {
                {NavKind.CATEGORY, Category.CONFIGS, "Конфиги", Icons.SLIDERS},
                {NavKind.FRIENDS, null, "Друзья", Icons.FRIENDS},
                {NavKind.WALLPAPER, null, "Обои", Icons.LAYERS},
                {NavKind.CATEGORY, Category.CLIENT, "Настройки", Icons.GEAR}};
        for (Object[] o : client) {
            Nav n = new Nav((NavKind) o[0], (Category) o[1], (String) o[2], (Integer) o[3]);
            n.y = y;
            nav.add(n);
            y += NAV_H + 2;
        }
        // место под подпись: от текста до счётчика «10/12» справа
        int avail = Theme.SIDEBAR_W - 16 - 28 - 11 - d.width("10/12") - 6;
        for (Nav n : nav) {
            int lw = d.width(n.label);
            n.labelScale = lw > avail ? Math.max(0.7f, avail / (float) lw) : 1f;
        }
        for (Card c : cardState.values()) c.descW = -1;
        rebuildList();
    }

    private int cX() { return wx + Theme.SIDEBAR_W; }
    private int cW() { return ww - Theme.SIDEBAR_W; }
    private int areaX() { return cX() + 12; }
    private int areaY() { return wy + Theme.TOPBAR_H + 2; }
    private int areaW() { return cW() - 24 - previewSpace(); }

    // ── живое превью: модель игрока с крыльями, мечом и питомцем ──
    private static final int PREVIEW_W = 168;
    private final PlayerPreview preview = new PlayerPreview();
    private boolean previewDrag;

    /** Превью — на вкладках косметики, где его видно: мечи, крылья, питомцы. */
    private boolean previewMode() {
        return search.isEmpty() && !favorites && (category == Category.SWORDS || category == Category.WINGS || category == Category.PETS)
                && cW() - 24 - PREVIEW_W - COL_GAP >= 240;
    }

    private int previewSpace() { return previewMode() ? PREVIEW_W + COL_GAP : 0; }
    private int previewX() { return cX() + cW() - 12 - PREVIEW_W; }
    private int previewY() { return areaY() + 4; }
    private int previewH() { return areaH() - 8; }
    private int areaH() { return wh - Theme.TOPBAR_H - 4 - HINT_H; }
    private int searchX() { return wx + ww - 14 - Theme.SEARCH_W; }
    private int searchY() { return wy + (Theme.TOPBAR_H - 22) / 2; }
    private int profileY() { return wy + wh - 52; }
    private int themeX() { return wx + Theme.SIDEBAR_W - 14 - 18; }
    private int themeY() { return profileY() + 12; }

    private Card card(Module m) {
        return cardState.computeIfAbsent(m, Card::new);
    }

    private void rebuildList() {
        list.clear();
        if (!search.isEmpty()) {
            String q = searchView.toLowerCase(Locale.ROOT);
            for (Module m : mm.all()) {
                if (m.name.toLowerCase(Locale.ROOT).contains(q) || m.description.toLowerCase(Locale.ROOT).contains(q)) list.add(m);
            }
        } else if (favorites) {
            for (Module m : mm.all()) if (favs.contains(m.id)) list.add(m);
        } else {
            list.addAll(mm.in(category));
        }
        cards.clear();
        for (Module m : list) cards.add(card(m));
        if (list.size() == 1) cards.get(0).open = true;
        int on = 0;
        for (Module m : list) if (m.isEnabled() && !m.alwaysOn()) on++;
        subtitle = list.size() + " " + plural(list.size(), "модуль", "модуля", "модулей") + (on > 0 ? " · " + on + " включено" : "");
        contentTarget = Math.min(contentTarget, Math.max(0, contentMax));
    }

    private static String plural(int n, String one, String few, String many) {
        int a = n % 100, b = n % 10;
        if (a >= 11 && a <= 14) return many;
        return b == 1 ? one : b >= 2 && b <= 4 ? few : many;
    }

    private void selectCategory(Category c) {
        category = c;
        favorites = false;
        clearSearch();
        contentScroll = contentTarget = 0;
        contentT = cs.animations.get() ? 0f : 1f;
        rebuildList();
        if (c == Category.CONFIGS) profiles.refresh();
    }

    /** Dev-стенд: открыть вкладку из сценария. */
    public void harnessCategory(Category c) {
        selectCategory(c);
    }

    private void showFavorites() {
        favorites = true;
        clearSearch();
        contentScroll = contentTarget = 0;
        contentT = cs.animations.get() ? 0f : 1f;
        rebuildList();
    }

    private void clearSearch() {
        if (!search.isEmpty()) {
            search.setLength(0);
            searchView = "";
        }
        searchFocused = false;
    }

    /** Открыть модуль: перейти в его категорию, раскрыть и прокрутить к нему. */
    private void reveal(Module m) {
        if (m == null) return;
        selectCategory(m.category);
        Card c = card(m);
        c.open = true;
        layoutCards();
        contentTarget = clamp(c.y - areaY() - 4, 0, Math.max(0, contentMax + c.fullH));
    }

    private void buildRows(Card c) {
        c.rows.clear();
        c.groups.clear();
        if (!c.m.alwaysOn()) {
            c.rows.add(new BindWidget(c.m.bind, this, true));
            c.groups.add(null);
        }
        String last = null;
        boolean many = c.m.settings().stream().map(Setting::group).distinct().count() > 1;
        for (Setting<?> s : c.m.settings()) {
            String g = many && !s.group().equals(last) ? s.group().toUpperCase(Locale.ROOT) : null;
            last = s.group();
            c.rows.add(widgetFor(s));
            c.groups.add(g);
        }
    }

    /** Раскладка карточек: две колонки (или одна на узком окне / для одного модуля). */
    private void layoutCards() {
        int ax = areaX(), ay = areaY() + 4, aw = areaW();
        int cols = list.size() > 1 && aw >= 2 * 240 + COL_GAP ? 2 : 1;
        int colW = (aw - (cols - 1) * COL_GAP) / cols;
        int[] colY = new int[cols];
        for (int i = 0; i < cols; i++) colY[i] = ay;
        for (int i = 0; i < cards.size(); i++) {
            Card c = cards.get(i);
            int col = i % cols;
            c.x = ax + col * (colW + COL_GAP);
            c.y = colY[col];
            if (c.w != colW || c.descW < 0) {
                c.w = colW;
                c.descW = colW - 36 - 70;
                c.desc = Draw.ellipsize(c.m.description, Math.max(20, c.descW));
            }
            if (c.openT > 0.001f && c.rows.isEmpty()) buildRows(c);
            int y = c.y + CARD_HEAD + 6;
            c.rowY.clear();
            for (int r = 0; r < c.rows.size(); r++) {
                if (c.groups.get(r) != null) y += 16;
                c.rowY.add(y);
                c.rows.get(r).setBounds(c.x + 12, y, c.w - 24, Theme.ROW_H);
                y += Theme.ROW_H;
            }
            c.fullH = c.rows.isEmpty() ? CARD_HEAD : y - c.y + 8;
            c.h = CARD_HEAD + Math.round((c.fullH - CARD_HEAD) * ease(c.openT));
            colY[col] += c.h + CARD_GAP;
        }
        int bottom = ay;
        for (int v : colY) bottom = Math.max(bottom, v - CARD_GAP);
        contentMax = Math.max(0, bottom + 6 - (areaY() + areaH()));
    }

    private SettingWidget widgetFor(Setting<?> s) {
        if (s instanceof BoolSetting b) return new ToggleWidget(b, this);
        if (s instanceof SliderSetting sl) return new SliderWidget(sl, this);
        if (s instanceof EnumSetting en) return new EnumWidget(en, this, font);
        if (s instanceof ColorSetting c) return new ColorWidget(c, this);
        if (s instanceof KeySetting key) return new BindWidget(key, this, true);
        if (s instanceof dev.mraz1visual.setting.ActionSetting a) return new dev.mraz1visual.ui.widget.ActionWidget(a, this);
        throw new IllegalArgumentException("No widget for " + s.getClass().getName());
    }

    // ── Host ─────────────────────────────────────────────────

    @Override
    public void openPopup(Popup p) {
        p.clampInto(wx, wy, ww, wh);
        popup = p;
    }

    @Override
    public void startListening(BindWidget w) {
        if (listening != null && listening != w) listening.cancel();
        listening = w;
    }

    @Override
    public float scrollOf(Widget w) {
        return contentScroll;
    }

    // ── рисование ────────────────────────────────────────────

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        String mode = cs.background.getId();
        if (mode.equals("none")) return;
        if (mode.equals("blur")) super.renderBackground(g, mouseX, mouseY, partialTick);
        int a = (int) Math.round(cs.dim.get() * 2.55);
        if (a > 0) g.fill(0, 0, width, height, a << 24);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        boolean light = cs.theme.getId().equals("light");
        if (light) Theme.light();
        try {
            renderWindow(g, mouseX, mouseY);
        } finally {
            if (light) Theme.dark();
        }
    }

    private void renderWindow(GuiGraphics g, int mouseX, int mouseY) {
        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 1f : Math.min(0.1f, (now - lastNanos) / 1e9f);
        float animDt = lastNanos == 0 ? 0f : dt;
        lastNanos = now;
        Anim.enabled = cs.animations.get();
        if (width != layoutW || height != layoutH || cs.size.get() != layoutSize) relayout();

        double rmx = mouseX / k, rmy = mouseY / k;
        boolean overPopup = popup != null && popup.hit(rmx, rmy);
        double mx = overPopup ? -1e6 : rmx, my = overPopup ? -1e6 : rmy;

        contentScroll = Anim.to(contentScroll, contentTarget, dt);
        if (closing) {
            openT = Math.max(0f, openT - animDt / 0.16f);
            if (openT <= 0f) {
                finishClose();
                return;
            }
        } else if (openT < 1f) openT = Math.min(1f, openT + animDt / 0.32f);
        if (contentT < 1f) contentT = Math.min(1f, contentT + animDt / 0.38f);
        for (Card c : cards) c.openT = Anim.enabled ? approach(c.openT, c.open ? 1 : 0, dt * 5f) : c.open ? 1 : 0;
        layoutCards();

        float e = ease(openT);
        // появление: окно «выплывает» из 92% с лёгким перелётом, при закрытии — сжимается
        float s = closing ? 0.94f + 0.06f * e : 0.92f + 0.08f * easeBack(openT);
        boolean scaled = s < 0.999f || s > 1.001f;
        if (scaled) {
            float cx = (wx + ww / 2f) * k, cy = (wy + wh / 2f) * k;
            g.pose().pushMatrix();
            g.pose().translate(cx, cy);
            g.pose().scale(s, s);
            g.pose().translate(-cx, -cy);
        }
        d.begin(g, font, k);
        drawWindow();
        d.clip(wx, wy, ww, wh);
        drawSidebar(mx, my, dt);
        drawTopBar(mx, my, now);
        drawContent(mx, my, dt);
        drawHints();
        if (e < 1f) d.round(wx, wy, ww, wh, Theme.RADIUS, Colors.withAlpha(Theme.isLight() ? 0xF4F4F5 : 0x0A0A0C, Math.round(255 * (1 - e))));
        d.unclip();
        if (popup != null) {
            if (popup.isClosed()) popup = null;
            else {
                g.nextStratum();
                popup.render(d, rmx, rmy, dt);
            }
        }
        d.end();
        if (scaled) g.pose().popMatrix();
    }

    /** Плавный выход с небольшим перелётом за 1 и возвратом — «пружинка». */
    private static float easeBack(float t) {
        float c1 = 1.4f, c3 = c1 + 1f;
        float x = t - 1f;
        return 1f + c3 * x * x * x + c1 * x * x;
    }

    private static float approach(float v, float target, float step) {
        return v < target ? Math.min(target, v + step) : Math.max(target, v - step);
    }

    private static float ease(float t) {
        return 1f - (float) Math.pow(1f - t, 3);
    }

    private boolean wallpaperOn;

    private void drawWindow() {
        d.round(wx - 10, wy - 6, ww + 20, wh + 22, Theme.RADIUS + 10, 0x14000000);
        d.round(wx - 4, wy - 2, ww + 8, wh + 9, Theme.RADIUS + 4, Theme.SHADOW);
        int r = Theme.RADIUS;
        // обои — внутри окна вместо его фона; фон за окном не трогаем
        wallpaperOn = cs.wallGui.get() && dev.mraz1visual.wallpaper.WallpaperPlayer.render(d.g, wx, wy, ww, wh, r, Math.max(20, cs.wallDim.get() - 10));
        if (!wallpaperOn) d.round(wx, wy, ww, wh, r, Theme.GLASS_WINDOW);
        else if (Theme.isLight()) d.round(wx, wy, ww, wh, r, 0x80FFFFFF);
        d.glass(wx, wy, ww, wh, r, 0, 1f);
    }

    /** Стеклянная панель: размытые обои под ней (если есть), тонировка, блик и кант. */
    private void glassPanel(int x, int y, int w, int h, int r, int tint, float strength, int mapDy) {
        if (wallpaperOn) dev.mraz1visual.wallpaper.WallpaperPlayer.glass(d.g, x, y, w, h, r, mapDy);
        d.glass(x, y, w, h, r, tint, strength);
    }

    // ── боковая панель ───────────────────────────────────────

    private boolean navActive(Nav n) {
        if (!search.isEmpty()) return false;
        return switch (n.kind) {
            case CATEGORY -> !favorites && category == n.category;
            case FAVORITES -> favorites;
            default -> false;
        };
    }

    private void drawSidebar(double mx, double my, float dt) {
        int sw = Theme.SIDEBAR_W;
        glassPanel(wx + 6, wy + 6, sw - 6, wh - 12, Theme.RADIUS - 2, Theme.GLASS_PANEL, 1f, 0);

        // логотип
        d.glass(wx + 16, wy + 16, 18, 18, 6, Theme.isLight() ? 0xFF0A0A0A : 0x26FFFFFF, 1f);
        d.textCenter("M", wx + 25, wy + 21, 0xFFFAFAFA);
        d.text(BRAND, Fonts.Kind.HEAD, wx + 40, wy + 16, Theme.TEXT);
        d.text("liquid glass", wx + 40, wy + 26, Theme.TEXT_MUTED);

        d.textScaled("МОДУЛИ", Fonts.Kind.HEAD, wx + 19, sectionModulesY, 0.72f, Theme.TEXT_MUTED);
        d.textScaled("КЛИЕНТ", Fonts.Kind.HEAD, wx + 19, sectionClientY, 0.72f, Theme.TEXT_MUTED);
        int nx = wx + 8, nw = sw - 16;
        Nav activeNav = null;
        for (Nav n : nav) if (navActive(n)) activeNav = n;
        if (activeNav != null) {
            if (selY < 0) selY = activeNav.y;
            selY = Anim.to(selY, activeNav.y, dt * 0.8f);
            d.glass(nx + 3, Math.round(selY), nw - 6, NAV_H, 6, Theme.GLASS_ACTIVE, 0.9f);
            // акцентная полоска слева у активного пункта
            d.round(nx + 3, Math.round(selY) + 5, 2, NAV_H - 10, 1, Theme.accent());
        } else selY = -1;
        for (Nav n : nav) {
            boolean on = navActive(n);
            boolean hov = Widget.hit(mx, my, nx, n.y, nw, NAV_H);
            n.hover = Anim.to(n.hover, hov ? 1 : 0, dt);
            if (!on && n.hover > 0.01f) d.round(nx + 3, n.y, nw - 6, NAV_H, 6, Colors.mulAlpha(Theme.HOVER, n.hover * 1.4f));
            int c = on ? Theme.TEXT : Colors.lerp(Theme.TEXT_DIM, Theme.TEXT, n.hover);
            d.icon(n.icon, nx + 12, n.y + (NAV_H - ICON) / 2, ICON, c);
            if (n.labelScale < 1f) {
                // длинное название («Производительность») чуть уменьшаем, чтобы не наезжало на счётчик
                d.textScaled(n.label, Fonts.Kind.TEXT, nx + 28, Draw.textY(n.y, NAV_H) + Math.round(Theme.FONT_H * (1f - n.labelScale) / 2f), n.labelScale, c);
            } else d.text(n.label, nx + 28, Draw.textY(n.y, NAV_H), c);
            String count = navCount(n);
            if (!count.isEmpty()) d.textRight(count, nx + nw - 11, Draw.textY(n.y, NAV_H), Theme.TEXT_MUTED);
        }
        drawProfile(mx, my, dt);
    }

    private String navCount(Nav n) {
        if (n.kind == NavKind.FAVORITES) return favs.isEmpty() ? "" : Integer.toString(favs.size());
        if (n.kind != NavKind.CATEGORY || n.category == Category.CONFIGS || n.category == Category.CLIENT) return "";
        int on = 0, all = 0;
        for (Module m : mm.in(n.category)) {
            all++;
            if (m.isEnabled() && !m.alwaysOn()) on++;
        }
        return on + "/" + all;
    }

    private void drawProfile(double mx, double my, float dt) {
        int px = wx + 12, py = profileY(), pw = Theme.SIDEBAR_W - 18, ph = 40;
        d.glass(px, py, pw, ph, 8, Theme.GLASS_ACTIVE & 0x80FFFFFF, 0.9f);
        drawAvatar(px + 9, py + 9, 22);
        String name = minecraft.getUser().getName();
        d.text(Draw.ellipsize(name, pw - 70), Fonts.Kind.BOLD, px + 37, py + 10, Theme.TEXT);
        String server = minecraft.getCurrentServer() != null ? minecraft.getCurrentServer().ip
                : minecraft.level != null ? "Одиночная игра" : "В меню";
        d.text(Draw.ellipsize(server, pw - 70), px + 37, py + 22, Theme.TEXT_MUTED);
        // переключатель темы: солнце / луна
        int tx = themeX(), ty = themeY();
        boolean hov = Widget.hit(mx, my, tx, ty, 18, 18);
        themeHover = Anim.to(themeHover, hov ? 1 : 0, dt);
        d.glass(tx, ty, 18, 18, 6, Colors.withAlpha(Theme.isLight() ? 0xFFFFFF : 0xFFFFFF, Math.round((Theme.isLight() ? 160 : 18) + 30 * themeHover)), 0.8f);
        int ic = Colors.lerp(Theme.TEXT_DIM, Theme.TEXT, themeHover);
        if (Theme.isLight()) {
            d.icon(Icons.SUN, tx + 4, ty + 4, 10, ic);
        } else {
            d.disc(tx + 9f, ty + 9f, 4.5f, ic);
            d.disc(tx + 11f, ty + 7.2f, 3.8f, 0xFF1A1A1C);
        }
    }

    // ── шапка ────────────────────────────────────────────────

    private String title() {
        if (!search.isEmpty()) return "Поиск";
        if (favorites) return "Избранное";
        return category == Category.CLIENT ? "Настройки клиента" : category == Category.CONFIGS ? "Конфиги" : category.title;
    }

    private void drawTopBar(double mx, double my, long now) {
        int x = cX() + 16;
        d.textScaled(title(), Fonts.Kind.HEAD, x, wy + 11, 1.25f, Theme.TEXT);
        String sub = configsMode() ? "Профили настроек: сохранить, поделиться, загрузить" : !search.isEmpty() && list.isEmpty() ? "Ничего не найдено" : subtitle;
        d.text(sub, x, wy + 26, Theme.TEXT_MUTED);

        int sx = searchX(), sy = searchY(), sw = Theme.SEARCH_W, sh = 22;
        boolean hov = Widget.hit(mx, my, sx, sy, sw, sh);
        glassPanel(sx, sy, sw, sh, 8, searchFocused || hov ? Theme.GLASS_CARD + 0x10000000 : Theme.GLASS_CARD, searchFocused ? 1.4f : 1f, 0);
        d.icon(Icons.SEARCH, sx + 8, sy + (sh - 8) / 2, 8, Theme.TEXT_MUTED);
        int ty = Draw.textY(sy, sh);
        if (!search.isEmpty()) d.text(searchView, sx + 21, ty, Theme.TEXT);
        else d.text("Поиск модулей…", sx + 21, ty, Theme.TEXT_MUTED);
        if (searchFocused && (now / 500_000_000L & 1) == 0) {
            d.rect(sx + 21 + (search.isEmpty() ? 0 : d.width(searchView)) + 1, sy + 7, 1, 8, Theme.TEXT);
        }
        d.hLine(cX() + 12, wy + Theme.TOPBAR_H, cW() - 24, Colors.withAlpha(Theme.isLight() ? 0x000000 : 0xFFFFFF, 0x14));
    }

    // ── карточки модулей ─────────────────────────────────────

    private void drawContent(double mx, double my, float dt) {
        int ax = cX() + 1, ay = areaY(), aw = cW() - 2, ah = areaH();
        if (configsMode()) {
            int lw = 168;
            profiles.renderSidebar(d, mx, my, dt, areaX(), ay + 6, lw, ah - 12);
            d.vLine(areaX() + lw + 6, ay + 6, ah - 12, Theme.DIVIDER);
            profiles.renderContent(d, mx, my, dt, areaX() + lw + 8, ay, cX() + cW() - (areaX() + lw + 8), ah);
            return;
        }
        if (previewMode()) {
            aw -= previewSpace();
            drawPreview(mx, my, dt);
        }
        if (cards.isEmpty()) {
            String t = favorites ? "Нажмите звезду на модуле — он появится здесь." : search.isEmpty() ? category.emptyText : "Ничего не найдено";
            d.textCenter(t, ax + aw / 2, ay + ah / 2 - 4, Theme.TEXT_MUTED);
            return;
        }
        boolean inArea = Widget.hit(mx, my, ax, ay, aw, ah);
        int scroll = Math.round(contentScroll);
        double cmy = inArea ? my + scroll : -1e6;
        double cmx = inArea ? mx : -1e6;

        d.clip(ax, ay, aw, ah);
        d.g.pose().pushMatrix();
        d.g.pose().translate(0, -scroll);
        int top = ay + scroll, bottom = ay + ah + scroll;
        for (int i = 0; i < cards.size(); i++) {
            Card c = cards.get(i);
            if (c.y > bottom || c.y + c.h < top) continue;
            // карточки въезжают снизу по очереди при смене вкладки
            float ci = Math.max(0f, Math.min(1f, contentT * 1.8f - Math.min(i, 12) * 0.07f));
            float off = (1f - ease(ci)) * 18f;
            if (off > 0.05f) {
                d.g.pose().pushMatrix();
                d.g.pose().translate(0, off);
                drawCard(c, cmx, cmy - off, dt);
                d.g.pose().popMatrix();
            } else drawCard(c, cmx, cmy, dt);
        }
        d.g.pose().popMatrix();
        d.unclip();

        if (contentMax > 0) {
            int trackH = ah - 8;
            int thumbH = Math.max(18, trackH * ah / (ah + contentMax));
            int thumbY = ay + 4 + Math.round((trackH - thumbH) * (contentScroll / contentMax));
            d.round(cX() + cW() - 6 - previewSpace(), thumbY, 3, thumbH, 1, Theme.ELEVATED);
        }

    }

    /** Панель превью: стекло, подпись, модель (крутится мышкой). Во время анимации окна модель не рисуем — она вне масштаба окна. */
    private void drawPreview(double mx, double my, float dt) {
        int x = previewX(), y = previewY(), w = PREVIEW_W, h = previewH();
        glassPanel(x, y, w, h, CARD_R + 2, Theme.GLASS_CARD, 1.1f, 0);
        d.textScaled("ПРЕВЬЮ", Fonts.Kind.HEAD, x + 10, y + 9, 0.72f, Theme.TEXT_MUTED);
        boolean steady = openT >= 1f && !closing;
        if (!preview.render(d.g, k, x + 4, y + 18, w - 8, h - 36, steady, dt)) {
            d.textCenter("Зайдите в мир —", x + w / 2, y + h / 2 - 10, Theme.TEXT_MUTED);
            d.textCenter("здесь будет ваша модель", x + w / 2, y + h / 2 + 2, Theme.TEXT_MUTED);
        }
        boolean hov = Widget.hit(mx, my, x, y, w, h);
        d.textCenter(previewDrag ? "Отпустите" : hov ? "Тяните, чтобы повернуть" : "Мечи, крылья, питомец",
                x + w / 2, y + h - 14, Theme.TEXT_MUTED);
    }

    private int toggleX(Card c) { return c.x + c.w - 12 - Theme.SWITCH_W - 4; }
    private int starX(Card c) { return toggleX(c) - 18; }

    private void drawCard(Card c, double mx, double my, float dt) {
        Module m = c.m;
        boolean hovHead = Widget.hit(mx, my, c.x, c.y, c.w, CARD_HEAD);
        c.hover = Anim.to(c.hover, hovHead || Widget.hit(mx, my, c.x, c.y, c.w, c.h) ? 1 : 0, dt);
        c.tog = Anim.to(c.tog, m.isEnabled() ? 1 : 0, dt);
        boolean fav = favs.contains(m.id);
        c.star = Anim.to(c.star, fav ? 1 : hovHead ? 0.6f : 0, dt);

        int tint = Theme.GLASS_CARD;
        if (c.hover > 0.01f) tint = Colors.lerp(tint, Theme.isLight() ? 0xCCFFFFFF : 0x8C1C1C20, c.hover * 0.8f);
        glassPanel(c.x, c.y, c.w, c.h, CARD_R + 2, tint, c.open ? 1.5f : 0.9f + 0.4f * c.hover, -Math.round(contentScroll));

        // иконка
        int tile = TILE, tx = c.x + 9, ty = c.y + (CARD_HEAD - tile) / 2;
        boolean on = m.isEnabled() && !m.alwaysOn();
        if (on) d.glass(tx, ty, tile, tile, 7, Theme.TEXT, 0.6f);
        else d.glass(tx, ty, tile, tile, 7, Theme.isLight() ? 0x14000000 : 0x14FFFFFF, 0.8f);
        int iconColor = on ? (Theme.isLight() ? 0xFFFAFAFA : 0xFF0A0A0A) : Theme.TEXT_DIM;
        d.icon(Icons.forModule(m.id, Icons.forCategory(m.category)), tx + 5, ty + 5, 10, iconColor);

        d.text(m.name, Fonts.Kind.BOLD, c.x + 37, c.y + 9, Theme.TEXT);
        d.text(c.desc, c.x + 37, c.y + 21, Theme.TEXT_MUTED);

        // справа: клавиша, звезда, тумблер
        if (!m.alwaysOn()) d.toggle(toggleX(c), c.y + (CARD_HEAD - Theme.SWITCH_H) / 2, c.tog);
        int sx = starX(c);
        if (c.star > 0.02f) {
            int sc = fav ? Theme.TEXT : Colors.mulAlpha(Theme.TEXT_MUTED, c.star);
            d.icon(Icons.STAR, sx, c.y + (CARD_HEAD - 9) / 2, 9, sc);
        }
        if (!m.alwaysOn() && m.bind.isBound()) {
            String b = m.bind.display();
            int bw = d.width(b) + 10;
            int bx = sx - 6 - bw;
            d.round(bx, c.y + (CARD_HEAD - 14) / 2, bw, 14, 5, Theme.isLight() ? 0x14000000 : 0x1AFFFFFF);
            d.textCenter(b, bx + bw / 2, Draw.textY(c.y + (CARD_HEAD - 14) / 2, 14), Theme.TEXT_DIM);
        }

        // настройки внутри
        if (c.openT > 0.001f && !c.rows.isEmpty()) {
            float a = ease(c.openT);
            d.hLine(c.x + 10, c.y + CARD_HEAD, c.w - 20, Colors.withAlpha(Theme.isLight() ? 0x000000 : 0xFFFFFF, Math.round(0x16 * a)));
            int limit = c.y + c.h - 4;
            for (int i = 0; i < c.rows.size(); i++) {
                int ry = c.rowY.get(i);
                String grp = c.groups.get(i);
                if (grp != null && ry - 12 + 8 <= limit) {
                    d.textScaled(grp, Fonts.Kind.HEAD, c.x + 12, ry - 12, 0.72f, Theme.TEXT_MUTED);
                }
                if (ry + Theme.ROW_H > limit + 2) break;
                c.rows.get(i).render(d, mx, my, dt);
            }
        }
    }

    // ── подсказки снизу ──────────────────────────────────────

    private void drawHints() {
        int y = wy + wh - HINT_H;
        glassPanel(cX() + 6, y - 2, cW() - 12, HINT_H - 4, 7, Theme.GLASS_PANEL, 0.7f, 0);
        int ty = Draw.textY(y - 2, HINT_H - 4);
        int x = cX() + 14;
        String[][] hints = configsMode()
                ? new String[][]{{"ЛКМ", "выбрать"}, {"2×ЛКМ", "переименовать"}, {"Перетащите файл", "импорт"}, {"Esc", "закрыть"}}
                : new String[][]{{"ЛКМ", "настройки"}, {"ПКМ", "вкл/выкл"}, {"СКМ", "в избранное"}, {"Печатайте", "поиск"}, {"Esc", "закрыть"}};
        for (String[] h : hints) {
            int kw = d.width(h[0]) + 8;
            d.round(x, y + 1, kw, HINT_H - 8, 4, Theme.isLight() ? 0x14000000 : 0x1AFFFFFF);
            d.textCenter(h[0], x + kw / 2, ty, Theme.TEXT_DIM);
            d.text(h[1], x + kw + 5, ty, Theme.TEXT_MUTED);
            x += kw + 5 + d.width(h[1]) + 14;
        }
        int on = 0;
        for (Module m : mm.all()) if (m.isEnabled() && !m.alwaysOn()) on++;
        if (!Fonts.crispError.isEmpty()) {
            d.textRight(Draw.ellipsize("Сглаживание текста не запустилось: " + Fonts.crispError, 260), cX() + cW() - 16, ty, 0xFFFF8A8A);
        } else {
            d.textRight("Включено: " + on, cX() + cW() - 16, ty, Theme.TEXT_MUTED);
        }
    }

    /** Аватар игрока: лицо со скина, в меню — буква ника. */
    private void drawAvatar(int x, int y, int size) {
        Identifier skin = null;
        try {
            if (minecraft.player != null) skin = minecraft.player.getSkin().body().texturePath();
        } catch (Throwable ignored) {
        }
        if (skin != null) {
            d.g.blit(RenderPipelines.GUI_TEXTURED, skin, x, y, 8f, 8f, size, size, 8, 8, 64, 64, 0xFFFFFFFF);
            d.g.blit(RenderPipelines.GUI_TEXTURED, skin, x, y, 40f, 8f, size, size, 8, 8, 64, 64, 0xFFFFFFFF);
        } else {
            String name = minecraft.getUser().getName();
            d.round(x, y, size, size, 6, Theme.ELEVATED);
            d.textCenter(name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(Locale.ROOT), x + size / 2, Draw.textY(y, size), Theme.TEXT);
        }
    }

    // ── ввод ─────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        double mx = e.x() / k, my = e.y() / k;
        int b = e.button();

        if (popup != null) {
            if (popup.mouseClicked(mx, my, b) && !popup.isClosed()) active = popup;
            else popup = null;
            return true;
        }
        if (listening != null) {
            listening.cancel();
            listening = null;
        }
        if (!Widget.hit(mx, my, wx, wy, ww, wh)) {
            onClose();
            return true;
        }

        // боковая панель
        if (mx < wx + Theme.SIDEBAR_W) {
            searchFocused = false;
            if (Widget.hit(mx, my, themeX(), themeY(), 18, 18)) {
                cs.theme.set(cs.theme.getId().equals("light") ? 0 : 1);
                return true;
            }
            for (Nav n : nav) {
                if (!Widget.hit(mx, my, wx + 8, n.y, Theme.SIDEBAR_W - 16, NAV_H)) continue;
                switch (n.kind) {
                    case CATEGORY -> selectCategory(n.category);
                    case FAVORITES -> showFavorites();
                    case FRIENDS -> reveal(mm.find("friends"));
                    case WALLPAPER -> minecraft.setScreen(new dev.mraz1visual.ui.menu.WallpaperScreen(this));
                }
                return true;
            }
            return true;
        }

        if (Widget.hit(mx, my, searchX(), searchY(), Theme.SEARCH_W, 22)) {
            searchFocused = true;
            return true;
        }
        searchFocused = false;
        if (my < wy + Theme.TOPBAR_H || my > wy + wh - HINT_H) return true;

        int ay = areaY(), ah = areaH();
        if (configsMode()) {
            if (b != 0) return true;
            int lw = 168;
            if (Widget.hit(mx, my, areaX(), ay + 6, lw, ah - 12)) profiles.clickSidebar(mx, my, areaX(), ay + 6, lw, doubleClick);
            else profiles.clickContent(mx, my, areaX() + lw + 8, ay);
            return true;
        }
        if (previewMode() && Widget.hit(mx, my, previewX(), previewY(), PREVIEW_W, previewH())) {
            if (b == 0) {
                previewDrag = true;
                preview.dragStart(mx);
            }
            return true;
        }
        if (!Widget.hit(mx, my, cX(), ay, cW(), ah)) return true;
        double cmy = my + contentScroll;
        for (Card c : cards) {
            if (!Widget.hit(mx, cmy, c.x, c.y, c.w, c.h)) continue;
            Module m = c.m;
            if (cmy < c.y + CARD_HEAD) {
                if (b == 2) {
                    toggleFavorite(m);
                } else if (b == 1) {
                    if (!m.alwaysOn()) m.toggle();
                } else if (b == 0) {
                    if (!m.alwaysOn() && mx >= toggleX(c) - 4) m.toggle();
                    else if (Widget.hit(mx, cmy, starX(c) - 3, c.y + 8, 15, CARD_HEAD - 16)) toggleFavorite(m);
                    else c.open = !c.open;
                }
                rebuildSubtitle();
                return true;
            }
            for (SettingWidget w : c.rows) {
                if (w.mouseClicked(mx, cmy, b)) {
                    active = w;
                    return true;
                }
            }
            return true;
        }
        return true;
    }

    private void rebuildSubtitle() {
        int on = 0;
        for (Module m : list) if (m.isEnabled() && !m.alwaysOn()) on++;
        subtitle = list.size() + " " + plural(list.size(), "модуль", "модуля", "модулей") + (on > 0 ? " · " + on + " включено" : "");
    }

    private double activeY(double my) {
        return active == popup ? my : my + contentScroll;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        if (previewDrag) {
            previewDrag = false;
            preview.dragEnd();
        }
        if (active != null) {
            active.mouseReleased(e.x() / k, activeY(e.y() / k));
            active = null;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dragX, double dragY) {
        if (previewDrag) preview.drag(e.x() / k);
        if (active != null) active.mouseDragged(e.x() / k, activeY(e.y() / k));
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (popup != null) return true;
        double mx = mouseX / k, my = mouseY / k;
        if (configsMode()) {
            return true;
        }
        if (Widget.hit(mx, my, cX(), areaY(), cW(), areaH())) {
            contentTarget = clamp((float) (contentTarget - scrollY * 32), 0, contentMax);
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        int key = e.key();
        if (listening != null) {
            listening.onKey(key);
            listening = null;
            return true;
        }
        if (popup != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE) popup = null;
            return true;
        }
        if (configsMode() && profiles.isTyping()) {
            boolean ctrl = (e.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0;
            return profiles.keyPressed(key, ctrl, minecraft.keyboardHandler::getClipboard);
        }
        if (searchFocused) {
            switch (key) {
                case GLFW.GLFW_KEY_ESCAPE -> {
                    if (!search.isEmpty()) setSearch("");
                    else searchFocused = false;
                }
                case GLFW.GLFW_KEY_BACKSPACE -> {
                    int len = search.length();
                    if (len > 0) {
                        int cp = search.codePointBefore(len);
                        search.setLength(len - Character.charCount(cp));
                        onSearchChanged();
                    }
                }
                case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                    if (!cards.isEmpty()) cards.get(0).open = true;
                    searchFocused = false;
                }
                default -> {
                    return false;
                }
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE || key == cs.menuKey.get()) {
            onClose();
            return true;
        }
        return false;
    }

    /** Печать в любом месте окна сразу ищет по модулям. */
    @Override
    public boolean charTyped(CharacterEvent e) {
        if (listening != null || popup != null) return true;
        int cp = e.codepoint();
        if (configsMode() && profiles.isTyping()) return profiles.charTyped(cp);
        if (Character.isISOControl(cp)) return false;
        if (search.length() >= SEARCH_MAX) return true;
        searchFocused = true;
        search.appendCodePoint(cp);
        onSearchChanged();
        return true;
    }

    /** Перетащили файлы в окно — пробуем импортировать как профили. */
    @Override
    public void onFilesDrop(List<Path> paths) {
        if (category != Category.CONFIGS || favorites) selectCategory(Category.CONFIGS);
        profiles.filesDropped(paths);
    }

    private void setSearch(String s) {
        search.setLength(0);
        search.append(s);
        onSearchChanged();
    }

    private void onSearchChanged() {
        searchView = search.toString();
        contentScroll = contentTarget = 0;
        rebuildList();
    }

    private static float clamp(float v, float min, float max) {
        return v < min ? min : Math.min(v, max);
    }
}

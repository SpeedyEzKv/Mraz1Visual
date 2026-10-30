package dev.mraz1visual.ui;

import com.mojang.blaze3d.platform.InputConstants;
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
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Меню клиента v2: окно по центру поверх размытого мира. Сверху — вкладки с иконками и стеклянной «каплей»,
 * которая перетекает к выбранной; справа — поиск, настройки, друзья, уведомления и аватар.
 * Слева — список модулей вкладки, справа — большой баннер с живым превью (параллакс, «● ПРЕДПРОСМОТР»,
 * клавиша и главный переключатель), под ним — карточки настроек с сегментами и ползунками.
 * Маркет, Форум и Конфиги — свои панели в той же области.
 */
public final class ClientScreen extends Screen implements Host {
    private static final int TOP_H = 48, LIST_W = 206, ROW = 28, HEAD = 18, PAD = 12, GAP = 10;
    private static final int SEARCH_W = 150, SEARCH_MAX = 32, CARD_R = 14;

    /** Вкладки верхней панели. Порядок здесь = порядок в окне. */
    enum Tab {
        PLAYER("Player", Icons.PLAYER, Category.PLAYER),
        PVP("PvP", Icons.SWORD, Category.PVP),
        HUD("HUD", Icons.HUD, Category.HUD),
        VISUALS("Visuals", Icons.EYE, Category.VISUALS),
        WORLD("World", Icons.CLOUD, Category.SKY),
        COMBAT("Combat FX", Icons.STAR, Category.COMBAT),
        SHADERS("Shaders", Icons.SUN, Category.SHADERS),
        MISC("Misc", Icons.GRID, Category.MISC, Category.PERFORMANCE),
        STAFF("Staff", Icons.SHIELD, Category.STAFF),
        COSMETICS("Cosmetics", Icons.HAT, Category.COSMETICS, Category.SWORDS, Category.WINGS, Category.PETS),
        MARKET("Market", Icons.GEM),
        MODS("Mods", Icons.LAYERS),
        FORUM("Forum", Icons.CHAT),
        MUSIC("Music", Icons.NOTE, Category.MUSIC),
        CONFIGS("Configs", Icons.SLIDERS),
        /** Настройки клиента — открываются шестерёнкой справа, в полосе вкладок их нет. */
        CLIENT("Настройки", Icons.GEAR, Category.CLIENT);

        final String label;
        final int icon;
        final Category[] cats;

        Tab(String label, int icon, Category... cats) {
            this.label = label;
            this.icon = icon;
            this.cats = cats;
        }

        /** Подпись в боковой панели — по-русски. */
        String sideLabel() {
            return switch (this) {
                case PLAYER -> "Игрок";
                case VISUALS -> "Визуалы";
                case WORLD -> "Мир";
                case COMBAT -> "Боевые эффекты";
                case SHADERS -> "Шейдеры";
                case MISC -> "FPS и разное";
                case COSMETICS -> "Косметика";
                case MARKET -> "Маркет";
                case MODS -> Lang.get("mraz1visual.mods.title");
                case FORUM -> "Форум";
                case MUSIC -> "Музыка";
                case CONFIGS -> "Конфиги";
                default -> label;
            };
        }

        static final Tab[] BAR = {PLAYER, PVP, HUD, VISUALS, WORLD, COMBAT, SHADERS, MISC, STAFF, COSMETICS, MARKET, MODS, FORUM, MUSIC, CONFIGS};
    }

    private final ModuleManager mm = Mraz1Visual.MODULES;
    private final ClientSettings cs = Modules.client;
    private final Draw d = new Draw();

    // геометрия (единицы окна)
    private float k = 1f;
    private int vw, vh, wx, wy, ww, wh;
    private int layoutW = -1, layoutH = -1, layoutSize = -1;
    /** Вкладки: целевые и текущие (анимированные) позиции; без места под подписи подпись есть только у активной. */
    private final int[] tabX = new int[Tab.BAR.length], tabW = new int[Tab.BAR.length];
    private final float[] tabAX = new float[Tab.BAR.length], tabAW = new float[Tab.BAR.length];
    private int tabsX, tabsW, tabsLeft, tabsRight;
    private boolean tabLabels = true, tabSnap = true;

    // состояние
    private Tab tab = Tab.VISUALS;
    private Module sel;
    private final Map<Tab, Module> lastSel = new EnumMap<>(Tab.class);
    /** Строки списка: String — заголовок секции, Module — модуль. */
    private final List<Object> rows = new ArrayList<>();
    private final int[] rowY = new int[512];
    private float listScroll, listTarget;
    private int listMax;

    private final StringBuilder search = new StringBuilder();
    private String searchView = "";
    private boolean searchFocused;

    // детали модуля: карточки настроек по группам
    private static final class Group {
        final String title;
        final List<SettingWidget> rows = new ArrayList<>();
        int x, y, w, h;

        Group(String title) {
            this.title = title;
        }
    }

    private final List<Group> groups = new ArrayList<>();
    private BindWidget bindChip;
    private float detailScroll, detailTarget;
    private int detailMax;

    private final PreviewScene scene = new PreviewScene();
    private final ProfilesPanel profiles = new ProfilesPanel();
    private final Map<Tab, Panel> panels = new EnumMap<>(Tab.class);

    private Popup popup;
    private BindWidget listening;
    private Widget active;
    private boolean bellOpen;

    // анимации
    private long lastNanos;
    private float openT, contentT = 1f;
    private boolean closing;
    private final Spring dropX = Spring.ui(-1), dropW = Spring.ui(0), listDrop = Spring.ui(-1), sideDrop = Spring.ui(-1);
    private static final int SIDE_W = 128;
    private float togT, bannerHover, searchGlow;
    private final float[] tabHover = new float[Tab.BAR.length];
    private final float[] iconHover = new float[4];

    private Screen parent;
    /** Вкладка Cosmetics: гардероб или список модулей косметики. */
    private final WardrobePanel wardrobe = new WardrobePanel(() -> cosmeticsModules = true);
    private boolean cosmeticsModules;

    private Panel panel() {
        if (tab == Tab.COSMETICS && cosmeticsModules) return null;
        return panels.get(tab);
    }

    public ClientScreen() {
        super(Component.literal("Mraz1Visual"));
        panels.put(Tab.MARKET, new MarketPanel());
        panels.put(Tab.MODS, new ModsPanel());
        panels.put(Tab.COSMETICS, wardrobe);
        panels.put(Tab.FORUM, new ForumPanel());
    }

    public void setParent(Screen parent) {
        this.parent = parent;
    }

    /** Подменить панель особой вкладки (Маркет, Форум). */
    public void setPanel(String tabName, Panel p) {
        panels.put(Tab.valueOf(tabName), p);
    }

    // ── жизненный цикл ───────────────────────────────────────

    private boolean restored;

    @Override
    protected void init() {
        d.font = this.font;
        Lang.setLanguage(cs.language.getId());
        if (!restored) {
            restored = true;
            restore();
        }
        openT = cs.animations.get() ? 0f : 1f;
        closing = false;
        profiles.refresh();
        relayout();
    }

    private void restore() {
        Tab t = Tab.VISUALS;
        try {
            if (!Mraz1Visual.CONFIG.uiCategory.isEmpty()) t = Tab.valueOf(Mraz1Visual.CONFIG.uiCategory);
        } catch (IllegalArgumentException ignored) {
            // старое значение (категория прошлого меню) — открываем «Visuals»
        }
        selectTab(t);
        Module m = mm.find(Mraz1Visual.CONFIG.uiModule);
        if (m != null && rows.contains(m)) select(m);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        if (cs.animations.get() && !closing && openT > 0.05f) {
            closing = true;
            popup = null;
            return;
        }
        finishClose();
    }

    private void finishClose() {
        wardrobe.left();
        closing = false;
        popup = null;
        if (listening != null) listening.cancel();
        listening = null;
        searchFocused = false;
        String t = tab.name(), mod = sel == null ? "" : sel.id;
        if (!t.equals(Mraz1Visual.CONFIG.uiCategory) || !mod.equals(Mraz1Visual.CONFIG.uiModule)) {
            Mraz1Visual.CONFIG.uiCategory = t;
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
        int prefW = 860, prefH = 500;
        double fit = Math.min(pw / (double) (prefW + 24), ph / (double) (prefH + 24));
        double unit = Math.max(0.6, Math.min(base, fit));
        k = (float) (unit / gui);
        d.scale = k;
        vw = (int) (width / k);
        vh = (int) (height / k);
        ww = Math.min(prefW, vw - 16);
        wh = Math.min(prefH, vh - 16);
        wx = (vw - ww) / 2;
        wy = (vh - wh) / 2;

        // вкладки: по центру между логотипом и правым блоком
        tabsLeft = wx + 118;
        tabsRight = wx + ww - (SEARCH_W + 4 * 26 + 24);
        int full = 0;
        for (Tab t : Tab.BAR) full += 22 + d.width(t.label, Fonts.Kind.BOLD) + 14;
        tabLabels = full <= tabsRight - tabsLeft;
        tabSnap = true;
        layoutTabs();
        rebuild();
    }

    /** Ширины вкладок: все с подписями, или иконки + подпись у активной. */
    private void layoutTabs() {
        int x = 0;
        for (int i = 0; i < Tab.BAR.length; i++) {
            Tab t = Tab.BAR[i];
            boolean label = tabLabels || t == tab;
            int w = label ? 22 + d.width(t.label, Fonts.Kind.BOLD) + 14 : 30;
            tabX[i] = x;
            tabW[i] = w;
            x += w;
        }
        tabsW = x + 8;
        tabsX = tabsLeft + Math.max(0, (tabsRight - tabsLeft - tabsW) / 2);
        for (int i = 0; i < Tab.BAR.length; i++) {
            tabX[i] += tabsX + 4;
            if (tabSnap) {
                tabAX[i] = tabX[i];
                tabAW[i] = tabW[i];
            }
        }
        tabSnap = false;
    }

    private int bodyY() { return wy + TOP_H + 6; }
    private int bodyH() { return wh - TOP_H - 6 - PAD; }
    private int listX() { return contentX(); }
    /** Левый край содержимого: правее боковой панели вкладок, если она включена. */
    private int contentX() { return wx + PAD + (side() ? SIDE_W + GAP : 0); }
    private int contentW() { return wx + ww - PAD - contentX(); }
    private boolean side() { return cs.tabsLayout.getId().equals("side"); }
    private int sideRow() { return Math.max(18, Math.min(28, (bodyH() - 16) / Tab.BAR.length)); }

    private int sideIndex(double my) {
        int i = (int) Math.floor((my - bodyY() - 8) / sideRow());
        return i >= 0 && i < Tab.BAR.length ? i : -1;
    }
    private int detailX() { return listX() + LIST_W + GAP; }
    private int detailW() { return wx + ww - PAD - detailX(); }
    private int bannerH() { return Math.max(118, Math.min(196, Math.round(bodyH() * 0.42f))); }
    private int cardsY() { return bodyY() + bannerH() + GAP; }
    private int cardsH() { return bodyY() + bodyH() - cardsY(); }
    private int searchX() { return wx + ww - 14 - 4 * 26 - SEARCH_W - 6; }
    private int searchY() { return wy + (TOP_H - 24) / 2; }
    private int iconX(int i) { return wx + ww - 14 - (4 - i) * 26; }

    // ── выбор вкладки и модуля ───────────────────────────────

    private void selectTab(Tab t) {
        // повторный клик по Cosmetics — обратно в гардероб
        if (t == Tab.COSMETICS) cosmeticsModules = false;
        if (tab == Tab.COSMETICS && t != Tab.COSMETICS) wardrobe.left();
        if (tab != t && sel != null) lastSel.put(tab, sel);
        tab = t;
        if (!tabLabels) layoutTabs();
        clearSearch();
        listScroll = listTarget = 0;
        contentT = cs.animations.get() ? 0f : 1f;
        rebuild();
        Module m = lastSel.get(t);
        if (m == null || !rows.contains(m)) m = firstModule();
        select(m);
        if (t == Tab.CONFIGS) profiles.refresh();
        Panel p = panels.get(t);
        if (p != null) p.opened();
    }

    private Module firstModule() {
        for (Object o : rows) if (o instanceof Module m) return m;
        return null;
    }

    private void select(Module m) {
        if (m == sel && !groups.isEmpty()) return;
        sel = m;
        detailScroll = detailTarget = 0;
        togT = m != null && m.isEnabled() ? 1 : 0;
        scene.restart();
        buildGroups();
    }

    /** Открыть модуль: перейти на его вкладку и выбрать. */
    private void reveal(Module m) {
        if (m == null) return;
        for (Tab t : Tab.values()) {
            for (Category c : t.cats) {
                if (c == m.category) {
                    selectTab(t);
                    if (t == Tab.COSMETICS) cosmeticsModules = true;
                    select(m);
                    scrollListTo(m);
                    return;
                }
            }
        }
    }

    private void rebuild() {
        rows.clear();
        if (!search.isEmpty()) {
            String q = searchView.toLowerCase(Locale.ROOT);
            rows.add("Поиск");
            for (Module m : mm.all()) {
                if (m.category == Category.CONFIGS) continue;
                boolean hit = m.name.toLowerCase(Locale.ROOT).contains(q) || m.description.toLowerCase(Locale.ROOT).contains(q);
                if (!hit) for (Setting<?> s : m.settings()) if (s.name.toLowerCase(Locale.ROOT).contains(q)) { hit = true; break; }
                if (hit) rows.add(m);
            }
        } else if (tab == Tab.VISUALS) {
            // визуалы — секциями: Бой, Игрок, Мир, HUD, Небо
            Map<String, List<Module>> secs = new LinkedHashMap<>();
            for (String[] s : dev.mraz1visual.module.VisualPresets.SECTIONS) if (!s[0].equals("all")) secs.put(s[1], new ArrayList<>());
            secs.put(Category.SKY.title, new ArrayList<>());
            for (Module m : mm.in(Category.VISUALS)) {
                String id = dev.mraz1visual.module.VisualPresets.section(m);
                String title = "HUD";
                for (String[] s : dev.mraz1visual.module.VisualPresets.SECTIONS) if (s[0].equals(id) && !id.equals("all")) title = s[1];
                secs.get(title).add(m);
            }
            secs.get(Category.SKY.title).addAll(mm.in(Category.SKY));
            for (var e : secs.entrySet()) {
                if (e.getValue().isEmpty()) continue;
                rows.add(e.getKey());
                rows.addAll(e.getValue());
            }
        } else {
            boolean many = tab.cats.length > 1;
            for (Category c : tab.cats) {
                List<Module> ms = mm.in(c);
                if (ms.isEmpty()) continue;
                if (many) rows.add(c.title);
                rows.addAll(ms);
            }
        }
        layoutList();
    }

    private static final String[] PVP_WARN = Lang.lines("mraz1visual.pvp.warning");

    /** Высота плашки-предупреждения над списком (только вкладка PvP без поиска). */
    private int warnH() {
        return tab == Tab.PVP && search.isEmpty() ? PVP_WARN.length * 9 + 14 : 0;
    }

    private void layoutList() {
        int y = 0;
        for (int i = 0; i < rows.size() && i < rowY.length; i++) {
            rowY[i] = y;
            y += rows.get(i) instanceof String ? HEAD : ROW;
        }
        int listH = bodyH() - 30 - warnH();
        listMax = Math.max(0, y - listH + 6);
        listTarget = Math.min(listTarget, listMax);
    }

    private void scrollListTo(Module m) {
        int i = rows.indexOf(m);
        if (i < 0 || i >= rowY.length) return;
        int listH = bodyH() - 30 - warnH();
        if (rowY[i] < listTarget) listTarget = rowY[i];
        else if (rowY[i] + ROW > listTarget + listH) listTarget = Math.min(listMax, rowY[i] + ROW - listH);
    }

    /** Карточки настроек выбранного модуля: по группам, бинд — в баннере. */
    private void buildGroups() {
        groups.clear();
        bindChip = null;
        if (sel == null) return;
        if (!sel.alwaysOn()) bindChip = new BindWidget(sel.bind, this, false);
        Group g = null;
        for (Setting<?> s : sel.settings()) {
            String title = s.group() == null || s.group().isEmpty() ? "Основное" : s.group();
            if (g == null || !g.title.equals(title)) {
                g = null;
                for (Group e : groups) if (e.title.equals(title)) g = e;
                if (g == null) groups.add(g = new Group(title));
            }
            g.rows.add(widgetFor(s));
        }
        layoutGroups();
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

    /** Карточки — «кирпичной кладкой» в две колонки (одна на узком окне). */
    private void layoutGroups() {
        int x0 = detailX(), y0 = cardsY(), w = detailW();
        int cols = groups.size() > 1 && w >= 2 * 230 + GAP ? 2 : 1;
        int colW = (w - (cols - 1) * GAP) / cols;
        int[] colY = new int[cols];
        for (int i = 0; i < cols; i++) colY[i] = y0;
        for (Group g : groups) {
            int col = 0;
            for (int i = 1; i < cols; i++) if (colY[i] < colY[col]) col = i;
            g.x = x0 + col * (colW + GAP);
            g.y = colY[col];
            g.w = colW;
            int y = g.y + 26;
            for (SettingWidget r : g.rows) {
                r.setBounds(g.x + 12, y, g.w - 24, Theme.ROW_H);
                y += Theme.ROW_H;
            }
            g.h = y - g.y + 8;
            colY[col] += g.h + GAP;
        }
        int bottom = y0;
        for (int v : colY) bottom = Math.max(bottom, v - GAP);
        detailMax = Math.max(0, bottom - (cardsY() + cardsH()));
        detailTarget = Math.min(detailTarget, detailMax);
    }

    private void clearSearch() {
        if (!search.isEmpty()) {
            search.setLength(0);
            searchView = "";
        }
        searchFocused = false;
    }

    private void onSearchChanged() {
        searchView = search.toString();
        listScroll = listTarget = 0;
        rebuild();
        if (!search.isEmpty() || sel == null || !rows.contains(sel)) {
            Module m = firstModule();
            if (m != null) select(m);
        }
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
        return w == bindChip ? 0 : detailScroll;
    }

    // ── рисование ────────────────────────────────────────────

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        String mode = cs.background.getId();
        if (mode.equals("none")) return;
        if (mode.equals("blur")) {
            if (minecraft.level != null && GlassBlur.update(4)) GlassBlur.draw(g, 0, 0, width, height, 0xFFFFFFFF);
            else super.renderBackground(g, mouseX, mouseY, partialTick);
        }
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

    private boolean wallpaperOn;

    private void renderWindow(GuiGraphics g, int mouseX, int mouseY) {
        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 1f / 60 : Math.min(0.1f, (now - lastNanos) / 1e9f);
        float animDt = lastNanos == 0 ? 0f : dt;
        lastNanos = now;
        Anim.enabled = cs.animations.get();
        Anim.reduceMotion = cs.reduceMotion.get();
        if (width != layoutW || height != layoutH || cs.size.get() != layoutSize) relayout();

        double rmx = mouseX / k, rmy = mouseY / k;
        boolean overPopup = popup != null && popup.hit(rmx, rmy);
        double mx = overPopup ? -1e6 : rmx, my = overPopup ? -1e6 : rmy;

        if (closing) {
            openT = Math.max(0f, openT - animDt / 0.16f);
            if (openT <= 0f) {
                finishClose();
                return;
            }
        } else if (openT < 1f) openT = Math.min(1f, openT + animDt / 0.32f);
        if (contentT < 1f) contentT = Math.min(1f, contentT + animDt / 0.34f);
        listScroll = Anim.to(listScroll, listTarget, dt);
        detailScroll = Anim.to(detailScroll, detailTarget, dt);
        layoutGroups();

        float e = ease(openT);
        float s = Anim.reduceMotion ? 1f : closing ? 0.96f + 0.04f * e : 0.96f + 0.04f * easeBack(openT);
        boolean scaled = s < 0.999f || s > 1.001f;
        if (scaled) {
            float cx = (wx + ww / 2f) * k, cy = (wy + wh / 2f) * k;
            g.pose().pushMatrix();
            g.pose().translate(cx, cy);
            g.pose().scale(s, s);
            g.pose().translate(-cx, -cy);
        }
        d.begin(g, font, k);
        // окно: мягкая тень, обои или стекло поверх размытого мира
        d.shadow(wx, wy + 8, ww, wh, 22, 34f, Theme.SHADOW);
        wallpaperOn = cs.wallGui.get() && dev.mraz1visual.wallpaper.WallpaperPlayer.render(d.g, wx, wy, ww, wh, 22, Math.max(20, cs.wallDim.get() - 10));
        if (wallpaperOn && Theme.isLight()) d.round(wx, wy, ww, wh, 22, 0x80FFFFFF);
        d.glass(wx, wy, ww, wh, 22, wallpaperOn ? 0x330C0C10 : Theme.GLASS_WINDOW, 1.1f);
        d.clip(wx, wy, ww, wh);
        drawTopBar(mx, my, dt, now);
        Panel panel = panel();
        if (tab == Tab.CONFIGS && search.isEmpty()) drawConfigs(mx, my, dt);
        else if (panel != null && search.isEmpty()) panel.render(d, mx, my, dt, contentX(), bodyY(), contentW(), bodyH());
        else {
            drawList(mx, my, dt);
            drawDetail(mx, my, dt);
        }
        if (e < 1f) d.round(wx, wy, ww, wh, 22, Colors.withAlpha(Theme.isLight() ? 0xF4F4F5 : 0x0A0A0C, Math.round(255 * (1 - e))));
        d.unclip();
        if (bellOpen) drawBell(mx, my);
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

    private static float easeBack(float t) {
        float c1 = 1.4f, c3 = c1 + 1f, x = t - 1f;
        return 1f + c3 * x * x * x + c1 * x * x;
    }

    private static float ease(float t) {
        return 1f - (float) Math.pow(1f - t, 3);
    }

    private void glassPanel(int x, int y, int w, int h, int r, int tint, float strength) {
        if (wallpaperOn) dev.mraz1visual.wallpaper.WallpaperPlayer.glass(d.g, x, y, w, h, r, 0);
        d.glass(x, y, w, h, r, tint, strength);
    }

    // ── шапка: логотип, вкладки, поиск, значки ───────────────

    private void drawTopBar(double mx, double my, float dt, long now) {
        // логотип: стеклянная монетка и название
        int lx = wx + 16, ly = wy + (TOP_H - 26) / 2;
        d.glass(lx, ly, 26, 26, 13, Colors.withAlpha(Theme.accent(), Theme.isLight() ? 0xD0 : 0x44), 1.2f);
        d.textCenter("M", lx + 13, Draw.textY(ly, 26), Theme.isLight() ? 0xFFFFFFFF : Theme.TEXT);
        d.text("Mraz1Visual", Fonts.Kind.BOLD, lx + 32, ly + 4, Theme.TEXT);
        d.text("v2", lx + 32, ly + 15, Theme.TEXT_MUTED);

        if (side()) drawSide(mx, my, dt);
        else drawTabRail(mx, my, dt);
        drawSearchAndIcons(mx, my, dt, now);
    }

    /** Боковая панель вкладок: иконка и подпись, «капля» перетекает к выбранной. */
    private void drawSide(double mx, double my, float dt) {
        int x = wx + PAD, y = bodyY(), w = SIDE_W, h = bodyH(), row = sideRow();
        glassPanel(x, y, w, h, CARD_R, Theme.GLASS_PANEL, 0.9f);
        int ai = -1;
        for (int i = 0; i < Tab.BAR.length; i++) if (Tab.BAR[i] == tab && search.isEmpty()) ai = i;
        if (ai >= 0) {
            float target = y + 8 + ai * row;
            if (sideDrop.value < 0) sideDrop.snap(target);
            sideDrop.target = target;
            float sy = sideDrop.update(dt);
            float stretch = Math.min(10f, Math.abs(sideDrop.velocity) * 0.02f);
            d.glass(x + 6, Math.round(sy - stretch / 2 + 1), w - 12, Math.round(row - 2 + stretch), 10,
                    Colors.withAlpha(Theme.accent(), Theme.isLight() ? 0x60 : 0x3C), 1.35f);
        } else sideDrop.snap(-1);
        for (int i = 0; i < Tab.BAR.length; i++) {
            Tab t = Tab.BAR[i];
            int ry = y + 8 + i * row;
            boolean on = i == ai, hov = Widget.hit(mx, my, x + 6, ry, w - 12, row);
            tabHover[i] = Anim.to(tabHover[i], hov ? 1 : 0, dt);
            if (hov && !on) d.round(x + 6, ry + 1, w - 12, row - 2, 10, Theme.HOVER);
            int c = on ? (Theme.isLight() ? Theme.TEXT : 0xFFFFFFFF) : Colors.lerp(Theme.TEXT_DIM, Theme.TEXT, tabHover[i]);
            d.icon(t.icon, x + 16, ry + (row - 10) / 2, 10, c);
            d.text(Draw.ellipsize(t.sideLabel(), w - 44), on ? Fonts.Kind.BOLD : Fonts.Kind.TEXT, x + 34, Draw.textY(ry, row), c);
        }
    }

    private void drawTabRail(double mx, double my, float dt) {
        // полоса вкладок — стеклянная капсула; вкладки плавно раздвигаются под подпись активной
        int ty = wy + (TOP_H - 30) / 2, th = 30;
        for (int i = 0; i < Tab.BAR.length; i++) {
            tabAX[i] = Anim.to(tabAX[i], tabX[i], dt);
            tabAW[i] = Anim.to(tabAW[i], tabW[i], dt);
        }
        d.glass(tabsX, ty, tabsW, th, th / 2, Theme.GLASS_RAIL, 0.9f);
        int ai = -1;
        for (int i = 0; i < Tab.BAR.length; i++) if (Tab.BAR[i] == tab && search.isEmpty()) ai = i;
        if (ai >= 0) {
            // «капля»: перетекает к выбранной вкладке и вытягивается по ходу движения
            if (dropX.value < 0) {
                dropX.snap(tabX[ai]);
                dropW.snap(tabW[ai]);
            }
            dropX.target = tabX[ai];
            dropW.target = tabW[ai];
            if (!tabLabels) dropW.snap(tabAW[ai]);
            float x = dropX.update(dt), w = dropW.update(dt);
            float stretch = Math.min(18f, Math.abs(dropX.velocity) * 0.03f);
            float squash = Math.min(4f, Math.abs(dropX.velocity) * 0.006f);
            d.glass(Math.round(x - stretch / 2), Math.round(ty + 3 + squash / 2), Math.round(w + stretch), Math.round(th - 6 - squash), (th - 6) / 2,
                    Colors.withAlpha(Theme.accent(), Theme.isLight() ? 0x60 : 0x3C), 1.35f);
        }
        for (int i = 0; i < Tab.BAR.length; i++) {
            Tab t = Tab.BAR[i];
            int tx = Math.round(tabAX[i]), tw = Math.round(tabAW[i]);
            boolean on = i == ai, hov = Widget.hit(mx, my, tx, ty, tw, th);
            tabHover[i] = Anim.to(tabHover[i], hov ? 1 : 0, dt);
            int c = on ? (Theme.isLight() ? Theme.TEXT : 0xFFFFFFFF) : Colors.lerp(Theme.TEXT_DIM, Theme.TEXT, tabHover[i]);
            if (tw > 40) {
                d.icon(t.icon, tx + 11, ty + (th - 9) / 2, 9, c);
                // подпись проявляется, пока вкладка раздвигается
                float a = Math.min(1f, (tw - 40) / Math.max(1f, tabW[i] - 40f));
                if (a > 0.05f) {
                    d.clip(tx, ty, tw - 6, th);
                    d.text(t.label, Fonts.Kind.BOLD, tx + 24, Draw.textY(ty, th), Colors.mulAlpha(c, a));
                    d.unclip();
                }
            } else {
                d.icon(t.icon, tx + (tw - 10) / 2, ty + (th - 10) / 2, 10, c);
            }
        }
    }

    private void drawSearchAndIcons(double mx, double my, float dt, long now) {
        // поиск
        int sx = searchX(), sy = searchY(), sw = SEARCH_W, sh = 24;
        boolean hov = Widget.hit(mx, my, sx, sy, sw, sh);
        searchGlow = Anim.to(searchGlow, searchFocused ? 1 : hov ? 0.4f : 0, dt);
        d.glass(sx, sy, sw, sh, sh / 2, Colors.lerp(Theme.GLASS_CARD, Theme.GLASS_ACTIVE, searchGlow * 0.6f), 1f + 0.4f * searchGlow);
        if (searchGlow > 0.01f) d.rim(sx, sy, sw, sh, sh / 2, Colors.withAlpha(Theme.accent(), Math.round(150 * searchGlow)), Colors.withAlpha(Theme.accent(), Math.round(60 * searchGlow)));
        d.icon(Icons.SEARCH, sx + 10, sy + (sh - 8) / 2, 8, Theme.TEXT_MUTED);
        int tyy = Draw.textY(sy, sh);
        if (!search.isEmpty()) d.text(Draw.ellipsize(searchView, sw - 34), sx + 24, tyy, Theme.TEXT);
        else d.text("Поиск", sx + 24, tyy, Theme.TEXT_MUTED);
        if (searchFocused && (now / 500_000_000L & 1) == 0) d.rect(sx + 24 + (search.isEmpty() ? 0 : Math.min(sw - 34, d.width(searchView))) + 1, sy + 8, 1, 8, Theme.accent());

        // значки: настройки, друзья, уведомления, аватар
        int[] icons = {Icons.GEAR, Icons.FRIENDS, Icons.BELL};
        for (int i = 0; i < 4; i++) {
            int ix = iconX(i), iy = wy + (TOP_H - 22) / 2;
            boolean h = Widget.hit(mx, my, ix, iy, 22, 22);
            iconHover[i] = Anim.to(iconHover[i], h ? 1 : 0, dt);
            boolean on = i == 0 && tab == Tab.CLIENT || i == 2 && bellOpen;
            if (i < 3) {
                if (on || iconHover[i] > 0.01f) d.glass(ix, iy, 22, 22, 11, on ? Colors.withAlpha(Theme.accent(), 0x44) : Colors.mulAlpha(Theme.GLASS_ACTIVE, iconHover[i]), 1f);
                d.icon(icons[i], ix + 6, iy + 6, 10, Colors.lerp(Theme.TEXT_DIM, Theme.TEXT, Math.max(iconHover[i], on ? 1 : 0)));
                if (i == 2 && Notices.unread() > 0) d.disc(ix + 17, iy + 5, 3f, 0xFFFF453A);
            } else {
                d.shadow(ix, iy + 2, 22, 22, 11, 6f, Theme.SHADOW);
                drawAvatar(ix + 2, iy + 2, 18);
                d.rim(ix, iy, 22, 22, 11, 0x66FFFFFF, 0x1AFFFFFF);
            }
        }
    }

    private void drawAvatar(int x, int y, int size) {
        Identifier skin = null;
        try {
            if (minecraft.player != null) skin = minecraft.player.getSkin().body().texturePath();
        } catch (Throwable ignored) {
            // скин ещё не загружен — рисуем букву
        }
        if (skin != null) {
            d.g.blit(RenderPipelines.GUI_TEXTURED, skin, x, y, 8f, 8f, size, size, 8, 8, 64, 64, 0xFFFFFFFF);
            d.g.blit(RenderPipelines.GUI_TEXTURED, skin, x, y, 40f, 8f, size, size, 8, 8, 64, 64, 0xFFFFFFFF);
        } else {
            String name = minecraft.getUser().getName();
            d.round(x, y, size, size, size / 2, Theme.ELEVATED);
            d.textCenter(name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(Locale.ROOT), x + size / 2, Draw.textY(y, size), Theme.TEXT);
        }
    }

    // ── список модулей ───────────────────────────────────────

    private void drawList(double mx, double my, float dt) {
        int x = listX(), y = bodyY(), w = LIST_W, h = bodyH();
        glassPanel(x, y, w, h, CARD_R, Theme.GLASS_PANEL, 0.9f);
        // шапка: название вкладки и счётчик
        int on = 0, all = 0;
        for (Object o : rows) {
            if (o instanceof Module m && !m.alwaysOn()) {
                all++;
                if (m.isEnabled()) on++;
            }
        }
        String title = !search.isEmpty() ? "Поиск" : tab == Tab.CLIENT ? "Настройки" : tab.label;
        d.text(title, Fonts.Kind.BOLD, x + 12, y + 9, Theme.TEXT);
        if (all > 0) d.textRight(on + " / " + all, x + w - 12, y + 9, Theme.TEXT_MUTED);
        int ly = y + 26, lh = h - 30;
        if (warnH() > 0) {
            // плашка вкладки PvP: предупреждение о бане
            int bh = warnH() - 4;
            d.round(x + 8, ly, w - 16, bh, 8, Theme.isLight() ? 0x30FF8A3D : 0x38FF8A3D);
            for (int i = 0; i < PVP_WARN.length; i++) d.textScaled(PVP_WARN[i], Fonts.Kind.TEXT, x + 14, ly + 6 + i * 9, 0.72f, Theme.isLight() ? 0xFF9A4A10 : 0xFFFFC08A);
            ly += warnH();
            lh -= warnH();
        }
        if (rows.isEmpty() || rows.size() == 1 && rows.get(0) instanceof String) {
            String t = !search.isEmpty() ? "Ничего не найдено" : tab.cats.length > 0 ? tab.cats[0].emptyText : "";
            d.textCenter(Draw.ellipsize(t, w - 20), x + w / 2, ly + lh / 2 - 4, Theme.TEXT_MUTED);
            return;
        }
        int scroll = Math.round(listScroll);
        boolean inList = Widget.hit(mx, my, x, ly, w, lh);
        d.clip(x + 2, ly, w - 4, lh);
        // «капля» выбранного модуля
        int si = sel == null ? -1 : rows.indexOf(sel);
        if (si >= 0 && si < rowY.length) {
            float target = ly + rowY[si] - scroll;
            if (listDrop.value < 0) listDrop.snap(target);
            listDrop.target = target;
            float sy = listDrop.update(dt);
            float stretch = Math.min(10f, Math.abs(listDrop.velocity) * 0.02f);
            d.glass(x + 6, Math.round(sy - stretch / 2 + 2), w - 12, Math.round(ROW - 4 + stretch), 10, Colors.withAlpha(Theme.accent(), Theme.isLight() ? 0x50 : 0x30), 1.25f);
        } else listDrop.snap(-1);
        for (int i = 0; i < rows.size() && i < rowY.length; i++) {
            int ry = ly + rowY[i] - scroll;
            Object o = rows.get(i);
            int rh = o instanceof String ? HEAD : ROW;
            if (ry + rh < ly || ry > ly + lh) continue;
            if (o instanceof String s) {
                d.textScaled(s.toUpperCase(Locale.ROOT), Fonts.Kind.HEAD, x + 14, ry + 7, 0.68f, Theme.TEXT_MUTED);
                continue;
            }
            Module m = (Module) o;
            boolean hov = inList && Widget.hit(mx, my, x + 6, ry + 2, w - 12, ROW - 4);
            if (hov && m != sel) d.round(x + 6, ry + 2, w - 12, ROW - 4, 10, Theme.HOVER);
            // иконка: включённый модуль — плитка с акцентом
            boolean en = m.isEnabled() && !m.alwaysOn();
            int tile = 18, tx = x + 12, ty = ry + (ROW - tile) / 2;
            d.round(tx, ty, tile, tile, 6, en ? Colors.withAlpha(Theme.accent(), Theme.isLight() ? 0xD0 : 0x60) : Theme.isLight() ? 0x10000000 : 0x14FFFFFF);
            d.icon(Icons.forModule(m.id, Icons.forCategory(m.category)), tx + 4, ty + 4, 10, en ? 0xFFFFFFFF : Theme.TEXT_DIM);
            int nameW = w - 12 - 24 - 34 - (m.isNew() ? 26 : 0);
            String shown = Draw.ellipsize(m.name, nameW);
            d.text(shown, m == sel ? Fonts.Kind.BOLD : Fonts.Kind.TEXT, x + 36, Draw.textY(ry, ROW), m == sel || en ? Theme.TEXT : Theme.TEXT_DIM);
            if (m.isNew()) {
                // бейдж NEW: градиентная капсула #8B6CFF → #4FD1FF
                int bx = x + 36 + d.width(shown, m == sel ? Fonts.Kind.BOLD : Fonts.Kind.TEXT) + 5, by = ry + (ROW - 10) / 2;
                d.round(bx, by, 22, 10, 5, dev.mraz1visual.render.HudStyle.GRAD_A);
                d.round(bx + 8, by, 14, 10, 5, Colors.withAlpha(dev.mraz1visual.render.HudStyle.GRAD_B, 0xC0));
                d.textScaled("NEW", Fonts.Kind.BOLD, bx + 4, by + 2, 0.62f, 0xFFFFFFFF);
            }
            // мини-переключатель справа
            if (!m.alwaysOn()) {
                int sx = x + w - 34, sy = ry + (ROW - 12) / 2;
                d.round(sx, sy, 20, 12, 6, en ? Theme.accent() : Theme.TRACK);
                d.disc(sx + (en ? 14f : 6f), sy + 6f, 4.2f, 0xFFFFFFFF);
            }
        }
        d.unclip();
        if (listMax > 0) {
            int trackH = lh - 8, thumbH = Math.max(18, trackH * lh / (lh + listMax));
            int thumbY = ly + 4 + Math.round((trackH - thumbH) * (listScroll / listMax));
            d.round(x + w - 5, thumbY, 3, thumbH, 1, Theme.ELEVATED);
        }
    }

    // ── детали: баннер и карточки ────────────────────────────

    private void drawDetail(double mx, double my, float dt) {
        int x = detailX(), y = bodyY(), w = detailW(), bh = bannerH();
        if (sel == null) {
            glassPanel(x, y, w, bodyH(), CARD_R, Theme.GLASS_PANEL, 0.9f);
            d.textCenter("Выберите модуль слева", x + w / 2, y + bodyH() / 2 - 4, Theme.TEXT_MUTED);
            return;
        }
        Module m = sel;
        togT = Anim.to(togT, m.isEnabled() ? 1 : 0, dt);
        // ── баннер ──
        boolean hb = Widget.hit(mx, my, x, y, w, bh);
        bannerHover = Anim.to(bannerHover, hb ? 1 : 0, dt);
        float px = hb ? (float) ((mx - (x + w / 2.0)) / (w / 2.0)) : 0, py = hb ? (float) ((my - (y + bh / 2.0)) / (bh / 2.0)) : 0;
        parX = parX + (px - parX) * Math.min(1, dt * 6);
        parY = parY + (py - parY) * Math.min(1, dt * 6);
        d.shadow(x + 4, y + 6, w - 8, bh, CARD_R, 16f, Colors.mulAlpha(Theme.SHADOW, 0.9f));
        d.g.nextStratum();
        scene.render(d, m, x, y, w, bh, dt, parX, parY);
        // затемнение снизу под текст
        d.gradientV(x, y + bh - 64, w, 64, 0x00000000, 0xB0000000);
        d.rim(x, y, w, bh, CARD_R, 0x40FFFFFF, 0x10FFFFFF);
        // «● ПРЕДПРОСМОТР»
        String pv = "ПРЕДПРОСМОТР";
        int pw = d.width(pv) + 26;
        d.glass(x + 10, y + 10, pw, 18, 9, 0x50000000, 1f);
        float blink = 0.6f + 0.4f * (float) Math.sin(System.nanoTime() / 2.5e8);
        d.disc(x + 19, y + 19, 3f, Colors.withAlpha(0xFF453A, Math.round(255 * blink)));
        d.text(pv, Fonts.Kind.BOLD, x + 26, Draw.textY(y + 10, 18), 0xFFFFFFFF);
        String style = PreviewScene.style(m);
        if (!style.isEmpty()) {
            int sw = d.width(style) + 16;
            d.glass(x + w - 10 - sw, y + 10, sw, 18, 9, 0x50000000, 1f);
            d.textCenter(style, x + w - 10 - sw / 2, Draw.textY(y + 10, 18), 0xFFFFFFFF);
        }
        // название и описание
        d.textScaled(m.name, Fonts.Kind.HEAD, x + 16, y + bh - 42, 1.45f, 0xFFFFFFFF);
        d.text(Draw.ellipsize(m.description, w - 32 - 120), x + 16, y + bh - 20, 0xD0FFFFFF);
        // клавиша и главный переключатель
        if (!m.alwaysOn()) {
            int tw = 40, th = 22, tx = x + w - 16 - tw, tyy = y + bh - 16 - th;
            d.round(tx, tyy, tw, th, th / 2, Colors.lerp(0x60FFFFFF, Theme.accent() | 0xFF000000, togT));
            d.shadow(Math.round(tx + 3 + (tw - th) * togT), tyy + 3, th - 6, th - 6, (th - 6) / 2, 4f, 0x50000000);
            d.disc(tx + th / 2f + (tw - th) * togT, tyy + th / 2f, th / 2f - 3, 0xFFFFFFFF);
            if (bindChip != null) {
                int bw = Math.max(46, bindChip.buttonWidth(d) + 4);
                bindChip.setBounds(tx - 8 - bw, tyy + 3, bw, th - 6);
                d.glass(tx - 8 - bw - 2, tyy, bw + 4, th, th / 2, 0x40000000, 1f);
                bindChip.render(d, mx, my, dt);
            }
        } else {
            String a = "Всегда включён";
            int aw = d.width(a) + 16;
            d.glass(x + w - 16 - aw, y + bh - 16 - 20, aw, 20, 10, 0x40000000, 1f);
            d.textCenter(a, x + w - 16 - aw / 2, Draw.textY(y + bh - 36, 20), 0xFFFFFFFF);
        }

        // ── карточки настроек ──
        int cy = cardsY(), ch = cardsH();
        if (groups.isEmpty()) {
            glassPanel(x, cy, w, ch, CARD_R, Theme.GLASS_CARD, 0.9f);
            d.textCenter("У модуля нет настроек — только включить и выключить.", x + w / 2, cy + ch / 2 - 4, Theme.TEXT_MUTED);
            return;
        }
        boolean inArea = Widget.hit(mx, my, x, cy, w, ch);
        int scroll = Math.round(detailScroll);
        double cmy = inArea ? my + scroll : -1e6, cmx = inArea ? mx : -1e6;
        d.clip(x - 2, cy, w + 4, ch);
        d.g.pose().pushMatrix();
        d.g.pose().translate(0, -scroll);
        int top = cy + scroll, bottom = cy + ch + scroll;
        for (int i = 0; i < groups.size(); i++) {
            Group gr = groups.get(i);
            if (gr.y > bottom || gr.y + gr.h < top) continue;
            d.g.nextStratum();
            float ci = Math.max(0f, Math.min(1f, contentT * 1.8f - Math.min(i, 8) * 0.1f));
            float off = (1f - ease(ci)) * 14f;
            if (off > 0.05f) {
                d.g.pose().pushMatrix();
                d.g.pose().translate(0, off);
                drawGroup(gr, cmx, cmy - off, dt);
                d.g.pose().popMatrix();
            } else drawGroup(gr, cmx, cmy, dt);
        }
        d.g.pose().popMatrix();
        d.unclip();
        if (detailMax > 0) {
            int trackH = ch - 8, thumbH = Math.max(18, trackH * ch / (ch + detailMax));
            int thumbY = cy + 4 + Math.round((trackH - thumbH) * (detailScroll / detailMax));
            d.round(x + w - 4, thumbY, 3, thumbH, 1, Theme.ELEVATED);
        }
    }

    private float parX, parY;

    private void drawGroup(Group g, double mx, double my, float dt) {
        glassPanel(g.x, g.y, g.w, g.h, CARD_R, Theme.GLASS_CARD, 1f);
        d.textScaled(g.title.toUpperCase(Locale.ROOT), Fonts.Kind.HEAD, g.x + 12, g.y + 10, 0.7f, Theme.TEXT_MUTED);
        for (SettingWidget r : g.rows) r.render(d, mx, my, dt);
    }

    // ── конфиги ──────────────────────────────────────────────

    private void drawConfigs(double mx, double my, float dt) {
        int x = contentX(), y = bodyY(), w = contentW(), h = bodyH();
        glassPanel(x, y, w, h, CARD_R, Theme.GLASS_PANEL, 0.9f);
        int lw = 180;
        profiles.renderSidebar(d, mx, my, dt, x + 8, y + 8, lw, h - 16);
        d.vLine(x + lw + 14, y + 10, h - 20, Theme.DIVIDER);
        profiles.renderContent(d, mx, my, dt, x + lw + 16, y, w - lw - 16, h);
    }

    // ── уведомления ──────────────────────────────────────────

    private int bellX() { return iconX(2) + 22 - 240; }
    private int bellY() { return wy + TOP_H - 4; }

    private void drawBell(double mx, double my) {
        List<Notices.Notice> list = Notices.list();
        int bx = bellX(), by = bellY(), bw = 240, bh = Math.max(64, Math.min(5, list.size()) * 34 + 34);
        d.g.nextStratum();
        d.shadow(bx, by + 4, bw, bh, 14, 16f, Theme.SHADOW);
        d.glass(bx, by, bw, bh, 14, Theme.isLight() ? 0xF0FFFFFF : 0xEC16161C, 1.2f);
        d.text("Уведомления", Fonts.Kind.BOLD, bx + 12, by + 10, Theme.TEXT);
        if (list.isEmpty()) {
            d.textCenter("Пока ничего нового", bx + bw / 2, by + 38, Theme.TEXT_MUTED);
            return;
        }
        int y = by + 28;
        for (int i = 0; i < Math.min(5, list.size()); i++) {
            Notices.Notice n = list.get(i);
            d.text(Draw.ellipsize(n.title(), bw - 90), Fonts.Kind.BOLD, bx + 12, y + 3, Theme.TEXT);
            d.textRight(Notices.ago(n.at()), bx + bw - 12, y + 3, Theme.TEXT_MUTED);
            d.text(Draw.ellipsize(n.text(), bw - 24), bx + 12, y + 15, Theme.TEXT_DIM);
            y += 34;
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
        if (bellOpen) {
            bellOpen = false;
            Notices.markRead();
            if (Widget.hit(mx, my, bellX(), bellY(), 240, 200)) return true;
        }
        if (!Widget.hit(mx, my, wx, wy, ww, wh)) {
            onClose();
            return true;
        }
        // шапка
        if (side() && Widget.hit(mx, my, wx + PAD, bodyY(), SIDE_W, bodyH())) {
            int i = sideIndex(my);
            if (i >= 0) selectTab(Tab.BAR[i]);
            return true;
        }
        if (my < wy + TOP_H) {
            searchFocused = false;
            for (int i = 0; i < Tab.BAR.length && !side(); i++) {
                if (Widget.hit(mx, my, Math.round(tabAX[i]), wy + (TOP_H - 30) / 2, Math.round(tabAW[i]), 30)) {
                    selectTab(Tab.BAR[i]);
                    return true;
                }
            }
            if (Widget.hit(mx, my, searchX(), searchY(), SEARCH_W, 24)) {
                searchFocused = true;
                return true;
            }
            for (int i = 0; i < 4; i++) {
                if (!Widget.hit(mx, my, iconX(i), wy + (TOP_H - 22) / 2, 22, 22)) continue;
                switch (i) {
                    case 0 -> selectTab(tab == Tab.CLIENT ? Tab.VISUALS : Tab.CLIENT);
                    case 1 -> reveal(mm.find("friends"));
                    case 2 -> bellOpen = true;
                    default -> reveal(mm.find("local_skin"));
                }
                return true;
            }
            return true;
        }
        searchFocused = false;
        Panel panel = panel();
        if (search.isEmpty() && tab == Tab.CONFIGS) {
            if (b != 0) return true;
            int x = contentX(), y = bodyY(), lw = 180;
            if (Widget.hit(mx, my, x + 8, y + 8, lw, bodyH() - 16)) profiles.clickSidebar(mx, my, x + 8, y + 8, lw, doubleClick);
            else profiles.clickContent(mx, my, x + lw + 16, y);
            return true;
        }
        if (search.isEmpty() && panel != null) {
            panel.click(mx, my, b);
            return true;
        }
        // список
        int lx = listX(), ly = bodyY() + 26 + warnH(), lh = bodyH() - 30 - warnH();
        if (Widget.hit(mx, my, lx, ly, LIST_W, lh)) {
            double ry = my - ly + listScroll;
            for (int i = 0; i < rows.size() && i < rowY.length; i++) {
                if (!(rows.get(i) instanceof Module m)) continue;
                if (ry < rowY[i] || ry >= rowY[i] + ROW) continue;
                boolean onSwitch = mx >= lx + LIST_W - 38;
                if (b == 1 || b == 0 && onSwitch) {
                    if (!m.alwaysOn()) m.toggle();
                } else if (b == 0) {
                    select(m);
                }
                return true;
            }
            return true;
        }
        if (sel == null) return true;
        // баннер: переключатель и клавиша
        int x = detailX(), y = bodyY(), w = detailW(), bh = bannerH();
        if (Widget.hit(mx, my, x, y, w, bh)) {
            if (bindChip != null && bindChip.mouseClicked(mx, my, b)) return true;
            int tx = x + w - 16 - 40, tyy = y + bh - 16 - 22;
            if (!sel.alwaysOn() && (b == 1 || Widget.hit(mx, my, tx - 4, tyy - 4, 48, 30))) sel.toggle();
            return true;
        }
        // карточки
        if (Widget.hit(mx, my, x, cardsY(), w, cardsH())) {
            double cmy = my + detailScroll;
            for (Group g : groups) {
                if (!Widget.hit(mx, cmy, g.x, g.y, g.w, g.h)) continue;
                for (SettingWidget r : g.rows) {
                    if (r.mouseClicked(mx, cmy, b)) {
                        active = r;
                        return true;
                    }
                }
                return true;
            }
        }
        return true;
    }

    private double activeY(double my) {
        return active == popup || active == bindChip ? my : my + detailScroll;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        Panel panel = panel();
        if (panel != null && search.isEmpty()) panel.release(e.x() / k, e.y() / k);
        if (active != null) {
            active.mouseReleased(e.x() / k, activeY(e.y() / k));
            active = null;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dragX, double dragY) {
        Panel panel = panel();
        if (panel != null && search.isEmpty()) panel.drag(e.x() / k, e.y() / k);
        if (active != null) active.mouseDragged(e.x() / k, activeY(e.y() / k));
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (popup != null) return true;
        double mx = mouseX / k, my = mouseY / k;
        Panel panel = panel();
        if (panel != null && search.isEmpty()) {
            panel.scroll(mx, my, scrollY);
            return true;
        }
        if (Widget.hit(mx, my, listX(), bodyY(), LIST_W, bodyH())) listTarget = clamp((float) (listTarget - scrollY * 30), 0, listMax);
        else if (Widget.hit(mx, my, detailX(), cardsY(), detailW(), cardsH())) detailTarget = clamp((float) (detailTarget - scrollY * 30), 0, detailMax);
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
            if (key == InputConstants.KEY_ESCAPE) popup = null;
            return true;
        }
        if (bellOpen && key == InputConstants.KEY_ESCAPE) {
            bellOpen = false;
            return true;
        }
        boolean ctrl = (e.modifiers() & InputConstants.MOD_CONTROL) != 0;
        if (tab == Tab.CONFIGS && search.isEmpty() && profiles.isTyping()) return profiles.keyPressed(key, ctrl, minecraft.keyboardHandler::getClipboard);
        Panel panel = panel();
        if (panel != null && search.isEmpty() && panel.typing()) return panel.key(key, ctrl);
        if (searchFocused) {
            switch (key) {
                case InputConstants.KEY_ESCAPE -> {
                    if (!search.isEmpty()) {
                        search.setLength(0);
                        onSearchChanged();
                    } else searchFocused = false;
                }
                case InputConstants.KEY_BACKSPACE -> {
                    int len = search.length();
                    if (len > 0) {
                        int cp = search.codePointBefore(len);
                        search.setLength(len - Character.charCount(cp));
                        onSearchChanged();
                    }
                }
                case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> searchFocused = false;
                case InputConstants.KEY_DOWN, InputConstants.KEY_UP -> step(key == InputConstants.KEY_DOWN ? 1 : -1);
                default -> {
                    return false;
                }
            }
            return true;
        }
        switch (key) {
            case InputConstants.KEY_DOWN, InputConstants.KEY_UP -> {
                step(key == InputConstants.KEY_DOWN ? 1 : -1);
                return true;
            }
            case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER, InputConstants.KEY_SPACE -> {
                if (sel != null && !sel.alwaysOn()) sel.toggle();
                return true;
            }
            case InputConstants.KEY_TAB -> {
                int i = 0;
                for (int j = 0; j < Tab.BAR.length; j++) if (Tab.BAR[j] == tab) i = j;
                int n = Tab.BAR.length, dir = (e.modifiers() & InputConstants.MOD_SHIFT) != 0 ? -1 : 1;
                selectTab(Tab.BAR[(i + dir + n) % n]);
                return true;
            }
            default -> {
            }
        }
        if (key == InputConstants.KEY_ESCAPE || key == cs.menuKey.get()) {
            onClose();
            return true;
        }
        return false;
    }

    /** Стрелки: соседний модуль в списке. */
    private void step(int dir) {
        int i = sel == null ? -1 : rows.indexOf(sel);
        for (int j = i + dir; j >= 0 && j < rows.size(); j += dir) {
            if (rows.get(j) instanceof Module m) {
                select(m);
                scrollListTo(m);
                return;
            }
        }
    }

    @Override
    public boolean charTyped(CharacterEvent e) {
        if (listening != null || popup != null) return true;
        int cp = e.codepoint();
        if (tab == Tab.CONFIGS && search.isEmpty() && profiles.isTyping()) return profiles.charTyped(cp);
        Panel panel = panel();
        if (panel != null && search.isEmpty() && panel.typing()) return panel.chr(cp);
        if (Character.isISOControl(cp)) return false;
        if (!searchFocused && cp == ' ') return false;
        if (search.length() >= SEARCH_MAX) return true;
        searchFocused = true;
        search.appendCodePoint(cp);
        onSearchChanged();
        return true;
    }

    @Override
    public void onFilesDrop(List<Path> paths) {
        if (tab != Tab.CONFIGS) selectTab(Tab.CONFIGS);
        profiles.filesDropped(paths);
    }

    // ── стенд ────────────────────────────────────────────────

    /** Dev-стенд: открыть вкладку (имя Tab или старой категории). */
    public void harnessTab(String name) {
        String n = name.toUpperCase(Locale.ROOT);
        for (Tab t : Tab.values()) {
            if (t.name().equals(n)) {
                selectTab(t);
                return;
            }
            for (Category c : t.cats) {
                if (c.name().equals(n)) {
                    selectTab(t);
                    return;
                }
            }
        }
    }

    /** Dev-стенд: выбрать модуль. */
    public void harnessReveal(String id) {
        reveal(mm.find(id));
    }

    private static float clamp(float v, float min, float max) {
        return v < min ? min : Math.min(v, max);
    }
}

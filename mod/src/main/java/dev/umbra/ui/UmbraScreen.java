package dev.umbra.ui;

import com.mojang.blaze3d.platform.Window;
import dev.umbra.Umbra;
import dev.umbra.core.Category;
import dev.umbra.core.Module;
import dev.umbra.core.ModuleManager;
import dev.umbra.core.Modules;
import dev.umbra.module.ClientSettings;
import dev.umbra.setting.BoolSetting;
import dev.umbra.setting.ColorSetting;
import dev.umbra.setting.EnumSetting;
import dev.umbra.setting.KeySetting;
import dev.umbra.setting.Setting;
import dev.umbra.setting.SliderSetting;
import dev.umbra.ui.widget.BindWidget;
import dev.umbra.ui.widget.ColorWidget;
import dev.umbra.ui.widget.EnumWidget;
import dev.umbra.ui.widget.Host;
import dev.umbra.ui.widget.Popup;
import dev.umbra.ui.widget.SettingWidget;
import dev.umbra.ui.widget.SliderWidget;
import dev.umbra.ui.widget.ToggleWidget;
import dev.umbra.ui.widget.Widget;
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
 * Окно настроек. Сверху вкладки, слева модули, справа настройки модуля.
 * До любой опции — максимум два клика (вкладка → модуль) или поиск с клавиатуры.
 *
 * Всё, что можно посчитать заранее, считается в relayout()/rebuild*(), а не в кадре.
 */
public final class UmbraScreen extends Screen implements Host {
    /** Вкладки сверху. «Настройки клиента» — шестерёнка справа. */
    private static final Category[] TABS = java.util.Arrays.stream(Category.values()).filter(c -> c != Category.CLIENT).toArray(Category[]::new);
    private static final String BRAND = "UMBRA";
    private static final int SEARCH_MAX = 32;

    private final ModuleManager mm = Umbra.MODULES;
    private final ClientSettings cs = Modules.client;
    private final Draw d = new Draw();
    /** [наведение, тумблер] для каждой строки списка. */
    private final Map<Module, float[]> rowAnim = new IdentityHashMap<>();

    // геометрия (единицы окна)
    private float k = 1f;
    private int vw, vh, wx, wy, ww, wh;
    private int layoutW = -1, layoutH = -1, layoutSize = -1;
    private final int[] tabX = new int[TABS.length], tabW = new int[TABS.length];
    private final float[] tabHover = new float[TABS.length];
    private float ulX, ulW;
    private boolean ulInit;

    // состояние
    private Category category = Category.VISUALS;
    private Module selected;
    private final EnumMap<Category, Module> lastInCategory = new EnumMap<>(Category.class);
    private boolean restored;

    private final List<Module> list = new ArrayList<>();
    private float listScroll, listTarget;
    private int listMax;

    private final List<SettingWidget> widgets = new ArrayList<>();
    private final List<int[]> cards = new ArrayList<>();
    private final List<String> cardTitles = new ArrayList<>();
    private float contentScroll, contentTarget;
    private int contentMax;
    private BindWidget headerBind;
    private float headerToggle;
    private String headerDesc = "";

    private Popup popup;
    private BindWidget listening;
    private Widget active;

    private final StringBuilder search = new StringBuilder();
    private String searchView = "";
    private boolean searchFocused;

    private long lastNanos;
    private final ProfilesPanel profiles = new ProfilesPanel();

    /** Анимация открытия (0..1) и смены содержимого (0..1). */
    private float openT, contentT = 1f;
    private long startNanos;
    private float gearHover, friendsHover;

    private boolean configsMode() {
        return category == Category.CONFIGS && search.isEmpty();
    }
    /** Куда вернуться при закрытии: null — в игру, иначе в меню, откуда открыли. */
    private Screen parent;

    public void setParent(Screen parent) {
        this.parent = parent;
    }

    public UmbraScreen() {
        super(Component.literal("Umbra"));
        for (Module m : mm.all()) rowAnim.put(m, new float[]{0, m.isEnabled() ? 1 : 0});
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
        startNanos = System.nanoTime();
        profiles.refresh();
        relayout();
    }

    private void restoreState() {
        Category c = Category.VISUALS;
        try {
            if (!Umbra.CONFIG.uiCategory.isEmpty()) c = Category.valueOf(Umbra.CONFIG.uiCategory);
        } catch (IllegalArgumentException ignored) {
        }
        Module m = mm.find(Umbra.CONFIG.uiModule);
        if (m != null) lastInCategory.put(m.category, m);
        category = c;
        Module pick = lastInCategory.get(c);
        if (pick == null && !mm.in(c).isEmpty()) pick = mm.in(c).get(0);
        selected = pick;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        popup = null;
        if (listening != null) listening.cancel();
        listening = null;
        searchFocused = false;
        String cat = category.name(), mod = selected == null ? "" : selected.id;
        if (!cat.equals(Umbra.CONFIG.uiCategory) || !mod.equals(Umbra.CONFIG.uiModule)) {
            Umbra.CONFIG.uiCategory = cat;
            Umbra.CONFIG.uiModule = mod;
            Umbra.CONFIG.markDirty();
        }
        Umbra.CONFIG.saveIfDirty();
        Screen back = parent;
        parent = null;
        minecraft.setScreen(back);
    }

    @Override
    public void removed() {
        Umbra.CONFIG.saveIfDirty();
        lastNanos = 0;
        active = null;
    }

    // ── раскладка ────────────────────────────────────────────

    private void relayout() {
        layoutW = width;
        layoutH = height;
        layoutSize = cs.size.get();

        // Размер как у референса: окно ~54% ширины экрана при 1080p, без привязки к GUI scale.
        Window win = minecraft.getWindow();
        double gui = win.getGuiScale();
        int pw = win.getWidth(), ph = win.getHeight();
        double base = Math.max(0.75, ph / 1080.0) * switch (cs.size.getId()) {
            case "small" -> 1.2;
            case "large" -> 1.65;
            default -> 1.4;
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

        int x = wx + Theme.PAD + Fonts.width(BRAND, Fonts.Kind.HEAD) + 20;
        for (int i = 0; i < TABS.length; i++) {
            tabX[i] = x;
            tabW[i] = ICON + 5 + Fonts.width(TABS[i].title);
            x += tabW[i] + Theme.TAB_GAP;
        }
        ulInit = false;
        rebuildList();
        rebuildContent();
    }

    private static final int ICON = 9;
    private static final int TILE = 19;
    private static final int HEADER = Theme.HEADER_H;

    private int listX() { return wx + 6; }
    private int listY() { return wy + Theme.TOPBAR_H + 6; }
    private int listW() { return Theme.SIDEBAR_W - 12; }
    private int listH() { return wh - Theme.TOPBAR_H - 12; }
    private int cX() { return wx + Theme.SIDEBAR_W; }
    private int cY() { return wy + Theme.TOPBAR_H; }
    private int cW() { return ww - Theme.SIDEBAR_W; }
    private int cH() { return wh - Theme.TOPBAR_H; }
    private int headerX() { return cX() + Theme.PAD + 4; }
    private int headerY() { return cY() + 10; }
    private int cardsY() { return headerY() + HEADER + Theme.GAP + 2; }
    private int cardsH() { return wy + wh - cardsY() - 4; }
    private int avatarX() { return wx + ww - Theme.PAD - TILE; }
    private int gearX() { return avatarX() - 6 - TILE; }
    private int tileY() { return wy + (Theme.TOPBAR_H - TILE) / 2; }
    private int friendsX() { return gearX() - 5 - TILE; }
    private int searchX() { return friendsX() - 8 - Theme.SEARCH_W; }
    private int searchY() { return wy + (Theme.TOPBAR_H - TILE) / 2; }
    private int headerToggleX() { return cX() + cW() - Theme.PAD - 6 - Theme.SWITCH_W; }
    private int headerMidY() { return headerY() + HEADER / 2 - 3; }

    private void rebuildList() {
        list.clear();
        if (search.isEmpty()) {
            list.addAll(mm.in(category));
        } else {
            String q = searchView.toLowerCase(Locale.ROOT);
            for (Module m : mm.all()) {
                if (m.name.toLowerCase(Locale.ROOT).contains(q) || m.description.toLowerCase(Locale.ROOT).contains(q)) list.add(m);
            }
        }
        listMax = Math.max(0, list.size() * Theme.ROW_H - listH());
        listTarget = Math.min(listTarget, listMax);
        listScroll = Math.min(listScroll, listMax);
    }

    private void selectCategory(Category c) {
        category = c;
        if (!search.isEmpty()) {
            search.setLength(0);
            searchView = "";
        }
        searchFocused = false;
        listScroll = listTarget = 0;
        rebuildList();
        Module m = lastInCategory.get(c);
        if (m == null && !list.isEmpty()) m = list.get(0);
        select(m);
        if (c == Category.CONFIGS) profiles.refresh();
    }

    private void select(Module m) {
        selected = m;
        if (m != null) {
            lastInCategory.put(m.category, m);
            if (category != m.category && search.isEmpty()) {
                category = m.category;
                listScroll = listTarget = 0;
                rebuildList();
            }
            category = m.category;
        }
        contentScroll = contentTarget = 0;
        contentT = cs.animations.get() ? 0f : 1f;
        rebuildContent();
    }

    private void rebuildContent() {
        widgets.clear();
        cards.clear();
        cardTitles.clear();
        if (listening != null) listening.cancel();
        listening = null;
        headerBind = null;
        contentMax = 0;
        if (selected == null) return;

        headerToggle = selected.isEnabled() ? 1 : 0;
        headerDesc = Draw.ellipsize(selected.description, cW() - 2 * Theme.PAD - 30 - (selected.alwaysOn() ? 0 : 130));
        if (!selected.alwaysOn()) {
            headerBind = new BindWidget(selected.bind, this, false);
            headerBind.setBounds(headerToggleX() - 10 - 60, headerMidY() - 8, 60, 16);
        }

        Map<String, List<Setting<?>>> groups = new LinkedHashMap<>();
        for (Setting<?> s : selected.settings()) groups.computeIfAbsent(s.group(), g -> new ArrayList<>()).add(s);

        int inner = cW() - 2 * Theme.PAD;
        int cols = inner >= 2 * Theme.CARD_MIN_W + Theme.GAP ? 2 : 1;
        int colW = (inner - (cols - 1) * Theme.GAP) / cols;
        int[] colY = new int[cols];
        for (int i = 0; i < cols; i++) colY[i] = cardsY() + 2;

        for (Map.Entry<String, List<Setting<?>>> e : groups.entrySet()) {
            int col = 0;
            for (int i = 1; i < cols; i++) if (colY[i] < colY[col]) col = i;
            int x = cX() + Theme.PAD + col * (colW + Theme.GAP);
            int y = colY[col];
            List<Setting<?>> ss = e.getValue();
            int h = Theme.CARD_TITLE_H + ss.size() * Theme.ROW_H + Theme.CARD_PAD - 4;
            cards.add(new int[]{x, y, colW, h});
            cardTitles.add(e.getKey().toUpperCase(Locale.ROOT));
            for (int i = 0; i < ss.size(); i++) {
                SettingWidget w = widgetFor(ss.get(i));
                w.setBounds(x + Theme.CARD_PAD, y + Theme.CARD_TITLE_H + i * Theme.ROW_H, colW - 2 * Theme.CARD_PAD, Theme.ROW_H);
                widgets.add(w);
            }
            colY[col] += h + Theme.GAP;
        }
        int bottom = 0;
        for (int v : colY) bottom = Math.max(bottom, v - Theme.GAP);
        contentMax = Math.max(0, bottom + 8 - (cardsY() + cardsH()));
    }

    private SettingWidget widgetFor(Setting<?> s) {
        if (s instanceof BoolSetting b) return new ToggleWidget(b, this);
        if (s instanceof SliderSetting sl) return new SliderWidget(sl, this);
        if (s instanceof EnumSetting en) return new EnumWidget(en, this, font);
        if (s instanceof ColorSetting c) return new ColorWidget(c, this);
        if (s instanceof KeySetting key) return new BindWidget(key, this, true);
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
        return w == headerBind ? 0 : contentScroll;
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
        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 1f : Math.min(0.1f, (now - lastNanos) / 1e9f);
        float animDt = lastNanos == 0 ? 0f : dt;
        lastNanos = now;
        Anim.enabled = cs.animations.get();
        if (width != layoutW || height != layoutH || cs.size.get() != layoutSize) relayout();

        double rmx = mouseX / k, rmy = mouseY / k;
        boolean overPopup = popup != null && popup.hit(rmx, rmy);
        double mx = overPopup ? -1e6 : rmx, my = overPopup ? -1e6 : rmy;

        listScroll = Anim.to(listScroll, listTarget, dt);
        contentScroll = Anim.to(contentScroll, contentTarget, dt);
        if (openT < 1f) openT = Math.min(1f, openT + animDt / 0.75f);
        if (contentT < 1f) contentT = Math.min(1f, contentT + animDt / 0.22f);
        float time = (now - startNanos) / 1e9f;

        d.begin(g, font, k);
        if (openT < 1f) drawOpening(openT);
        float grow = ease(clamp((openT - 0.22f) / 0.4f, 0, 1));
        if (grow <= 0f) {
            d.end();
            return;
        }
        int gh = Math.max(2, Math.round(wh * grow));
        int gy = wy + (wh - gh) / 2;
        drawWindow(gy, gh);
        d.clip(wx, gy, ww, gh);
        drawTopBar(mx, my, dt, now);
        drawSidebar(mx, my, dt);
        drawContent(mx, my, dt, time);
        float reveal = clamp((openT - 0.5f) / 0.35f, 0, 1);
        if (reveal < 1f) d.rect(wx + 1, gy + 1, ww - 2, gh - 2, Colors.withAlpha(0x0A0A0E, Math.round(255 * (1 - ease(reveal)))));
        d.unclip();
        if (popup != null) {
            if (popup.isClosed()) popup = null;
            else {
                g.nextStratum();
                popup.render(d, rmx, rmy, dt);
            }
        }
        d.end();
    }

    private static float ease(float t) {
        return 1f - (float) Math.pow(1f - t, 3);
    }

    /** Открытие: затмение и полоса света, из которой раскрывается окно. */
    private void drawOpening(float t) {
        float a = t < 0.15f ? t / 0.15f : t < 0.45f ? 1f : Math.max(0f, 1f - (t - 0.45f) / 0.25f);
        if (a <= 0f) return;
        float cx = wx + ww * 0.78f, cy = wy - wh * 0.02f, r = wh * 0.22f;
        int acc = Theme.accent();
        for (int i = 8; i > 0; i--) d.disc(cx, cy, r + i * 3f, Colors.withAlpha(acc, Math.round(a * (8 + (8 - i) * 5))));
        d.disc(cx, cy, r + 1.5f, Colors.withAlpha(0xE8E4FF, Math.round(a * 230)));
        d.disc(cx, cy, r, Colors.withAlpha(0x050508, Math.round(a * 255)));
        // горизонт: линия света раскрывается из центра
        float line = ease(clamp(t / 0.3f, 0, 1));
        int lw = Math.round(ww * 1.1f * line);
        int ly = wy + wh / 2;
        d.rect(wx + (ww - lw) / 2, ly, lw, 1, Colors.withAlpha(0xE8E4FF, Math.round(a * 160)));
        d.rect(wx + (ww - lw) / 2, ly - 2, lw, 5, Colors.withAlpha(acc, Math.round(a * 30)));
    }

    private void drawWindow(int y, int h) {
        d.round(wx - 8, y - 5, ww + 16, h + 18, Theme.RADIUS + 8, 0x18000000);
        d.round(wx - 3, y - 1, ww + 6, h + 7, Theme.RADIUS + 3, Theme.SHADOW);
        d.card(wx, y, ww, h, Theme.RADIUS, Theme.WINDOW, Theme.WINDOW_EDGE);
    }

    private void drawTopBar(double mx, double my, float dt, long now) {
        int ty = Draw.textY(wy, Theme.TOPBAR_H);
        d.text(BRAND, Fonts.Kind.HEAD, wx + Theme.PAD, ty, Theme.TEXT);

        boolean searching = !search.isEmpty();
        for (int i = 0; i < TABS.length; i++) {
            boolean hov = Widget.hit(mx, my, tabX[i] - 6, wy, tabW[i] + 12, Theme.TOPBAR_H);
            tabHover[i] = Anim.to(tabHover[i], hov ? 1 : 0, dt);
            boolean on = TABS[i] == category && !searching;
            int c = on ? Theme.TEXT : Colors.lerp(Theme.TEXT_MUTED, Theme.TEXT_DIM, tabHover[i]);
            d.icon(Icons.forCategory(TABS[i]), tabX[i], wy + (Theme.TOPBAR_H - ICON) / 2, ICON, c);
            d.text(TABS[i].title, tabX[i] + ICON + 5, ty, c);
            if (TABS[i] == category) {
                if (!ulInit) {
                    ulX = tabX[i];
                    ulW = tabW[i];
                    ulInit = true;
                }
                ulX = Anim.to(ulX, tabX[i], dt);
                ulW = Anim.to(ulW, tabW[i], dt);
            }
        }
        if (!searching && ulInit && category != Category.CLIENT) {
            int ux = Math.round(ulX), uw = Math.round(ulW);
            d.round(ux - 4, wy + Theme.TOPBAR_H - 4, uw + 8, 4, 2, Colors.withAlpha(Theme.accent(), 0x26));
            d.round(ux, wy + Theme.TOPBAR_H - 2, uw, 2, 1, Theme.accent());
        }

        int sx = searchX(), sy = searchY();
        boolean sh = Widget.hit(mx, my, sx, sy, Theme.SEARCH_W, TILE);
        d.card(sx, sy, Theme.SEARCH_W, TILE, Theme.RADIUS_SM + 1, sh || searchFocused ? Theme.FIELD_HOVER : Theme.FIELD,
                searchFocused ? Theme.accentSoft() : Theme.CARD_EDGE);
        int textY = Draw.textY(sy, TILE);
        d.icon(Icons.SEARCH, sx + 6, sy + (TILE - 8) / 2, 8, Theme.TEXT_MUTED);
        if (searching) d.text(searchView, sx + 19, textY, Theme.TEXT);
        else d.text("Поиск", sx + 19, textY, Theme.TEXT_MUTED);
        if (searchFocused && (now / 500_000_000L & 1) == 0) {
            d.rect(sx + 19 + (searching ? d.width(searchView) : 0) + 1, sy + 5, 1, 8, Theme.TEXT);
        }

        // шестерёнка — настройки клиента
        int gx = gearX(), gy = tileY();
        boolean gh = Widget.hit(mx, my, gx, gy, TILE, TILE);
        gearHover = Anim.to(gearHover, gh || category == Category.CLIENT ? 1 : 0, dt);
        d.card(gx, gy, TILE, TILE, Theme.RADIUS_SM + 1, Colors.lerp(Theme.FIELD, Theme.FIELD_HOVER, gearHover),
                category == Category.CLIENT && !searching ? Theme.accentSoft() : Theme.CARD_EDGE);
        d.icon(Icons.GEAR, gx + 4, gy + 4, TILE - 8, Colors.lerp(Theme.TEXT_DIM, Theme.TEXT, gearHover));
        // друзья — открывает Misc → Friends
        int fx = friendsX();
        boolean fh = Widget.hit(mx, my, fx, gy, TILE, TILE);
        friendsHover = Anim.to(friendsHover, fh ? 1 : 0, dt);
        d.card(fx, gy, TILE, TILE, Theme.RADIUS_SM + 1, Colors.lerp(Theme.FIELD, Theme.FIELD_HOVER, friendsHover), Theme.CARD_EDGE);
        d.icon(Icons.FRIENDS, fx + 4, gy + 4, TILE - 8, Colors.lerp(Theme.TEXT_DIM, Theme.TEXT, friendsHover));
        drawAvatar(avatarX(), tileY());

        d.hLine(wx + 1, wy + Theme.TOPBAR_H, ww - 2, Theme.DIVIDER);
    }

    private void drawSidebar(double mx, double my, float dt) {
        d.vLine(wx + Theme.SIDEBAR_W, wy + Theme.TOPBAR_H + 1, wh - Theme.TOPBAR_H - 2, Theme.DIVIDER);
        int lx = listX(), ly = listY(), lw = listW(), lh = listH();
        if (configsMode()) {
            profiles.renderSidebar(d, mx, my, dt, lx, ly, lw, lh);
            return;
        }
        boolean inList = Widget.hit(mx, my, lx, ly, lw, lh);
        int scroll = Math.round(listScroll);

        // анимации тумблеров считаем для всех видимых строк
        d.clip(lx, ly, lw, lh);
        int first = Math.max(0, scroll / Theme.ROW_H);
        for (int i = first; i < list.size(); i++) {
            int ry = ly + i * Theme.ROW_H - scroll;
            if (ry > ly + lh) break;
            Module m = list.get(i);
            float[] a = rowAnim.get(m);
            boolean hov = inList && Widget.hit(mx, my, lx, ry, lw, Theme.ROW_H);
            a[0] = Anim.to(a[0], hov ? 1 : 0, dt);
            a[1] = Anim.to(a[1], m.isEnabled() ? 1 : 0, dt);

            if (m == selected) d.round(lx, ry + 1, lw, Theme.ROW_H - 2, Theme.RADIUS_SM, Theme.SELECTED);
            else if (a[0] > 0.01f) d.round(lx, ry + 1, lw, Theme.ROW_H - 2, Theme.RADIUS_SM, Colors.mulAlpha(Theme.SELECTED, a[0] * 0.6f));
            if (!m.alwaysOn() && a[1] > 0.01f) d.round(lx + 2, ry + 6, 2, Theme.ROW_H - 12, 1, Colors.mulAlpha(Theme.accent(), a[1]));

            int nameColor = m == selected || (m.isEnabled() && !m.alwaysOn()) ? Theme.TEXT : Colors.lerp(Theme.TEXT_DIM, Theme.TEXT, a[0]);
            int textY = Draw.textY(ry, Theme.ROW_H);
            d.icon(Icons.forModule(m.id, Icons.forCategory(m.category)), lx + 8, ry + (Theme.ROW_H - 8) / 2, 8,
                    Colors.lerp(Theme.TEXT_MUTED, nameColor, 0.6f));
            d.text(m.name, lx + 22, textY, nameColor);
            if (!m.alwaysOn()) {
                int tx = lx + lw - 6 - Theme.SWITCH_W;
                d.toggle(tx, ry + (Theme.ROW_H - Theme.SWITCH_H) / 2, a[1]);
                if (m.bind.isBound()) {
                    String b = m.bind.display();
                    int bw = d.width(b) + 8;
                    d.round(tx - 6 - bw, ry + 5, bw, Theme.ROW_H - 10, 3, Theme.FIELD);
                    d.textCenter(b, tx - 6 - bw / 2, textY, Theme.TEXT_MUTED);
                }
            }
        }
        if (list.isEmpty() && !search.isEmpty()) d.text("Ничего не найдено", lx + 9, Draw.textY(ly, Theme.ROW_H), Theme.TEXT_MUTED);
        d.unclip();
    }

    private void drawContent(double mx, double my, float dt, float time) {
        int cx = cX(), cy = cY(), cw = cW(), ch = cH();
        if (configsMode()) {
            profiles.renderContent(d, mx, my, dt, cx, cy, cw, ch);
            return;
        }
        if (selected == null) {
            String t = search.isEmpty() ? category.emptyText : "";
            d.textCenter(t, cx + cw / 2, cy + ch / 2 - 4, Theme.TEXT_MUTED);
            return;
        }

        // шапка модуля: иконка, название, описание; справа клавиша и тумблер
        int hx = headerX(), hy = headerY();
        int iconIdx = Icons.forModule(selected.id, Icons.forCategory(selected.category));
        d.icon(iconIdx, hx, hy + 9, 14, Theme.TEXT);
        d.textScaled(selected.name, Fonts.Kind.HEAD, hx + 22, hy + 4, 1.45f, Theme.TEXT);
        d.text(headerDesc, hx + 22, hy + 24, Theme.TEXT_DIM);
        if (!selected.alwaysOn()) {
            headerToggle = Anim.to(headerToggle, selected.isEnabled() ? 1 : 0, dt);
            d.toggle(headerToggleX(), headerMidY() - Theme.SWITCH_H / 2, headerToggle);
            headerBind.render(d, mx, my, dt);
            int labelRight = headerBind.x + headerBind.w - headerBind.buttonWidth(d) - 6;
            d.textScaled("КЛАВИША", Fonts.Kind.HEAD, Math.round(labelRight - Fonts.width("КЛАВИША", Fonts.Kind.HEAD) * 0.8f), Draw.textY(headerBind.y, headerBind.h) + 1, 0.8f, Theme.TEXT_MUTED);
        }
        d.hLine(cx + 1, hy + HEADER, cw - 2, Theme.DIVIDER);

        // карточки настроек
        int ay = cardsY(), ah = cardsH();
        if (widgets.isEmpty()) {
            d.text("Настроек нет — только вкл/выкл.", cx + Theme.PAD, ay + 10, Theme.TEXT_MUTED);
            fadeContent(cx, cy, cw, ch);
            return;
        }
        boolean inArea = Widget.hit(mx, my, cx, ay, cw, ah);
        int scroll = Math.round(contentScroll);
        double cmy = inArea ? my + scroll : -1e6;

        d.clip(cx + 1, ay, cw - 2, ah);
        d.g.pose().pushMatrix();
        d.g.pose().translate(0, -scroll);
        int top = ay + scroll, bottom = ay + ah + scroll;
        for (int i = 0; i < cards.size(); i++) {
            int[] c = cards.get(i);
            if (c[1] > bottom || c[1] + c[3] < top) continue;
            d.card(c[0], c[1], c[2], c[3], Theme.RADIUS_SM + 2, Theme.CARD, Theme.CARD_EDGE);
            d.text(cardTitles.get(i), Fonts.Kind.HEAD, c[0] + Theme.CARD_PAD, c[1] + 7, Theme.TEXT_MUTED);
        }
        for (int i = 0; i < widgets.size(); i++) {
            SettingWidget w = widgets.get(i);
            if (w.y > bottom || w.y + w.h < top) continue;
            w.render(d, mx, cmy, dt);
        }
        d.g.pose().popMatrix();
        d.unclip();

        if (contentMax > 0) {
            int trackH = ah - 8;
            int thumbH = Math.max(16, trackH * ah / (ah + contentMax));
            int thumbY = ay + 4 + Math.round((trackH - thumbH) * (contentScroll / contentMax));
            d.round(cx + cw - 5, thumbY, 2, thumbH, 1, Theme.SELECTED);
        }
        fadeContent(cx, cy, cw, ch);
    }

    /** Плавное появление содержимого при смене модуля/вкладки. */
    private void fadeContent(int cx, int cy, int cw, int ch) {
        if (contentT >= 1f) return;
        d.rect(cx + 1, cy + 1, cw - 2, ch - 2, Colors.withAlpha(0x0A0A0E, Math.round(255 * (1 - ease(contentT)))));
    }

    /** Закрашивает углы прямоугольника, чтобы он выглядел скруглённым. */
    private void corners(int x, int y, int w, int h, int r, int color) {
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int in = (int) Math.round(r - Math.sqrt(Math.max(0, r * r - dy * dy)));
            if (in <= 0) continue;
            d.rect(x, y + i, in, 1, color);
            d.rect(x + w - in, y + i, in, 1, color);
            d.rect(x, y + h - 1 - i, in, 1, color);
            d.rect(x + w - in, y + h - 1 - i, in, 1, color);
        }
    }

    /** Аватар игрока: лицо со скина, в меню — буква ника. */
    private void drawAvatar(int x, int y) {
        Identifier skin = null;
        try {
            if (minecraft.player != null) skin = minecraft.player.getSkin().body().texturePath();
        } catch (Throwable ignored) {
        }
        if (skin != null) {
            d.round(x - 1, y - 1, TILE + 2, TILE + 2, 4, Theme.CARD_EDGE);
            d.g.blit(RenderPipelines.GUI_TEXTURED, skin, x, y, 8f, 8f, TILE, TILE, 8, 8, 64, 64, 0xFFFFFFFF);
            d.g.blit(RenderPipelines.GUI_TEXTURED, skin, x, y, 40f, 8f, TILE, TILE, 8, 8, 64, 64, 0xFFFFFFFF);
        } else {
            String name = minecraft.getUser().getName();
            d.round(x, y, TILE, TILE, 4, 0xFF2A2A3C);
            d.textCenter(name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(Locale.ROOT), x + TILE / 2, Draw.textY(y, TILE), Theme.TEXT);
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

        if (Widget.hit(mx, my, searchX(), searchY(), Theme.SEARCH_W, TILE)) {
            searchFocused = true;
            return true;
        }
        searchFocused = false;
        if (Widget.hit(mx, my, friendsX(), tileY(), TILE, TILE)) {
            Module f = mm.find("friends");
            if (f != null) select(f);
            return true;
        }
        if (Widget.hit(mx, my, gearX(), tileY(), TILE, TILE)) {
            selectCategory(Category.CLIENT);
            return true;
        }

        if (my < wy + Theme.TOPBAR_H) {
            for (int i = 0; i < TABS.length; i++) {
                if (Widget.hit(mx, my, tabX[i] - 6, wy, tabW[i] + 12, Theme.TOPBAR_H)) {
                    selectCategory(TABS[i]);
                    return true;
                }
            }
            return true;
        }

        int lx = listX(), ly = listY(), lw = listW();
        if (configsMode()) {
            if (b != 0) return true;
            if (Widget.hit(mx, my, lx, ly, lw, listH())) profiles.clickSidebar(mx, my, lx, ly, lw, doubleClick);
            else if (Widget.hit(mx, my, cX(), cY(), cW(), cH())) profiles.clickContent(mx, my, cX(), cY());
            return true;
        }
        if (Widget.hit(mx, my, lx, ly, lw, listH())) {
            int i = (int) Math.floor((my - ly + listScroll) / Theme.ROW_H);
            if (i >= 0 && i < list.size()) {
                Module m = list.get(i);
                boolean onToggle = mx >= lx + lw - 6 - Theme.SWITCH_W - 4;
                if (!m.alwaysOn() && (b == 1 || (b == 0 && onToggle))) m.toggle();
                else if (b == 0) select(m);
            }
            return true;
        }

        if (selected != null && !selected.alwaysOn()) {
            if (Widget.hit(mx, my, headerToggleX() - 4, headerMidY() - 9, Theme.SWITCH_W + 8, 18)) {
                if (b == 0) selected.toggle();
                return true;
            }
            if (headerBind.mouseClicked(mx, my, b)) return true;
        }

        if (Widget.hit(mx, my, cX(), cardsY(), cW(), cardsH())) {
            double cmy = my + contentScroll;
            for (int i = 0; i < widgets.size(); i++) {
                SettingWidget w = widgets.get(i);
                if (w.mouseClicked(mx, cmy, b)) {
                    active = w;
                    return true;
                }
            }
        }
        return true;
    }

    private double activeY(double my) {
        return active == popup || active == headerBind ? my : my + contentScroll;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        if (active != null) {
            active.mouseReleased(e.x() / k, activeY(e.y() / k));
            active = null;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dragX, double dragY) {
        if (active != null) active.mouseDragged(e.x() / k, activeY(e.y() / k));
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (popup != null) return true;
        double mx = mouseX / k, my = mouseY / k;
        if (Widget.hit(mx, my, listX(), listY(), listW(), listH())) {
            listTarget = clamp((float) (listTarget - scrollY * Theme.ROW_H * 1.5), 0, listMax);
        } else if (Widget.hit(mx, my, cX(), cardsY(), cW(), cardsH())) {
            contentTarget = clamp((float) (contentTarget - scrollY * 28), 0, contentMax);
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
                    if (!list.isEmpty()) select(list.get(0));
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
        if (category != Category.CONFIGS) selectCategory(Category.CONFIGS);
        profiles.filesDropped(paths);
    }

    private void setSearch(String s) {
        search.setLength(0);
        search.append(s);
        onSearchChanged();
    }

    private void onSearchChanged() {
        searchView = search.toString();
        listScroll = listTarget = 0;
        rebuildList();
    }

    private static float clamp(float v, float min, float max) {
        return v < min ? min : Math.min(v, max);
    }
}

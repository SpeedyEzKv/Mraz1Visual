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
    private static final Category[] TABS = Category.values();
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

        Window win = minecraft.getWindow();
        double gui = win.getGuiScale();
        int pw = win.getWidth(), ph = win.getHeight();
        int fit = 1;
        while (pw / (fit + 1) >= Theme.WINDOW_W * 1.15 && ph / (fit + 1) >= Theme.WINDOW_H * 1.15) fit++;
        int tight = fit;
        while (pw / (tight + 1) >= Theme.WINDOW_MIN_W + 16 && ph / (tight + 1) >= Theme.WINDOW_MIN_H + 16) tight++;
        int unit = switch (cs.size.getId()) {
            case "small" -> Math.max(1, fit - 1);
            case "large" -> Math.max(fit, Math.min(tight, fit + 1));
            default -> fit;
        };
        k = (float) (unit / gui);
        d.scale = k;
        vw = (int) (width / k);
        vh = (int) (height / k);

        ww = Math.min(Theme.WINDOW_W, vw - 16);
        wh = Math.min(Theme.WINDOW_H, vh - 16);
        wx = (vw - ww) / 2;
        wy = (vh - wh) / 2;

        int x = wx + Theme.PAD + Fonts.width(BRAND, Fonts.Kind.HEAD) + 22;
        for (int i = 0; i < TABS.length; i++) {
            tabX[i] = x;
            tabW[i] = Fonts.width(TABS[i].title);
            x += tabW[i] + Theme.TAB_GAP;
        }
        ulInit = false;
        rebuildList();
        rebuildContent();
    }

    private int listX() { return wx + 6; }
    private int listY() { return wy + Theme.TOPBAR_H + 6; }
    private int listW() { return Theme.SIDEBAR_W - 12; }
    private int listH() { return wh - Theme.TOPBAR_H - 12; }
    private int cX() { return wx + Theme.SIDEBAR_W; }
    private int cY() { return wy + Theme.TOPBAR_H; }
    private int cW() { return ww - Theme.SIDEBAR_W; }
    private int cH() { return wh - Theme.TOPBAR_H; }
    private int cardsY() { return cY() + Theme.HEADER_H; }
    private int cardsH() { return cH() - Theme.HEADER_H; }
    private int searchX() { return wx + ww - Theme.PAD - Theme.SEARCH_W; }
    private int searchY() { return wy + (Theme.TOPBAR_H - 16) / 2; }
    private int headerToggleX() { return cX() + cW() - Theme.PAD - Theme.SWITCH_W; }
    private int headerMidY() { return cY() + 18; }

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
            category = m.category;
        }
        contentScroll = contentTarget = 0;
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
        headerDesc = Draw.ellipsize(selected.description, cW() - 2 * Theme.PAD - (selected.alwaysOn() ? 0 : 120));
        if (!selected.alwaysOn()) {
            headerBind = new BindWidget(selected.bind, this, false);
            headerBind.setBounds(headerToggleX() - 12 - 60, headerMidY() - 10, 60, 20);
        }

        Map<String, List<Setting<?>>> groups = new LinkedHashMap<>();
        for (Setting<?> s : selected.settings()) groups.computeIfAbsent(s.group(), g -> new ArrayList<>()).add(s);

        int inner = cW() - 2 * Theme.PAD;
        int cols = inner >= 2 * Theme.CARD_MIN_W + Theme.GAP ? 2 : 1;
        int colW = (inner - (cols - 1) * Theme.GAP) / cols;
        int[] colY = new int[cols];
        for (int i = 0; i < cols; i++) colY[i] = cardsY() + 6;

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
        lastNanos = now;
        Anim.enabled = cs.animations.get();
        if (width != layoutW || height != layoutH || cs.size.get() != layoutSize) relayout();

        double rmx = mouseX / k, rmy = mouseY / k;
        boolean overPopup = popup != null && popup.hit(rmx, rmy);
        double mx = overPopup ? -1e6 : rmx, my = overPopup ? -1e6 : rmy;

        listScroll = Anim.to(listScroll, listTarget, dt);
        contentScroll = Anim.to(contentScroll, contentTarget, dt);

        d.begin(g, font, k);
        drawWindow();
        drawTopBar(mx, my, dt, now);
        drawSidebar(mx, my, dt);
        drawContent(mx, my, dt);
        if (popup != null) {
            if (popup.isClosed()) popup = null;
            else {
                g.nextStratum();
                popup.render(d, rmx, rmy, dt);
            }
        }
        d.end();
    }

    private void drawWindow() {
        d.round(wx - 8, wy - 5, ww + 16, wh + 18, Theme.RADIUS + 8, 0x18000000);
        d.round(wx - 3, wy - 1, ww + 6, wh + 7, Theme.RADIUS + 3, Theme.SHADOW);
        d.card(wx, wy, ww, wh, Theme.RADIUS, Theme.WINDOW, Theme.WINDOW_EDGE);
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
            d.text(TABS[i].title, tabX[i], ty, c);
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
        if (!searching && ulInit) {
            int ux = Math.round(ulX), uw = Math.round(ulW);
            d.round(ux - 4, wy + Theme.TOPBAR_H - 4, uw + 8, 4, 2, Colors.withAlpha(Theme.accent(), 0x26));
            d.round(ux, wy + Theme.TOPBAR_H - 2, uw, 2, 1, Theme.accent());
        }

        int sx = searchX(), sy = searchY();
        boolean sh = Widget.hit(mx, my, sx, sy, Theme.SEARCH_W, 16);
        d.card(sx, sy, Theme.SEARCH_W, 16, Theme.RADIUS_SM, sh || searchFocused ? Theme.FIELD_HOVER : Theme.FIELD,
                searchFocused ? Theme.accentSoft() : Theme.CARD_EDGE);
        int textY = Draw.textY(sy, 16);
        if (searching) d.text(searchView, sx + 7, textY, Theme.TEXT);
        else d.text("Поиск", sx + 7, textY, Theme.TEXT_MUTED);
        if (searchFocused && (now / 500_000_000L & 1) == 0) {
            d.rect(sx + 7 + (searching ? d.width(searchView) : 0) + 1, sy + 4, 1, 8, Theme.TEXT);
        }

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
            d.text(m.name, lx + 9, textY, nameColor);
            if (!m.alwaysOn()) {
                int tx = lx + lw - 6 - Theme.SWITCH_W;
                d.toggle(tx, ry + (Theme.ROW_H - Theme.SWITCH_H) / 2, a[1]);
                if (m.bind.isBound()) d.textRight(m.bind.display(), tx - 6, textY, Theme.TEXT_MUTED);
            }
        }
        if (list.isEmpty() && !search.isEmpty()) d.text("Ничего не найдено", lx + 9, Draw.textY(ly, Theme.ROW_H), Theme.TEXT_MUTED);
        d.unclip();
    }

    private void drawContent(double mx, double my, float dt) {
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

        // шапка модуля
        d.textScaled(selected.name, Fonts.Kind.HEAD, cx + Theme.PAD, cy + 10, 1.75f, Theme.TEXT);
        d.text(headerDesc, cx + Theme.PAD, cy + 30, Theme.TEXT_DIM);
        if (!selected.alwaysOn()) {
            headerToggle = Anim.to(headerToggle, selected.isEnabled() ? 1 : 0, dt);
            d.toggle(headerToggleX(), headerMidY() - Theme.SWITCH_H / 2, headerToggle);
            headerBind.render(d, mx, my, dt);
            int labelRight = headerBind.x + headerBind.w - headerBind.buttonWidth(d) - 6;
            d.textRight("Клавиша", labelRight, Draw.textY(headerBind.y, headerBind.h), Theme.TEXT_MUTED);
        }

        // карточки настроек
        int ay = cardsY(), ah = cardsH();
        if (widgets.isEmpty()) {
            d.text("Настроек нет — только вкл/выкл.", cx + Theme.PAD, ay + 10, Theme.TEXT_MUTED);
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

        if (Widget.hit(mx, my, searchX(), searchY(), Theme.SEARCH_W, 16)) {
            searchFocused = true;
            return true;
        }
        searchFocused = false;

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

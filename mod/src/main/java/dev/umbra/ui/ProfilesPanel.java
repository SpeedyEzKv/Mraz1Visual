package dev.umbra.ui;

import dev.umbra.Umbra;
import dev.umbra.config.ConfigManager;
import dev.umbra.config.ConfigManager.ProfileInfo;
import dev.umbra.core.Category;
import dev.umbra.core.Module;
import dev.umbra.ui.widget.TextInput;
import dev.umbra.ui.widget.Widget;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Вкладка Configs: слева список профилей, справа выбранный профиль.
 * Всё считается при смене выбора, а не в кадре.
 */
final class ProfilesPanel {
    private static final int ROW_H = 24;
    private static final String[] COUNTS = new String[100];

    static {
        for (int i = 0; i < COUNTS.length; i++) COUNTS[i] = Integer.toString(i);
    }
    private static final int BANNER_H = 64;

    private final ConfigManager cfg = Umbra.CONFIG;
    private List<ProfileInfo> profiles = new ArrayList<>();
    private String selected = "";

    // кэш выбранного
    private ProfileInfo info;
    private final Map<Category, List<String>> inside = new EnumMap<>(Category.class);
    private int insideCount;
    private String chipModules = "", chipUpdated = "", chipSize = "", insideTitle = "", countLabel = "";

    private final TextInput rename = new TextInput("Название", 32);
    private boolean renaming;

    private String status = "";
    private long statusUntil;
    private long deleteArmedUntil;
    private final float[] hover = new float[8];
    private final float[] rowHover = new float[ConfigManager.MAX_PROFILES + 4];

    // прямоугольники кнопок (считаются в кадре, ловятся в клике)
    private final int[] bx = new int[8], by = new int[8], bw = new int[8];
    private static final int B_NEW = 0, B_IMPORT = 1, B_ENABLE = 2, B_COPY = 3, B_EXPORT = 4, B_RESET = 5, B_DELETE = 6;
    private static final int BTN_H = 16;

    void refresh() {
        profiles = cfg.list();
        countLabel = profiles.size() + "/" + ConfigManager.MAX_PROFILES;
        if (find(selected) == null) selected = cfg.activeFile();
        rebuildSelected();
    }

    private ProfileInfo find(String file) {
        for (ProfileInfo p : profiles) if (p.file().equals(file)) return p;
        return null;
    }

    private void rebuildSelected() {
        info = find(selected);
        inside.clear();
        insideCount = 0;
        renaming = false;
        if (info == null) return;
        List<String> ids = cfg.enabledIn(info.file());
        for (String id : ids) {
            Module m = Umbra.MODULES.find(id);
            if (m == null) continue;
            inside.computeIfAbsent(m.category, c -> new ArrayList<>()).add(m.name);
            insideCount++;
        }
        chipModules = insideCount + " " + plural(insideCount, "модуль", "модуля", "модулей");
        chipUpdated = "Изменён " + ago(info.updated());
        insideTitle = "ВНУТРИ · " + chipModules.toUpperCase(Locale.ROOT);
        chipSize = String.format(Locale.ROOT, "%.1f КБ", info.size() / 1024f).replace('.', ',');
    }

    private void say(String s) {
        status = s;
        statusUntil = System.currentTimeMillis() + 4000;
    }

    // ── рисование ────────────────────────────────────────────

    void renderSidebar(Draw d, double mx, double my, float dt, int lx, int ly, int lw, int lh) {
        d.text("МОИ КОНФИГИ", Fonts.Kind.HEAD, lx + 4, ly + 4, Theme.TEXT_MUTED);
        d.textRight(countLabel, lx + lw - 4, ly + 4, Theme.TEXT_MUTED);

        int half = (lw - 6) / 2;
        button(d, B_NEW, "+ Новый", lx, ly + 18, half, mx, my, dt, true);
        button(d, B_IMPORT, "Из файла", lx + half + 6, ly + 18, half, mx, my, dt, true);

        int y0 = ly + 42;
        for (int i = 0; i < profiles.size(); i++) {
            ProfileInfo p = profiles.get(i);
            int ry = y0 + i * ROW_H;
            if (ry + ROW_H > ly + lh) break;
            boolean hov = Widget.hit(mx, my, lx, ry, lw, ROW_H);
            rowHover[i] = Anim.to(rowHover[i], hov ? 1 : 0, dt);
            boolean sel = p.file().equals(selected);
            if (sel) d.card(lx, ry + 1, lw, ROW_H - 2, Theme.RADIUS_SM + 1, 0xFF15151E, Theme.CARD_EDGE);
            else if (rowHover[i] > 0.01f) d.round(lx, ry + 1, lw, ROW_H - 2, Theme.RADIUS_SM, Colors.mulAlpha(Theme.SELECTED, rowHover[i] * 0.6f));
            boolean active = p.file().equals(cfg.activeFile());
            d.round(lx + 5, ry + 5, 14, 14, 3, active ? Theme.accentSoft() : Theme.FIELD);
            d.textCenter(initial(p.name()), lx + 12, ry + 8, active ? Theme.TEXT : Theme.TEXT_DIM);
            d.text(p.name(), lx + 25, ry + 4, sel || active ? Theme.TEXT : Theme.TEXT_DIM);
            if (active) {
                d.circle(lx + 27, ry + 17, 3, Theme.accent());
                d.text("Включён", lx + 32, ry + 13, Theme.TEXT_MUTED);
            }
        }
    }

    void renderContent(Draw d, double mx, double my, float dt, int cx, int cy, int cw, int ch) {
        int x = cx + Theme.PAD, w = cw - 2 * Theme.PAD, y = cy + Theme.PAD;
        if (info == null) {
            d.textCenter("Выбери профиль слева", cx + cw / 2, cy + ch / 2 - 4, Theme.TEXT_MUTED);
            return;
        }
        boolean active = info.file().equals(cfg.activeFile());

        // баннер
        d.card(x, y, w, BANNER_H, Theme.RADIUS, 0xFF141420, Colors.withAlpha(Theme.accent(), 0x40));
        d.gradientV(x + 1, y + 1, w - 2, BANNER_H - 2, Colors.withAlpha(Theme.accent(), 0x30), 0x00000000);
        // «созвездие» как на скрине — несколько точек, соединённых линией
        constellation(d, x + w - 150, y + 14);
        if (renaming) {
            rename.setBounds(x + 10, y + 12, Math.min(220, w - 120), 20);
            rename.render(d, mx, my, dt);
        } else {
            d.textScaled(info.name(), Fonts.Kind.HEAD, x + 12, y + 16, 1.75f, Theme.TEXT);
            int nw = Math.round(Fonts.width(info.name(), Fonts.Kind.HEAD) * 1.75f);
            d.text("✎", x + 18 + nw, y + 20, Theme.TEXT_MUTED);
        }
        int chx = x + 12, chy = y + BANNER_H - 20;
        chx = chip(d, chipModules, chx, chy) + 4;
        chx = chip(d, chipUpdated, chx, chy) + 4;
        chip(d, chipSize, chx, chy);

        // статус / включить
        if (active) {
            int sw = Fonts.width("Включён") + 20;
            d.round(x + w - sw - 10, y + 10, sw, 16, 8, 0x40000000);
            d.circle(x + w - sw - 1, y + 18, 4, Theme.accent());
            d.text("Включён", x + w - sw + 6, y + 14, Theme.TEXT);
            bw[B_ENABLE] = 0;
        } else {
            int ew = Fonts.width("Включить") + 20;
            button(d, B_ENABLE, "Включить", x + w - ew - 10, y + 10, ew, mx, my, dt, true);
        }

        // действия
        int ay = y + BANNER_H + 8;
        int ax = x;
        ax = textButton(d, B_COPY, "+ Копия", ax, ay, mx, my, dt, true) + 14;
        ax = textButton(d, B_EXPORT, "В файл", ax, ay, mx, my, dt, true) + 14;
        if (active) ax = textButton(d, B_RESET, "Сбросить", ax, ay, mx, my, dt, true) + 14;
        else bw[B_RESET] = 0;
        boolean armed = System.currentTimeMillis() < deleteArmedUntil;
        boolean canDelete = !active && profiles.size() > 1;
        textButton(d, B_DELETE, armed ? "Точно удалить?" : "Удалить", ax, ay, mx, my, dt, canDelete);

        // внутри
        int iy = ay + 22;
        d.text(insideTitle, Fonts.Kind.HEAD, x, iy, Theme.TEXT_MUTED);
        d.rect(x, iy + 13, w, 1, Theme.DIVIDER);
        iy += 22;
        for (Category c : Category.values()) {
            if (c == Category.CONFIGS || c == Category.CLIENT) continue;
            List<String> names = inside.get(c);
            int n = names == null ? 0 : names.size();
            if (iy + 30 > cy + ch - 16) break;
            d.text(c.title, Fonts.Kind.BOLD, x, iy, Theme.TEXT);
            d.text(COUNTS[Math.min(n, COUNTS.length - 1)], x + Fonts.width(c.title, Fonts.Kind.BOLD) + 6, iy, Theme.TEXT_MUTED);
            iy += 12;
            if (n == 0) {
                d.text("Ничего не включено", x, iy, Theme.TEXT_MUTED);
                iy += 16;
            } else {
                int px = x;
                for (String name : names) {
                    int cwid = Fonts.width(name) + 14;
                    if (px + cwid > x + w) {
                        px = x;
                        iy += 18;
                    }
                    d.card(px, iy - 2, cwid, 14, 7, 0xFF16161F, Theme.CARD_EDGE);
                    d.text(name, px + 7, iy + 1, Theme.TEXT_DIM);
                    px += cwid + 4;
                }
                iy += 20;
            }
        }

        if (System.currentTimeMillis() < statusUntil) d.text(status, x, cy + ch - 14, Theme.TEXT_DIM);
        else d.text("Перетащи .json профиля в окно, чтобы добавить его.", x, cy + ch - 14, Theme.TEXT_MUTED);
    }

    private static void constellation(Draw d, int x, int y) {
        int[] px = {0, 40, 62, 96, 130}, py = {28, 10, 30, 22, 6};
        for (int i = 0; i + 1 < px.length; i++) line(d, x + px[i], y + py[i], x + px[i + 1], y + py[i + 1]);
        for (int i = 0; i < px.length; i++) d.circle(x + px[i], y + py[i], i == 1 ? 5 : 3, 0xE6FFFFFF);
    }

    private static void line(Draw d, int x0, int y0, int x1, int y1) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        for (int i = 0; i <= steps; i += 2) {
            int xx = x0 + (x1 - x0) * i / Math.max(1, steps), yy = y0 + (y1 - y0) * i / Math.max(1, steps);
            d.rect(xx, yy, 1, 1, 0x50FFFFFF);
        }
    }

    private static int chip(Draw d, String s, int x, int y) {
        int w = Fonts.width(s) + 12;
        d.round(x, y, w, 13, 6, 0x50000000);
        d.text(s, x + 6, y + 3, Theme.TEXT_DIM);
        return x + w;
    }

    private void button(Draw d, int id, String s, int x, int y, int w, double mx, double my, float dt, boolean enabled) {
        bx[id] = x; by[id] = y; bw[id] = w;
        boolean hov = enabled && Widget.hit(mx, my, x, y, w, BTN_H);
        hover[id] = Anim.to(hover[id], hov ? 1 : 0, dt);
        d.card(x, y, w, BTN_H, Theme.RADIUS_SM, Colors.lerp(Theme.FIELD, Theme.FIELD_HOVER, hover[id]), Theme.CARD_EDGE);
        d.textCenter(s, x + w / 2, y + 4, enabled ? Theme.TEXT : Theme.TEXT_MUTED);
    }

    private int textButton(Draw d, int id, String s, int x, int y, double mx, double my, float dt, boolean enabled) {
        int w = Fonts.width(s);
        bx[id] = x; by[id] = y - 3; bw[id] = enabled ? w : 0;
        boolean hov = enabled && Widget.hit(mx, my, x, y - 3, w, BTN_H);
        hover[id] = Anim.to(hover[id], hov ? 1 : 0, dt);
        int c = enabled ? Colors.lerp(Theme.TEXT_DIM, Theme.TEXT, hover[id]) : Colors.withAlpha(Theme.TEXT_MUTED, 0x80);
        d.text(s, x, y, c);
        return x + w;
    }

    private boolean hitBtn(int id, double mx, double my) {
        return bw[id] > 0 && Widget.hit(mx, my, bx[id], by[id], bw[id], BTN_H);
    }

    // ── ввод ─────────────────────────────────────────────────

    /** Клик в области списка. */
    boolean clickSidebar(double mx, double my, int lx, int ly, int lw, boolean doubleClick) {
        if (hitBtn(B_NEW, mx, my)) {
            String f = cfg.create("Новый профиль");
            if (f == null) say("Максимум " + ConfigManager.MAX_PROFILES + " профилей.");
            else {
                selected = f;
                refresh();
                renaming = true;
                rename.set(info == null ? "" : info.name());
                rename.focused = true;
            }
            return true;
        }
        if (hitBtn(B_IMPORT, mx, my)) {
            say("Перетащи .json профиля прямо в это окно.");
            return true;
        }
        int y0 = ly + 42;
        int i = (int) Math.floor((my - y0) / ROW_H);
        if (i >= 0 && i < profiles.size() && mx >= lx && mx < lx + lw) {
            String f = profiles.get(i).file();
            if (!f.equals(selected)) {
                selected = f;
                deleteArmedUntil = 0;
                rebuildSelected();
            }
            if (doubleClick) enable();
            return true;
        }
        return false;
    }

    /** Клик в области содержимого. */
    boolean clickContent(double mx, double my, int cx, int cy) {
        if (info == null) return false;
        if (renaming) {
            if (!rename.mouseClicked(mx, my, 0)) commitRename();
            return true;
        }
        int x = cx + Theme.PAD, y = cy + Theme.PAD;
        int nw = Math.round(Fonts.width(info.name(), Fonts.Kind.HEAD) * 1.75f) + 16;
        if (Widget.hit(mx, my, x + 8, y + 10, nw, 24)) {
            renaming = true;
            rename.set(info.name());
            rename.focused = true;
            return true;
        }
        if (hitBtn(B_ENABLE, mx, my)) { enable(); return true; }
        if (hitBtn(B_COPY, mx, my)) {
            String f = cfg.duplicate(info.file());
            if (f == null) say("Максимум " + ConfigManager.MAX_PROFILES + " профилей.");
            else { selected = f; refresh(); say("Копия создана."); }
            return true;
        }
        if (hitBtn(B_EXPORT, mx, my)) {
            Path p = cfg.export(info.file());
            say(p == null ? "Не удалось сохранить файл." : "Сохранено: config/umbra/exports/" + p.getFileName());
            return true;
        }
        if (hitBtn(B_RESET, mx, my)) {
            cfg.resetActive();
            refresh();
            say("Профиль сброшен: всё выключено, настройки по умолчанию.");
            return true;
        }
        if (hitBtn(B_DELETE, mx, my)) {
            long now = System.currentTimeMillis();
            if (now > deleteArmedUntil) {
                deleteArmedUntil = now + 3000;
            } else {
                deleteArmedUntil = 0;
                if (cfg.delete(info.file())) {
                    selected = cfg.activeFile();
                    refresh();
                    say("Профиль удалён.");
                }
            }
            return true;
        }
        return false;
    }

    private void enable() {
        if (info == null || info.file().equals(cfg.activeFile())) return;
        cfg.switchTo(info.file());
        refresh();
        say("Профиль «" + (info == null ? "" : info.name()) + "» включён.");
    }

    private void commitRename() {
        renaming = false;
        rename.focused = false;
        if (info != null && !rename.value().isBlank()) {
            cfg.rename(info.file(), rename.value());
            refresh();
        }
    }

    boolean isTyping() { return renaming; }

    boolean keyPressed(int key, boolean ctrl, Supplier<String> clipboard) {
        if (!renaming) return false;
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            commitRename();
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            renaming = false;
            return true;
        }
        rename.keyPressed(key, ctrl, clipboard);
        return true;
    }

    boolean charTyped(int cp) {
        return renaming && rename.charTyped(cp);
    }

    void filesDropped(List<Path> files) {
        int ok = 0;
        String last = null;
        for (Path p : files) {
            if (!p.toString().toLowerCase(Locale.ROOT).endsWith(".json")) continue;
            String f = cfg.importFile(p);
            if (f != null) { ok++; last = f; }
        }
        if (last != null) selected = last;
        refresh();
        say(ok > 0 ? "Добавлено профилей: " + ok : "Не похоже на профиль Umbra.");
    }

    // ── мелочи ───────────────────────────────────────────────

    private static String initial(String s) {
        return s.isEmpty() ? "?" : s.substring(0, 1).toUpperCase(Locale.ROOT);
    }

    static String ago(long t) {
        long s = Math.max(0, (System.currentTimeMillis() - t) / 1000);
        if (s < 60) return "только что";
        long m = s / 60;
        if (m < 60) return m + " " + plural((int) m, "минуту", "минуты", "минут") + " назад";
        long h = m / 60;
        if (h < 24) return h + " " + plural((int) h, "час", "часа", "часов") + " назад";
        long dd = h / 24;
        return dd + " " + plural((int) dd, "день", "дня", "дней") + " назад";
    }

    static String plural(int n, String one, String few, String many) {
        int a = Math.abs(n) % 100, b = a % 10;
        if (a > 10 && a < 20) return many;
        if (b == 1) return one;
        if (b >= 2 && b <= 4) return few;
        return many;
    }
}

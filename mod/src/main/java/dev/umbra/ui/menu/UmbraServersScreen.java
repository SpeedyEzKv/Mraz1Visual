package dev.umbra.ui.menu;

import dev.umbra.ui.Anim;
import dev.umbra.ui.Colors;
import dev.umbra.ui.Draw;
import dev.umbra.ui.Fonts;
import dev.umbra.ui.Theme;
import dev.umbra.ui.widget.TextInput;
import dev.umbra.ui.widget.Widget;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Список серверов: слева разделы, справа список / форма. Двойной клик — зайти. */
public final class UmbraServersScreen extends MenuBase {
    private enum Mode { LIST, ADD, DIRECT }

    private static final int ROW_H = 28;

    private final Screen parent;
    private final ServerPing ping = new ServerPing();
    private ServerList servers;
    private final List<Row> rows = new ArrayList<>();
    private Mode mode = Mode.LIST;
    private Item itList, itAdd, itDirect;

    private int px, py, pw, ph;
    private float scroll, scrollTarget;
    private int selected = -1;
    private long deleteArmedUntil;

    private final TextInput nameIn = new TextInput("Мой сервер", 48);
    private final TextInput addrIn = new TextInput("mc.example.net", 128);
    private final TextInput directIn = new TextInput("mc.example.net", 128);
    private String formError = "";
    private final float[] actHover = new float[4];

    /** Кэш отрисовки строки: пересчитывается только при изменении данных сервера. */
    private static final class Row {
        final ServerData data;
        Component lastMotd;
        List<FormattedCharSequence> motd = List.of();
        String status = "";
        long lastPing = Long.MIN_VALUE;
        int lastOnline = -1;
        ServerData.State lastState;
        String letter;
        String nameView = "";
        int nameW = -1;
        float hover;

        Row(ServerData data) {
            this.data = data;
            this.letter = data.name.isEmpty() ? "?" : data.name.substring(0, 1).toUpperCase();
        }
    }

    public UmbraServersScreen(Screen parent) {
        super("Серверы");
        this.parent = parent;
        itList = item("СЕРВЕРЫ", () -> setMode(Mode.LIST));
        itAdd = item("ДОБАВИТЬ", () -> minecraft.setScreen(new UmbraAddServerScreen(this, null, this::addServer)));
        itDirect = item("ПО АДРЕСУ", () -> setMode(Mode.DIRECT));
        itemAfterGap("НАЗАД", this::onClose);
        itList.selected = true;
    }

    @Override protected float sunScreenX() { return 0.50f; }
    @Override protected float sunScreenY() { return 0.02f; }

    @Override
    protected void init() {
        if (servers == null) {
            servers = new ServerList(minecraft);
            servers.load();
            reloadRows(true);
            directIn.set(minecraft.options.lastMpIp == null ? "" : minecraft.options.lastMpIp);
        }
        super.init();
    }

    @Override
    protected void onLayout() {
        int lx = Math.max(24, Math.round(vw * 0.06f));
        layoutItems(lx, Math.round(vh * 0.34f));
        int itemsRight = lx;
        for (Item it : items) itemsRight = Math.max(itemsRight, it.x + it.w);
        px = Math.max(itemsRight + 40, Math.round(vw * 0.27f));
        py = Math.round(vh * 0.38f);
        pw = vw - px - Math.max(24, Math.round(vw * 0.06f));
        ph = vh - py - 48;
        int fw = Math.min(280, pw);
        nameIn.setBounds(px, py + 16, fw, 18);
        addrIn.setBounds(px, py + 58, fw, 18);
        directIn.setBounds(px, py + 16, fw, 18);
    }

    private void reloadRows(boolean pingAll) {
        rows.clear();
        for (int i = 0; i < servers.size(); i++) {
            Row r = new Row(servers.get(i));
            rows.add(r);
            if (pingAll) ping.ping(r.data, servers::save);
        }
        if (selected >= rows.size()) selected = rows.size() - 1;
    }

    private void setMode(Mode m) {
        mode = m;
        itList.selected = m == Mode.LIST;
        itAdd.selected = m == Mode.ADD;
        itDirect.selected = m == Mode.DIRECT;
        formError = "";
        nameIn.focused = m == Mode.ADD;
        addrIn.focused = false;
        directIn.focused = m == Mode.DIRECT;
        if (m == Mode.ADD) {
            nameIn.set("");
            addrIn.set("");
        }
    }

    // ── действия ─────────────────────────────────────────────

    private void join(ServerData data) {
        ConnectScreen.startConnecting(this, minecraft, ServerAddress.parseString(data.ip), data, false, null);
    }

    private void submitAdd() {
        String addr = addrIn.value().trim();
        if (!ServerAddress.isValidAddress(addr)) {
            formError = "Проверь адрес";
            addrIn.focused = true;
            nameIn.focused = false;
            return;
        }
        String name = nameIn.value().trim().isEmpty() ? addr : nameIn.value().trim();
        ServerData data = new ServerData(name, addr, ServerData.Type.OTHER);
        servers.add(data, false);
        servers.save();
        reloadRows(false);
        ping.ping(data, servers::save);
        selected = rows.size() - 1;
        setMode(Mode.LIST);
    }

    private void addServer(ServerData data) {
        servers.add(data, false);
        servers.save();
        reloadRows(false);
        ping.ping(data, servers::save);
        selected = rows.size() - 1;
    }

    private void editSelected() {
        if (selected < 0 || selected >= rows.size()) return;
        ServerData data = rows.get(selected).data;
        minecraft.setScreen(new UmbraAddServerScreen(this, data, d -> {
            servers.save();
            reloadRows(false);
            ping.ping(d, servers::save);
        }));
    }

    private void submitDirect() {
        String addr = directIn.value().trim();
        if (!ServerAddress.isValidAddress(addr)) {
            formError = "Проверь адрес";
            return;
        }
        minecraft.options.lastMpIp = addr;
        minecraft.options.save();
        join(new ServerData("Сервер", addr, ServerData.Type.OTHER));
    }

    private void refresh() {
        for (Row r : rows) ping.ping(r.data, servers::save);
    }

    private void deleteSelected() {
        if (selected < 0 || selected >= rows.size()) return;
        long now = System.currentTimeMillis();
        if (now > deleteArmedUntil) {
            deleteArmedUntil = now + 3000;
            return;
        }
        deleteArmedUntil = 0;
        servers.remove(rows.get(selected).data);
        servers.save();
        reloadRows(false);
        selected = Math.min(selected, rows.size() - 1);
    }

    // ── рисование ────────────────────────────────────────────

    @Override
    protected void renderMenu(double mx, double my, float dt) {
        d.gradientV(0, Math.round(vh * 0.25f), vw, vh - Math.round(vh * 0.25f), 0x00000000, 0xB4000000);
        renderItems(mx, my, dt);
        renderBrand(Math.max(24, Math.round(vw * 0.056f)));
        switch (mode) {
            case LIST -> renderList(mx, my, dt);
            case ADD -> renderAdd(mx, my, dt);
            case DIRECT -> renderDirect(mx, my, dt);
        }
    }

    private int label(String s, int x, int y) {
        d.text(s, Fonts.Kind.HEAD, x, y, Colors.mulAlpha(Theme.TEXT_MUTED, fade));
        return y;
    }

    private void renderList(double mx, double my, float dt) {
        label("МОИ СЕРВЕРЫ", px, py - 16);
        scroll = Anim.to(scroll, scrollTarget, dt);
        int listH = ph - 24;
        if (rows.isEmpty()) {
            d.text("Пусто. Нажми «Добавить» слева.", px, py + 6, Theme.TEXT_MUTED);
        } else {
            d.clip(px - 6, py, pw + 12, listH);
            int sc = Math.round(scroll);
            for (int i = 0; i < rows.size(); i++) {
                int ry = py + i * ROW_H - sc;
                if (ry + ROW_H < py) continue;
                if (ry > py + listH) break;
                renderRow(rows.get(i), i, ry, mx, my, dt);
            }
            d.unclip();
        }

        // действия внизу справа
        int ay = py + ph - 14;
        int right = px + pw;
        boolean has = selected >= 0 && selected < rows.size();
        boolean armed = System.currentTimeMillis() < deleteArmedUntil;
        right = action(2, "ОБНОВИТЬ", right, ay, mx, my, dt, true);
        right = action(1, armed ? "ТОЧНО?" : "УДАЛИТЬ", right - 22, ay, mx, my, dt, has);
        right = action(3, "ИЗМЕНИТЬ", right - 22, ay, mx, my, dt, has);
        action(0, "ИГРАТЬ", right - 22, ay, mx, my, dt, has);
    }

    private final int[] actX = new int[4], actW = new int[4];
    private int actY;

    /** Текстовая кнопка, выровненная по правому краю. Возвращает её левый край. */
    private int action(int idx, String text, int right, int y, double mx, double my, float dt, boolean enabled) {
        int w = Fonts.width(text, Fonts.Kind.HEAD);
        int x = right - w;
        actX[idx] = x;
        actW[idx] = w;
        actY = y;
        boolean hov = enabled && Widget.hit(mx, my, x - 4, y - 4, w + 8, 16);
        actHover[idx] = Anim.to(actHover[idx], hov ? 1 : 0, dt);
        int base = enabled ? Theme.TEXT_DIM : Colors.withAlpha(Theme.TEXT_MUTED, 0x80);
        d.text(text, Fonts.Kind.HEAD, x, y, Colors.mulAlpha(Colors.lerp(base, Theme.TEXT, actHover[idx]), fade));
        return x;
    }

    private void renderRow(Row r, int i, int ry, double mx, double my, float dt) {
        updateRow(r);
        boolean hov = Widget.hit(mx, my, px - 6, ry, pw + 12, ROW_H) && my >= py && my < py + ph - 24;
        r.hover = Anim.to(r.hover, hov ? 1 : 0, dt);
        if (i == selected) d.round(px - 6, ry + 1, pw + 12, ROW_H - 2, Theme.RADIUS_SM, 0x14FFFFFF);
        else if (r.hover > 0.01f) d.round(px - 6, ry + 1, pw + 12, ROW_H - 2, Theme.RADIUS_SM, Colors.mulAlpha(0x0CFFFFFF, r.hover));
        if (i == selected) d.round(px - 6, ry + 6, 2, ROW_H - 12, 1, Theme.accent());

        int ix = px, iy = ry + (ROW_H - 18) / 2;
        d.round(ix, iy, 18, 18, 4, Theme.FIELD);
        d.textCenter(r.letter, ix + 9, iy + 5, Theme.TEXT_DIM);

        int tx = px + 26;
        d.text(r.nameView, Fonts.Kind.BOLD, tx, ry + 5, Colors.mulAlpha(Theme.TEXT, fade));
        if (!r.motd.isEmpty()) {
            d.g.drawString(font, r.motd.get(0), tx, ry + 16, Colors.mulAlpha(0xFFB0B0C0, fade), false);
        }
        d.textRight(r.status, px + pw, ry + 5, Colors.mulAlpha(Theme.TEXT_MUTED, fade));
    }

    private void updateRow(Row r) {
        ServerData s = r.data;
        if (r.nameW != pw) {
            r.nameW = pw;
            r.nameView = Draw.ellipsize(s.name, pw / 2);
            r.lastMotd = null;
        }
        if (s.motd != r.lastMotd) {
            r.lastMotd = s.motd;
            r.motd = s.motd == null ? List.of() : font.split(s.motd, Math.max(40, pw - 26 - 90));
        }
        ServerData.State st = s.state();
        int online = s.players == null ? -1 : s.players.online();
        if (st != r.lastState || s.ping != r.lastPing || online != r.lastOnline) {
            r.lastState = st;
            r.lastPing = s.ping;
            r.lastOnline = online;
            r.status = switch (st) {
                case PINGING, INITIAL -> "…";
                case UNREACHABLE -> "нет ответа";
                case INCOMPATIBLE -> "другая версия";
                case SUCCESSFUL -> (s.players == null ? "" : s.players.online() + "/" + s.players.max() + "  ·  ")
                        + (s.ping >= 0 ? s.ping + " мс" : "");
            };
        }
    }

    private void renderAdd(double mx, double my, float dt) {
        label("НАЗВАНИЕ", px, py);
        nameIn.render(d, mx, my, dt);
        label("АДРЕС", px, py + 42);
        addrIn.render(d, mx, my, dt);
        renderSubmit("ДОБАВИТЬ", py + 90, mx, my, dt);
    }

    private void renderDirect(double mx, double my, float dt) {
        label("АДРЕС СЕРВЕРА", px, py);
        directIn.render(d, mx, my, dt);
        renderSubmit("ПОДКЛЮЧИТЬСЯ", py + 48, mx, my, dt);
    }

    private int submitY;
    private float submitHover;

    private void renderSubmit(String text, int y, double mx, double my, float dt) {
        submitY = y;
        int w = Fonts.width(text, Fonts.Kind.HEAD) + 24;
        boolean hov = Widget.hit(mx, my, px, y, w, 20);
        submitHover = Anim.to(submitHover, hov ? 1 : 0, dt);
        d.round(px, y, w, 20, Theme.RADIUS_SM, Colors.lerp(0xFFE4E4F2, 0xFFFFFFFF, submitHover));
        d.text(text, Fonts.Kind.HEAD, px + 12, y + 6, 0xFF0A0A0E);
        if (!formError.isEmpty()) d.text(formError, px + w + 12, y + 6, Theme.TEXT);
    }

    private boolean hitSubmit(double mx, double my, String text) {
        int w = Fonts.width(text, Fonts.Kind.HEAD) + 24;
        return Widget.hit(mx, my, px, submitY, w, 20);
    }

    // ── ввод ─────────────────────────────────────────────────

    @Override
    public void tick() {
        ping.tick();
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public void removed() {
        super.removed();
        ping.stop();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        double mx = e.x() / k, my = e.y() / k;
        if (e.button() != 0 && e.button() != 1) return false;
        if (e.button() == 0 && clickItems(mx, my)) return true;

        switch (mode) {
            case LIST -> {
                for (int i = 0; i < 4; i++) {
                    if (e.button() == 0 && Widget.hit(mx, my, actX[i] - 4, actY - 4, actW[i] + 8, 16)) {
                        if (i == 3) editSelected();
                        else if (i == 2) refresh();
                        else if (i == 1) deleteSelected();
                        else if (selected >= 0 && selected < rows.size()) join(rows.get(selected).data);
                        return true;
                    }
                }
                if (Widget.hit(mx, my, px - 6, py, pw + 12, ph - 24)) {
                    int i = (int) Math.floor((my - py + scroll) / ROW_H);
                    if (i >= 0 && i < rows.size()) {
                        if (selected != i) deleteArmedUntil = 0;
                        selected = i;
                        if (e.button() == 1) deleteSelected();
                        else if (doubleClick) join(rows.get(i).data);
                    }
                    return true;
                }
            }
            case ADD -> {
                nameIn.mouseClicked(mx, my, 0);
                addrIn.mouseClicked(mx, my, 0);
                if (hitSubmit(mx, my, "ДОБАВИТЬ")) submitAdd();
                return true;
            }
            case DIRECT -> {
                directIn.mouseClicked(mx, my, 0);
                if (hitSubmit(mx, my, "ПОДКЛЮЧИТЬСЯ")) submitDirect();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mode != Mode.LIST) return false;
        int max = Math.max(0, rows.size() * ROW_H - (ph - 24));
        scrollTarget = (float) Math.max(0, Math.min(max, scrollTarget - scrollY * ROW_H));
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        int key = e.key();
        boolean ctrl = (e.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0;
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (mode != Mode.LIST) setMode(Mode.LIST);
            else onClose();
            return true;
        }
        boolean enter = key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER;
        switch (mode) {
            case LIST -> {
                if (enter && selected >= 0 && selected < rows.size()) join(rows.get(selected).data);
                else if (key == GLFW.GLFW_KEY_DOWN && !rows.isEmpty()) selected = Math.min(rows.size() - 1, selected + 1);
                else if (key == GLFW.GLFW_KEY_UP && !rows.isEmpty()) selected = Math.max(0, selected - 1);
                else if (key == GLFW.GLFW_KEY_DELETE) deleteSelected();
                else return false;
                return true;
            }
            case ADD -> {
                if (enter) submitAdd();
                else if (key == GLFW.GLFW_KEY_TAB) {
                    boolean toAddr = nameIn.focused;
                    nameIn.focused = !toAddr;
                    addrIn.focused = toAddr;
                } else {
                    nameIn.keyPressed(key, ctrl, minecraft.keyboardHandler::getClipboard);
                    addrIn.keyPressed(key, ctrl, minecraft.keyboardHandler::getClipboard);
                }
                return true;
            }
            case DIRECT -> {
                if (enter) submitDirect();
                else directIn.keyPressed(key, ctrl, minecraft.keyboardHandler::getClipboard);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean charTyped(CharacterEvent e) {
        int cp = e.codepoint();
        return switch (mode) {
            case ADD -> nameIn.charTyped(cp) | addrIn.charTyped(cp);
            case DIRECT -> directIn.charTyped(cp);
            default -> false;
        };
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

package dev.umbra.ui.menu;

import dev.umbra.Umbra;
import dev.umbra.account.AccountStore;
import dev.umbra.account.MicrosoftAuth;
import dev.umbra.overlay.Screenshots;
import dev.umbra.ui.Anim;
import dev.umbra.ui.Colors;
import dev.umbra.ui.Draw;
import dev.umbra.ui.Fonts;
import dev.umbra.ui.Theme;
import dev.umbra.ui.widget.Widget;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/** Главное меню: затмение справа, пункты слева, аккаунт сверху справа. */
public final class UmbraTitleScreen extends MenuBase {
    private static final int PANEL_W = 240;

    private int left;
    private final dev.umbra.music.MusicWidget music = new dev.umbra.music.MusicWidget();

    private int musicX() { return vw - 36 - dev.umbra.music.MusicWidget.W; }
    private int musicY() { return vh - 36 - dev.umbra.music.MusicWidget.H; }
    private boolean accountsOpen;
    private float panelAnim;
    private final float[] rowHover = new float[16];
    private float loginHover, openHover, cancelHover;

    public UmbraTitleScreen() {
        super("Umbra");
        item("ОДИНОЧНАЯ ИГРА", () -> minecraft.setScreen(new SelectWorldScreen(this)));
        item("СЕТЕВАЯ ИГРА", () -> minecraft.setScreen(new UmbraServersScreen(this)));
        itemAfterGap("АККАУНТЫ", () -> accountsOpen = !accountsOpen);
        item("НАСТРОЙКИ", () -> minecraft.setScreen(new UmbraOptionsScreen(this)));
        item("UMBRA", () -> Umbra.openMenu(minecraft));
        itemAfterGap("ВЫХОД", () -> minecraft.stop());
    }

    @Override protected float sunScreenX() { return 0.66f; }
    @Override protected float sunScreenY() { return 0.364f; }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    protected void onLayout() {
        left = Math.max(24, Math.round(vw * 0.056f));
        layoutItems(left, Math.round(vh * 0.19f));
    }

    private boolean currentIsMicrosoft() {
        for (AccountStore.Account a : AccountStore.accounts()) if (AccountStore.isCurrent(a)) return true;
        return false;
    }

    @Override
    protected void renderMenu(double mx, double my, float dt) {
        items.get(2).selected = accountsOpen;
        renderItems(mx, my, dt);
        renderBrand(left);

        // аккаунт: ник, тип, плашка с буквой
        String name = minecraft.getUser().getName();
        int tile = 24, tx = vw - 36 - tile, ty = 24;
        d.round(tx, ty, tile, tile, 3, Colors.mulAlpha(0xFF2A2A3C, fade));
        d.textCenter(name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(), tx + tile / 2, ty + 8, Colors.mulAlpha(ITEM_HOVER, fade));
        int right = tx - 9;
        d.text(name, Fonts.Kind.BOLD, right - Fonts.width(name, Fonts.Kind.BOLD), ty + 2, Colors.mulAlpha(0xFFE6E6F4, fade));
        d.textRight(currentIsMicrosoft() ? "Microsoft" : "локальная сессия", right, ty + 14, Colors.mulAlpha(0xFF8686A2, fade));

        if (dev.umbra.music.MediaClient.available()) music.render(d, musicX(), musicY(), fade, true, mx, my);

        panelAnim = Anim.to(panelAnim, accountsOpen ? 1 : 0, dt);
        if (panelAnim > 0.01f) renderAccounts(mx, my, dt, ty + tile + 12);
    }

    // ── панель аккаунтов ─────────────────────────────────────

    private int px() { return vw - 36 - PANEL_W; }

    private void renderAccounts(double mx, double my, float dt, int y0) {
        float a = panelAnim;
        int x = px(), w = PANEL_W, y = y0 - Math.round((1 - a) * 8);
        List<AccountStore.Account> list = AccountStore.accounts();
        MicrosoftAuth.DeviceCode code = AccountStore.pendingCode;

        int h = 14 + list.size() * 24 + 30 + (code != null ? 74 : 0) + 22;
        d.card(x, y, w, h, Theme.RADIUS, Colors.mulAlpha(0xE80C0C12, a), Colors.mulAlpha(Theme.CARD_EDGE, a));
        int ry = y + 8;
        for (int i = 0; i < list.size() && i < rowHover.length; i++) {
            AccountStore.Account acc = list.get(i);
            boolean hov = Widget.hit(mx, my, x + 6, ry, w - 12, 22);
            rowHover[i] = Anim.to(rowHover[i], hov ? 1 : 0, dt);
            if (rowHover[i] > 0.01f) d.round(x + 6, ry, w - 12, 22, 4, Colors.mulAlpha(Theme.HOVER, rowHover[i] * 2 * a));
            boolean cur = AccountStore.isCurrent(acc);
            if (cur) d.circle(x + 16, ry + 11, 5, Colors.mulAlpha(Theme.accent(), a));
            d.text(acc.name, Fonts.Kind.BOLD, x + 26, ry + 4, Colors.mulAlpha(Theme.TEXT, a));
            d.text("Microsoft", x + 26, ry + 13, Colors.mulAlpha(Theme.TEXT_MUTED, a));
            if (hov) d.textRight("✕", x + w - 14, ry + 7, Colors.mulAlpha(Theme.TEXT_DIM, a));
            ry += 24;
        }
        if (!list.isEmpty()) {
            d.rect(x + 10, ry + 2, w - 20, 1, Colors.mulAlpha(Theme.DIVIDER, a));
            ry += 6;
        }

        // войти
        boolean busy = AccountStore.isBusy();
        String login = "ВОЙТИ ЧЕРЕЗ MICROSOFT";
        boolean lh = !busy && Widget.hit(mx, my, x + 10, ry, w - 20, 20);
        loginHover = Anim.to(loginHover, lh ? 1 : 0, dt);
        d.text(login, Fonts.Kind.HEAD, x + 14, ry + 6, Colors.mulAlpha(Colors.lerp(busy ? Theme.TEXT_MUTED : Theme.TEXT_DIM, Theme.TEXT, loginHover), a));
        msLogo(x + w - 26, ry + 5, a);
        ry += 26;

        // код для входа
        if (code != null) {
            d.text("Код:", x + 14, ry, Colors.mulAlpha(Theme.TEXT_MUTED, a));
            d.textScaled(code.userCode(), Fonts.Kind.HEAD, x + 14, ry + 12, 1.6f, Colors.mulAlpha(Theme.TEXT, a));
            int by = ry + 44;
            boolean oh = Widget.hit(mx, my, x + 12, by - 3, 150, 16);
            openHover = Anim.to(openHover, oh ? 1 : 0, dt);
            d.text("Скопировать и открыть сайт →", x + 14, by, Colors.mulAlpha(Colors.lerp(Theme.TEXT_DIM, Theme.TEXT, openHover), a));
            boolean ch = Widget.hit(mx, my, x + w - 60, by - 3, 50, 16);
            cancelHover = Anim.to(cancelHover, ch ? 1 : 0, dt);
            d.textRight("Отмена", x + w - 14, by, Colors.mulAlpha(Colors.lerp(Theme.TEXT_MUTED, Theme.TEXT, cancelHover), a));
            ry += 74;
        }

        String st = AccountStore.status;
        if (st != null && !st.isEmpty()) d.text(Draw.ellipsize(st, w - 28), x + 14, ry, Colors.mulAlpha(Theme.TEXT_MUTED, a));
        else if (!AccountStore.hasClientId()) d.text("Нужен Client ID — см. README", x + 14, ry, Colors.mulAlpha(Theme.TEXT_MUTED, a));
    }

    /** Четыре квадратика, как у Microsoft, но в цветах интерфейса. */
    private void msLogo(int x, int y, float a) {
        int c = Colors.mulAlpha(Theme.TEXT_DIM, a);
        d.rect(x, y, 4, 4, c);
        d.rect(x + 5, y, 4, 4, c);
        d.rect(x, y + 5, 4, 4, c);
        d.rect(x + 5, y + 5, 4, 4, c);
    }

    private boolean clickAccounts(double mx, double my) {
        int x = px(), w = PANEL_W, ry = 24 + 24 + 12 + 8;
        List<AccountStore.Account> list = AccountStore.accounts();
        for (int i = 0; i < list.size() && i < rowHover.length; i++) {
            if (Widget.hit(mx, my, x + 6, ry, w - 12, 22)) {
                AccountStore.Account acc = list.get(i);
                if (mx > x + w - 28) AccountStore.remove(acc);
                else if (!AccountStore.isCurrent(acc)) AccountStore.switchTo(acc);
                return true;
            }
            ry += 24;
        }
        if (!list.isEmpty()) ry += 6;
        if (Widget.hit(mx, my, x + 10, ry, w - 20, 20)) {
            AccountStore.startLogin();
            return true;
        }
        ry += 26;
        MicrosoftAuth.DeviceCode code = AccountStore.pendingCode;
        if (code != null) {
            int by = ry + 44;
            if (Widget.hit(mx, my, x + 12, by - 3, 150, 16)) {
                minecraft.keyboardHandler.setClipboard(code.userCode());
                Screenshots.openExternal(code.verificationUri());
                return true;
            }
            if (Widget.hit(mx, my, x + w - 60, by - 3, 50, 16)) {
                AccountStore.cancelLogin();
                return true;
            }
        }
        // клик внутри панели не закрывает её
        int h = 14 + list.size() * 24 + 30 + (code != null ? 74 : 0) + 22;
        return Widget.hit(mx, my, x, 24 + 24 + 12, w, h);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        if (e.button() != 0) return false;
        double mx = e.x() / k, my = e.y() / k;
        if (accountsOpen && clickAccounts(mx, my)) return true;
        if (dev.umbra.music.MediaClient.available() && music.click(musicX(), musicY(), mx, my)) return true;
        if (clickItems(mx, my)) return true;
        if (accountsOpen) {
            accountsOpen = false;
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (e.key() == GLFW.GLFW_KEY_ESCAPE && accountsOpen) {
            accountsOpen = false;
            return true;
        }
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

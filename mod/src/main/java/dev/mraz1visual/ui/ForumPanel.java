package dev.mraz1visual.ui;

import dev.mraz1visual.forum.GitHubForum;
import dev.mraz1visual.ui.widget.Widget;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

/**
 * Вкладка «Forum»: обсуждения Mraz1Visual на GitHub Discussions. Вход — GitHub Device Flow (код на github.com/login/device),
 * темы открываются в браузере. Без настроенного client_id — подсказка, как включить.
 */
public final class ForumPanel implements Panel {
    private enum State { NO_CLIENT, LOGGED_OUT, WAITING, LIST }

    private State state;
    private GitHubForum.DeviceCode code;
    private final List<GitHubForum.Topic> topics = new ArrayList<>();
    private String message = "";
    private boolean loading;
    private float scroll, target;
    private int maxScroll;
    private final List<Object[]> hits = new ArrayList<>();

    @Override
    public void opened() {
        refreshState();
    }

    private void refreshState() {
        if (GitHubForum.clientId().isEmpty()) state = State.NO_CLIENT;
        else if (!GitHubForum.loggedIn()) state = state == State.WAITING ? State.WAITING : State.LOGGED_OUT;
        else {
            state = State.LIST;
            load();
        }
    }

    private void load() {
        if (loading) return;
        loading = true;
        message = "";
        GitHubForum.topics().whenComplete((list, t) -> Minecraft.getInstance().execute(() -> {
            loading = false;
            topics.clear();
            if (t != null) {
                Throwable c = t.getCause() != null ? t.getCause() : t;
                message = String.valueOf(c.getMessage());
                if (!GitHubForum.loggedIn()) state = State.LOGGED_OUT;
            } else topics.addAll(list);
        }));
    }

    @Override
    public void render(Draw d, double mx, double my, float dt, int x, int y, int w, int h) {
        if (state == null) refreshState();
        hits.clear();
        d.glass(x, y, w, h, 14, Theme.GLASS_PANEL, 0.9f);
        d.text("Форум Mraz1Visual", Fonts.Kind.BOLD, x + 14, y + 12, Theme.TEXT);
        d.text("GitHub Discussions · " + GitHubForum.REPO_OWNER + "/" + GitHubForum.REPO_NAME, x + 14, y + 24, Theme.TEXT_MUTED);
        int bx = x + w - 14;
        bx = button(d, mx, my, bx, y + 12, "Открыть в браузере", "browser") - 6;
        if (state == State.LIST) {
            bx = button(d, mx, my, bx, y + 12, "Новая тема", "new") - 6;
            bx = button(d, mx, my, bx, y + 12, "Обновить", "reload") - 6;
            button(d, mx, my, bx, y + 12, "Выйти", "logout");
        }
        int cx = x + w / 2, cy = y + h / 2 - 30;
        switch (state) {
            case NO_CLIENT -> {
                d.textCenter("Вход через GitHub ещё не настроен в этой сборке.", cx, cy, Theme.TEXT_DIM);
                d.textCenter("Владелец клиента указывает client_id OAuth-приложения (Device Flow)", cx, cy + 14, Theme.TEXT_MUTED);
                d.textCenter("в config/mraz1visual/github.json — токены в коде не хранятся.", cx, cy + 26, Theme.TEXT_MUTED);
                d.textCenter("А пока обсуждения можно читать в браузере (кнопка справа сверху)", cx, cy + 46, Theme.TEXT_DIM);
            }
            case LOGGED_OUT -> {
                d.textCenter("Войдите через GitHub, чтобы читать и обсуждать.", cx, cy, Theme.TEXT_DIM);
                d.textCenter("Пароль вводится только на github.com — клиент его не видит.", cx, cy + 14, Theme.TEXT_MUTED);
                int bw = d.width("Войти через GitHub") + 30;
                bigButton(d, mx, my, cx - bw / 2, cy + 34, bw, "Войти через GitHub", "login");
                if (!message.isEmpty()) d.textCenter(Draw.ellipsize(message, w - 40), cx, cy + 70, 0xFFFF8A8A);
            }
            case WAITING -> {
                d.textCenter("Откройте " + (code == null ? "github.com/login/device" : code.verificationUri()) + " и введите код:", cx, cy - 6, Theme.TEXT_DIM);
                if (code != null) {
                    String c = code.userCode();
                    int cw = Math.round(d.width(c, Fonts.Kind.HEAD) * 2f) + 40;
                    d.glass(cx - cw / 2, cy + 10, cw, 40, 14, Colors.withAlpha(Theme.accent(), 0x40), 1.3f);
                    d.textScaled(c, Fonts.Kind.HEAD, cx - Math.round(d.width(c, Fonts.Kind.HEAD) * 2f) / 2, cy + 22, 2f, Theme.TEXT);
                    int bw = d.width("Открыть страницу и скопировать код") + 30;
                    bigButton(d, mx, my, cx - bw / 2, cy + 60, bw, "Открыть страницу и скопировать код", "open-device");
                }
                d.textCenter("Ждём подтверждения…", cx, cy + 96, Theme.TEXT_MUTED);
            }
            case LIST -> list(d, mx, my, dt, x, y + 44, w, h - 50);
        }
    }

    private void list(Draw d, double mx, double my, float dt, int x, int y, int w, int h) {
        if (topics.isEmpty()) {
            d.textCenter(loading ? "Загружаю…" : !message.isEmpty() ? message : "Пока нет обсуждений — начните первое!", x + w / 2, y + h / 2, Theme.TEXT_MUTED);
            return;
        }
        int rowH = 40;
        maxScroll = Math.max(0, topics.size() * (rowH + 6) - h);
        target = Math.max(0, Math.min(target, maxScroll));
        scroll = Anim.to(scroll, target, dt);
        d.clip(x + 4, y, w - 8, h);
        for (int i = 0; i < topics.size(); i++) {
            GitHubForum.Topic t = topics.get(i);
            int ry = y + i * (rowH + 6) - Math.round(scroll);
            if (ry + rowH < y || ry > y + h) continue;
            boolean hv = Widget.hit(mx, my, x + 12, ry, w - 24, rowH) && my >= y && my <= y + h;
            d.glass(x + 12, ry, w - 24, rowH, 12, hv ? Theme.GLASS_ACTIVE : Theme.GLASS_CARD, 1f);
            d.text(Draw.ellipsize(t.title(), w - 160), Fonts.Kind.BOLD, x + 24, ry + 8, Theme.TEXT);
            d.text(t.category() + " · " + t.author() + " · " + t.updated(), x + 24, ry + 22, Theme.TEXT_MUTED);
            d.textRight(t.comments() + " отв.", x + w - 24, Draw.textY(ry, rowH), Theme.TEXT_DIM);
            hits.add(new Object[]{x + 12, ry, w - 24, rowH, t});
        }
        d.unclip();
    }

    private int button(Draw d, double mx, double my, int right, int y, String label, String id) {
        int w = d.width(label) + 18, x = right - w;
        boolean hv = Widget.hit(mx, my, x, y, w, 20);
        d.round(x, y, w, 20, 10, hv ? Theme.GLASS_ACTIVE : Theme.GLASS_CARD);
        d.textCenter(label, x + w / 2, Draw.textY(y, 20), Theme.TEXT_DIM);
        hits.add(new Object[]{x, y, w, 20, id});
        return x;
    }

    private void bigButton(Draw d, double mx, double my, int x, int y, int w, String label, String id) {
        boolean hv = Widget.hit(mx, my, x, y, w, 26);
        d.round(x, y, w, 26, 13, Colors.withAlpha(Theme.accent(), hv ? 0xD0 : 0xA0));
        d.textCenter(label, x + w / 2, Draw.textY(y, 26), 0xFFFFFFFF);
        hits.add(new Object[]{x, y, w, 26, id});
    }

    @Override
    public boolean click(double mx, double my, int button) {
        if (button != 0) return true;
        for (Object[] h : hits) {
            if (!Widget.hit(mx, my, (int) h[0], (int) h[1], (int) h[2], (int) h[3])) continue;
            Object a = h[4];
            if (a instanceof GitHubForum.Topic t) open(t.url());
            else switch ((String) a) {
                case "browser" -> open(GitHubForum.discussionsUrl());
                case "new" -> open(GitHubForum.discussionsUrl() + "/new/choose");
                case "reload" -> load();
                case "logout" -> {
                    GitHubForum.logout();
                    topics.clear();
                    state = State.LOGGED_OUT;
                }
                case "login" -> {
                    message = "";
                    state = State.WAITING;
                    code = null;
                    GitHubForum.start().whenComplete((c, t) -> Minecraft.getInstance().execute(() -> {
                        if (t != null) {
                            state = State.LOGGED_OUT;
                            message = String.valueOf((t.getCause() != null ? t.getCause() : t).getMessage());
                            return;
                        }
                        code = c;
                        GitHubForum.poll(c).whenComplete((ok, t2) -> Minecraft.getInstance().execute(() -> {
                            if (Boolean.TRUE.equals(ok)) {
                                state = State.LIST;
                                Notices.add("Форум", "Вход через GitHub выполнен");
                                load();
                            } else {
                                state = State.LOGGED_OUT;
                                message = "Код не подтверждён — попробуйте ещё раз";
                            }
                        }));
                    }));
                }
                case "open-device" -> {
                    if (code != null) {
                        Minecraft.getInstance().keyboardHandler.setClipboard(code.userCode());
                        open(code.verificationUri());
                    }
                }
                default -> {
                }
            }
            return true;
        }
        return true;
    }

    private static void open(String url) {
        dev.mraz1visual.compat.Platform.openUri(url);
    }

    @Override
    public boolean scroll(double mx, double my, double amount) {
        target = Math.max(0, Math.min(maxScroll, target - (float) amount * 30));
        return true;
    }
}

package dev.mraz1visual.ui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.mraz1visual.market.Modrinth;
import dev.mraz1visual.ui.widget.Widget;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Вкладка «Маркет»: моды, ресурспаки и шейдеры с Modrinth и подборка Mraz1.
 * Сверху — вид, поиск, сортировка, «только моя версия», для ресурспаков — разрешение; ниже — карточки с установкой.
 * Сеть — в фоне (Modrinth), панель только рисует то, что уже пришло.
 */
public final class MarketPanel implements Panel {
    private static final String[][] KINDS = {{"MOD", "Моды"}, {"RESOURCEPACK", "Ресурспаки"}, {"SHADER", "Шейдеры"}, {"MRAZ1", "Mraz1"}};
    private static final String[][] SORTS = {{"downloads", "Популярные"}, {"updated", "Обновлённые"}, {"newest", "Новые"}};
    private static final String[][] RES = {{"", "Любое"}, {"16x", "16×"}, {"32x", "32×"}, {"64x", "64×"}, {"128x", "128×+"}};
    /** Подборка Mraz1: проверенные клиентские моды под Fabric. */
    private static final String[][] PICKS = {
            {"sodium", "MOD", "Рендер в разы быстрее — основа для FPS."},
            {"iris", "MOD", "Шейдерпаки, включая Mraz1 Shaders (нужен Sodium)."},
            {"immediatelyfast", "MOD", "Быстрее HUD, текст и интерфейс."},
            {"entityculling", "MOD", "Не рисует сущности за стенами — больше FPS."},
            {"lithium", "MOD", "Оптимизация логики игры (одиночная игра)."},
            {"modmenu", "MOD", "Список модов и их настройки."},
            {"simple-voice-chat", "MOD", "Голосовой чат (нужен и на сервере)."},
            {"replaymod", "MOD", "Запись и просмотр повторов."},
            {"xaeros-minimap", "MOD", "Миникарта."},
    };
    private static final int CARD_H = 64, GAP = 8;

    private String kind = "MOD", sort = "downloads", res = "";
    private boolean currentOnly = true;
    private final StringBuilder query = new StringBuilder();
    private boolean focused;
    private final List<Modrinth.Hit> hits = new ArrayList<>();
    private final Map<String, String> state = new HashMap<>(); // id → «Ставлю…», «Ошибка…»
    private String error = "";
    private boolean loading, more = true;
    private int generation;
    private long typedAt;
    private float scroll, target;
    private int maxScroll;
    private final List<int[]> chipHits = new ArrayList<>(); // x, y, w, h, тип (0 вид, 1 сорт, 2 разрешение, 3 версия), индекс
    private final List<Object[]> btnHits = new ArrayList<>();
    private int fieldX, fieldY, fieldW;
    private boolean opened;

    @Override
    public void opened() {
        if (!opened) {
            opened = true;
            reload();
        }
    }

    private void reload() {
        generation++;
        hits.clear();
        error = "";
        more = true;
        scroll = target = 0;
        load();
    }

    private void load() {
        if (loading || !more) return;
        loading = true;
        int gen = generation;
        if (kind.equals("MRAZ1")) {
            more = false;
            List<java.util.concurrent.CompletableFuture<Modrinth.Hit>> fs = new ArrayList<>();
            for (String[] p : PICKS) fs.add(Modrinth.project(p[0], Modrinth.Kind.valueOf(p[1])));
            java.util.concurrent.CompletableFuture.allOf(fs.toArray(new java.util.concurrent.CompletableFuture[0])).whenComplete((v, t) ->
                    net.minecraft.client.Minecraft.getInstance().execute(() -> {
                        if (gen != generation) return;
                        loading = false;
                        for (var f : fs) if (!f.isCompletedExceptionally()) hits.add(f.join());
                        if (hits.isEmpty()) error = "Нет связи с Modrinth";
                    }));
            return;
        }
        Modrinth.search(Modrinth.Kind.valueOf(kind), query.toString(), query.isEmpty() ? sort : "relevance", currentOnly, res, hits.size())
                .whenComplete((list, t) -> net.minecraft.client.Minecraft.getInstance().execute(() -> {
                    if (gen != generation) return;
                    loading = false;
                    if (t != null) {
                        error = "Нет связи с Modrinth";
                        more = false;
                        return;
                    }
                    hits.addAll(list);
                    more = list.size() >= 24;
                }));
    }

    @Override
    public void render(Draw d, double mx, double my, float dt, int x, int y, int w, int h) {
        // поиск с задержкой: перезагрузка через 350 мс после последней буквы
        if (typedAt > 0 && System.currentTimeMillis() - typedAt > 350) {
            typedAt = 0;
            reload();
        }
        chipHits.clear();
        btnHits.clear();
        d.glass(x, y, w, h, 14, Theme.GLASS_PANEL, 0.9f);
        // ── полоса фильтров ──
        int cx = x + 12, cy = y + 10, ch = 20;
        for (int i = 0; i < KINDS.length; i++) cx = chip(d, mx, my, cx, cy, ch, KINDS[i][1], KINDS[i][0].equals(kind), 0, i) + 4;
        // поле поиска
        fieldW = Math.min(220, Math.max(120, x + w - 12 - (cx + 10)));
        fieldX = x + w - 12 - fieldW;
        fieldY = cy;
        boolean fh = Widget.hit(mx, my, fieldX, fieldY, fieldW, ch);
        d.round(fieldX, fieldY, fieldW, ch, ch / 2, focused ? Theme.FIELD_HOVER : fh ? Theme.FIELD_HOVER : Theme.FIELD);
        d.icon(Icons.SEARCH, fieldX + 8, fieldY + (ch - 8) / 2, 8, Theme.TEXT_MUTED);
        String q = query.toString();
        d.text(q.isEmpty() ? (kind.equals("MRAZ1") ? "Подборка Mraz1" : "Поиск на Modrinth") : Draw.ellipsize(q, fieldW - 30), fieldX + 20, Draw.textY(fieldY, ch), q.isEmpty() ? Theme.TEXT_MUTED : Theme.TEXT);
        if (focused && (System.nanoTime() / 500_000_000L & 1) == 0) d.rect(fieldX + 21 + Math.min(fieldW - 30, d.width(q)), fieldY + 6, 1, 8, Theme.accent());

        int ry = cy + ch + 6;
        cx = x + 12;
        if (!kind.equals("MRAZ1")) {
            for (int i = 0; i < SORTS.length; i++) cx = chip(d, mx, my, cx, ry, ch, SORTS[i][1], SORTS[i][0].equals(sort) && query.isEmpty(), 1, i) + 4;
            cx += 8;
            if (!kind.equals("SHADER")) cx = chip(d, mx, my, cx, ry, ch, "Только " + Modrinth.gameVersion(), currentOnly, 3, 0) + 4;
            if (kind.equals("RESOURCEPACK")) {
                cx += 8;
                for (int i = 0; i < RES.length; i++) cx = chip(d, mx, my, cx, ry, ch, RES[i][1], RES[i][0].equals(res), 2, i) + 4;
            }
        } else {
            d.text("Проверенные моды для клиента. Моды заработают после перезапуска игры.", x + 14, Draw.textY(ry, ch), Theme.TEXT_DIM);
        }

        // ── карточки ──
        int gy = ry + ch + 10, gh = y + h - 8 - gy;
        int cols = Math.max(1, (w - 24 + GAP) / (260 + GAP));
        int cw = (w - 24 - (cols - 1) * GAP) / cols;
        int rows = (hits.size() + cols - 1) / cols;
        int contentH = rows * (CARD_H + GAP) + (more || loading ? 30 : 0);
        maxScroll = Math.max(0, contentH - gh);
        target = Math.max(0, Math.min(target, maxScroll));
        scroll = Anim.to(scroll, target, dt);
        if (!loading && more && !hits.isEmpty() && target >= maxScroll - 40) load();
        d.clip(x + 4, gy, w - 8, gh);
        boolean in = Widget.hit(mx, my, x, gy, w, gh);
        for (int i = 0; i < hits.size(); i++) {
            int c = i % cols, r = i / cols;
            int bx = x + 12 + c * (cw + GAP), by = gy + r * (CARD_H + GAP) - Math.round(scroll);
            if (by + CARD_H < gy || by > gy + gh) continue;
            card(d, in ? mx : -1e6, in ? my : -1e6, hits.get(i), bx, by, cw);
        }
        String foot = loading ? "Загружаю…" : !error.isEmpty() ? error : hits.isEmpty() ? "Ничего не найдено" : "";
        if (!foot.isEmpty()) d.textCenter(foot, x + w / 2, hits.isEmpty() ? gy + gh / 2 - 4 : gy + rows * (CARD_H + GAP) + 8 - Math.round(scroll), Theme.TEXT_MUTED);
        d.unclip();
    }

    private int chip(Draw d, double mx, double my, int x, int y, int h, String label, boolean on, int type, int index) {
        int w = d.width(label) + 18;
        boolean hov = Widget.hit(mx, my, x, y, w, h);
        d.round(x, y, w, h, h / 2, on ? Colors.withAlpha(Theme.accent(), Theme.isLight() ? 0x60 : 0x40) : hov ? Theme.HOVER : Theme.GLASS_CARD);
        d.textCenter(label, x + w / 2, Draw.textY(y, h), on ? Theme.TEXT : Theme.TEXT_DIM);
        chipHits.add(new int[]{x, y, w, h, type, index});
        return x + w;
    }

    private void card(Draw d, double mx, double my, Modrinth.Hit hit, int x, int y, int w) {
        boolean hov = Widget.hit(mx, my, x, y, w, CARD_H);
        d.glass(x, y, w, CARD_H, 12, hov ? Theme.GLASS_ACTIVE : Theme.GLASS_CARD, 1f);
        // плитка с буквой: цвет — от названия
        int hue = hit.title().hashCode();
        int col = 0xFF000000 | java.awt.Color.HSBtoRGB((hue & 0xFF) / 255f, 0.45f, 0.85f);
        d.round(x + 10, y + 10, 44, 44, 12, Colors.withAlpha(col, 0x60));
        d.textScaled(hit.title().isEmpty() ? "?" : hit.title().substring(0, 1).toUpperCase(), Fonts.Kind.HEAD, x + 24, y + 24, 1.4f, 0xFFFFFFFF);
        int tx = x + 62, tw = w - 62 - 96;
        d.text(Draw.ellipsize(hit.title(), tw), Fonts.Kind.BOLD, tx, y + 10, Theme.TEXT);
        String sub = (hit.author().isEmpty() ? "" : hit.author() + " · ") + "↓ " + Modrinth.formatDownloads(hit.downloads());
        d.text(Draw.ellipsize(sub, tw), tx, y + 22, Theme.TEXT_MUTED);
        d.text(Draw.ellipsize(hit.description(), w - 62 - 10), tx, y + 40, Theme.TEXT_DIM);
        // кнопка
        boolean inst = Modrinth.installed(hit.id());
        String st = state.get(hit.id());
        String label = st != null ? st : inst ? "Удалить" : "Установить";
        int bw = Math.max(76, d.width(label) + 18), bx = x + w - 10 - bw, by = y + 10, bh = 20;
        boolean bh2 = Widget.hit(mx, my, bx, by, bw, bh);
        int fill = inst ? (bh2 ? 0x40FF453A : Theme.FIELD) : Colors.withAlpha(Theme.accent(), bh2 ? 0xC0 : 0x90);
        d.round(bx, by, bw, bh, bh / 2, fill);
        d.textCenter(label, bx + bw / 2, Draw.textY(by, bh), inst || st != null ? Theme.TEXT : 0xFFFFFFFF);
        btnHits.add(new Object[]{bx, by, bw, bh, hit});
    }

    @Override
    public boolean click(double mx, double my, int button) {
        if (button != 0) return true;
        focused = Widget.hit(mx, my, fieldX, fieldY, fieldW, 20);
        for (int[] c : chipHits) {
            if (!Widget.hit(mx, my, c[0], c[1], c[2], c[3])) continue;
            switch (c[4]) {
                case 0 -> {
                    kind = KINDS[c[5]][0];
                    query.setLength(0);
                }
                case 1 -> sort = SORTS[c[5]][0];
                case 2 -> res = RES[c[5]][0];
                default -> currentOnly = !currentOnly;
            }
            reload();
            return true;
        }
        for (Object[] b : btnHits) {
            if (!Widget.hit(mx, my, (int) b[0], (int) b[1], (int) b[2], (int) b[3])) continue;
            Modrinth.Hit hit = (Modrinth.Hit) b[4];
            if (state.containsKey(hit.id()) && "Ставлю…".equals(state.get(hit.id()))) return true;
            if (Modrinth.installed(hit.id())) {
                Modrinth.uninstall(hit);
                Notices.add("Удалено: " + hit.title(), hit.kind() == Modrinth.Kind.MOD ? "Изменения — после перезапуска игры" : "Файл убран из папки игры");
                return true;
            }
            state.put(hit.id(), "Ставлю…");
            Modrinth.install(hit).whenComplete((file, t) -> net.minecraft.client.Minecraft.getInstance().execute(() -> {
                if (t != null) {
                    state.put(hit.id(), "Ошибка");
                    Throwable c = t.getCause() != null ? t.getCause() : t;
                    Notices.add("Не удалось: " + hit.title(), String.valueOf(c.getMessage()));
                } else {
                    state.remove(hit.id());
                    Notices.add("Установлено: " + hit.title(), switch (hit.kind()) {
                        case MOD -> "Мод заработает после перезапуска игры";
                        case RESOURCEPACK -> "Включите в «Наборы ресурсов»";
                        case SHADER -> "Выберите в настройках шейдеров Iris";
                    });
                }
            }));
            return true;
        }
        return true;
    }

    @Override
    public boolean scroll(double mx, double my, double amount) {
        target = Math.max(0, Math.min(maxScroll, target - (float) amount * 34));
        return true;
    }

    @Override
    public boolean typing() {
        return focused;
    }

    @Override
    public boolean key(int key, boolean ctrl) {
        switch (key) {
            case InputConstants.KEY_ESCAPE, InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> focused = false;
            case InputConstants.KEY_BACKSPACE -> {
                if (!query.isEmpty()) {
                    query.setLength(query.length() - 1);
                    typedAt = System.currentTimeMillis();
                }
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean chr(int cp) {
        if (Character.isISOControl(cp) || query.length() >= 40) return true;
        if (kind.equals("MRAZ1")) kind = "MOD";
        query.appendCodePoint(cp);
        typedAt = System.currentTimeMillis();
        return true;
    }
}

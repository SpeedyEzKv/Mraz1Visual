package dev.umbra.ui.menu;

import com.mojang.blaze3d.platform.NativeImage;
import dev.umbra.core.ModuleManager;
import dev.umbra.ui.Anim;
import dev.umbra.ui.Colors;
import dev.umbra.ui.Draw;
import dev.umbra.ui.Fonts;
import dev.umbra.ui.Theme;
import dev.umbra.ui.widget.TextInput;
import dev.umbra.ui.widget.Widget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.LevelSummary;
import org.lwjgl.glfw.GLFW;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Одиночная игра: список миров с поиском, внизу «Играть / Изменить / Пересоздать / Удалить».
 * «Новый мир», «Изменить» и «Пересоздать» выполняет ванильный экран выбора миров (невидимый),
 * чтобы поведение и все проверки игры остались родными.
 */
public final class UmbraWorldsScreen extends MenuBase {
    private static final int ROW_H = 30;
    private static final SimpleDateFormat DATE = new SimpleDateFormat("dd.MM.yyyy, HH:mm", Locale.ROOT);
    private static final String[] ACTIONS = {"ИГРАТЬ", "ИЗМЕНИТЬ", "ПЕРЕСОЗДАТЬ", "УДАЛИТЬ"};

    private final Screen parent;
    private List<LevelSummary> all = new ArrayList<>();
    private final List<LevelSummary> shown = new ArrayList<>();
    private boolean loading = true;
    private String error = "";
    private int selected = -1; // индекс в all
    private long deleteArmedUntil;

    private final TextInput search = new TextInput("начните вводить", 40);
    private String lastQuery = "";
    private int left, px, py, pw, ph;
    private float scroll, scrollTarget;
    private final Map<String, Identifier> icons = new HashMap<>();
    private final Map<String, Float> rowHover = new HashMap<>();
    private final float[] actHover = new float[ACTIONS.length];
    private final int[] actX = new int[ACTIONS.length], actW = new int[ACTIONS.length];
    private int actY;

    // отложенное действие через ванильный экран
    private SelectWorldScreen vanilla;
    private String pendingKey;
    private int pendingIndex;
    private int pendingFrames;

    public UmbraWorldsScreen(Screen parent) {
        super("Миры");
        this.parent = parent;
        item("МИРЫ", () -> {}).selected = true;
        item("НОВЫЙ МИР", () -> vanillaAction(-1, "selectWorld.create"));
        itemAfterGap("НАЗАД", this::onClose);
        search.underline = true;
    }

    @Override protected float itemScale() { return 1.0f; }
    @Override protected int itemLine() { return 26; }
    @Override protected float sunScreenX() { return 0.5f; }
    @Override protected float sunScreenY() { return -0.02f; }

    @Override
    protected void init() {
        super.init();
        if (loading && all.isEmpty()) load();
    }

    private void load() {
        loading = true;
        try {
            LevelStorageSource src = minecraft.getLevelSource();
            LevelStorageSource.LevelCandidates candidates = src.findLevelCandidates();
            src.loadLevelSummaries(candidates).whenComplete((list, err) -> minecraft.execute(() -> {
                loading = false;
                if (err != null || list == null) {
                    error = "Не удалось прочитать миры";
                    return;
                }
                all = new ArrayList<>(list);
                if (selected >= all.size()) selected = all.isEmpty() ? -1 : 0;
                if (selected < 0 && !all.isEmpty()) selected = 0;
                filter();
            }));
        } catch (Exception e) {
            loading = false;
            error = "Не удалось прочитать миры";
            ModuleManager.LOG.error("Umbra worlds: load failed", e);
        }
    }

    private void filter() {
        shown.clear();
        String q = search.value().trim().toLowerCase(Locale.ROOT);
        for (LevelSummary s : all) {
            if (q.isEmpty() || s.getLevelName().toLowerCase(Locale.ROOT).contains(q) || s.getLevelId().toLowerCase(Locale.ROOT).contains(q)) {
                shown.add(s);
            }
        }
        lastQuery = search.value();
        scroll = scrollTarget = 0;
    }

    @Override
    protected void onLayout() {
        left = Math.max(24, Math.round(vw * 0.056f));
        layoutItems(left, Math.round(vh * 0.36f));
        int itemsRight = left;
        for (Item it : items) itemsRight = Math.max(itemsRight, it.x + it.w);
        px = Math.max(itemsRight + 50, Math.round(vw * 0.27f));
        py = Math.round(vh * 0.43f);
        pw = vw - px - Math.max(24, Math.round(vw * 0.056f));
        ph = vh - py - 70;
        search.setBounds(px + pw - 220, py - 22, 220, 14);
    }

    private LevelSummary current() {
        return selected >= 0 && selected < all.size() ? all.get(selected) : null;
    }

    // ── рисование ────────────────────────────────────────────

    @Override
    protected void renderMenu(double mx, double my, float dt) {
        d.gradientV(0, Math.round(vh * 0.25f), vw, vh - Math.round(vh * 0.25f), 0x00000000, 0xB4000000);
        renderItems(mx, my, dt);
        renderBrand(left);
        if (!search.value().equals(lastQuery)) filter();

        d.text("Поиск", px, py - 19, Colors.mulAlpha(Theme.TEXT_DIM, fade));
        search.render(d, mx, my, dt);
        d.rect(px, py - 6, pw, 1, Colors.mulAlpha(0x1AFFFFFF, fade));

        scroll = Anim.to(scroll, scrollTarget, dt);
        d.clip(px - 6, py, pw + 12, ph);
        int sc = Math.round(scroll);
        for (int i = 0; i < shown.size(); i++) {
            int ry = py + i * ROW_H - sc;
            if (ry + ROW_H < py) continue;
            if (ry > py + ph) break;
            renderRow(shown.get(i), ry, mx, my, dt);
        }
        if (shown.isEmpty()) {
            String t = loading ? "Загрузка…" : !error.isEmpty() ? error : all.isEmpty() ? "Миров пока нет — нажми «Новый мир»." : "Ничего не найдено";
            d.text(t, px, py + 8, Colors.mulAlpha(Theme.TEXT_MUTED, fade));
        }
        d.unclip();

        // нижняя строка: выбранный мир и действия
        int ay = vh - 52;
        LevelSummary cur = current();
        boolean has = cur != null && pendingKey == null;
        if (cur != null) {
            String nm = cur.getLevelName();
            d.text(nm, Fonts.Kind.BOLD, px, ay + 22, Colors.mulAlpha(Theme.TEXT_DIM, fade));
            d.text(Draw.ellipsize(cur.getInfo().getString(), pw / 2), px + Fonts.width(nm, Fonts.Kind.BOLD) + 6, ay + 22,
                    Colors.mulAlpha(Theme.TEXT_MUTED, fade));
        }
        boolean armed = System.currentTimeMillis() < deleteArmedUntil;
        int right = px + pw;
        for (int i = ACTIONS.length - 1; i >= 0; i--) {
            String label = i == 3 && armed ? "ТОЧНО?" : ACTIONS[i];
            right = action(i, label, right, ay, mx, my, dt, has) - 18;
        }
        if (pendingKey != null) runPending(d.g);
    }

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

    private void renderRow(LevelSummary s, int ry, double mx, double my, float dt) {
        boolean sel = s == current();
        boolean hov = Widget.hit(mx, my, px - 6, ry, pw + 12, ROW_H) && my >= py && my < py + ph;
        float h = Anim.to(rowHover.getOrDefault(s.getLevelId(), 0f), hov ? 1 : 0, dt);
        rowHover.put(s.getLevelId(), h);
        if (sel) {
            d.round(px - 6, ry + 1, pw + 12, ROW_H - 2, Theme.RADIUS_SM, 0x10FFFFFF);
            d.rect(px - 6, ry + 4, 1, ROW_H - 8, 0xFFE8D9A0);
        } else if (h > 0.01f) {
            d.round(px - 6, ry + 1, pw + 12, ROW_H - 2, Theme.RADIUS_SM, Colors.mulAlpha(0x0AFFFFFF, h));
        }
        int is = ROW_H - 8;
        Identifier icon = icon(s);
        if (icon != null) {
            d.g.blit(RenderPipelines.GUI_TEXTURED, icon, px, ry + 4, 0f, 0f, is, is, 64, 64, 64, 64, Colors.withAlpha(0xFFFFFF, Math.round(255 * fade)));
        } else {
            d.round(px, ry + 4, is, is, 3, Theme.FIELD);
            d.textCenter(s.getLevelName().isEmpty() ? "?" : s.getLevelName().substring(0, 1).toUpperCase(Locale.ROOT), px + is / 2, ry + 12, Theme.TEXT_DIM);
        }
        int tx = px + is + 8;
        d.text(s.getLevelName(), Fonts.Kind.BOLD, tx, ry + 4, Colors.mulAlpha(sel ? 0xFFF3E8C0 : Theme.TEXT, fade));
        String sub;
        synchronized (DATE) {
            sub = s.getLevelId() + " · " + DATE.format(new Date(s.getLastPlayed()));
        }
        d.text(sub, tx, ry + 13, Colors.mulAlpha(Theme.TEXT_DIM, fade));
        d.text(Draw.ellipsize(s.getInfo().getString(), pw - is - 16), tx, ry + 21, Colors.mulAlpha(Theme.TEXT_MUTED, fade));
    }

    private Identifier icon(LevelSummary s) {
        String key = s.getLevelId();
        if (icons.containsKey(key)) return icons.get(key);
        Identifier id = null;
        try {
            Path p = s.getIcon();
            if (p != null && Files.isRegularFile(p)) {
                try (InputStream in = Files.newInputStream(p)) {
                    NativeImage img = NativeImage.read(in);
                    id = Identifier.fromNamespaceAndPath("umbra", "world_icon/" + Integer.toHexString(key.hashCode()));
                    minecraft.getTextureManager().register(id, new DynamicTexture(() -> "umbra world icon", img));
                }
            }
        } catch (Exception ignored) {
            id = null;
        }
        icons.put(key, id);
        return id;
    }

    // ── действия ─────────────────────────────────────────────

    private void play() {
        LevelSummary s = current();
        if (s == null) return;
        minecraft.createWorldOpenFlows().openWorld(s.getLevelId(), () -> minecraft.setScreen(new UmbraWorldsScreen(parent)));
    }

    private void delete() {
        LevelSummary s = current();
        if (s == null) return;
        long now = System.currentTimeMillis();
        if (now > deleteArmedUntil) {
            deleteArmedUntil = now + 3000;
            return;
        }
        deleteArmedUntil = 0;
        try (LevelStorageSource.LevelStorageAccess access = minecraft.getLevelSource().createAccess(s.getLevelId())) {
            access.deleteLevel();
        } catch (Exception e) {
            error = "Не удалось удалить мир";
            ModuleManager.LOG.error("Umbra worlds: delete failed", e);
        }
        Identifier icon = icons.remove(s.getLevelId());
        if (icon != null) minecraft.getTextureManager().release(icon);
        all.clear();
        shown.clear();
        selected = -1;
        load();
    }

    /** Действие ванильного экрана: выбрать мир (index ≥ 0) и нажать кнопку с ключом перевода. */
    private void vanillaAction(int index, String key) {
        try {
            vanilla = new SelectWorldScreen(parent);
            vanilla.init(width, height);
            pendingKey = key;
            pendingIndex = index;
            pendingFrames = 0;
            if (index < 0) runPending(null);
        } catch (Throwable t) {
            ModuleManager.LOG.error("Umbra worlds: vanilla action failed", t);
            pendingKey = null;
        }
    }

    private WorldSelectionList vanillaList() {
        for (var c : vanilla.children()) if (c instanceof WorldSelectionList l) return l;
        return null;
    }

    /** Ванильный список наполняется при отрисовке — рисуем его в пустую область, пока миры не загрузятся. */
    private void runPending(GuiGraphics g) {
        String key = pendingKey;
        if (key == null || vanilla == null) return;
        try {
            if (pendingIndex >= 0) {
                WorldSelectionList list = vanillaList();
                if (list == null) throw new IllegalStateException("no list");
                List<WorldSelectionList.WorldListEntry> entries = new ArrayList<>();
                for (var e : list.children()) if (e instanceof WorldSelectionList.WorldListEntry w) entries.add(w);
                if (entries.size() != all.size()) {
                    if (g != null) {
                        g.enableScissor(0, 0, 0, 0);
                        list.render(g, -1, -1, 0f);
                        g.disableScissor();
                    }
                    if (++pendingFrames > 300) throw new IllegalStateException("vanilla list did not load");
                    return;
                }
                list.setSelected(entries.get(pendingIndex));
            }
            pendingKey = null;
            String label = Component.translatable(key).getString();
            for (var c : vanilla.children()) {
                if (c instanceof Button b && b.getMessage().getString().equals(label)) {
                    b.onPress(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0));
                    return;
                }
            }
            throw new IllegalStateException("button " + key + " not found");
        } catch (Throwable t) {
            pendingKey = null;
            error = "Не получилось — открою обычный экран";
            ModuleManager.LOG.error("Umbra worlds: vanilla action failed", t);
            MenuRouter.bypassWorlds = true;
            minecraft.setScreen(vanilla);
            MenuRouter.bypassWorlds = false;
        }
    }

    // ── ввод ─────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        double mx = e.x() / k, my = e.y() / k;
        if (e.button() != 0) return false;
        if (clickItems(mx, my)) return true;
        search.mouseClicked(mx, my, 0);
        if (current() != null && pendingKey == null) {
            for (int i = 0; i < ACTIONS.length; i++) {
                if (Widget.hit(mx, my, actX[i] - 4, actY - 4, actW[i] + 8, 16)) {
                    switch (i) {
                        case 0 -> play();
                        case 1 -> vanillaAction(selected, "selectWorld.edit");
                        case 2 -> vanillaAction(selected, "selectWorld.recreate");
                        default -> delete();
                    }
                    return true;
                }
            }
        }
        if (Widget.hit(mx, my, px - 6, py, pw + 12, ph)) {
            int i = (int) Math.floor((my - py + scroll) / ROW_H);
            if (i >= 0 && i < shown.size()) {
                int idx = all.indexOf(shown.get(i));
                if (idx != selected) deleteArmedUntil = 0;
                selected = idx;
                if (doubleClick) play();
            }
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double sx, double sy) {
        int max = Math.max(0, shown.size() * ROW_H - ph);
        scrollTarget = (float) Math.max(0, Math.min(max, scrollTarget - sy * ROW_H));
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        int key = e.key();
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (search.focused) search.focused = false;
            else onClose();
            return true;
        }
        if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && current() != null) {
            play();
            return true;
        }
        if ((key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_UP) && !shown.isEmpty()) {
            int pos = shown.indexOf(current());
            pos = key == GLFW.GLFW_KEY_DOWN ? Math.min(shown.size() - 1, pos + 1) : Math.max(0, pos - 1);
            selected = all.indexOf(shown.get(pos));
            return true;
        }
        return search.keyPressed(key, (e.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0, minecraft.keyboardHandler::getClipboard);
    }

    @Override
    public boolean charTyped(CharacterEvent e) {
        if (!search.focused) search.focused = true;
        return search.charTyped(e.codepoint());
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

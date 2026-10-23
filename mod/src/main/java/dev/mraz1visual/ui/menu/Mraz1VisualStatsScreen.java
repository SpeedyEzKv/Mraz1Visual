package dev.mraz1visual.ui.menu;

import com.mojang.blaze3d.platform.InputConstants;
import dev.mraz1visual.ui.Anim;
import dev.mraz1visual.ui.Colors;
import dev.mraz1visual.ui.Draw;
import dev.mraz1visual.ui.Fonts;
import dev.mraz1visual.ui.Theme;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;
import net.minecraft.stats.StatsCounter;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Статистика: «Основное» (сводка и все счётчики), «Предметы», «Мобы». */
public final class Mraz1VisualStatsScreen extends MenuBase {
    private enum Tab { GENERAL, ITEMS, MOBS }

    private record Row(String name, String value) {}

    private final Screen parent;
    private Tab tab = Tab.GENERAL;
    private final Item[] tabItems = new Item[3];
    private final List<Row> rows = new ArrayList<>();
    private String[] summary = {"0", "0", "0", "0"};
    private int left, px, py, pw, ph;
    private float scroll, scrollTarget;
    private int rebuildIn;

    private static final int ROW_H = 24;

    public Mraz1VisualStatsScreen(Screen parent) {
        super("Статистика");
        this.parent = parent;
        tabItems[0] = item("ОСНОВНОЕ", () -> setTab(Tab.GENERAL));
        tabItems[1] = item("ПРЕДМЕТЫ", () -> setTab(Tab.ITEMS));
        tabItems[2] = item("МОБЫ", () -> setTab(Tab.MOBS));
        itemAfterGap("НАЗАД", this::onClose);
        tabItems[0].selected = true;
    }

    @Override protected float sunScreenX() { return 0.66f; }
    @Override protected float sunScreenY() { return 0.02f; }

    @Override
    protected void init() {
        super.init();
        if (minecraft.getConnection() != null) {
            minecraft.getConnection().send(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.REQUEST_STATS));
        }
        rebuild();
    }

    @Override
    protected void onLayout() {
        left = Math.max(24, Math.round(vw * 0.056f));
        layoutItems(left, Math.round(vh * 0.32f));
        int itemsRight = left;
        for (Item it : items) itemsRight = Math.max(itemsRight, it.x + it.w);
        px = Math.max(itemsRight + 50, Math.round(vw * 0.29f));
        py = Math.round(vh * 0.40f);
        pw = vw - px - Math.max(24, Math.round(vw * 0.056f));
        ph = vh - py - 48;
    }

    private void setTab(Tab t) {
        tab = t;
        for (int i = 0; i < 3; i++) tabItems[i].selected = i == t.ordinal();
        scroll = scrollTarget = 0;
        rebuild();
    }

    private StatsCounter stats() {
        return minecraft.player == null ? null : minecraft.player.getStats();
    }

    private int custom(Identifier id) {
        StatsCounter s = stats();
        return s == null ? 0 : s.getValue(Stats.CUSTOM.get(id));
    }

    private static String num(double v) {
        return String.format(Locale.ROOT, "%.2f", v).replace('.', ',');
    }

    private void rebuild() {
        rows.clear();
        StatsCounter s = stats();
        if (s == null) return;
        summary = new String[]{
                num(custom(Stats.PLAY_TIME) / 20.0 / 3600.0),
                num(custom(Stats.WALK_ONE_CM) / 100.0),
                Integer.toString(custom(Stats.MOB_KILLS) + custom(Stats.PLAYER_KILLS)),
                Integer.toString(custom(Stats.DEATHS))};
        switch (tab) {
            case GENERAL -> {
                for (Identifier id : BuiltInRegistries.CUSTOM_STAT) {
                    Stat<Identifier> st = Stats.CUSTOM.get(id);
                    String name = Component.translatable("stat." + id.toString().replace(':', '.')).getString();
                    rows.add(new Row(name, st.format(s.getValue(st)).replace('.', ',')));
                }
            }
            case ITEMS -> {
                for (net.minecraft.world.item.Item item : BuiltInRegistries.ITEM) {
                    int mined = item instanceof BlockItem bi ? s.getValue(Stats.BLOCK_MINED.get(bi.getBlock())) : 0;
                    int crafted = s.getValue(Stats.ITEM_CRAFTED.get(item));
                    int used = s.getValue(Stats.ITEM_USED.get(item));
                    int picked = s.getValue(Stats.ITEM_PICKED_UP.get(item));
                    int dropped = s.getValue(Stats.ITEM_DROPPED.get(item));
                    if (mined + crafted + used + picked + dropped == 0) continue;
                    StringBuilder v = new StringBuilder();
                    part(v, "добыто", mined);
                    part(v, "создано", crafted);
                    part(v, "исп.", used);
                    part(v, "подобрано", picked);
                    part(v, "выброшено", dropped);
                    rows.add(new Row(new ItemStack(item).getHoverName().getString(), v.toString()));
                }
            }
            case MOBS -> {
                for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
                    int killed = s.getValue(Stats.ENTITY_KILLED.get(type));
                    int by = s.getValue(Stats.ENTITY_KILLED_BY.get(type));
                    if (killed + by == 0) continue;
                    StringBuilder v = new StringBuilder();
                    part(v, "убито", killed);
                    part(v, "убили вас", by);
                    rows.add(new Row(type.getDescription().getString(), v.toString()));
                }
            }
        }
        if (tab != Tab.GENERAL) rows.sort(Comparator.comparing(Row::name));
    }

    private static void part(StringBuilder sb, String label, int v) {
        if (v == 0) return;
        if (!sb.isEmpty()) sb.append("  ·  ");
        sb.append(label).append(' ').append(v);
    }

    @Override
    public void tick() {
        // статистика приходит с сервера чуть позже открытия — обновляем раз в секунду
        if (++rebuildIn >= 20) {
            rebuildIn = 0;
            rebuild();
        }
    }

    @Override
    protected void renderMenu(double mx, double my, float dt) {
        d.gradientV(0, Math.round(vh * 0.25f), vw, vh - Math.round(vh * 0.25f), 0x00000000, 0xB4000000);
        renderItems(mx, my, dt);
        renderBrand(left);

        // сводка
        String[] labels = {"Ч В ИГРЕ", "М ПЕШКОМ", "УБИЙСТВА", "СМЕРТИ"};
        int sx = px;
        for (int i = 0; i < 4; i++) {
            String v = summary[i];
            d.textScaled(v, Fonts.Kind.TITLE, sx, py - 40, 1.0f, Colors.mulAlpha(Theme.TEXT, fade));
            int vw0 = Fonts.width(v, Fonts.Kind.TITLE);
            d.text(labels[i], Fonts.Kind.HEAD, sx + vw0 + 6, py - 34, Colors.mulAlpha(Theme.TEXT_DIM, fade));
            sx += vw0 + Fonts.width(labels[i], Fonts.Kind.HEAD) + 30;
        }
        d.rect(px, py - 12, pw, 1, Colors.mulAlpha(0x22FFFFFF, fade));

        // строки в две колонки
        scroll = Anim.to(scroll, scrollTarget, dt);
        int cols = tab == Tab.GENERAL ? 2 : 1;
        int colW = cols == 2 ? (pw - 40) / 2 : pw;
        int perCol = (rows.size() + cols - 1) / cols;
        d.clip(px - 4, py, pw + 8, ph);
        int sc = Math.round(scroll);
        for (int i = 0; i < rows.size(); i++) {
            int col = cols == 2 ? i / Math.max(1, perCol) : 0;
            int row = cols == 2 ? i % Math.max(1, perCol) : i;
            int x = px + col * (colW + 40), y = py + row * ROW_H - sc;
            if (y + ROW_H < py || y > py + ph) continue;
            Row r = rows.get(i);
            boolean zero = r.value.equals("0") || r.value.equals("0,00");
            d.text(Draw.ellipsize(r.name, colW - 90), x, Draw.textY(y, ROW_H), Colors.mulAlpha(zero ? Theme.TEXT_DIM : Theme.TEXT, fade));
            d.textRight(r.value, x + colW, Draw.textY(y, ROW_H), Colors.mulAlpha(zero ? Theme.TEXT_DIM : Theme.TEXT, fade));
        }
        if (rows.isEmpty()) d.text(tab == Tab.GENERAL ? "Загрузка…" : "Пока пусто", px, py + 8, Colors.mulAlpha(Theme.TEXT_MUTED, fade));
        d.unclip();
    }

    private int contentH() {
        int cols = tab == Tab.GENERAL ? 2 : 1;
        return ((rows.size() + cols - 1) / cols) * ROW_H;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double sx, double sy) {
        int max = Math.max(0, contentH() - ph);
        scrollTarget = (float) Math.max(0, Math.min(max, scrollTarget - sy * ROW_H * 2));
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        if (e.button() == 0) clickItems(e.x() / k, e.y() / k);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (e.key() == InputConstants.KEY_ESCAPE) {
            onClose();
            return true;
        }
        return false;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}

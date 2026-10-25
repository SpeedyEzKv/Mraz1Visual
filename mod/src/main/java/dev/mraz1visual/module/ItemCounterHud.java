package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.HudLayer;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.render.HudKit;
import dev.mraz1visual.render.HudPos;
import dev.mraz1visual.setting.BoolSetting;
import dev.mraz1visual.ui.Fonts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Сколько в инвентаре тотемов, кристаллов, жемчуга, стрел и золотых яблок: иконка и число.
 * Считается раз в тик (а не в кадре), строки чисел — из готовой таблицы.
 */
public final class ItemCounterHud extends Module implements HudLayer {
    private static final String[] NUM = new String[1000];
    private static final int CELL = 20, PAD = 4;

    final HudPos pos = hudPos(1.0, 0.55);
    final BoolSetting totems = add(new BoolSetting("totems", "Тотемы", true).group("Предметы"));
    final BoolSetting crystals = add(new BoolSetting("crystals", "Кристаллы Энда", true).group("Предметы"));
    final BoolSetting pearls = add(new BoolSetting("pearls", "Эндер-жемчуг", true).group("Предметы"));
    final BoolSetting arrows = add(new BoolSetting("arrows", "Стрелы", true).group("Предметы"));
    final BoolSetting gapples = add(new BoolSetting("gapples", "Золотые яблоки", false).group("Предметы"));
    final BoolSetting hideZero = add(new BoolSetting("hide_zero", "Скрывать, когда 0", true).group("Вид"));
    final BoolSetting vertical = add(new BoolSetting("vertical", "Столбиком", true).group("Вид"));
    final BoolSetting background = add(new BoolSetting("bg", "Фон-карточка", true).group("Вид"));

    private final BoolSetting[] toggles = {totems, crystals, pearls, arrows, gapples};
    private Item[] items;
    private ItemStack[] icons;
    private final int[] counts = new int[5];
    private int shown;

    public ItemCounterHud() {
        super("hud_items", "Item Counter", "Счётчик тотемов, кристаллов, жемчуга и стрел.", Category.HUD);
    }

    private void init() {
        if (items != null) return;
        items = new Item[]{Items.TOTEM_OF_UNDYING, Items.END_CRYSTAL, Items.ENDER_PEARL, Items.ARROW, Items.GOLDEN_APPLE};
        icons = new ItemStack[items.length];
        for (int i = 0; i < items.length; i++) icons[i] = new ItemStack(items[i]);
    }

    @Override
    public void onTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null) return;
        init();
        java.util.Arrays.fill(counts, 0);
        var inv = p.getInventory();
        for (int s = 0, n = inv.getContainerSize(); s < n; s++) {
            ItemStack st = inv.getItem(s);
            if (st.isEmpty()) continue;
            Item it = st.getItem();
            for (int i = 0; i < items.length; i++) if (it == items[i]) counts[i] += st.getCount();
        }
        int k = 0;
        for (int i = 0; i < items.length; i++) if (toggles[i].get() && (counts[i] > 0 || !hideZero.get())) k++;
        shown = k;
    }

    @Override
    public void renderHud(GuiGraphics g, float pt) {
        if (items == null || shown == 0) return;
        Minecraft mc = Minecraft.getInstance();
        boolean v = vertical.get();
        int cellW = CELL + 22;
        int w = v ? cellW + PAD * 2 : shown * cellW + PAD * 2, h = v ? shown * CELL + PAD * 2 : CELL + PAD * 2;
        pos.begin(g, w, h);
        if (background.get()) HudKit.card(g, 0, 0, w, h, 1f);
        int j = 0;
        for (int i = 0; i < items.length; i++) {
            if (!toggles[i].get() || counts[i] == 0 && hideZero.get()) continue;
            int x = PAD + (v ? 0 : j * cellW), y = PAD + (v ? j * CELL : 0);
            g.renderItem(icons[i], x + 2, y + 2);
            int c = counts[i];
            String s = c < NUM.length ? num(c) : "999+";
            Fonts.drawShadow(g, s, Fonts.Kind.BOLD, x + 21, y + 6, c == 0 ? 0xFFFF6B6B : 0xFFE9E9F1);
            j++;
        }
        pos.end(g);
    }

    private static String num(int c) {
        String s = NUM[c];
        if (s == null) NUM[c] = s = Integer.toString(c);
        return s;
    }
}

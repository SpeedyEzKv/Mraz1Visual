package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.HudLayer;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.render.Ease;
import dev.mraz1visual.render.HudStyle;
import dev.mraz1visual.setting.BoolSetting;
import dev.mraz1visual.setting.EnumSetting;
import dev.mraz1visual.setting.SliderSetting;
import dev.mraz1visual.ui.Colors;
import dev.mraz1visual.ui.Fonts;
import dev.mraz1visual.ui.Sdf;
import dev.mraz1visual.ui.Spring;
import dev.mraz1visual.ui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Уведомления.
 * «Остров» сверху — включение и выключение функций (стеклянная капсула пружиной растягивается под текст).
 * Справа сверху — стопка всплывашек (до трёх): подобран предмет, сработал тотем, тебя упомянули в чате.
 * Одинаковые подряд склеиваются («+16 Эндер-жемчуг»), всплывашки выезжают справа и уезжают обратно.
 */
public final class Notifications extends Module implements HudLayer {
    private static Notifications instance;
    final EnumSetting style = add(new EnumSetting("style", "Вид", 0, "island", "Остров сверху", "cards", "Карточки справа").group("Функции"));
    final BoolSetting modules = add(new BoolSetting("modules", "Включение функций", true).group("Функции"));
    final BoolSetting inMenu = add(new BoolSetting("in_menu", "И при переключении в меню", false).group("Функции"));
    final BoolSetting pickup = add(new BoolSetting("pickup", "Подобран предмет", true).group("Всплывашки"));
    final BoolSetting totem = add(new BoolSetting("totem", "Сработал тотем", true).group("Всплывашки"));
    final BoolSetting mention = add(new BoolSetting("mention", "Упоминание в чате", true).group("Всплывашки"));
    final BoolSetting sound = add(new BoolSetting("sound", "Звук", false).group("Всплывашки"));
    final SliderSetting duration = add(new SliderSetting("duration", "Длительность", 3, 1, 6, 0.5, " с").group("Всплывашки"));

    // ── остров ──
    private String name = "", state = "";
    private boolean on;
    private long at;
    private float textW;
    private final Spring width = new Spring(320f, 22f, 0f);
    private float show, textA;
    private long last;

    // ── всплывашки: кольцо из трёх, без аллокаций в кадре ──
    private static final int SLOTS = 3, KIND_ITEM = 0, KIND_TOTEM = 1, KIND_CHAT = 2;
    private final int[] kind = new int[SLOTS], amount = new int[SLOTS];
    private final Item[] item = new Item[SLOTS];
    private final ItemStack[] icon = new ItemStack[SLOTS];
    private final String[] title = new String[SLOTS], sub = new String[SLOTS];
    private final long[] born = new long[SLOTS];
    private final float[] slide = new float[SLOTS], slotY = new float[SLOTS];
    private int head;

    // подсчёт подобранного: всего предметов каждого вида в прошлом тике
    private Map<Item, Integer> prev = new IdentityHashMap<>(), cur = new IdentityHashMap<>();
    private int warmup;

    public Notifications() {
        super("notifications", "Notifications", "Функции, подобранные предметы, тотемы и упоминания в чате.", Category.HUD);
        instance = this;
        loadEnabled(true);
        markNew();
    }

    /** Из Module.setEnabled. */
    public static void post(Module m, boolean on) {
        Notifications n = instance;
        if (n == null || !n.isEnabled() || !n.modules.get() || m == n || m.alwaysOn()) return;
        Minecraft mc = Minecraft.getInstance();
        if (!n.inMenu.get() && mc.screen != null) return;
        n.name = m.name;
        n.on = on;
        n.state = on ? "включено" : "выключено";
        n.at = System.currentTimeMillis();
        n.textA = 0;
        n.textW = Fonts.width(n.name, Fonts.Kind.BOLD) + Fonts.width(n.state, Fonts.Kind.TEXT) + 12;
    }

    // ── источники всплывашек ─────────────────────────────────

    /** Свой тотем сработал (из ParticleEngineMixin). Остаток считается в следующем тике — тотем уже убран из руки. */
    public static void totemPopped() {
        Notifications n = instance;
        if (n == null || !n.isEnabled() || !n.totem.get()) return;
        n.pendingTotem = true;
    }

    private boolean pendingTotem;

    /** Входящее сообщение чата (из ChatComponentMixin): упомянули мой ник. */
    public static void chat(Component message) {
        Notifications n = instance;
        if (n == null || !n.isEnabled() || !n.mention.get() || message == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getUser() == null || mc.player == null) return;
        String me = mc.getUser().getName();
        String text = message.getString();
        String low = text.toLowerCase(Locale.ROOT), meLow = me.toLowerCase(Locale.ROOT);
        int i = low.indexOf(meLow);
        if (i < 0) return;
        // своё же сообщение: ник стоит как отправитель («<Ник>», «[G] Ник: …», «Ник » …») и больше не встречается
        int after = i + meLow.length();
        boolean asSender = low.contains("<" + meLow + ">") || i < 32 && (low.startsWith(":", after) || low.startsWith(" »", after)
                || low.startsWith(" ->", after) || low.startsWith(" →", after));
        if (asSender && low.indexOf(meLow, after) < 0) return;
        String body = text.length() > 60 ? text.substring(0, 58) + "…" : text;
        n.push(KIND_CHAT, null, 0, "Упоминание", StreamerMode.filter(body));
    }

    @Override
    public void onTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null) {
            warmup = 0;
            return;
        }
        if (pendingTotem) {
            pendingTotem = false;
            int left = count(p, Items.TOTEM_OF_UNDYING);
            push(KIND_TOTEM, Items.TOTEM_OF_UNDYING, 1, "Тотем сработал", left == 0 ? "Тотемов больше нет" : "Осталось: " + left);
        }
        if (!pickup.get()) return;
        cur.clear();
        var inv = p.getInventory();
        for (int s = 0, n = inv.getContainerSize(); s < n; s++) {
            ItemStack st = inv.getItem(s);
            if (!st.isEmpty()) cur.merge(st.getItem(), st.getCount(), Integer::sum);
        }
        // первые тики после входа/смерти — инвентарь только приходит с сервера
        if (warmup < 40 || mc.screen != null || p.isCreative()) {
            warmup++;
        } else {
            for (Map.Entry<Item, Integer> e : cur.entrySet()) {
                int d = e.getValue() - prev.getOrDefault(e.getKey(), 0);
                if (d > 0) push(KIND_ITEM, e.getKey(), d, null, null);
            }
        }
        Map<Item, Integer> t = prev;
        prev = cur;
        cur = t;
    }

    private static int count(LocalPlayer p, Item it) {
        int c = 0;
        var inv = p.getInventory();
        for (int s = 0, n = inv.getContainerSize(); s < n; s++) {
            ItemStack st = inv.getItem(s);
            if (st.getItem() == it) c += st.getCount();
        }
        return c;
    }

    private void push(int k, Item it, int amt, String t, String s) {
        long now = System.currentTimeMillis();
        long life = Math.round(duration.get() * 1000);
        // склейка: такой же предмет ещё на экране — прибавляем и продлеваем
        if (k == KIND_ITEM) {
            for (int i = 0; i < SLOTS; i++) {
                if (kind[i] == KIND_ITEM && item[i] == it && born[i] != 0 && now - born[i] < life) {
                    amount[i] += amt;
                    title[i] = "+" + amount[i] + " " + icon[i].getHoverName().getString();
                    born[i] = now;
                    return;
                }
            }
        }
        int i = head;
        head = (head + 1) % SLOTS;
        kind[i] = k;
        item[i] = it;
        amount[i] = amt;
        icon[i] = it == null ? null : new ItemStack(it);
        title[i] = k == KIND_ITEM ? "+" + amt + " " + icon[i].getHoverName().getString() : t;
        sub[i] = s;
        born[i] = now;
        slide[i] = 0;
        if (sound.get()) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), k == KIND_CHAT ? 1.6f : 1.2f, 0.5f));
        }
    }

    @Override
    public void renderHud(GuiGraphics g, float pt) {
        long nowNs = System.nanoTime();
        float dt = last == 0 ? 0 : Math.min(0.05f, (nowNs - last) / 1e9f);
        last = nowNs;
        renderToasts(g, dt);
        renderIsland(g, dt);
    }

    private void renderToasts(GuiGraphics g, float dt) {
        long now = System.currentTimeMillis();
        long life = Math.round(duration.get() * 1000);
        int sw = g.guiWidth(), y = 6, h = 26;
        // новые сверху: обходим от последнего добавленного
        for (int k = 0; k < SLOTS; k++) {
            int i = (head - 1 - k + SLOTS * 2) % SLOTS;
            if (born[i] == 0) continue;
            long age = now - born[i];
            boolean alive = age < life;
            slide[i] = Ease.approach(slide[i], alive ? 1 : 0, alive ? 14 : 9, dt);
            if (!alive && slide[i] < 0.01f) {
                born[i] = 0;
                continue;
            }
            slotY[i] = slotY[i] == 0 ? y : slotY[i] + (y - slotY[i]) * Math.min(1f, dt * 12f);
            float e = Ease.outCubic(slide[i]);
            String t = title[i], s = sub[i];
            float w = Math.max(Fonts.width(t, Fonts.Kind.BOLD), s == null ? 0 : Fonts.width(s, Fonts.Kind.TEXT)) + 34;
            float x = sw - 6 - w + (1 - e) * (w + 12);
            float ty = slotY[i];
            HudStyle.panel(g, x, ty, w, h, 9, e);
            int accent = kind[i] == KIND_TOTEM ? HudStyle.YELLOW : kind[i] == KIND_CHAT ? HudStyle.GRAD_B : HudStyle.GRAD_A;
            Sdf.fill(g, x + 2, ty + 5, 2, h - 10, 1, Colors.mulAlpha(accent, e));
            if (icon[i] != null) g.renderItem(icon[i], Math.round(x + 8), Math.round(ty + 5));
            else Fonts.draw(g, "@", Fonts.Kind.BOLD, x + 11, ty + 9, Colors.mulAlpha(accent, e));
            float tx = x + 28;
            if (s == null) Fonts.draw(g, t, Fonts.Kind.BOLD, tx, ty + 9, Colors.mulAlpha(HudStyle.TEXT, e));
            else {
                Fonts.draw(g, t, Fonts.Kind.BOLD, tx, ty + 4, Colors.mulAlpha(HudStyle.TEXT, e));
                Fonts.draw(g, s, Fonts.Kind.TEXT, tx, ty + 14, Colors.mulAlpha(HudStyle.TEXT_DIM, e));
            }
            y += Math.round((h + 4) * e);
        }
    }

    private void renderIsland(GuiGraphics g, float dt) {
        float age = (System.currentTimeMillis() - at) / 1000f;
        boolean alive = at != 0 && age < 2.2f;
        show = Ease.approach(show, alive ? 1 : 0, alive ? 14 : 7, dt);
        if (show < 0.01f) {
            width.snap(0);
            return;
        }
        boolean cards = style.getId().equals("cards");
        float target = alive ? textW + 34 : 22;
        width.target = target;
        float w = Math.max(22, width.update(dt));
        textA = Ease.approach(textA, alive && w > target * 0.85f ? 1 : 0, 12, dt);
        float h = 22, sw = g.guiWidth();
        float x = cards ? sw - 6 - w : (sw - w) / 2f, y = cards ? g.guiHeight() - 34 : 6 + (1 - Ease.outCubic(show)) * -10;
        int a = Math.round(255 * show);
        Sdf.shape(g, x, y + 2, w, h, h / 2, Sdf.SHADOW, 10, Colors.withAlpha(0x000000, a / 3), Colors.withAlpha(0x000000, a / 3));
        Sdf.shape(g, x, y, w, h, h / 2, Sdf.GLASS, 8f, Colors.withAlpha(0x0B0B10, Math.round(0xD8 * show)), Colors.withAlpha(0x0B0B10, Math.round(0xD8 * show)));
        // индикатор: акцент — включено, серый — выключено
        int dot = on ? Theme.accent() : 0xFF6A6A78;
        Sdf.fill(g, x + 8, y + h / 2 - 3, 6, 6, 3, Colors.mulAlpha(dot, show));
        if (textA > 0.02f) {
            float ta = textA * show;
            g.enableScissor(Math.round(x), Math.round(y), Math.round(x + w), Math.round(y + h));
            float tx = x + 20;
            Fonts.draw(g, name, Fonts.Kind.BOLD, tx, y + 7, Colors.mulAlpha(0xFFF2F2F7, ta));
            Fonts.draw(g, state, Fonts.Kind.TEXT, tx + Fonts.width(name, Fonts.Kind.BOLD) + 6, y + 7, Colors.mulAlpha(on ? 0xFFB8D8FF : 0xFF8E8EA0, ta));
            g.disableScissor();
        }
    }
}

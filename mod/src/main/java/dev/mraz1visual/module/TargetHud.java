package dev.mraz1visual.module;

import dev.mraz1visual.core.AttackListener;
import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.HudLayer;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.core.WorldLayer;
import dev.mraz1visual.render.Ease;
import dev.mraz1visual.render.HudPos;
import dev.mraz1visual.render.HudStyle;
import dev.mraz1visual.render.WorldDraw;
import dev.mraz1visual.setting.BoolSetting;
import dev.mraz1visual.setting.EnumSetting;
import dev.mraz1visual.setting.SliderSetting;
import dev.mraz1visual.ui.Colors;
import dev.mraz1visual.ui.Fonts;
import dev.mraz1visual.ui.Sdf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;

import java.util.Locale;

/**
 * Target HUD: появляется, когда смотришь на игрока или бьёшь цель.
 * «Классика» — голова, ник, дистанция, HP цифрой и полосой, 4 ячейки брони с прочностью в %.
 * «Плашка» — узкая капсула: голова, ник, полоса HP, число.
 * Искры при ударе, светящиеся частицы и кольцо вокруг цели (в мире, со своей глубиной — сквозь стены не видны).
 * TargetCooldowns — только то, что клиент видит сам: брошенный этой целью жемчуг (КД 1 с) и щит, выбитый твоим топором (5 с).
 * Ни одного пакета — всё по уже полученным данным.
 */
public final class TargetHud extends Module implements HudLayer, AttackListener, WorldLayer {
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final int SPARKS = 16;

    final EnumSetting style = add(new EnumSetting("style", "Стиль", 0, "classic", "Классика", "pill", "Плашка"));
    final SliderSetting keep = add(new SliderSetting("keep", "Время показа после удара", 2, 1, 5, 0.5, " с"));
    final BoolSetting onLook = add(new BoolSetting("on_look", "Когда смотришь на игрока", true));
    final EnumSetting place = add(new EnumSetting("place", "Где", 0, "cross", "У прицела", "top", "Сверху", "bottom", "Над хотбаром",
            "free", "Свободно (редактор HUD)"));
    final HudPos pos = hudPos(0.5, 0.62).activeWhen(() -> place.getId().equals("free"), () -> place.set(3));
    final SliderSetting scale = add(new SliderSetting("scale", "Размер", 1, 0.7, 1.5, 0.05, "×"));
    final BoolSetting sparks = add(new BoolSetting("sparks", "Искры при ударе", true).group("Эффекты"));
    final BoolSetting particles = add(new BoolSetting("particles", "Частицы вокруг цели", true).group("Эффекты"));
    final SliderSetting particleCount = add(new SliderSetting("particle_count", "Количество частиц", 30, 10, 60, 1, "").group("Эффекты"));
    final BoolSetting cooldowns = add(new BoolSetting("cooldowns", "TargetCooldowns", true).group("Эффекты")
            .hint("Жемчуг цели (по брошенному снаряду) и щит, выбитый твоим топором. Остальное клиент надёжно знать не может."));

    private LivingEntity target;
    private long hitAt, lookAt;
    private float shownHp = -1, lagHp, alpha, pop;
    private long last, lagHold;
    private String name = "", hpText = "", letter = "", distText = "";
    private float lastHp = -1;
    private int lastDist = -1;
    private final int[] armorPct = new int[4];
    private final String[] armorText = new String[4];
    private final ItemStack[] armorStack = new ItemStack[4];

    // искры: координаты в плашке, скорость, возраст
    private final float[] spX = new float[SPARKS], spY = new float[SPARKS], spVx = new float[SPARKS], spVy = new float[SPARKS], spAge = new float[SPARKS];
    private int spNext;

    // TargetCooldowns: конец КД в мс; -1 — нет
    private long pearlUntil, shieldUntil;
    private int lastPearlId = -1;
    private String pearlText = "", shieldText = "";
    private int pearlTenths = -1, shieldTenths = -1;

    public TargetHud() {
        super("target_hud", "Target HUD", "Цель: два стиля, броня, искры, частицы и КД предметов врага.", Category.HUD);
        markNew();
        java.util.Arrays.fill(spAge, 99);
    }

    @Override
    public void onAttack(Entity e) {
        if (!(e instanceof LivingEntity le)) return;
        Minecraft mc = Minecraft.getInstance();
        setTarget(le);
        hitAt = System.currentTimeMillis();
        pop = 1f;
        if (sparks.get()) spawnSparks();
        // щит выбит своим топором: ваниль отключает щит на 5 секунд
        if (mc.player != null && le.isBlocking() && mc.player.getItemInHand(InteractionHand.MAIN_HAND).is(ItemTags.AXES)) {
            shieldUntil = System.currentTimeMillis() + 5000;
        }
    }

    private void setTarget(LivingEntity le) {
        if (le == target) return;
        target = le;
        name = ellipsize(StreamerMode.filter(le.getName().getString()), 82);
        letter = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(Locale.ROOT);
        lastHp = -1;
        lastDist = -1;
        pearlUntil = shieldUntil = 0;
        // HP-полоса не прыгает: плавно доезжает от прошлой цели к новой
        if (shownHp < 0) shownHp = lagHp = le.getHealth() / Math.max(1f, le.getMaxHealth());
    }

    private void spawnSparks() {
        for (int i = 0; i < 6; i++) {
            int s = spNext;
            spNext = (spNext + 1) % SPARKS;
            double a = Math.random() * Math.PI * 2;
            float v = 60 + (float) Math.random() * 60;
            spX[s] = 20;
            spY[s] = 20;
            spVx[s] = (float) Math.cos(a) * v;
            spVy[s] = (float) Math.sin(a) * v;
            spAge[s] = 0;
        }
    }

    @Override
    public void onTick(Minecraft mc) {
        if (mc.player == null || mc.level == null) return;
        // смотришь на игрока — он цель
        if (onLook.get() && mc.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof Player pl && pl != mc.player) {
            setTarget(pl);
            lookAt = System.currentTimeMillis();
        }
        if (target == null || !cooldowns.get()) return;
        // жемчуг, брошенный целью: снаряд виден клиенту вместе с владельцем
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof ThrownEnderpearl tp && tp.getId() > lastPearlId && tp.getOwner() == target) {
                lastPearlId = tp.getId();
                pearlUntil = System.currentTimeMillis() + 1000;
            }
        }
    }

    @Override
    public void renderWorld(WorldDraw d, float pt) {
        if (!particles.get() || alpha < 0.05f || target == null) return;
        double x = net.minecraft.util.Mth.lerp(pt, target.xo, target.getX());
        double y = net.minecraft.util.Mth.lerp(pt, target.yo, target.getY());
        double z = net.minecraft.util.Mth.lerp(pt, target.zo, target.getZ());
        float t = (System.nanoTime() % 1_000_000_000_000L) / 1e9f;
        int n = particleCount.getI();
        double r = Math.max(0.5, target.getBbWidth() * 0.9), hgt = target.getBbHeight();
        for (int i = 0; i < n; i++) {
            float u = i / (float) n;
            double ang = t * (0.8 + (i % 3) * 0.25) + u * Math.PI * 2;
            double py = y + 0.15 + (Math.sin(t * 1.6 + i * 1.7) * 0.5 + 0.5) * hgt;
            int c = Colors.mulAlpha(HudStyle.grad(u), alpha * (0.6f + 0.4f * (float) Math.sin(t * 3 + i)));
            d.billboardDot(x + Math.cos(ang) * r, py, z + Math.sin(ang) * r, 0.045, c, 6);
        }
        d.ring(x, y + 0.02, z, r * 0.75, r * 0.85, 48, Colors.mulAlpha(HudStyle.GRAD_A, alpha * 0.8f), Colors.mulAlpha(HudStyle.GRAD_B, alpha * 0.25f));
    }

    @Override
    public void renderHud(GuiGraphics g, float pt) {
        long now = System.nanoTime();
        float dt = last == 0 ? 0 : Math.min(0.1f, (now - last) / 1e9f);
        last = now;
        Minecraft mc = Minecraft.getInstance();
        long ms = System.currentTimeMillis();
        boolean recent = ms - hitAt < keep.get() * 1000 || ms - lookAt < 300;
        boolean visible = target != null && !target.isRemoved() && !target.isDeadOrDying() && recent
                && mc.player != null && mc.player.hasLineOfSight(target);
        alpha = Ease.approach(alpha, visible ? 1 : 0, visible ? 12 : 8, dt);
        pop = Ease.approach(pop, 0, 7, dt);
        if (alpha < 0.02f || target == null) {
            if (alpha < 0.02f) shownHp = -1;
            return;
        }
        update(mc, now, dt);

        boolean pill = style.getId().equals("pill");
        int w = pill ? 150 : 162, h = pill ? 22 : 54;
        int chips = chipsHeight(ms);
        float k = scale.getF();
        int sw = g.guiWidth(), sh = g.guiHeight();
        boolean free = pos.active();
        float cx, cy;
        if (free) {
            pos.begin(g, Math.round(w * k), Math.round((h + chips) * k));
            cx = 0;
            cy = 0;
        } else {
            cx = switch (place.getId()) {
                case "top", "bottom" -> sw / 2f - w * k / 2;
                default -> sw / 2f + 16;
            };
            cy = switch (place.getId()) {
                case "top" -> 26;
                case "bottom" -> sh - 72 - (h + chips) * k;
                default -> sh / 2f + 14;
            };
            pos.track(Math.round(cx), Math.round(cy), Math.round(w * k), Math.round((h + chips) * k));
        }
        float s = (free ? 1 : k) * (1 + 0.03f * Ease.smooth(pop)) * (0.94f + 0.06f * alpha);
        g.pose().pushMatrix();
        g.pose().translate(cx + w * k / 2, cy + h * k / 2);
        g.pose().scale(s, s);
        g.pose().translate(-w / 2f, -h / 2f);
        if (pill) drawPill(g, w, h);
        else drawClassic(g, w, h);
        drawChips(g, h + 3, ms);
        drawSparks(g, dt);
        g.pose().popMatrix();
        if (free) pos.end(g);
    }

    private void update(Minecraft mc, long now, float dt) {
        float hp = target.getHealth() + target.getAbsorptionAmount();
        float max = Math.max(1f, target.getMaxHealth());
        float f = Math.min(1f, hp / max);
        if (hp != lastHp) {
            if (lastHp >= 0 && hp < lastHp) lagHold = now + 350_000_000L; // урон: «след» постоит и догонит
            lastHp = hp;
            int t = Math.round(hp * 5); // половинки сердец с десятыми
            hpText = t / 10 + "," + t % 10;
        }
        if (shownHp < 0) shownHp = lagHp = f;
        shownHp = Ease.approach(shownHp, f, 14, dt);
        if (now > lagHold || f > lagHp) lagHp = Ease.approach(lagHp, f, 4, dt);
        int dist = mc.player == null ? 0 : Math.round(mc.player.distanceTo(target) * 10);
        if (dist != lastDist) {
            lastDist = dist;
            distText = dist / 10 + "," + dist % 10 + " м";
        }
        for (int i = 0; i < 4; i++) {
            ItemStack st = target.getItemBySlot(ARMOR[i]);
            armorStack[i] = st;
            int pct = st.isEmpty() ? -1 : st.isDamageableItem() ? Math.round(100f * (st.getMaxDamage() - st.getDamageValue()) / Math.max(1, st.getMaxDamage())) : 100;
            if (pct != armorPct[i] || armorText[i] == null) {
                armorPct[i] = pct;
                armorText[i] = pct < 0 ? "" : pct + "%";
            }
        }
    }

    private void head(GuiGraphics g, int hx, int hy, int hs, float r) {
        int a255 = Math.round(255 * alpha);
        if (target instanceof AbstractClientPlayer pl) {
            Sdf.fill(g, hx - 1, hy - 1, hs + 2, hs + 2, r, Colors.mulAlpha(0x40FFFFFF, alpha));
            net.minecraft.client.gui.components.PlayerFaceRenderer.draw(g, pl.getSkin(), hx, hy, hs, Colors.withAlpha(0xFFFFFF, a255));
        } else {
            Sdf.fill(g, hx, hy, hs, hs, r, Colors.mulAlpha(0x26FFFFFF, alpha));
            int lw = Fonts.width(letter, Fonts.Kind.BOLD);
            Fonts.draw(g, letter, Fonts.Kind.BOLD, hx + hs / 2f - lw / 2f, hy + hs / 2f - 4, Colors.mulAlpha(0xFFE6E6F4, alpha));
        }
    }

    private int hpColor() {
        return Colors.lerp(HudStyle.RED, Colors.lerp(HudStyle.YELLOW, HudStyle.GREEN, Math.max(0, shownHp - 0.5f) * 2), Math.min(1, shownHp * 2));
    }

    private void hpBar(GuiGraphics g, float bx, float by, float bw, float bh) {
        Sdf.fill(g, bx, by, bw, bh, bh / 2, Colors.mulAlpha(0x24FFFFFF, alpha));
        if (lagHp > shownHp) Sdf.fill(g, bx, by, bw * lagHp, bh, bh / 2, Colors.mulAlpha(0x88FFFFFF, alpha));
        if (shownHp > 0.005f) Sdf.fill(g, bx, by, Math.max(bh, bw * shownHp), bh, bh / 2, Colors.mulAlpha(hpColor(), alpha));
    }

    private void drawClassic(GuiGraphics g, int w, int h) {
        HudStyle.panel(g, 0, 0, w, h, 14, alpha);
        int hx = 6, hy = 6, hs = 28;
        head(g, hx, hy, hs, 8);
        int tx = hx + hs + 7;
        Fonts.draw(g, name, Fonts.Kind.BOLD, tx, 7, Colors.mulAlpha(HudStyle.TEXT, alpha));
        int dw = Fonts.width(distText, Fonts.Kind.TEXT);
        Fonts.draw(g, distText, Fonts.Kind.TEXT, w - 8 - dw, 7, Colors.mulAlpha(HudStyle.TEXT_DIM, alpha));
        int tw = Fonts.width(hpText, Fonts.Kind.BOLD);
        Fonts.draw(g, hpText, Fonts.Kind.BOLD, tx, 18, Colors.mulAlpha(hpColor(), alpha));
        hpBar(g, tx + tw + 5, 20, w - tx - tw - 13, 5);
        // 4 ячейки брони: иконка и прочность (зелёный > 50, жёлтый > 25, иначе красный)
        for (int i = 0; i < 4; i++) {
            int cx = 6 + i * 38, cy = 37;
            Sdf.fill(g, cx, cy, 36, 13, 4, Colors.mulAlpha(0x14FFFFFF, alpha));
            if (armorPct[i] < 0) continue;
            g.pose().pushMatrix();
            g.pose().translate(cx + 1, cy + 0.5f);
            g.pose().scale(0.75f, 0.75f);
            g.renderItem(armorStack[i], 0, 0);
            g.pose().popMatrix();
            int c = armorPct[i] > 50 ? HudStyle.GREEN : armorPct[i] > 25 ? HudStyle.YELLOW : HudStyle.RED;
            Fonts.draw(g, armorText[i], Fonts.Kind.BOLD, cx + 14, cy + 3.5f, 0.78f, Colors.mulAlpha(c, alpha));
        }
    }

    private void drawPill(GuiGraphics g, int w, int h) {
        HudStyle.panel(g, 0, 0, w, h, h / 2f, alpha);
        head(g, 3, 3, h - 6, (h - 6) / 2f);
        int tx = h + 2;
        Fonts.draw(g, name, Fonts.Kind.BOLD, tx, 4, Colors.mulAlpha(HudStyle.TEXT, alpha));
        int tw = Fonts.width(hpText, Fonts.Kind.BOLD);
        Fonts.draw(g, hpText, Fonts.Kind.BOLD, w - 9 - tw, 7, Colors.mulAlpha(hpColor(), alpha));
        hpBar(g, tx, 14, w - tx - tw - 15, 3.5f);
    }

    private int chipsHeight(long ms) {
        return cooldowns.get() && (pearlUntil > ms || shieldUntil > ms) ? 15 : 0;
    }

    private void drawChips(GuiGraphics g, int y, long ms) {
        if (!cooldowns.get()) return;
        float x = 0;
        if (pearlUntil > ms) {
            int t = (int) ((pearlUntil - ms + 99) / 100);
            if (t != pearlTenths) { pearlTenths = t; pearlText = "Перл " + t / 10 + "," + t % 10 + "с"; }
            x = chip(g, x, y, pearlText, HudStyle.GRAD_B);
        }
        if (shieldUntil > ms) {
            int t = (int) ((shieldUntil - ms + 99) / 100);
            if (t != shieldTenths) { shieldTenths = t; shieldText = "Щит " + t / 10 + "," + t % 10 + "с"; }
            chip(g, x, y, shieldText, HudStyle.YELLOW);
        }
    }

    private float chip(GuiGraphics g, float x, int y, String text, int color) {
        float w = Fonts.width(text, Fonts.Kind.BOLD) + 12;
        HudStyle.panel(g, x, y, w, 12, 6, alpha);
        Sdf.fill(g, x + 4, y + 4.5f, 3, 3, 1.5f, Colors.mulAlpha(color, alpha));
        Fonts.draw(g, text, Fonts.Kind.BOLD, x + 9, y + 2, Colors.mulAlpha(HudStyle.TEXT, alpha));
        return x + w + 3;
    }

    /** Короткие жёлтые штрихи разлетаются от головы и гаснут за 0.35 с. */
    private void drawSparks(GuiGraphics g, float dt) {
        for (int i = 0; i < SPARKS; i++) {
            if (spAge[i] > 0.35f) continue;
            spAge[i] += dt;
            spX[i] += spVx[i] * dt;
            spY[i] += spVy[i] * dt;
            float a = Math.max(0, 1 - spAge[i] / 0.35f) * alpha;
            float len = Math.max(1, (float) Math.hypot(spVx[i], spVy[i]));
            float ux = spVx[i] / len, uy = spVy[i] / len;
            for (int k = 0; k < 4; k++) {
                float px = spX[i] - ux * k * 1.5f, py = spY[i] - uy * k * 1.5f;
                Sdf.fill(g, px - 0.8f, py - 0.8f, 1.6f, 1.6f, 0.8f, Colors.mulAlpha(HudStyle.YELLOW, a * (1 - k / 4f)));
            }
        }
    }

    private static String ellipsize(String s, int maxW) {
        if (Fonts.width(s, Fonts.Kind.BOLD) <= maxW) return s;
        int end = s.length();
        while (end > 0 && Fonts.width(s.substring(0, end) + "…", Fonts.Kind.BOLD) > maxW) end--;
        return s.substring(0, end) + "…";
    }
}

package dev.mraz1visual.module;

import dev.mraz1visual.core.AttackListener;
import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.HudLayer;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.render.Ease;
import dev.mraz1visual.setting.EnumSetting;
import dev.mraz1visual.setting.SliderSetting;
import dev.mraz1visual.ui.Colors;
import dev.mraz1visual.ui.Fonts;
import dev.mraz1visual.ui.Sdf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.Locale;

/**
 * Стеклянная плашка цели после твоего удара: голова скина (у игроков), ник, полоса здоровья с «догоняющей»
 * полосой урона, броня. Только та информация, что видна и в ванили, и только пока цель в прямой видимости.
 */
public final class TargetHud extends Module implements HudLayer, AttackListener {
    final SliderSetting keep = add(new SliderSetting("keep", "Показывать", 3, 1, 10, 0.5, " с"));
    final EnumSetting place = add(new EnumSetting("place", "Где", 0, "cross", "У прицела", "top", "Сверху", "bottom", "Над хотбаром"));
    final SliderSetting scale = add(new SliderSetting("scale", "Размер", 1, 0.7, 1.5, 0.05, "×"));

    private LivingEntity target;
    private long hitAt;
    private float shownHp, lagHp, alpha, pop;
    private long last, lagHold;
    private String name = "", hpText = "", letter = "";
    private float lastHp = -1;
    private int lastArmor = -1;
    private String armorText = "";

    public TargetHud() {
        super("target_hud", "Target HUD", "Стеклянная плашка цели: голова, ник, здоровье с плавной полосой, броня.", Category.VISUALS);
    }

    @Override
    public void onAttack(Entity e) {
        if (!(e instanceof LivingEntity le)) return;
        if (le != target) {
            target = le;
            name = ellipsize(le.getName().getString(), 132 - 41 - 34);
            letter = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(Locale.ROOT);
            shownHp = lagHp = le.getHealth() / Math.max(1f, le.getMaxHealth());
            lastHp = -1;
        }
        hitAt = System.currentTimeMillis();
        pop = 1f;
    }

    @Override
    public void renderHud(GuiGraphics g, float pt) {
        long now = System.nanoTime();
        float dt = last == 0 ? 0 : Math.min(0.1f, (now - last) / 1e9f);
        last = now;
        Minecraft mc = Minecraft.getInstance();
        boolean visible = target != null && !target.isRemoved() && !target.isDeadOrDying()
                && System.currentTimeMillis() - hitAt < keep.get() * 1000 && mc.player != null && mc.player.hasLineOfSight(target);
        alpha = Ease.approach(alpha, visible ? 1 : 0, visible ? 12 : 8, dt);
        pop = Ease.approach(pop, 0, 7, dt);
        if (alpha < 0.02f || target == null) return;

        float hp = target.getHealth() + target.getAbsorptionAmount();
        float max = Math.max(1f, target.getMaxHealth());
        float f = Math.min(1f, hp / max);
        if (hp != lastHp) {
            if (lastHp >= 0 && hp < lastHp) lagHold = now + 350_000_000L; // урон: «след» постоит и догонит
            lastHp = hp;
            hpText = String.format(Locale.ROOT, "%.1f", hp / 2f).replace('.', ',');
        }
        shownHp = Ease.approach(shownHp, f, 14, dt);
        if (now > lagHold || f > lagHp) lagHp = Ease.approach(lagHp, f, 4, dt);

        float k = scale.getF();
        int w = 132, h = 40;
        int sw = g.guiWidth(), sh = g.guiHeight();
        float cx = switch (place.getId()) {
            case "top", "bottom" -> sw / 2f - w * k / 2;
            default -> sw / 2f + 16;
        };
        float cy = switch (place.getId()) {
            case "top" -> 26;
            case "bottom" -> sh - 72 - h * k;
            default -> sh / 2f + 14;
        };
        float s = k * (1 + 0.03f * Ease.smooth(pop));
        g.pose().pushMatrix();
        g.pose().translate(cx + w * k / 2, cy + h * k / 2);
        g.pose().scale(s * (0.94f + 0.06f * alpha), s * (0.94f + 0.06f * alpha));
        g.pose().translate(-w / 2f, -h / 2f);
        int a255 = Math.round(255 * alpha);
        // стекло: мягкая тень, тёмная тонировка, светлый кант сверху
        Sdf.shape(g, 0, 3, w, h, 12, Sdf.SHADOW, 14, Colors.withAlpha(0x000000, a255 / 3), Colors.withAlpha(0x000000, a255 / 3));
        Sdf.shape(g, 0, 0, w, h, 12, Sdf.GLASS, 8f, Colors.mulAlpha(0xC40E0E14, alpha), Colors.mulAlpha(0xC40E0E14, alpha));
        // голова
        int hx = 6, hy = 6, hs = 28;
        if (target instanceof AbstractClientPlayer pl) {
            Sdf.fill(g, hx - 1, hy - 1, hs + 2, hs + 2, 8, Colors.mulAlpha(0x40FFFFFF, alpha));
            net.minecraft.client.gui.components.PlayerFaceRenderer.draw(g, pl.getSkin(), hx, hy, hs, Colors.withAlpha(0xFFFFFF, a255));
        } else {
            Sdf.fill(g, hx, hy, hs, hs, 8, Colors.mulAlpha(0x26FFFFFF, alpha));
            int lw = Fonts.width(letter, Fonts.Kind.BOLD);
            Fonts.draw(g, letter, Fonts.Kind.BOLD, hx + hs / 2f - lw / 2f, hy + hs / 2f - 4, Colors.mulAlpha(0xFFE6E6F4, alpha));
        }
        int tx = hx + hs + 7;
        Fonts.draw(g, name, Fonts.Kind.BOLD, tx, 7, Colors.mulAlpha(0xFFF2F2F7, alpha));
        int tw = Fonts.width(hpText, Fonts.Kind.TEXT);
        Fonts.draw(g, hpText, Fonts.Kind.TEXT, w - 18 - tw, 7, Colors.mulAlpha(0xFFB4B4C4, alpha));
        Sdf.fill(g, w - 14, 8.5f, 6, 6, 3, Colors.mulAlpha(0xFFFF6B7D, alpha));
        // полоса здоровья: трек, «след» урона (светлый), само здоровье (цвет по доле)
        int bx = tx, by = 22, bw = w - tx - 8;
        Sdf.fill(g, bx, by, bw, 5, 2.5f, Colors.mulAlpha(0x24FFFFFF, alpha));
        if (lagHp > shownHp) Sdf.fill(g, bx, by, bw * lagHp, 5, 2.5f, Colors.mulAlpha(0x88FFFFFF, alpha));
        int hpc = Colors.lerp(0xFFFF6B6B, Colors.lerp(0xFFFFD166, 0xFF6EE7A8, Math.max(0, shownHp - 0.5f) * 2), Math.min(1, shownHp * 2));
        if (shownHp > 0.005f) Sdf.fill(g, bx, by, Math.max(5, bw * shownHp), 5, 2.5f, Colors.mulAlpha(hpc, alpha));
        // броня — только значение, видимое и в ванили (иконки брони)
        int armor = target.getArmorValue();
        if (armor > 0) {
            if (armor != lastArmor) { lastArmor = armor; armorText = "Броня " + armor; }
            Fonts.draw(g, armorText, Fonts.Kind.TEXT, bx, 30, Colors.mulAlpha(0xFF8E8EA0, alpha));
        }
        g.pose().popMatrix();
    }

    private static String ellipsize(String s, int maxW) {
        if (Fonts.width(s, Fonts.Kind.BOLD) <= maxW) return s;
        int end = s.length();
        while (end > 0 && Fonts.width(s.substring(0, end) + "…", Fonts.Kind.BOLD) > maxW) end--;
        return s.substring(0, end) + "…";
    }
}

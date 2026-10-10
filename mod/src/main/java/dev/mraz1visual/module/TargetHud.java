package dev.mraz1visual.module;

import dev.mraz1visual.core.AttackListener;
import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.HudLayer;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.setting.SliderSetting;
import dev.mraz1visual.ui.Anim;
import dev.mraz1visual.ui.Colors;
import dev.mraz1visual.ui.Fonts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.Locale;

/** Карточка цели после твоего удара: имя и полоса здоровья. Пропадает через несколько секунд. */
public final class TargetHud extends Module implements HudLayer, AttackListener {
    final SliderSetting keep = add(new SliderSetting("keep", "Показывать", 3, 1, 10, 0.5, " с"));

    private LivingEntity target;
    private long hitAt;
    private float shownHp, alpha;
    private long last;
    private String name = "", hpText = "", letter = "";
    private float lastHp = -1;

    public TargetHud() {
        super("target_hud", "Target HUD", "Здоровье цели после твоего удара.", Category.VISUALS);
    }

    @Override
    public void onAttack(Entity e) {
        if (!(e instanceof LivingEntity le)) return;
        if (le != target) {
            target = le;
            name = le.getName().getString();
            letter = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(Locale.ROOT);
            shownHp = le.getHealth() / Math.max(1f, le.getMaxHealth());
            lastHp = -1;
        }
        hitAt = System.currentTimeMillis();
    }

    @Override
    public void renderHud(GuiGraphics g, float pt) {
        long now = System.nanoTime();
        float dt = last == 0 ? 0 : Math.min(0.1f, (now - last) / 1e9f);
        last = now;
        boolean visible = target != null && !target.isRemoved() && !target.isDeadOrDying()
                && System.currentTimeMillis() - hitAt < keep.get() * 1000;
        alpha = Anim.to(alpha, visible ? 1 : 0, dt);
        if (alpha < 0.02f || target == null) return;

        float hp = target.getHealth() + target.getAbsorptionAmount();
        float max = Math.max(1f, target.getMaxHealth());
        if (hp != lastHp) {
            lastHp = hp;
            hpText = String.format(Locale.ROOT, "%.1f", hp / 2f).replace('.', ',') + " ❤";
        }
        shownHp = Anim.to(shownHp, Math.min(1f, hp / max), dt * 0.6f);

        Minecraft mc = Minecraft.getInstance();
        int w = 120, h = 34;
        int x = g.guiWidth() / 2 + 14, y = g.guiHeight() / 2 + 14;
        g.fill(x, y, x + w, y + h, Colors.mulAlpha(0xE00C0C12, alpha));
        g.fill(x, y, x + w, y + 1, Colors.mulAlpha(0x30FFFFFF, alpha));
        g.fill(x + 5, y + 5, x + 29, y + 29, Colors.mulAlpha(0xFF22222E, alpha));
        int lw = mc.font.width(Fonts.of(letter, Fonts.Kind.BOLD));
        g.drawString(mc.font, Fonts.of(letter, Fonts.Kind.BOLD), x + 17 - lw / 2, y + 13, Colors.mulAlpha(0xFFE6E6F4, alpha), false);
        g.drawString(mc.font, Fonts.of(name, Fonts.Kind.BOLD), x + 34, y + 6, Colors.mulAlpha(0xFFE9E9F1, alpha), false);
        int bw = w - 40;
        g.fill(x + 34, y + 21, x + 34 + bw, y + 25, Colors.mulAlpha(0x40FFFFFF, alpha));
        int hpc = Colors.lerp(0xFFE05A5A, 0xFF7AD67A, shownHp);
        g.fill(x + 34, y + 21, x + 34 + Math.round(bw * shownHp), y + 25, Colors.mulAlpha(hpc, alpha));
        int tw = mc.font.width(Fonts.of(hpText, Fonts.Kind.TEXT));
        g.drawString(mc.font, Fonts.of(hpText, Fonts.Kind.TEXT), x + w - 6 - tw, y + 6, Colors.mulAlpha(0xFFB4B4C4, alpha), false);
    }
}

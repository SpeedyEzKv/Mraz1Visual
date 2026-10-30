package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.HudLayer;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.render.HudStyle;
import dev.mraz1visual.setting.BoolSetting;
import dev.mraz1visual.setting.EnumSetting;
import dev.mraz1visual.ui.Colors;
import dev.mraz1visual.ui.Fonts;
import dev.mraz1visual.ui.Sdf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;

/**
 * Здоровье и еда — две аккуратные полосы на месте ванильных значков: плавная заливка и подпись «14/20».
 * Еда жёлто-оранжевая, ниже 6 — красная; насыщение — тонкая линия под едой. Поглощение — золотая надставка.
 */
public final class HealthBars extends Module implements HudLayer {
    private static HealthBars instance;
    final EnumSetting style = add(new EnumSetting("style", "Стиль", 0, "bar", "Полоса", "vanilla", "Ванильный"));
    final BoolSetting saturation = add(new BoolSetting("saturation", "Насыщение тонкой линией", true));

    private float hp = -1, food = -1, sat = -1;
    private int lastHp = -1, lastMax = -1, lastFood = -1;
    private String hpStr = "", foodStr = "";
    private long last;

    public HealthBars() {
        super("health_bars", "Health & Food", "Полосы здоровья и еды вместо ванильных значков.", Category.HUD);
        instance = this;
        markNew();
    }

    /** Ванильные сердца и еду не рисовать (из VanillaHudHooks). */
    public static boolean replacesVanilla() {
        HealthBars h = instance;
        Minecraft mc = Minecraft.getInstance();
        return h != null && h.isEnabled() && h.style.getId().equals("bar") && mc.player != null && mc.gameMode != null && mc.gameMode.canHurtPlayer();
    }

    @Override
    public void renderHud(GuiGraphics g, float pt) {
        if (!replacesVanilla()) return;
        LocalPlayer p = Minecraft.getInstance().player;
        long now = System.nanoTime();
        float dt = last == 0 ? 0 : Math.min(0.1f, (now - last) / 1e9f);
        last = now;
        float max = Math.max(1, p.getMaxHealth()), abs = p.getAbsorptionAmount();
        float health = p.getHealth();
        int fl = p.getFoodData().getFoodLevel();
        float sl = p.getFoodData().getSaturationLevel();
        float k = 1f - (float) Math.exp(-10f * dt);
        hp = hp < 0 ? health : hp + (health - hp) * k;
        food = food < 0 ? fl : food + (fl - food) * k;
        sat = sat < 0 ? sl : sat + (sl - sat) * k;

        int hi = (int) Math.ceil(health), mi = Math.round(max);
        if (hi != lastHp || mi != lastMax) { lastHp = hi; lastMax = mi; hpStr = hi + "/" + mi; }
        if (fl != lastFood) { lastFood = fl; foodStr = fl + "/20"; }

        int cx = g.guiWidth() / 2, y = g.guiHeight() - 38, w = 81, h = 9;
        // здоровье — слева над хотбаром
        int lx = cx - 91;
        HudStyle.panel(g, lx - 1, y - 1, w + 2, h + 2, 5, 0.9f);
        int hc1 = health <= max * 0.3f ? HudStyle.RED : 0xFFFF6B81, hc2 = health <= max * 0.3f ? 0xFFFF8A5A : HudStyle.RED;
        HudStyle.gradBar(g, lx, y, w, h, hp / max, hc1, hc2, 1f);
        if (abs > 0) Sdf.fill(g, lx, y - 3, w * Math.min(1f, abs / max), 2, 1, HudStyle.YELLOW);
        Fonts.drawShadow(g, hpStr, Fonts.Kind.BOLD, lx + w / 2f - Fonts.width(hpStr, Fonts.Kind.BOLD) / 2f, y + 1, 0xFFFFFFFF);
        // еда — справа над хотбаром
        int rx = cx + 10;
        HudStyle.panel(g, rx - 1, y - 1, w + 2, h + 2, 5, 0.9f);
        boolean low = fl < 6;
        HudStyle.gradBar(g, rx, y, w, h, food / 20f, low ? HudStyle.RED : HudStyle.YELLOW, low ? 0xFFFF8A5A : 0xFFFF9F43, 1f);
        if (saturation.get() && sat > 0.05f) Sdf.fill(g, rx, y + h + 1.5f, w * Math.min(1f, sat / 20f), 1.5f, 0.75f, Colors.withAlpha(0xFFF3C6, 0xD0));
        Fonts.drawShadow(g, foodStr, Fonts.Kind.BOLD, rx + w / 2f - Fonts.width(foodStr, Fonts.Kind.BOLD) / 2f, y + 1, 0xFFFFFFFF);
    }
}

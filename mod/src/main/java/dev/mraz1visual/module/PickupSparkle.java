package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.render.Fx;
import dev.mraz1visual.render.FxParticles;
import dev.mraz1visual.render.Palette;
import dev.mraz1visual.setting.BoolSetting;
import dev.mraz1visual.setting.EnumSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

/**
 * Подобрал предмет — у пояса вспыхивает пара мягких искорок, у редких предметов (зачарованных, эпических) — звёздочка
 * цвета редкости. Счёт ведётся по своему инвентарю, ничего не отправляется.
 */
public final class PickupSparkle extends Module {
    final EnumSetting preset = add(new EnumSetting("preset", "Цвет", 0, Palette.OPTIONS));
    final BoolSetting rareOnly = add(new BoolSetting("rare_only", "Только редкие предметы", false));

    private final int[] counts = new int[41];
    private boolean primed;

    public PickupSparkle() {
        super("pickup_sparkle", "Pickup Sparkle", "Мягкие искорки, когда подбираешь предмет (ярче у редких).", Category.VISUALS);
    }

    @Override
    protected void onEnable() {
        primed = false;
    }

    @Override
    public void onTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.screen != null) {
            primed = false;
            return;
        }
        var inv = p.getInventory();
        int n = Math.min(counts.length, inv.getContainerSize());
        for (int i = 0; i < n; i++) {
            ItemStack s = inv.getItem(i);
            int c = s.isEmpty() ? 0 : s.getCount();
            if (primed && c > counts[i]) sparkle(p, s);
            counts[i] = c;
        }
        primed = true;
    }

    private void sparkle(LocalPlayer p, ItemStack s) {
        boolean rare = s.isEnchanted() || s.getRarity() != Rarity.COMMON;
        if (rareOnly.get() && !rare) return;
        int color = switch (s.getRarity()) {
            case UNCOMMON -> 0xFFFFE27A;
            case RARE -> 0xFF7FE6FF;
            case EPIC -> 0xFFD79BFF;
            default -> Fx.color(preset.getId(), 0.2f, Fx.time(), 0xFFFFFFFF, 0xFFFFFFFF);
        };
        int k = Fx.count(rare ? 7 : 3);
        for (int i = 0; i < k; i++) {
            double a = Math.random() * Math.PI * 2;
            FxParticles.spawn(p.getX() + Math.cos(a) * 0.35, p.getY() + 0.8 + Math.random() * 0.4, p.getZ() + Math.sin(a) * 0.35,
                    Math.cos(a) * 0.3, 0.5 + Math.random() * 0.4, Math.sin(a) * 0.3, 0.7f + (float) Math.random() * 0.3f,
                    rare && i == 0 ? 0.07f : 0.035f, color, -0.2f, true, rare && i == 0 ? FxParticles.STAR : FxParticles.DOT, true, 0.3f, 0.5f);
        }
    }
}

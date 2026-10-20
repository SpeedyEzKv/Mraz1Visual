package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.setting.BoolSetting;
import dev.mraz1visual.setting.SliderSetting;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Что показывать в руках от первого лица. Меняет только картинку у тебя. */
public final class Hands extends Module {
    private static Hands instance;
    final BoolSetting hideEmpty = add(new BoolSetting("hide_empty", "Скрыть пустую руку", false));
    final BoolSetting hideOffhand = add(new BoolSetting("hide_offhand", "Скрыть вторую руку", false));
    final BoolSetting hideTotem = add(new BoolSetting("hide_totem", "Скрыть тотем во второй руке", true));
    final BoolSetting hideShield = add(new BoolSetting("hide_shield", "Скрыть щит во второй руке", false));
    final BoolSetting glass = add(new BoolSetting("glass", "Стеклянная рука", false));
    final SliderSetting glassAlpha = add(new SliderSetting("glass_alpha", "Прозрачность руки", 40, 5, 95, 5, " %"));

    public Hands() {
        super("hands", "Hands", "Скрыть руку, щит или тотем, стеклянная рука.", Category.SWORDS);
        instance = this;
    }

    /** Из миксина ItemInHandRenderer.renderArmWithItem: true — эту руку не рисуем. */
    public static boolean hide(InteractionHand hand, ItemStack stack) {
        Hands h = instance;
        if (h == null || !h.isEnabled()) return false;
        if (hand == InteractionHand.OFF_HAND) {
            if (h.hideOffhand.get()) return true;
            if (h.hideTotem.get() && stack.is(Items.TOTEM_OF_UNDYING)) return true;
            if (h.hideShield.get() && stack.is(Items.SHIELD)) return true;
            return false;
        }
        return h.hideEmpty.get() && stack.isEmpty();
    }

    /** Из миксина AvatarRenderer.renderHand: цвет-множитель для руки от первого лица. -1 — рисовать как обычно. */
    public static int glassTint() {
        Hands h = instance;
        if (h == null || !h.isEnabled() || !h.glass.get()) return -1;
        int a = Math.round(255 * (1f - h.glassAlpha.getF() / 100f));
        return (Math.max(10, Math.min(255, a)) << 24) | 0xFFFFFF;
    }
}

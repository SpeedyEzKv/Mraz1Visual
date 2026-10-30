package dev.mraz1visual.module;

import dev.mraz1visual.core.AttackListener;
import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.setting.EnumSetting;
import dev.mraz1visual.setting.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;

/** Звук при твоём ударе. Играет только у тебя. */
public final class HitSounds extends Module implements AttackListener {
    final EnumSetting sound = add(new EnumSetting("sound", "Звук", 0,
            "orb", "Опыт", "bell", "Колокол", "chime", "Аметист", "click", "Щелчок", "pling", "Плинг"));
    final SliderSetting volume = add(new SliderSetting("volume", "Громкость", 60, 0, 100, 1, "%"));
    final SliderSetting pitch = add(new SliderSetting("pitch", "Тон", 1.0, 0.5, 2.0, 0.05, "×"));

    public HitSounds() {
        super("hit_sounds", "Hit Sounds", "Свой звук при каждом твоём ударе.", Category.COMBAT);
    }

    private SoundEvent event() {
        return switch (sound.getId()) {
            case "bell" -> SoundEvents.NOTE_BLOCK_BELL.value();
            case "chime" -> SoundEvents.AMETHYST_BLOCK_CHIME;
            case "click" -> SoundEvents.UI_BUTTON_CLICK.value();
            case "pling" -> SoundEvents.NOTE_BLOCK_PLING.value();
            default -> SoundEvents.EXPERIENCE_ORB_PICKUP;
        };
    }

    @Override
    public void onAttack(Entity target) {
        float vol = volume.getF() / 100f;
        if (vol <= 0) return;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(event(), pitch.getF(), vol));
    }
}

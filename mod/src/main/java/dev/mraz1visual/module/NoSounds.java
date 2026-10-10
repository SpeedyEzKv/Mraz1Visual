package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.setting.BoolSetting;

/** Глушит надоедливые звуки: опыт, колокол, иссушитель, варден, аметист, взрывы, гром. */
public final class NoSounds extends Module {
    private static NoSounds instance;
    final BoolSetting xp = add(new BoolSetting("xp", "Опыт", true));
    final BoolSetting bell = add(new BoolSetting("bell", "Колокол", true));
    final BoolSetting wither = add(new BoolSetting("wither", "Иссушитель", true));
    final BoolSetting warden = add(new BoolSetting("warden", "Варден", false));
    final BoolSetting chime = add(new BoolSetting("chime", "Аметист", false));
    final BoolSetting explode = add(new BoolSetting("explode", "Взрывы", false));
    final BoolSetting thunder = add(new BoolSetting("thunder", "Гром", false));

    public NoSounds() {
        super("no_sounds", "No Sounds", "Выключает надоедливые звуки.", Category.MISC);
        instance = this;
    }

    /** Из миксина AbstractSoundInstance.getVolume: path — путь звука, например entity.experience_orb.pickup. */
    public static boolean muted(String path) {
        NoSounds n = instance;
        if (n == null || !n.isEnabled() || path == null) return false;
        return (n.xp.get() && (path.startsWith("entity.experience_orb") || path.equals("entity.player.levelup")))
                || (n.bell.get() && path.startsWith("block.bell"))
                || (n.wither.get() && path.startsWith("entity.wither"))
                || (n.warden.get() && path.startsWith("entity.warden"))
                || (n.chime.get() && path.startsWith("block.amethyst"))
                || (n.explode.get() && (path.equals("entity.generic.explode") || path.startsWith("entity.dragon_fireball")))
                || (n.thunder.get() && path.startsWith("entity.lightning_bolt"));
    }
}

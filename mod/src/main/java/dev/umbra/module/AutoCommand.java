package dev.umbra.module;

import dev.umbra.core.Category;
import dev.umbra.core.Module;
import dev.umbra.setting.SliderSetting;
import dev.umbra.util.TextFiles;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;

/** Команды при входе на сервер — из config/umbra/auto-commands.txt. */
public final class AutoCommand extends Module {
    private static AutoCommand instance;
    final SliderSetting delay = add(new SliderSetting("delay", "Задержка", 3, 0, 10, 0.5, " с"));
    private long joinAt = -1;

    public AutoCommand() {
        super("auto_command", "Auto Command", "Команды при входе на сервер (config/umbra/auto-commands.txt).", Category.MISC);
        instance = this;
    }

    public static void register() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            AutoCommand c = instance;
            if (c != null && c.isEnabled()) c.joinAt = System.currentTimeMillis();
        });
    }

    @Override
    protected void onEnable() {
        TextFiles.lines("auto-commands.txt", "# Команды при входе, по одной на строку, без /\n");
    }

    @Override
    public void onTick(Minecraft mc) {
        if (joinAt < 0 || mc.getConnection() == null || mc.player == null) return;
        if (System.currentTimeMillis() - joinAt < delay.get() * 1000) return;
        joinAt = -1;
        for (String cmd : TextFiles.lines("auto-commands.txt", "# Команды при входе, по одной на строку, без /\n")) {
            mc.getConnection().sendCommand(cmd.startsWith("/") ? cmd.substring(1) : cmd);
        }
    }
}

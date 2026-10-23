package dev.mraz1visual.module;

import com.mojang.blaze3d.platform.InputConstants;
import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.input.Keys;
import dev.mraz1visual.setting.ActionSetting;
import dev.mraz1visual.setting.KeySetting;
import dev.mraz1visual.ui.WheelScreen;
import dev.mraz1visual.util.TextFiles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

import java.util.ArrayList;
import java.util.List;

/** Колёса быстрых действий: Staff Wheel (команды модератора) и Phrase Wheel (фразы в чат). */
public final class Wheels {
    private Wheels() {}

    private static final String STAFF_FILE = "staff_wheel.txt", PHRASE_FILE = "phrase_wheel.txt";

    /**
     * Staff Wheel: команды модератора по колесу. Показываются только те, что сервер разрешил именно тебе
     * (их нет в дереве команд — значит, прав нет, и колесо не откроется). Команда уходит как обычная команда чата.
     */
    public static final class Staff extends Module {
        final KeySetting key = add(new KeySetting("key", "Клавиша (держать)", InputConstants.KEY_V));
        final ActionSetting edit = add(new ActionSetting("edit", "Список команд", "Открыть файл ›", () -> open(STAFF_FILE, STAFF_TEMPLATE)));
        private boolean was;

        public Staff() {
            super("staff_wheel", "Staff Wheel", "Колесо команд модератора — только тех, на которые у тебя есть права.", Category.STAFF);
        }

        @Override
        public void onTick(Minecraft mc) {
            boolean down = mc.player != null && mc.screen == null && Keys.isDown(key.get());
            if (down && !was) {
                List<String> cmds = allowed(mc, TextFiles.lines(STAFF_FILE, STAFF_TEMPLATE));
                if (cmds.isEmpty()) {
                    dev.mraz1visual.compat.Mc.message(mc.player, net.minecraft.network.chat.Component.literal("§7Mraz1Visual: на этом сервере у тебя нет команд модератора из списка"), true);
                } else {
                    mc.setScreen(new WheelScreen("Staff", cmds, key.get(), c -> {
                        ClientPacketListener con = Minecraft.getInstance().getConnection();
                        if (con != null) con.sendCommand(c.startsWith("/") ? c.substring(1) : c);
                    }));
                }
            }
            was = down;
        }
    }

    /** Phrase Wheel: свои фразы в обычный чат — выбираешь на колесе, отправляется одна фраза. */
    public static final class Phrase extends Module {
        final KeySetting key = add(new KeySetting("key", "Клавиша (держать)", InputConstants.KEY_B));
        final ActionSetting edit = add(new ActionSetting("edit", "Список фраз", "Открыть файл ›", () -> open(PHRASE_FILE, PHRASE_TEMPLATE)));
        private boolean was;

        public Phrase() {
            super("phrase_wheel", "Phrase Wheel", "Колесо быстрых фраз в чат: gg, привет, спасибо — свои в файле.", Category.MISC);
        }

        @Override
        public void onTick(Minecraft mc) {
            boolean down = mc.player != null && mc.screen == null && Keys.isDown(key.get());
            if (down && !was) {
                List<String> list = TextFiles.lines(PHRASE_FILE, PHRASE_TEMPLATE);
                if (list.size() > 10) list = list.subList(0, 10);
                if (!list.isEmpty()) mc.setScreen(new WheelScreen("Фразы", list, key.get(), s -> {
                    ClientPacketListener con = Minecraft.getInstance().getConnection();
                    if (con != null) con.sendChat(s);
                }));
            }
            was = down;
        }
    }

    /** Команды из списка, которые есть в дереве команд от сервера (значит, разрешены). */
    static List<String> allowed(Minecraft mc, List<String> list) {
        List<String> out = new ArrayList<>();
        ClientPacketListener con = mc.getConnection();
        if (con == null) return out;
        var root = con.getCommands().getRoot();
        for (String c : list) {
            String name = (c.startsWith("/") ? c.substring(1) : c).split(" ", 2)[0];
            if (root.getChild(name) != null) out.add(c);
            if (out.size() >= 10) break;
        }
        return out;
    }

    private static void open(String file, String template) {
        TextFiles.lines(file, template);
        dev.mraz1visual.compat.Platform.openPath(TextFiles.DIR.resolve(file));
    }

    private static final String STAFF_TEMPLATE = """
            # Команды Staff Wheel: по одной на строку, до 10. Видны только те, на которые сервер дал права.
            /gamemode spectator
            /gamemode survival
            /vanish
            /tp
            /kick
            /mute
            /ban
            /invsee
            /clear
            /time set day
            """;

    private static final String PHRASE_TEMPLATE = """
            # Фразы Phrase Wheel: по одной на строку, до 10.
            gg
            Привет!
            Спасибо!
            Удачи!
            Секунду
            Пойдём?
            Хорошая игра
            """;
}

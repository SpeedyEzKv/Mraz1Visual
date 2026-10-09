package dev.umbra.module;

import dev.umbra.core.AttackListener;
import dev.umbra.core.Category;
import dev.umbra.core.Friends;
import dev.umbra.core.Module;
import dev.umbra.setting.BoolSetting;
import dev.umbra.setting.SliderSetting;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Помощники чата и смерти:
 *  - Auto Accept: принять /tpa от друзей (friends.txt);
 *  - Auto Command: команды при входе на сервер (auto-commands.txt);
 *  - Kill Message: сообщение после твоего убийства игрока;
 *  - Auto Respawn: сразу возродиться.
 */
public final class ChatHelpers extends Module implements AttackListener {
    private static ChatHelpers instance;
    private static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("umbra");

    final BoolSetting autoAccept = add(new BoolSetting("auto_accept", "Принимать /tpa от друзей", true).group("Auto Accept"));
    final BoolSetting autoCommand = add(new BoolSetting("auto_command", "Команды при входе", false).group("Auto Command"));
    final SliderSetting commandDelay = add(new SliderSetting("command_delay", "Задержка", 3, 0, 10, 0.5, " с").group("Auto Command"));
    final BoolSetting killMessage = add(new BoolSetting("kill_message", "Сообщение после убийства", false).group("Kill Message"));
    final BoolSetting autoRespawn = add(new BoolSetting("auto_respawn", "Возрождаться сразу", false).group("Auto Respawn"));
    final BoolSetting noFriendDamage = add(new BoolSetting("no_friend_damage", "Не бить друзей", true).group("No Friend Damage")
            .onChange(this::syncGuard));

    private void syncGuard() {
        dev.umbra.render.FriendGuard.enabled = isEnabled() && noFriendDamage.get();
    }

    @Override
    protected void onEnable() { syncGuard(); }

    @Override
    protected void onDisable() { dev.umbra.render.FriendGuard.enabled = false; }

    private Player lastHit;
    private long lastHitAt;
    private long joinAt = -1;

    public ChatHelpers() {
        super("chat_helpers", "Chat Helpers", "Auto Accept, Auto Command, Kill Message, Auto Respawn.", Category.MISC);
        instance = this;
    }

    public static void register() {
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            ChatHelpers c = instance;
            if (c == null || !c.isEnabled() || overlay || !c.autoAccept.get()) return;
            String text = message.getString();
            String low = text.toLowerCase(Locale.ROOT);
            if (!(low.contains("tpaccept") || low.contains("телепорт"))) return;
            // ищем ник друга в тексте запроса
            for (String word : text.split("[^A-Za-z0-9_]+")) {
                if (word.length() >= 3 && Friends.is(word)) {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.getConnection() != null) mc.getConnection().sendCommand("tpaccept " + word);
                    return;
                }
            }
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            ChatHelpers c = instance;
            if (c != null && c.isEnabled() && c.autoCommand.get()) c.joinAt = System.currentTimeMillis();
        });
    }

    @Override
    public void onAttack(Entity e) {
        if (e instanceof Player p) {
            lastHit = p;
            lastHitAt = System.currentTimeMillis();
        }
    }

    @Override
    public void onTick(Minecraft mc) {
        if (mc.player == null || mc.getConnection() == null) return;

        if (joinAt > 0 && System.currentTimeMillis() - joinAt > commandDelay.get() * 1000) {
            joinAt = -1;
            for (String cmd : lines("auto-commands.txt", "# Команды при входе, по одной на строку, без /\n")) {
                mc.getConnection().sendCommand(cmd.startsWith("/") ? cmd.substring(1) : cmd);
            }
        }

        if (killMessage.get() && lastHit != null && lastHit.isDeadOrDying() && System.currentTimeMillis() - lastHitAt < 3000) {
            List<String> msgs = lines("kill-messages.txt", "# Сообщения после убийства, {name} — ник\ngg {name}\n");
            if (!msgs.isEmpty()) {
                String m = msgs.get((int) (Math.random() * msgs.size())).replace("{name}", lastHit.getName().getString());
                mc.getConnection().sendChat(m);
            }
            lastHit = null;
        }

        if (autoRespawn.get() && mc.screen instanceof DeathScreen) {
            mc.player.respawn();
            mc.setScreen(null);
        }
    }

    private static List<String> lines(String file, String template) {
        List<String> out = new ArrayList<>();
        try {
            Path f = DIR.resolve(file);
            if (!Files.exists(f)) {
                Files.createDirectories(DIR);
                Files.writeString(f, template, StandardCharsets.UTF_8);
            }
            for (String l : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                l = l.trim();
                if (!l.isEmpty() && !l.startsWith("#")) out.add(l);
            }
        } catch (Exception ignored) {
        }
        return out;
    }
}

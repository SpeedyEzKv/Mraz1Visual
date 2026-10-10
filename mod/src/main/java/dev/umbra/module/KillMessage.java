package dev.umbra.module;

import dev.umbra.core.AttackListener;
import dev.umbra.core.Category;
import dev.umbra.core.Module;
import dev.umbra.util.TextFiles;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.List;

/** Сообщение в чат после твоего убийства игрока — из config/umbra/kill-messages.txt. */
public final class KillMessage extends Module implements AttackListener {
    private static final String TEMPLATE = "# Сообщения после убийства, {name} — ник\ngg {name}\n";
    private Player lastHit;
    private long lastHitAt;

    public KillMessage() {
        super("kill_message", "Kill Message", "Сообщение в чат после убийства (config/umbra/kill-messages.txt).", Category.MISC);
    }

    @Override
    protected void onEnable() { TextFiles.lines("kill-messages.txt", TEMPLATE); }

    @Override
    public void onAttack(Entity e) {
        if (e instanceof Player p) {
            lastHit = p;
            lastHitAt = System.currentTimeMillis();
        }
    }

    @Override
    public void onTick(Minecraft mc) {
        if (lastHit == null || mc.getConnection() == null) return;
        if (System.currentTimeMillis() - lastHitAt > 3000) {
            lastHit = null;
            return;
        }
        if (!lastHit.isDeadOrDying()) return;
        List<String> msgs = TextFiles.lines("kill-messages.txt", TEMPLATE);
        if (!msgs.isEmpty()) {
            String m = msgs.get((int) (Math.random() * msgs.size())).replace("{name}", lastHit.getName().getString());
            mc.getConnection().sendChat(m);
        }
        lastHit = null;
    }
}

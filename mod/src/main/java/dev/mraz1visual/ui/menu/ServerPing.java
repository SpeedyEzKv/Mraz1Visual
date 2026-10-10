package dev.mraz1visual.ui.menu;

import dev.mraz1visual.core.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.EventLoopGroupHolder;

import java.net.UnknownHostException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Пинг серверов в фоне, как в ванили. Единственное место, где мод зовёт сетевой API игры.
 */
final class ServerPing {
    private static final ExecutorService POOL = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "Mraz1Visual server ping");
        t.setDaemon(true);
        return t;
    });

    final ServerStatusPinger pinger = new ServerStatusPinger();

    void ping(ServerData data, Runnable onSave) {
        data.setState(ServerData.State.PINGING);
        data.motd = Component.empty();
        data.ping = -1L;
        data.players = null;
        Minecraft mc = Minecraft.getInstance();
        POOL.submit(() -> {
            try {
                pinger.pingServer(data, () -> mc.execute(onSave), () -> data.setState(ServerData.State.UNREACHABLE),
                        EventLoopGroupHolder.remote(mc.options.useNativeTransport()));
            } catch (UnknownHostException e) {
                data.setState(ServerData.State.UNREACHABLE);
                data.motd = Component.literal("Адрес не найден");
            } catch (Exception e) {
                data.setState(ServerData.State.UNREACHABLE);
                data.motd = Component.literal("Нет ответа");
                ModuleManager.LOG.debug("Ping failed for {}", data.ip, e);
            }
        });
    }

    void tick() { pinger.tick(); }

    void stop() { pinger.removeAll(); }
}

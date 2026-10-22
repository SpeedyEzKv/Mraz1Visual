package dev.mraz1visual.module;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.core.ModuleManager;
import dev.mraz1visual.setting.ActionSetting;
import dev.mraz1visual.setting.BoolSetting;
import dev.mraz1visual.util.TextFiles;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;

/**
 * Статус в Discord: «Mraz1Visual» с логотипом-пауком, где ты (меню / одиночный мир / сервер) и сколько играешь.
 *
 * Работает напрямую с приложением Discord на компьютере (локальный канал discord-ipc), без библиотек и интернета.
 * Нужен Application ID приложения «Mraz1Visual» из Discord Developer Portal — см. APP_ID ниже.
 */
public final class DiscordRpc extends Module {
    /**
     * ID приложения в https://discord.com/developers/applications (имя приложения = то, что Discord пишет
     * «Играет в …»). Обычно его передаёт лаунчер из release/manifest.json (поле discord_app_id);
     * без лаунчера — файл config/mraz1visual/discord.txt или эта константа.
     */
    public static final String APP_ID = "YOUR_DISCORD_APP_ID";

    private static final String DOWNLOAD_URL = "https://github.com/SpeedyEzKv/Mraz1Visual/releases/latest";

    final BoolSetting showServer = add(new BoolSetting("show_server", "Показывать сервер", true));
    final BoolSetting showCount = add(new BoolSetting("show_count", "Число включённых визуалов", true));
    final BoolSetting button = add(new BoolSetting("button", "Кнопка «Скачать»", true));
    final BoolSetting music = add(new BoolSetting("music", "«Слушает: исполнитель — трек»", true));
    final ActionSetting portal = add(new ActionSetting("portal", "Discord Developer Portal", "Открыть ›",
            () -> dev.mraz1visual.overlay.Screenshots.openExternal("https://discord.com/developers/applications")));

    private static final long START = System.currentTimeMillis() / 1000;
    private volatile String wanted = "";
    private Thread worker;
    private int tick;

    public DiscordRpc() {
        super("discord_rpc", "Discord RPC", "Статус «Mraz1Visual» в профиле Discord: где играешь и сколько.", Category.MISC);
        loadEnabled(true);
    }

    static String appId() {
        String prop = System.getProperty("mraz1visual.discord", "").trim(); // передаёт лаунчер из manifest.json
        if (prop.matches("\\d{15,22}")) return prop;
        try {
            Path f = TextFiles.DIR.resolve("discord.txt");
            if (Files.exists(f)) {
                String s = Files.readString(f).trim();
                if (s.matches("\\d{15,22}")) return s;
            }
        } catch (Throwable ignored) {
        }
        return APP_ID.matches("[0-9]{15,22}") ? APP_ID : ""; // заглушка — модуль молча выключен
    }

    /** ID проверяется раз в 30 с (файл маленький), а не каждый тик; без ID поток даже не запускается. */
    private String cachedId;

    @Override
    public void onTick(Minecraft mc) {
        if (++tick % 40 != 1) return; // раз в 2 секунды достаточно
        if (cachedId == null || tick % 600 == 1) cachedId = appId();
        if (cachedId.isEmpty()) return;
        wanted = activity(mc).toString();
        if (worker == null || !worker.isAlive()) {
            worker = new Thread(this::loop, "Mraz1Visual Discord");
            worker.setDaemon(true);
            worker.start();
        }
    }

    @Override
    protected void onDisable() {
        wanted = "";
    }

    private JsonObject activity(Minecraft mc) {
        JsonObject a = new JsonObject();
        String details, state;
        boolean hide = !showServer.get() || StreamerMode.hidesCoords();
        if (mc.level == null) {
            details = "В главном меню";
        } else if (mc.getCurrentServer() != null) {
            details = hide ? "Играет на сервере" : "Сервер: " + mc.getCurrentServer().ip.toLowerCase(Locale.ROOT);
        } else {
            details = "Одиночный мир";
        }
        int on = 0;
        for (Module m : dev.mraz1visual.Mraz1Visual.MODULES.all()) if (m.isEnabled() && !m.alwaysOn()) on++;
        state = "Minecraft 1.21.11 · Mraz1Visual" + (showCount.get() ? " · визуалов: " + on : "");
        boolean listening = music.get() && dev.mraz1visual.music.NowPlaying.available() && dev.mraz1visual.music.NowPlaying.playing();
        if (listening) {
            String ar = dev.mraz1visual.music.NowPlaying.artist(), ti = dev.mraz1visual.music.NowPlaying.title();
            state = "Слушает: " + (ar.isBlank() ? ti : ar + " — " + ti);
            if (state.length() > 120) state = state.substring(0, 119) + "…";
        }
        a.addProperty("details", details);
        a.addProperty("state", state);
        JsonObject ts = new JsonObject();
        ts.addProperty("start", START);
        a.add("timestamps", ts);
        JsonObject assets = new JsonObject();
        assets.addProperty("large_image", "logo");
        assets.addProperty("large_text", "Mraz1Visual — визуальный клиент");
        // маленькая картинка — статус (ключи ассетов приложения: music, world, server, menu)
        assets.addProperty("small_image", listening ? "music" : mc.level == null ? "menu" : mc.getCurrentServer() != null ? "server" : "world");
        assets.addProperty("small_text", listening ? "Слушает музыку" : details);
        a.add("assets", assets);
        if (button.get()) {
            JsonArray b = new JsonArray();
            JsonObject btn = new JsonObject();
            btn.addProperty("label", "Скачать Mraz1Visual");
            btn.addProperty("url", DOWNLOAD_URL);
            b.add(btn);
            a.add("buttons", b);
        }
        return a;
    }

    // ── связь с Discord ──────────────────────────────────────

    private interface Pipe extends AutoCloseable {
        void write(byte[] b) throws IOException;
        void readFully(byte[] b) throws IOException;
        void close() throws IOException;
    }

    private void loop() {
        String sent = null;
        Pipe pipe = null;
        long retryAt = 0;
        while (true) {
            try {
                String want = isEnabled() ? wanted : "";
                if (want.isEmpty() && pipe == null) {
                    if (!isEnabled()) return;
                    Thread.sleep(1000);
                    continue;
                }
                String id = appId();
                if (id.isEmpty()) return; // ID не задан — молча выключаемся
                if (pipe == null) {
                    if (System.currentTimeMillis() < retryAt) {
                        Thread.sleep(1000);
                        continue;
                    }
                    pipe = connect();
                    if (pipe == null) {
                        retryAt = System.currentTimeMillis() + 15000; // Discord не запущен
                        continue;
                    }
                    send(pipe, 0, "{\"v\":1,\"client_id\":\"" + id + "\"}");
                    read(pipe);
                    sent = null;
                }
                if (!want.equals(sent)) {
                    JsonObject args = new JsonObject();
                    args.addProperty("pid", ProcessHandle.current().pid());
                    if (!want.isEmpty()) args.add("activity", JsonParser.parseString(want));
                    JsonObject cmd = new JsonObject();
                    cmd.addProperty("cmd", "SET_ACTIVITY");
                    cmd.add("args", args);
                    cmd.addProperty("nonce", UUID.randomUUID().toString());
                    send(pipe, 1, cmd.toString());
                    read(pipe);
                    sent = want;
                    if (want.isEmpty()) {
                        pipe.close();
                        return;
                    }
                }
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                return;
            } catch (Throwable t) {
                ModuleManager.LOG.debug("Discord RPC: {}", t.toString());
                try {
                    if (pipe != null) pipe.close();
                } catch (Throwable ignored) {
                }
                pipe = null;
                retryAt = System.currentTimeMillis() + 15000;
            }
        }
    }

    private static void send(Pipe p, int op, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        ByteBuffer b = ByteBuffer.allocate(8 + body.length).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(op).putInt(body.length).put(body);
        p.write(b.array());
    }

    private static String read(Pipe p) throws IOException {
        byte[] head = new byte[8];
        p.readFully(head);
        ByteBuffer h = ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN);
        int op = h.getInt(), len = h.getInt();
        if (len < 0 || len > 1 << 20) throw new IOException("bad frame");
        byte[] body = new byte[len];
        p.readFully(body);
        if (op == 2) throw new IOException("closed: " + new String(body, StandardCharsets.UTF_8));
        return new String(body, StandardCharsets.UTF_8);
    }

    private static Pipe connect() {
        boolean win = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        for (int i = 0; i < 10; i++) {
            try {
                if (win) {
                    RandomAccessFile f = new RandomAccessFile("\\\\.\\pipe\\discord-ipc-" + i, "rw");
                    return new Pipe() {
                        public void write(byte[] b) throws IOException { f.write(b); }
                        public void readFully(byte[] b) throws IOException { f.readFully(b); }
                        public void close() throws IOException { f.close(); }
                    };
                }
                for (String dir : unixDirs()) {
                    Path sock = Path.of(dir, "discord-ipc-" + i);
                    if (!Files.exists(sock)) continue;
                    SocketChannel ch = SocketChannel.open(StandardProtocolFamily.UNIX);
                    ch.connect(UnixDomainSocketAddress.of(sock));
                    return new Pipe() {
                        public void write(byte[] b) throws IOException {
                            ByteBuffer bb = ByteBuffer.wrap(b);
                            while (bb.hasRemaining()) ch.write(bb);
                        }
                        public void readFully(byte[] b) throws IOException {
                            ByteBuffer bb = ByteBuffer.wrap(b);
                            while (bb.hasRemaining()) if (ch.read(bb) < 0) throw new IOException("eof");
                        }
                        public void close() throws IOException { ch.close(); }
                    };
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static java.util.List<String> unixDirs() {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (String env : new String[]{"XDG_RUNTIME_DIR", "TMPDIR", "TMP", "TEMP"}) {
            String v = System.getenv(env);
            if (v == null || v.isEmpty()) continue;
            out.add(v);
            out.add(v + "/app/com.discordapp.Discord");
            out.add(v + "/snap.discord");
        }
        out.add("/tmp");
        return out;
    }
}

package dev.umbra.overlay;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.umbra.core.ModuleManager;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Сколько ты наиграл: всего, по дням, по серверам. Считается только время в мире
 * (не в меню и не на паузе). Файл: config/umbra/stats.json, пишется раз в минуту.
 */
public final class PlayStats {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("umbra").resolve("stats.json");

    public static long totalSec;
    public static final Map<String, Long> byDay = new LinkedHashMap<>();
    public static final Map<String, Long> byServer = new LinkedHashMap<>();
    public static long sessionSec;

    private static int ticks;
    private static int sinceSave;

    private PlayStats() {}

    public static void load() {
        try {
            if (!Files.exists(FILE)) return;
            JsonObject j = JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8)).getAsJsonObject();
            totalSec = j.has("total") ? j.get("total").getAsLong() : 0;
            if (j.has("days")) j.getAsJsonObject("days").entrySet().forEach(e -> byDay.put(e.getKey(), e.getValue().getAsLong()));
            if (j.has("servers")) j.getAsJsonObject("servers").entrySet().forEach(e -> byServer.put(e.getKey(), e.getValue().getAsLong()));
        } catch (Throwable t) {
            ModuleManager.LOG.warn("stats.json unreadable, starting fresh ({})", t.toString());
        }
    }

    /** Раз в тик, без аллокаций в обычном случае. */
    public static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null || mc.isPaused()) return;
        if (++ticks < 20) return;
        ticks = 0;
        totalSec++;
        sessionSec++;
        byDay.merge(LocalDate.now().toString(), 1L, Long::sum);
        String server = mc.getCurrentServer() != null ? mc.getCurrentServer().ip : "Одиночная игра";
        byServer.merge(server, 1L, Long::sum);
        if (++sinceSave >= 60) save();
    }

    public static void save() {
        sinceSave = 0;
        try {
            JsonObject j = new JsonObject();
            j.addProperty("total", totalSec);
            JsonObject days = new JsonObject();
            byDay.forEach(days::addProperty);
            j.add("days", days);
            JsonObject servers = new JsonObject();
            byServer.forEach(servers::addProperty);
            j.add("servers", servers);
            Files.createDirectories(FILE.getParent());
            Path tmp = FILE.resolveSibling("stats.json.tmp");
            Files.writeString(tmp, GSON.toJson(j), StandardCharsets.UTF_8);
            Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING);
        } catch (Throwable t) {
            ModuleManager.LOG.warn("Could not save stats", t);
        }
    }

    public static long today() {
        return byDay.getOrDefault(LocalDate.now().toString(), 0L);
    }

    /** «128 ч 14 мин» / «14 мин» / «40 с». */
    public static String format(long sec) {
        long h = sec / 3600, m = sec % 3600 / 60;
        if (h > 0) return h + " ч " + m + " мин";
        if (m > 0) return m + " мин";
        return sec + " с";
    }
}

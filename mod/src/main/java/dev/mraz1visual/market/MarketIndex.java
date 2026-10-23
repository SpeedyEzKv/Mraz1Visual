package dev.mraz1visual.market;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.mraz1visual.util.TextFiles;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Что поставлено через Маркет: id проекта → файл. Файл config/mraz1visual/market.json. */
final class MarketIndex {
    private MarketIndex() {}

    private static final Path FILE = TextFiles.DIR.resolve("market.json");
    private static JsonObject data;

    private static synchronized JsonObject data() {
        if (data == null) {
            try {
                data = Files.exists(FILE) ? JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8)).getAsJsonObject() : new JsonObject();
            } catch (Exception e) {
                data = new JsonObject();
            }
        }
        return data;
    }

    static synchronized void put(String id, Modrinth.Kind kind, String file) {
        JsonObject e = new JsonObject();
        e.addProperty("kind", kind.name());
        e.addProperty("file", file);
        data().add(id, e);
        save();
    }

    static synchronized void remove(String id) {
        data().remove(id);
        save();
    }

    static synchronized String file(String id) {
        JsonObject e = data().getAsJsonObject(id);
        return e == null ? null : e.get("file").getAsString();
    }

    public static synchronized boolean installed(String id) {
        return data().has(id);
    }

    private static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, data.toString(), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            // не критично: в худшем случае кнопка снова покажет «Установить»
        }
    }
}

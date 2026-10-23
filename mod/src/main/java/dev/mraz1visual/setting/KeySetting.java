package dev.mraz1visual.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import dev.mraz1visual.input.Keys;

/** Клавиша (код клавиши этой версии игры; в конфиге — GLFW-код, см. KeyCodes). -1 = не назначена. */
public final class KeySetting extends Setting<Integer> {
    public static final int NONE = -1;
    public final int def;
    private int key;
    private String display;
    /** Состояние клавиши в прошлом тике — для срабатывания по нажатию, а не по удержанию. */
    public boolean wasDown;

    public KeySetting(String id, String name, int def) {
        super(id, name);
        this.def = def;
        this.key = def;
        display = null;
    }

    public int get() { return key; }
    public boolean isBound() { return key != NONE; }

    public void set(int k) {
        if (k == key) return;
        key = k;
        display = null;
        changed();
    }

    /** Имя клавиши. Считается лениво и кэшируется. */
    public String display() {
        if (display == null) display = key == NONE ? "Нет" : Keys.name(key);
        return display;
    }

    @Override public void reset() { set(def); }

    @Override public JsonElement toJson() { return new JsonPrimitive(dev.mraz1visual.input.KeyCodes.toConfig(key)); }

    @Override public boolean fromJson(JsonElement json) {
        if (json == null || !json.isJsonPrimitive() || !json.getAsJsonPrimitive().isNumber()) return false;
        int k = json.getAsInt();
        if (k < NONE || k > 512) return false;
        key = dev.mraz1visual.input.KeyCodes.fromConfig(k);
        display = null;
        return true;
    }
}

package dev.umbra.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public final class BoolSetting extends Setting<Boolean> {
    public final boolean def;
    private boolean value;

    public BoolSetting(String id, String name, boolean def) {
        super(id, name);
        this.def = def;
        this.value = def;
    }

    public boolean get() { return value; }

    public void set(boolean v) {
        if (v == value) return;
        value = v;
        changed();
    }

    public void toggle() { set(!value); }

    public void setSilently(boolean v) { value = v; }

    @Override public void reset() { set(def); }

    @Override public JsonElement toJson() { return new JsonPrimitive(value); }

    @Override public boolean fromJson(JsonElement json) {
        if (json == null || !json.isJsonPrimitive() || !json.getAsJsonPrimitive().isBoolean()) return false;
        value = json.getAsBoolean();
        return true;
    }
}

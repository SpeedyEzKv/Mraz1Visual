package dev.mraz1visual.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** Строка (например «Свой ник»). В карточках меню не рисуется — правится своей кнопкой-действием рядом. */
public final class TextSetting extends Setting<String> {
    public final String def;
    public final int maxLen;
    private String value;

    public TextSetting(String id, String name, String def, int maxLen) {
        super(id, name);
        this.def = def;
        this.maxLen = maxLen;
        this.value = def;
    }

    public String get() { return value; }

    public void set(String v) {
        if (v == null) v = "";
        if (v.length() > maxLen) v = v.substring(0, maxLen);
        if (v.equals(value)) return;
        value = v;
        changed();
    }

    @Override public void reset() { set(def); }

    @Override public JsonElement toJson() { return new JsonPrimitive(value); }

    @Override public boolean fromJson(JsonElement json) {
        if (json == null || !json.isJsonPrimitive()) return false;
        String s = json.getAsString();
        value = s.length() > maxLen ? s.substring(0, maxLen) : s;
        return true;
    }
}

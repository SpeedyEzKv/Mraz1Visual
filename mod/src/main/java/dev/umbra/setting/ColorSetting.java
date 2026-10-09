package dev.umbra.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** Цвет ARGB. В JSON хранится строкой "#AARRGGBB" — читается глазами. */
public final class ColorSetting extends Setting<Integer> {
    public final int def;
    private int argb;
    private String hex;

    public ColorSetting(String id, String name, int defArgb) {
        super(id, name);
        this.def = defArgb;
        this.argb = defArgb;
        updateHex();
    }

    public int get() { return argb; }
    public int alpha() { return argb >>> 24; }

    public void set(int v) {
        if (v == argb) return;
        argb = v;
        updateHex();
        changed();
    }

    public String hex() { return hex; }

    private void updateHex() {
        hex = String.format("#%06X", argb & 0xFFFFFF) + (alpha() == 255 ? "" : "  " + Math.round(alpha() / 2.55f) + "%");
    }

    @Override public void reset() { set(def); }

    @Override public JsonElement toJson() { return new JsonPrimitive(String.format("#%08X", argb)); }

    @Override public boolean fromJson(JsonElement json) {
        if (json == null || !json.isJsonPrimitive()) return false;
        String s = json.getAsString().trim();
        if (s.startsWith("#")) s = s.substring(1);
        if (s.length() != 6 && s.length() != 8) return false;
        try {
            long v = Long.parseLong(s, 16);
            argb = s.length() == 6 ? (int) (0xFF000000L | v) : (int) v;
            updateHex();
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}

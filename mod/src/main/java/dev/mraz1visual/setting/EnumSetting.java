package dev.mraz1visual.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/**
 * Выбор из списка. В JSON хранится id варианта, а не индекс,
 * чтобы перестановка вариантов не ломала старые конфиги.
 */
public final class EnumSetting extends Setting<Integer> {
    public final String[] ids;
    public final String[] labels;
    public final int def;
    private int index;

    /** @param options пары id, подпись: "blur", "Размытие", "dim", "Затемнение" ... */
    public EnumSetting(String id, String name, int def, String... options) {
        super(id, name);
        if (options.length < 2 || options.length % 2 != 0) throw new IllegalArgumentException("options: id, label, ...");
        int n = options.length / 2;
        ids = new String[n];
        labels = new String[n];
        for (int i = 0; i < n; i++) {
            ids[i] = options[i * 2];
            labels[i] = options[i * 2 + 1];
        }
        this.def = def;
        this.index = def;
    }

    public int get() { return index; }
    public String getId() { return ids[index]; }
    public String label() { return labels[index]; }
    public int size() { return ids.length; }

    public void set(int i) {
        if (i < 0 || i >= ids.length || i == index) return;
        index = i;
        changed();
    }

    public void setSilently(int i) {
        if (i >= 0 && i < ids.length) index = i;
    }

    @Override public void reset() { set(def); }

    @Override public JsonElement toJson() { return new JsonPrimitive(ids[index]); }

    @Override public boolean fromJson(JsonElement json) {
        if (json == null || !json.isJsonPrimitive()) return false;
        String s = json.getAsString();
        for (int i = 0; i < ids.length; i++) {
            if (ids[i].equals(s)) { index = i; return true; }
        }
        return false;
    }
}

package dev.mraz1visual.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;

/** Не значение, а кнопка в строке настроек (например «Выбрать обои»). В конфиг не пишется. */
public final class ActionSetting extends Setting<Void> {
    public final String button;
    private final Runnable action;

    public ActionSetting(String id, String name, String button, Runnable action) {
        super(id, name);
        this.button = button;
        this.action = action;
    }

    public void run() { action.run(); }

    @Override public void reset() {}

    @Override public JsonElement toJson() { return JsonNull.INSTANCE; }

    @Override public boolean fromJson(JsonElement json) { return true; }
}

package dev.umbra.setting;

import com.google.gson.JsonElement;

/**
 * Одна настройка модуля. Значение хранится в поле, без обёрток,
 * чтобы чтение в рендере было бесплатным.
 */
public abstract class Setting<T> {
    /** Вызывается при любом изменении любой настройки (конфиг помечается грязным). */
    public static Runnable globalChangeHook = () -> {};

    public final String id;
    public final String name;
    private String group = "Основное";
    private Runnable onChange;
    private String hint = "";

    protected Setting(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String group() { return group; }

    /** Подсказка, которая показывается внизу окна при наведении. */
    public String hint() { return hint; }

    @SuppressWarnings("unchecked")
    public <S extends Setting<T>> S hint(String hint) {
        this.hint = hint;
        return (S) this;
    }

    @SuppressWarnings("unchecked")
    public <S extends Setting<T>> S group(String group) {
        this.group = group;
        return (S) this;
    }

    @SuppressWarnings("unchecked")
    public <S extends Setting<T>> S onChange(Runnable r) {
        this.onChange = r;
        return (S) this;
    }

    protected final void changed() {
        if (onChange != null) onChange.run();
        globalChangeHook.run();
    }

    public abstract void reset();

    public abstract JsonElement toJson();

    /** @return false, если значение битое — тогда настройка остаётся по умолчанию. */
    public abstract boolean fromJson(JsonElement json);
}

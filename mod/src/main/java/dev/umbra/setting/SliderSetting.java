package dev.umbra.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.Locale;

public final class SliderSetting extends Setting<Double> {
    public final double min, max, step, def;
    private final String suffix;
    private final int decimals;
    private double value;
    private String display;
    private java.util.function.DoubleFunction<String> formatter;

    public SliderSetting(String id, String name, double def, double min, double max, double step, String suffix) {
        super(id, name);
        this.min = min;
        this.max = max;
        this.step = step;
        this.def = def;
        this.suffix = suffix;
        this.decimals = step >= 1 ? 0 : (step >= 0.1 ? 1 : 2);
        this.value = snap(def);
        updateDisplay();
    }

    public double get() { return value; }
    public float getF() { return (float) value; }
    public int getI() { return (int) Math.round(value); }

    /** 0..1 — для отрисовки ползунка. */
    public double fraction() { return max == min ? 0 : (value - min) / (max - min); }

    public void setFraction(double f) { set(min + Math.max(0, Math.min(1, f)) * (max - min)); }

    public void set(double v) {
        double s = snap(v);
        if (s == value) return;
        value = s;
        updateDisplay();
        changed();
    }

    /** Готовая строка значения. Пересчитывается только при изменении, не в каждом кадре. */
    public String display() { return display; }

    private double snap(double v) {
        if (Double.isNaN(v)) return def;
        double c = Math.max(min, Math.min(max, v));
        if (step > 0) c = min + Math.round((c - min) / step) * step;
        return Math.max(min, Math.min(max, c));
    }

    /** Своя подпись значения (например «Без лимита» на максимуме). */
    public SliderSetting format(java.util.function.DoubleFunction<String> f) {
        this.formatter = f;
        updateDisplay();
        return this;
    }

    /** Тихо выставить значение (без onChange) — для синхронизации с внешним источником. */
    public void setSilently(double v) {
        value = snap(v);
        updateDisplay();
    }

    private void updateDisplay() {
        if (formatter != null) {
            display = formatter.apply(value);
            return;
        }
        String num = decimals == 0
                ? Long.toString(Math.round(value))
                : String.format(Locale.ROOT, "%." + decimals + "f", value);
        display = num + suffix;
    }

    @Override public void reset() { set(def); }

    @Override public JsonElement toJson() { return new JsonPrimitive(value); }

    @Override public boolean fromJson(JsonElement json) {
        if (json == null || !json.isJsonPrimitive() || !json.getAsJsonPrimitive().isNumber()) return false;
        value = snap(json.getAsDouble());
        updateDisplay();
        return true;
    }
}

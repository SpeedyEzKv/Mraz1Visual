package dev.mraz1visual.models;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;

/** Терпимое чтение JSON моделей: комментарии, BOM, числа строками. */
final class Json {
    private Json() {}

    static JsonObject parse(byte[] data) {
        String s = new String(data, StandardCharsets.UTF_8);
        if (!s.isEmpty() && s.charAt(0) == '﻿') s = s.substring(1);
        JsonReader r = new JsonReader(new StringReader(s));
        r.setStrictness(com.google.gson.Strictness.LENIENT);
        JsonElement e = JsonParser.parseReader(r);
        if (!e.isJsonObject()) throw new IllegalArgumentException("не объект JSON");
        return e.getAsJsonObject();
    }

    static float f(JsonElement e, float def) {
        if (e == null || e.isJsonNull()) return def;
        try {
            return e.getAsFloat();
        } catch (Exception ex) {
            return def;
        }
    }

    static int i(JsonObject o, String k, int def) {
        try {
            return o.has(k) ? o.get(k).getAsInt() : def;
        } catch (Exception ex) {
            return def;
        }
    }

    static float f(JsonObject o, String k, float def) {
        return o.has(k) ? f(o.get(k), def) : def;
    }

    static boolean b(JsonObject o, String k, boolean def) {
        try {
            return o.has(k) ? o.get(k).getAsBoolean() : def;
        } catch (Exception ex) {
            return def;
        }
    }

    static String s(JsonObject o, String k, String def) {
        try {
            return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : def;
        } catch (Exception ex) {
            return def;
        }
    }

    static JsonObject obj(JsonObject o, String k) {
        return o != null && o.has(k) && o.get(k).isJsonObject() ? o.getAsJsonObject(k) : null;
    }

    static JsonArray arr(JsonObject o, String k) {
        return o != null && o.has(k) && o.get(k).isJsonArray() ? o.getAsJsonArray(k) : null;
    }

    /** Массив из n чисел (или def). */
    static float[] vec(JsonObject o, String k, int n, float def) {
        float[] out = new float[n];
        java.util.Arrays.fill(out, def);
        JsonArray a = arr(o, k);
        if (a != null) for (int i = 0; i < Math.min(n, a.size()); i++) out[i] = f(a.get(i), def);
        return out;
    }
}

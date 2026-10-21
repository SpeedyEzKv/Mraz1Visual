package dev.mraz1visual.models;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Модели предметов в формате ресурспаков (item JSON): цепочка parent внутри пака, ссылки на текстуры
 * (#имя, namespace:путь), elements с поворотом элемента и rescale, per-face UV и его поворот, display.
 * Модель без elements (item/generated) — объёмная экструзия слоёв текстуры, как у ванили.
 */
final class ItemModelLoader {
    private ItemModelLoader() {}

    static final class Result {
        Mesh mesh;
        JsonObject display = new JsonObject();
        /** Встроенный родитель цепочки: "handheld", "generated", "block" или null. */
        String builtin;
    }

    /** Кэш текстур одного пака (одна картинка — на все модели пака). */
    static final class TexCache {
        final Map<String, TexImage> map = new HashMap<>();
    }

    static Result load(PackFs fs, String path, TexCache cache, boolean glowExtruded, Consumer<String> warn) throws Exception {
        JsonObject model = Json.parse(fs.get(path));
        Result r = new Result();
        JsonObject textures = new JsonObject();
        JsonArray elements = null;
        String dir = PackFs.dir(path);
        int depth = 0;
        JsonObject cur = model;
        while (cur != null && depth++ < 16) {
            JsonObject t = Json.obj(cur, "textures");
            if (t != null) for (var en : t.entrySet()) if (!textures.has(en.getKey())) textures.add(en.getKey(), en.getValue());
            if (elements == null) elements = Json.arr(cur, "elements");
            JsonObject disp = Json.obj(cur, "display");
            if (disp != null) for (var en : disp.entrySet()) if (!r.display.has(en.getKey())) r.display.add(en.getKey(), en.getValue());
            String parent = Json.s(cur, "parent", null);
            if (parent == null) break;
            String norm = parent.toLowerCase(Locale.ROOT).replace("minecraft:", "");
            String found = fs.findModel(parent);
            if (found == null && parent.startsWith("./")) {
                String rel = (dir.isEmpty() ? "" : dir + "/") + PackFs.normalize(parent) + ".json";
                if (fs.get(rel) != null) found = rel;
            }
            if (found == null) {
                r.builtin = builtin(norm);
                break;
            }
            cur = Json.parse(fs.get(found));
        }
        MeshBuilder b = new MeshBuilder();
        Map<String, Integer> texIndex = new HashMap<>();
        if (elements != null && !elements.isEmpty()) {
            for (JsonElement ee : elements) {
                JsonObject e = ee.getAsJsonObject();
                float[] from = Json.vec(e, "from", 3, 0), to = Json.vec(e, "to", 3, 16);
                Matrix4f m = elementRotation(Json.obj(e, "rotation"));
                JsonObject faces = Json.obj(e, "faces");
                float[][] fuv = new float[6][];
                int[] frot = new int[6], ftex = new int[6];
                for (int d = 0; d < 6; d++) {
                    ftex[d] = -1;
                    JsonObject f = faces == null ? null : Json.obj(faces, MeshBuilder.DIRS[d]);
                    if (f == null) continue;
                    String ref = resolve(textures, Json.s(f, "texture", "#missing"));
                    if (ref == null) continue;
                    Integer ti = texIndex.get(ref);
                    if (ti == null) {
                        TexImage img = texture(fs, ref, dir, cache, warn);
                        ti = b.addTexture(img);
                        texIndex.put(ref, ti);
                    }
                    ftex[d] = ti;
                    frot[d] = Math.round(Json.f(f, "rotation", 0));
                    fuv[d] = f.has("uv") ? Json.vec(f, "uv", 4, 0) : autoUv(d, from, to);
                }
                b.cube(from, to, fuv, frot, ftex, m, -1);
            }
        } else {
            // плоский предмет: слои layer0, layer1… → объём
            boolean any = false;
            for (int i = 0; i < 8; i++) {
                String ref = resolve(textures, "#layer" + i);
                if (ref == null) continue;
                Extruder.extrude(b, texture(fs, ref, dir, cache, warn), i * 0.01f, 1.25f, glowExtruded);
                any = true;
            }
            if (!any) throw new IllegalStateException("нет ни elements, ни layer0");
            if (r.builtin == null) r.builtin = "handheld";
        }
        if (b.quadCount() == 0) throw new IllegalStateException("модель пустая");
        r.mesh = b.build();
        return r;
    }

    private static String builtin(String parent) {
        if (parent.contains("handheld")) return "handheld";
        if (parent.contains("generated")) return "generated";
        return "block";
    }

    /** #ссылка → путь текстуры (цепочка #a → #b → путь). */
    private static String resolve(JsonObject textures, String ref) {
        for (int i = 0; i < 16 && ref != null; i++) {
            if (!ref.startsWith("#")) return ref;
            String key = ref.substring(1);
            ref = textures.has(key) && textures.get(key).isJsonPrimitive() ? textures.get(key).getAsString() : null;
        }
        return null;
    }

    private static TexImage texture(PackFs fs, String ref, String dir, TexCache cache, Consumer<String> warn) {
        String found = fs.findTexture(ref, dir);
        if (found == null) {
            warn.accept("текстура «" + ref + "» не найдена — серая");
            return TexImage.solid(ref, 0xFF9A9AA6);
        }
        TexImage t = cache.map.get(found);
        if (t != null) return t;
        try {
            byte[] meta = fs.get(found + ".mcmeta");
            t = TexImage.decode(PackFs.baseName(found), fs.get(found), meta == null ? null : Json.parse(meta), -1);
            // эмиссивная пара OptiFine: имя_e.png рядом
            String e = found.substring(0, found.length() - 4) + "_e.png";
            byte[] ep = fs.get(e);
            if (ep != null) t.setGlow(TexImage.decode(PackFs.baseName(e), ep, null, t.h));
        } catch (Exception ex) {
            warn.accept("текстура «" + ref + "»: " + ex.getMessage());
            t = TexImage.solid(ref, 0xFF9A9AA6);
        }
        cache.map.put(found, t);
        return t;
    }

    /** UV грани по умолчанию — из положения (как у ванили). */
    private static float[] autoUv(int d, float[] f, float[] t) {
        return switch (d) {
            case 0 -> new float[]{f[0], 16 - t[2], t[0], 16 - f[2]};
            case 1 -> new float[]{f[0], f[2], t[0], t[2]};
            case 2 -> new float[]{16 - t[0], 16 - t[1], 16 - f[0], 16 - f[1]};
            case 3 -> new float[]{f[0], 16 - t[1], t[0], 16 - f[1]};
            case 4 -> new float[]{f[2], 16 - t[1], t[2], 16 - f[1]};
            default -> new float[]{16 - t[2], 16 - t[1], 16 - f[2], 16 - f[1]};
        };
    }

    /** Поворот элемента: одна ось (ваниль, ±45°, 22.5°) или любые углы x/y/z; rescale растягивает остальные оси. */
    private static Matrix4f elementRotation(JsonObject r) {
        if (r == null) return null;
        float[] o = Json.vec(r, "origin", 3, 8);
        Matrix4f m = new Matrix4f().translate(o[0], o[1], o[2]);
        if (r.has("axis")) {
            float ang = Json.f(r, "angle", 0);
            if (ang == 0) return null;
            char axis = Json.s(r, "axis", "y").charAt(0);
            float rad = (float) Math.toRadians(ang);
            switch (axis) {
                case 'x' -> m.rotateX(rad);
                case 'z' -> m.rotateZ(rad);
                default -> m.rotateY(rad);
            }
            if (Json.b(r, "rescale", false)) {
                float k = 1f / (float) Math.cos(Math.abs(rad));
                m.scale(axis == 'x' ? 1 : k, axis == 'y' ? 1 : k, axis == 'z' ? 1 : k);
            }
        } else {
            float x = Json.f(r, "x", 0), y = Json.f(r, "y", 0), z = Json.f(r, "z", 0);
            if (x == 0 && y == 0 && z == 0) return null;
            m.rotateZYX((float) Math.toRadians(z), (float) Math.toRadians(y), (float) Math.toRadians(x));
        }
        return m.translate(-o[0], -o[1], -o[2]);
    }
}

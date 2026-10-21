package dev.mraz1visual.models;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Модели Blockbench (.bbmodel): java_block и free (форматы 4.x и 5.x). Кубы с поворотом вокруг origin,
 * per-face UV и box UV, несколько текстур (встроенные base64 или файлы рядом), дерево групп → кости,
 * анимации костей. Эмиссивная пара текстуры (*_e) становится слоем свечения.
 */
final class BbModelLoader {
    private BbModelLoader() {}

    static final class Result {
        Mesh mesh;
        final List<Anim> anims = new ArrayList<>();
        JsonObject display;
        String format;
        String name;
    }

    static Result load(byte[] data, String name, PackFs fs, String path, Consumer<String> warn) throws Exception {
        JsonObject j = Json.parse(data);
        Result r = new Result();
        JsonObject meta = Json.obj(j, "meta");
        r.format = meta == null ? "free" : Json.s(meta, "model_format", "free");
        r.name = Json.s(j, "name", name);
        boolean boxDefault = meta != null && Json.b(meta, "box_uv", false);
        JsonObject res = Json.obj(j, "resolution");
        float resW = res == null ? 16 : Json.f(res, "width", 16), resH = res == null ? 16 : Json.f(res, "height", 16);

        MeshBuilder b = new MeshBuilder();
        // ── текстуры ──
        JsonArray texArr = Json.arr(j, "textures");
        List<TexImage> texList = new ArrayList<>();
        Map<String, Integer> texById = new HashMap<>();
        if (texArr != null) {
            for (int i = 0; i < texArr.size(); i++) {
                JsonObject t = texArr.get(i).getAsJsonObject();
                String tn = Json.s(t, "name", "texture" + i);
                TexImage img = null;
                try {
                    byte[] png = null;
                    String src = Json.s(t, "source", "");
                    int comma = src.indexOf(',');
                    if (src.startsWith("data:") && comma > 0) png = Base64.getMimeDecoder().decode(src.substring(comma + 1));
                    if (png == null && fs != null) {
                        String rel = Json.s(t, "relative_path", Json.s(t, "path", tn));
                        String found = fs.findTexture(PackFs.baseName(PackFs.normalize(rel)), PackFs.dir(path));
                        if (found != null) png = fs.get(found);
                    }
                    if (png != null) {
                        float uw = Json.f(t, "uv_width", resW), uh = Json.f(t, "uv_height", resH);
                        int tw = Json.i(t, "width", 0);
                        // полоса кадров: высота кадра = uv_height в пикселях текстуры
                        int frameH = tw > 0 ? Math.round(uh * tw / uw) : -1;
                        img = TexImage.decode(tn, png, null, frameH);
                        img.uvW = uw;
                        img.uvH = uh;
                        if (img.frames.length > 1) {
                            int ft = Math.max(1, Math.round(Json.f(t, "frame_time", 1)));
                            java.util.Arrays.fill(img.ticks, ft);
                            img.interpolate = Json.b(t, "frame_interpolate", false);
                        }
                    }
                } catch (Exception ex) {
                    warn.accept("текстура «" + tn + "»: " + ex.getMessage());
                }
                if (img == null) {
                    warn.accept("текстура «" + tn + "» не найдена — серая");
                    img = TexImage.solid(tn, 0xFF9A9AA6);
                    img.uvW = resW;
                    img.uvH = resH;
                }
                texList.add(img);
                texById.put(Json.s(t, "uuid", "#" + i), i);
                texById.put("#" + i, i);
            }
        }
        // эмиссивные пары: name_e — слой свечения для name; на неё ссылаются грани — светится сама
        Set<Integer> usedTex = new HashSet<>();
        JsonArray els = Json.arr(j, "elements");
        if (els != null) for (JsonElement e : els) {
            JsonObject faces = Json.obj(e.getAsJsonObject(), "faces");
            if (faces != null) for (String d : MeshBuilder.DIRS) {
                JsonObject f = Json.obj(faces, d);
                if (f != null) usedTex.add(texIndex(f.get("texture"), texById, texList.size()));
            }
        }
        for (int i = 0; i < texList.size(); i++) {
            TexImage t = texList.get(i);
            String base = emissiveBase(t.name);
            if (base == null) continue;
            if (usedTex.contains(i)) {
                t.emissive = true;
                continue;
            }
            for (TexImage o : texList) {
                if (o != t && PackFs.stripExt(o.name).toLowerCase(Locale.ROOT).equals(base)) o.setGlow(t);
            }
        }
        for (TexImage t : texList) b.addTexture(t);

        // ── группы → кости ──
        Map<String, JsonObject> groups = new HashMap<>();
        JsonArray groupArr = Json.arr(j, "groups");
        if (groupArr != null) for (JsonElement g : groupArr) {
            JsonObject go = g.getAsJsonObject();
            groups.put(Json.s(go, "uuid", ""), go);
        }
        Map<String, Integer> elementBone = new HashMap<>();
        Map<String, Integer> boneByUuid = new HashMap<>();
        Set<String> hidden = new HashSet<>();
        JsonArray outliner = Json.arr(j, "outliner");
        if (outliner != null) for (JsonElement n : outliner) walk(n, -1, false, b, groups, elementBone, boneByUuid, hidden);

        // ── кубы ──
        if (els != null) {
            for (JsonElement ee : els) {
                JsonObject e = ee.getAsJsonObject();
                String type = Json.s(e, "type", "cube");
                String uuid = Json.s(e, "uuid", "");
                if (!type.equals("cube")) {
                    if (!type.equals("locator") && !type.equals("null_object")) warn.accept("элемент типа «" + type + "» пропущен");
                    continue;
                }
                if (!Json.b(e, "visibility", true) || !Json.b(e, "export", true) || hidden.contains(uuid)) continue;
                if (Json.s(e, "name", "").toLowerCase(Locale.ROOT).contains("hitbox")) continue;
                float[] from = Json.vec(e, "from", 3, 0), to = Json.vec(e, "to", 3, 0);
                float inflate = Json.f(e, "inflate", 0);
                for (int k = 0; k < 3; k++) {
                    float lo = Math.min(from[k], to[k]) - inflate, hi = Math.max(from[k], to[k]) + inflate;
                    from[k] = lo;
                    to[k] = hi;
                }
                float[] origin = Json.vec(e, "origin", 3, 0);
                float[] rot = rotation(e);
                Matrix4f m = MeshBuilder.rotation(origin[0], origin[1], origin[2], rot[0], rot[1], rot[2]);
                boolean box = e.has("box_uv") ? Json.b(e, "box_uv", false) : boxDefault;
                float[][] fuv = new float[6][];
                int[] frot = new int[6], ftex = new int[6];
                JsonObject faces = Json.obj(e, "faces");
                for (int d = 0; d < 6; d++) {
                    JsonObject f = faces == null ? null : Json.obj(faces, MeshBuilder.DIRS[d]);
                    ftex[d] = -1;
                    if (f == null) continue;
                    int ti = texIndex(f.get("texture"), texById, texList.size());
                    if (ti < 0) continue;
                    ftex[d] = ti;
                    frot[d] = Math.round(Json.f(f, "rotation", 0));
                    fuv[d] = Json.vec(f, "uv", 4, 0);
                }
                if (box) boxUv(e, from, to, inflate, fuv);
                int bone = elementBone.getOrDefault(uuid, -1);
                b.cube(from, to, fuv, frot, ftex, m, bone);
            }
        }
        if (b.quadCount() == 0) throw new IllegalStateException("в модели нет видимых кубов");

        // ── анимации ──
        JsonArray anims = Json.arr(j, "animations");
        if (anims != null) {
            for (JsonElement ae : anims) {
                try {
                    Anim a = anim(ae.getAsJsonObject(), boneByUuid, b.bones, warn);
                    if (a != null) r.anims.add(a);
                } catch (Exception ex) {
                    warn.accept("анимация пропущена: " + ex.getMessage());
                }
            }
        }
        r.display = Json.obj(j, "display");
        r.mesh = b.build();
        return r;
    }

    /** "name_e.png", "name_emissive", "name_glow" → "name"; иначе null. */
    static String emissiveBase(String texName) {
        String n = PackFs.stripExt(texName).toLowerCase(Locale.ROOT);
        for (String suf : new String[]{"_e", "_emissive", "_glow", "-e"}) {
            if (n.endsWith(suf) && n.length() > suf.length()) return n.substring(0, n.length() - suf.length());
        }
        return null;
    }

    private static int texIndex(JsonElement t, Map<String, Integer> byId, int count) {
        if (t == null || t.isJsonNull()) return -1;
        try {
            if (t.isJsonPrimitive() && t.getAsJsonPrimitive().isNumber()) {
                int i = t.getAsInt();
                return i >= 0 && i < count ? i : -1;
            }
            String s = t.getAsString();
            Integer i = byId.get(s);
            if (i != null) return i;
            int n = Integer.parseInt(s);
            return n >= 0 && n < count ? n : -1;
        } catch (Exception ex) {
            return -1;
        }
    }

    private static float[] rotation(JsonObject e) {
        JsonElement r = e.get("rotation");
        float[] out = new float[3];
        if (r != null && r.isJsonArray()) {
            JsonArray a = r.getAsJsonArray();
            for (int i = 0; i < Math.min(3, a.size()); i++) out[i] = Json.f(a.get(i), 0);
        } else if (r != null && r.isJsonObject()) {
            // java_block из Blockbench иногда пишет вращение в духе item JSON
            JsonObject o = r.getAsJsonObject();
            String axis = Json.s(o, "axis", "y");
            float ang = Json.f(o, "angle", 0);
            out["xyz".indexOf(axis.charAt(0))] = ang;
        }
        return out;
    }

    private static void walk(JsonElement n, int parent, boolean hiddenParent, MeshBuilder b, Map<String, JsonObject> groups,
                             Map<String, Integer> elementBone, Map<String, Integer> boneByUuid, Set<String> hidden) {
        if (n.isJsonPrimitive()) {
            String uuid = n.getAsString();
            if (groups.containsKey(uuid)) {
                JsonObject g = groups.get(uuid).deepCopy();
                walk(g, parent, hiddenParent, b, groups, elementBone, boneByUuid, hidden);
                return;
            }
            elementBone.put(uuid, parent);
            if (hiddenParent) hidden.add(uuid);
            return;
        }
        if (!n.isJsonObject()) return;
        JsonObject g = n.getAsJsonObject();
        String uuid = Json.s(g, "uuid", "");
        // формат 5.x: в outliner только uuid и дети, свойства группы — в массиве groups
        JsonObject props = !g.has("origin") && groups.containsKey(uuid) ? groups.get(uuid) : g;
        String name = Json.s(props, "name", "group");
        float[] o = Json.vec(props, "origin", 3, 0);
        float[] rot = rotation(props);
        boolean hide = hiddenParent || !Json.b(props, "visibility", true) || !Json.b(props, "export", true)
                || name.toLowerCase(Locale.ROOT).contains("hitbox");
        int bone = b.addBone(new Mesh.Bone(name, parent, o[0] / 16f, o[1] / 16f, o[2] / 16f,
                (float) Math.toRadians(rot[0]), (float) Math.toRadians(rot[1]), (float) Math.toRadians(rot[2])));
        boneByUuid.put(uuid, bone);
        JsonArray ch = Json.arr(g, "children");
        if (ch != null) for (JsonElement c : ch) walk(c, bone, hide, b, groups, elementBone, boneByUuid, hidden);
    }

    /** Box UV (развёртка «коробкой» от uv_offset). */
    private static void boxUv(JsonObject e, float[] from, float[] to, float inflate, float[][] fuv) {
        float[] off = Json.vec(e, "uv_offset", 2, 0);
        float w = (float) Math.floor(to[0] - from[0] - 2 * inflate + 1e-4f);
        float h = (float) Math.floor(to[1] - from[1] - 2 * inflate + 1e-4f);
        float d = (float) Math.floor(to[2] - from[2] - 2 * inflate + 1e-4f);
        float u = off[0], v = off[1];
        boolean mirror = Json.b(e, "mirror_uv", false);
        // порядок граней: down, up, north, south, west, east
        float[][] uv = {
                {u + d + w + w, v + d, u + d + w, v},
                {u + d + w, v + d, u + d, v},
                {u + d, v + d, u + d + w, v + d + h},
                {u + d + w + d, v + d, u + d + w + d + w, v + d + h},
                {u + d + w, v + d, u + d + w + d, v + d + h},
                {u, v + d, u + d, v + d + h},
        };
        if (mirror) {
            for (float[] f : uv) { float t = f[0]; f[0] = f[2]; f[2] = t; }
            float[] t = uv[4]; uv[4] = uv[5]; uv[5] = t;
        }
        for (int i = 0; i < 6; i++) if (fuv[i] != null || e.getAsJsonObject("faces") == null) fuv[i] = uv[i];
    }

    private static Anim anim(JsonObject a, Map<String, Integer> boneByUuid, List<Mesh.Bone> bones, Consumer<String> warn) {
        String name = Json.s(a, "name", "anim");
        float length = Json.f(a, "length", 0);
        String loopS = Json.s(a, "loop", "once");
        int loop = loopS.equals("loop") ? Anim.LOOP : loopS.equals("hold") ? Anim.HOLD : Anim.ONCE;
        JsonObject animators = Json.obj(a, "animators");
        List<Anim.Channel> out = new ArrayList<>();
        float maxT = 0;
        if (animators != null) {
            for (Map.Entry<String, JsonElement> en : animators.entrySet()) {
                JsonObject an = en.getValue().getAsJsonObject();
                String type = Json.s(an, "type", "bone");
                if (!type.equals("bone")) continue;
                Integer bone = boneByUuid.get(en.getKey());
                if (bone == null) {
                    // старые файлы: аниматор по имени группы
                    String bn = Json.s(an, "name", "");
                    for (int i = 0; i < bones.size(); i++) if (bones.get(i).name.equals(bn)) { bone = i; break; }
                }
                if (bone == null) continue;
                JsonArray kfs = Json.arr(an, "keyframes");
                if (kfs == null) continue;
                for (int ch = 0; ch < 3; ch++) {
                    String chName = ch == Anim.ROT ? "rotation" : ch == Anim.POS ? "position" : "scale";
                    List<JsonObject> list = new ArrayList<>();
                    for (JsonElement k : kfs) {
                        JsonObject ko = k.getAsJsonObject();
                        if (Json.s(ko, "channel", "").equals(chName)) list.add(ko);
                    }
                    if (list.isEmpty()) continue;
                    list.sort((x, y) -> Float.compare(Json.f(x, "time", 0), Json.f(y, "time", 0)));
                    int n = list.size();
                    float[] times = new float[n];
                    Molang.Expr[][] vals = new Molang.Expr[n][6];
                    byte[] interp = new byte[n];
                    float[][] bez = new float[n][];
                    float def = ch == Anim.SCALE ? 1 : 0;
                    for (int i = 0; i < n; i++) {
                        JsonObject ko = list.get(i);
                        times[i] = Json.f(ko, "time", 0);
                        maxT = Math.max(maxT, times[i]);
                        JsonArray dps = Json.arr(ko, "data_points");
                        JsonObject pre = dps != null && dps.size() > 0 ? dps.get(0).getAsJsonObject() : new JsonObject();
                        JsonObject post = dps != null && dps.size() > 1 ? dps.get(dps.size() - 1).getAsJsonObject() : pre;
                        for (int k = 0; k < 3; k++) {
                            String axis = "xyz".substring(k, k + 1);
                            vals[i][k] = value(pre, axis, def, warn);
                            vals[i][3 + k] = post == pre ? vals[i][k] : value(post, axis, def, warn);
                        }
                        String ip = Json.s(ko, "interpolation", "linear");
                        interp[i] = switch (ip) {
                            case "catmullrom" -> Anim.CATMULL;
                            case "step" -> Anim.STEP;
                            case "bezier" -> Anim.BEZIER;
                            default -> Anim.LINEAR;
                        };
                        if (interp[i] == Anim.BEZIER) {
                            float[] lt = Json.vec(ko, "bezier_left_time", 3, -0.1f), lv = Json.vec(ko, "bezier_left_value", 3, 0);
                            float[] rt = Json.vec(ko, "bezier_right_time", 3, 0.1f), rv = Json.vec(ko, "bezier_right_value", 3, 0);
                            bez[i] = new float[]{lt[0], lv[0], lv[1], lv[2], rt[0], rv[0], rv[1], rv[2]};
                        }
                    }
                    out.add(new Anim.Channel(bone, ch, times, vals, interp, bez));
                }
            }
        }
        if (length <= 0) length = maxT;
        return new Anim(name, length, loop, out.toArray(new Anim.Channel[0]));
    }

    private static Molang.Expr value(JsonObject dp, String axis, float def, Consumer<String> warn) {
        JsonElement v = dp.get(axis);
        if (v == null || v.isJsonNull()) return new Molang.Const(def);
        if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isNumber()) return new Molang.Const(v.getAsFloat());
        String s = v.getAsString();
        if (s.isBlank()) return new Molang.Const(def);
        return Molang.parse(s, warn);
    }
}

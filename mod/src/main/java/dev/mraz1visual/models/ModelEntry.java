package dev.mraz1visual.models;

import com.google.gson.JsonObject;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Одна импортированная модель: скин оружия, питомец, косметика или эффект. */
public final class ModelEntry {
    public enum Kind { WEAPON, PET, HAT, WING, EFFECT }

    /** Состояния предмета: обычное, натяжение лука 0–2, заброшенная удочка, поднятый щит. */
    public static final String DEFAULT = "default", PULL0 = "pull0", PULL1 = "pull1", PULL2 = "pull2", CAST = "cast", BLOCKING = "blocking";

    public final String id, name, source;
    public final Kind kind;
    public final Map<String, Mesh> states = new LinkedHashMap<>();
    /** Позы по контекстам (индекс — ordinal ItemDisplayContext), из display модели или по умолчанию. */
    public final float[][] baseDisplay = new float[ItemDisplayContext.values().length][];
    /** Готовые позы с ручной подстройкой. */
    public final ItemTransform[] display = new ItemTransform[ItemDisplayContext.values().length];
    /** Подсказка привязки из пака: sword, axe, bow… (или null). */
    public String bindHint;
    public final List<Anim> anims = new ArrayList<>();
    /** Эффекты «особой» анимации (vfx_* из того же пака). */
    public final List<ModelEntry> effects = new ArrayList<>();
    public boolean flying;
    public final List<String> warnings = new ArrayList<>();
    public String error;
    /** Вид оружия «по умолчанию» для подписи источника: CIT, CraftEngine, bbmodel, PNG. */
    public String format = "";
    /** Встроенная косметика (из .jar) или свои файлы игрока; коллекция и автор — для подписи и раздела «Авторы». */
    public boolean builtin;
    public String collection = "", sourceTitle = "", author = "";

    /** Подпись источника: коллекция встроенной косметики или «Мои файлы». */
    public String sourceLabel() {
        return builtin ? sourceTitle : "Мои файлы · " + source;
    }
    /** Ключ позы для кэша иконок: новый объект при каждой правке позы. */
    public Object poseKey = new Object();

    ModelEntry(String id, String name, String source, Kind kind) {
        this.id = id;
        this.name = name;
        this.source = source;
        this.kind = kind;
    }

    public Mesh mesh() {
        return states.get(DEFAULT);
    }

    public Mesh mesh(String state) {
        Mesh m = states.get(state);
        return m != null ? m : states.get(DEFAULT);
    }

    public boolean broken() {
        return error != null || mesh() == null;
    }

    public Anim anim(String name) {
        for (Anim a : anims) if (a.name.equalsIgnoreCase(name)) return a;
        return null;
    }

    /** Есть ли анимированные или светящиеся части (иконку в GUI тогда нельзя кэшировать навсегда). */
    public boolean animatedTextures() {
        for (Mesh m : states.values()) if (!m.atlas.anims.isEmpty()) return true;
        return false;
    }

    // ── позы ─────────────────────────────────────────────────

    /** Ручная подстройка: rx, ry, rz (°), tx, ty, tz (px), масштаб — для групп «1-е лицо», «3-е лицо», «иконка». */
    public static final int FP = 0, TP = 1, ICON = 2;

    public void rebuildDisplay(float[][] adjust) {
        poseKey = new Object();
        for (ItemDisplayContext ctx : ItemDisplayContext.values()) {
            float[] base = baseDisplay[ctx.ordinal()];
            if (base == null) base = new float[]{0, 0, 0, 0, 0, 0, 1, 1, 1};
            float[] a = adjust == null ? null : adjust[group(ctx)];
            float rx = base[0], ry = base[1], rz = base[2], tx = base[3], ty = base[4], tz = base[5];
            float sx = base[6], sy = base[7], sz = base[8];
            if (a != null) {
                rx += a[0]; ry += a[1]; rz += a[2];
                tx += a[3]; ty += a[4]; tz += a[5];
                sx *= a[6]; sy *= a[6]; sz *= a[6];
            }
            display[ctx.ordinal()] = new ItemTransform(new Vector3f(rx, ry, rz),
                    new Vector3f(clamp(tx / 16f, 5), clamp(ty / 16f, 5), clamp(tz / 16f, 5)),
                    new Vector3f(clamp(sx, 4), clamp(sy, 4), clamp(sz, 4)));
        }
    }

    public static int group(ItemDisplayContext ctx) {
        return switch (ctx) {
            case FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND -> FP;
            case GUI, GROUND, FIXED -> ICON;
            default -> TP;
        };
    }

    private static float clamp(float v, float lim) {
        return Math.max(-lim, Math.min(lim, v));
    }

    // ── позы по умолчанию (как у ванильных родителей) ─────────

    private static final Map<String, float[]> HANDHELD = new LinkedHashMap<>(), BLOCK = new LinkedHashMap<>(), GENERATED = new LinkedHashMap<>();

    static {
        HANDHELD.put("thirdperson_righthand", new float[]{0, -90, 55, 0, 4, 0.5f, 0.85f, 0.85f, 0.85f});
        HANDHELD.put("thirdperson_lefthand", new float[]{0, 90, -55, 0, 4, 0.5f, 0.85f, 0.85f, 0.85f});
        HANDHELD.put("firstperson_righthand", new float[]{0, -90, 25, 1.13f, 3.2f, 1.13f, 0.68f, 0.68f, 0.68f});
        HANDHELD.put("firstperson_lefthand", new float[]{0, 90, -25, 1.13f, 3.2f, 1.13f, 0.68f, 0.68f, 0.68f});
        HANDHELD.put("ground", new float[]{0, 0, 0, 0, 2, 0, 0.5f, 0.5f, 0.5f});
        HANDHELD.put("head", new float[]{0, 180, 0, 0, 13, 7, 1, 1, 1});
        HANDHELD.put("fixed", new float[]{0, 180, 0, 0, 0, 0, 1, 1, 1});
        GENERATED.putAll(HANDHELD);
        GENERATED.put("thirdperson_righthand", new float[]{0, 0, 0, 0, 3, 1, 0.55f, 0.55f, 0.55f});
        GENERATED.put("thirdperson_lefthand", new float[]{0, 0, 0, 0, 3, 1, 0.55f, 0.55f, 0.55f});
        BLOCK.put("gui", new float[]{30, 225, 0, 0, 0, 0, 0.625f, 0.625f, 0.625f});
        BLOCK.put("ground", new float[]{0, 0, 0, 0, 3, 0, 0.25f, 0.25f, 0.25f});
        BLOCK.put("fixed", new float[]{0, 0, 0, 0, 0, 0, 0.5f, 0.5f, 0.5f});
        BLOCK.put("thirdperson_righthand", new float[]{75, 45, 0, 0, 2.5f, 0, 0.375f, 0.375f, 0.375f});
        BLOCK.put("thirdperson_lefthand", new float[]{75, 45, 0, 0, 2.5f, 0, 0.375f, 0.375f, 0.375f});
        BLOCK.put("firstperson_righthand", new float[]{0, 45, 0, 0, 0, 0, 0.4f, 0.4f, 0.4f});
        BLOCK.put("firstperson_lefthand", new float[]{0, 225, 0, 0, 0, 0, 0.4f, 0.4f, 0.4f});
    }

    /** Позы из display модели; чего нет — левая рука как правая, остальное — по встроенному родителю (или как у меча). */
    void setDisplay(JsonObject display, String builtin) {
        Map<String, float[]> defs = "block".equals(builtin) ? BLOCK : "generated".equals(builtin) ? GENERATED : HANDHELD;
        for (ItemDisplayContext ctx : ItemDisplayContext.values()) {
            String key = ctx.getSerializedName();
            float[] v = read(display, key);
            if (v == null && key.endsWith("_lefthand")) v = read(display, key.replace("_lefthand", "_righthand"));
            if (v == null) v = defs.get(key);
            baseDisplay[ctx.ordinal()] = v;
        }
    }

    private static float[] read(JsonObject display, String key) {
        JsonObject o = Json.obj(display, key);
        if (o == null) return null;
        float[] r = Json.vec(o, "rotation", 3, 0), t = Json.vec(o, "translation", 3, 0), s = Json.vec(o, "scale", 3, 1);
        return new float[]{r[0], r[1], r[2], t[0], t[1], t[2], s[0], s[1], s[2]};
    }
}

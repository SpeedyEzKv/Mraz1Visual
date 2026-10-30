package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.core.WorldLayer;
import dev.mraz1visual.render.HudStyle;
import dev.mraz1visual.render.WorldDraw;
import dev.mraz1visual.setting.BoolSetting;
import dev.mraz1visual.setting.SliderSetting;
import dev.mraz1visual.ui.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.PrimedTnt;

import java.util.HashMap;
import java.util.Map;

/**
 * Таймеры над объектами в мире: подпись, оставшееся время и полоска.
 * TNT — по фьюзу сущности (сервер его присылает). Облако зелья — клиент знает только радиус, поэтому время
 * считается по скорости, с которой облако сжимается с момента, когда ты его увидел.
 */
public final class TntTimer extends Module implements WorldLayer {
    final BoolSetting tnt = add(new BoolSetting("tnt", "TNT", true));
    final BoolSetting clouds = add(new BoolSetting("clouds", "Облака зелий", true));
    final SliderSetting size = add(new SliderSetting("size", "Размер подписи", 1.2, 0.6, 2.5, 0.1, "×"));

    /** Облако: радиус при первом взгляде и когда, измеренная скорость сжатия. */
    private static final class Seen {
        float lastR;
        long lastAt;
        float rate;
        String text = "";
        int tenths = -1;
    }

    private final Map<Integer, Seen> seen = new HashMap<>();
    private int sweep;

    public TntTimer() {
        super("tnt_timer", "Timers", "Таймеры над TNT и облаками зелий: время и полоска.", Category.VISUALS);
        markNew();
    }

    @Override
    protected void onDisable() {
        seen.clear();
    }

    @Override
    public void onTick(Minecraft mc) {
        if (mc.level == null) {
            seen.clear();
            return;
        }
        if (!clouds.get()) return;
        long now = System.currentTimeMillis();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof AreaEffectCloud c)) continue;
            Seen s = seen.computeIfAbsent(c.getId(), k -> new Seen());
            float r = c.getRadius();
            if (s.lastAt != 0 && now > s.lastAt) {
                float d = (s.lastR - r) / ((now - s.lastAt) / 1000f);
                if (d > 0) s.rate = s.rate == 0 ? d : s.rate * 0.8f + d * 0.2f;
            }
            s.lastR = r;
            s.lastAt = now;
        }
        // раз в 5 секунд убрать исчезнувшие облака
        if (++sweep % 100 == 0) seen.keySet().removeIf(id -> mc.level.getEntity(id) == null);
    }

    @Override
    public void renderWorld(WorldDraw d, float pt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        float k = size.getF();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e.distanceToSqr(mc.player) > 48 * 48) continue;
            double x = e.xo + (e.getX() - e.xo) * pt, z = e.zo + (e.getZ() - e.zo) * pt;
            if (tnt.get() && e instanceof PrimedTnt t) {
                float sec = Math.max(0, (t.getFuse() - pt) / 20f);
                int c = sec > 2 ? HudStyle.GREEN : sec > 1 ? HudStyle.YELLOW : HudStyle.RED;
                double y = e.yo + (e.getY() - e.yo) * pt + 1.35;
                int tenths = Math.round(sec * 10);
                d.label(Component.literal("TNT  " + tenths / 10 + "." + tenths % 10 + "с"), x, y, z, k, c, 0x80000000);
                bar(d, x, y - 0.18 * k, z, sec / 4f, c, k);
            } else if (clouds.get() && e instanceof AreaEffectCloud cl) {
                Seen s = seen.get(cl.getId());
                if (s == null) continue;
                double y = e.getY() + 0.9;
                String text = "Облако";
                float f = 1;
                if (s.rate > 0.0005f) {
                    float sec = cl.getRadius() / s.rate;
                    int tenths = Math.round(sec * 10);
                    if (tenths != s.tenths) {
                        s.tenths = tenths;
                        s.text = "Облако  " + tenths / 10 + "." + tenths % 10 + "с";
                    }
                    text = s.text;
                    f = Math.min(1f, sec / 30f);
                }
                d.label(Component.literal(text), x, y, z, k, 0xFFE9E9F1, 0x80000000);
                bar(d, x, y - 0.18 * k, z, f, HudStyle.GRAD_B, k);
            }
        }
    }

    /** Полоска под подписью — к камере, как табличка. */
    private static void bar(WorldDraw d, double x, double y, double z, float f, int color, float k) {
        double w = 0.5 * k;
        double cx = d.camX() - x, cz = d.camZ() - z, l = Math.max(1e-3, Math.sqrt(cx * cx + cz * cz));
        double rx = -cz / l * w, rz = cx / l * w;
        f = Math.max(0, Math.min(1, f));
        d.line(x - rx, y, z - rz, x + rx, y, z + rz, 0.05 * k, 0x60000000);
        d.line(x - rx, y, z - rz, x - rx + rx * 2 * f, y, z - rz + rz * 2 * f, 0.04 * k, Colors.withAlpha(color, 0xF0));
    }
}

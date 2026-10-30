package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.core.WorldLayer;
import dev.mraz1visual.render.Fx;
import dev.mraz1visual.render.FxParticles;
import dev.mraz1visual.render.Palette;
import dev.mraz1visual.render.Ribbon;
import dev.mraz1visual.render.WorldDraw;
import dev.mraz1visual.setting.BoolSetting;
import dev.mraz1visual.setting.ColorSetting;
import dev.mraz1visual.setting.EnumSetting;
import dev.mraz1visual.setting.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * Лента-след за тобой на бегу и в полёте на элитрах: гладкая (точки каждый кадр, сплайн), сужается и тает.
 * Стили: лента света, звёздная пыль, стеклянный шлейф, огоньки. Только твой след и только у тебя на экране.
 */
public final class Trails extends Module implements WorldLayer {
    final EnumSetting style = add(new EnumSetting("style", "Вид", 4, "light", "Лента света", "stardust", "Звёздная пыль",
            "glass", "Стеклянный шлейф", "particles", "Огоньки", "soft", "Простой мягкий след")
            .alias("ribbon", "light").alias("line", "light").alias("both", "stardust"));
    final EnumSetting when = add(new EnumSetting("when", "Когда", 0, "move", "При движении", "sprint", "Только на бегу", "elytra", "Только на элитрах"));
    final EnumSetting preset = add(new EnumSetting("preset", "Цвет", 0, Palette.OPTIONS));
    final ColorSetting color = add(new ColorSetting("color", "Свой: центр", 0xFFB8E6FF));
    final ColorSetting color2 = add(new ColorSetting("color2", "Свой: край", 0xFF7A6BFF));
    final SliderSetting length = add(new SliderSetting("length_s", "Длина", 0.7, 0.2, 2.0, 0.05, " с"));
    final SliderSetting width = add(new SliderSetting("width", "Ширина", 0.5, 0.1, 1.2, 0.05, " бл."));
    final SliderSetting height = add(new SliderSetting("height", "Высота", 0.3, 0.05, 1.8, 0.05, " бл."));
    final SliderSetting opacity = add(new SliderSetting("opacity", "Прозрачность", 85, 20, 100, 5, " %"));
    final BoolSetting thirdOnly = add(new BoolSetting("third_only", "Только от 3-го лица", true));

    private final Ribbon ribbon = new Ribbon(160);
    private long last;
    private float dustAcc;

    public Trails() {
        super("trails", "Trails", "Гладкая лента за тобой на бегу и на элитрах — тает со временем.", Category.VISUALS);
    }

    @Override
    protected void onEnable() {
        ribbon.clear();
    }

    private boolean active(LocalPlayer p) {
        return switch (when.getId()) {
            case "sprint" -> p.isSprinting() || p.isFallFlying();
            case "elytra" -> p.isFallFlying();
            default -> true;
        };
    }

    @Override
    public void renderWorld(WorldDraw d, float pt) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) return;
        long now = System.nanoTime();
        float dt = last == 0 ? 0 : Math.min(0.05f, (now - last) / 1e9f);
        last = now;
        var pos = p.getPosition(pt);
        boolean fly = p.isFallFlying();
        double hy = fly ? 0.3 : height.get();
        ribbon.life = length.getF() * (fly ? 1.6f : 1f);
        if (active(p)) ribbon.push(pos.x, pos.y + hy, pos.z, dt);
        else ribbon.age(dt);
        if (thirdOnly.get() && mc.options.getCameraType().isFirstPerson()) return;
        float a = opacity.getF() / 100f;
        String pr = preset.getId();
        double w = width.get() * (fly ? 1.4 : 1);
        switch (style.getId()) {
            case "glass" -> {
                // почти прозрачная стенка и яркая тонкая кромка сверху
                ribbon.draw(d, Ribbon.WALL, -hy + 0.02, a * 0.22f, pr, color.get(), color2.get(), pos.x, pos.y + hy, pos.z);
                d.glowBegin();
                ribbon.draw(d, Ribbon.FACING, 0.045, a, pr, color.get(), color2.get(), pos.x, pos.y + hy, pos.z);
                d.glowEnd();
            }
            case "stardust" -> {
                d.glowBegin();
                ribbon.draw(d, fly ? Ribbon.FACING : Ribbon.FLAT, w * 0.35, a * 0.7f, pr, color.get(), color2.get(), pos.x, pos.y + hy, pos.z);
                d.glowEnd();
                dust(p, pos.x, pos.y + hy, pos.z, dt, a);
            }
            case "particles" -> dust(p, pos.x, pos.y + hy, pos.z, dt, a);
            // по умолчанию: одна тонкая мягкая лента без свечения и частиц
            case "soft" -> ribbon.draw(d, fly ? Ribbon.FACING : Ribbon.FLAT, w * 0.5, a * 0.55f, pr, color.get(), color2.get(), pos.x, pos.y + hy, pos.z);
            default -> {
                // широкое мягкое свечение и тонкая яркая середина; горизонтальная лента видна и сзади
                int mode = fly ? Ribbon.FACING : Ribbon.FLAT;
                d.glowBegin();
                ribbon.draw(d, mode, w, a * 0.8f, pr, color.get(), color2.get(), pos.x, pos.y + hy, pos.z);
                d.glowEnd();
                ribbon.draw(d, mode, w * 0.22, a, pr, color.get(), color2.get(), pos.x, pos.y + hy, pos.z);
            }
        }
    }

    /** Звёздная пыль: редкие мягкие звёздочки, медленно оседают и плывут (без мигания). */
    private void dust(LocalPlayer p, double x, double y, double z, float dt, float a) {
        if (!active(p) || p.getDeltaMovement().horizontalDistanceSqr() < 0.004) return;
        dustAcc += dt * 22 * Fx.quality();
        while (dustAcc >= 1) {
            dustAcc--;
            int c = Fx.color(preset.getId(), (float) Math.random() * 0.5f, Fx.time(), color.get(), color2.get(), a);
            boolean star = Math.random() < 0.35;
            FxParticles.spawn(x + (Math.random() - 0.5) * 0.4, y + (Math.random() - 0.5) * 0.3, z + (Math.random() - 0.5) * 0.4,
                    (Math.random() - 0.5) * 0.15, 0.05, (Math.random() - 0.5) * 0.15, 0.9f + (float) Math.random() * 0.6f,
                    star ? 0.05f : 0.03f, c, -0.05f, true, star ? FxParticles.STAR : FxParticles.DOT, true, 0.3f, 0.6f);
        }
    }
}

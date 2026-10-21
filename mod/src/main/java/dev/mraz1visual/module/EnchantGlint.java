package dev.mraz1visual.module;

import com.mojang.blaze3d.platform.NativeImage;
import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.core.ModuleManager;
import dev.mraz1visual.setting.ColorSetting;
import dev.mraz1visual.setting.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Свой цвет блеска зачарований — отдельно для предметов и брони, плюс скорость и сила блеска.
 * Перекрашивается сама текстура блеска (яркость сохраняется), скорость и сила — ванильные настройки
 * доступности; при выключении всё возвращается как было.
 */
public final class EnchantGlint extends Module {
    private static final Identifier ITEM = Identifier.withDefaultNamespace("textures/misc/enchanted_glint_item.png");
    private static final Identifier ARMOR = Identifier.withDefaultNamespace("textures/misc/enchanted_glint_armor.png");

    final ColorSetting item = add(new ColorSetting("item", "Цвет: оружие и предметы", 0xFF7FD8FF).onChange(this::apply));
    final ColorSetting armor = add(new ColorSetting("armor", "Цвет: броня", 0xFFB896FF).onChange(this::apply));
    final SliderSetting speed = add(new SliderSetting("speed", "Скорость блеска", 50, 0, 100, 5, " %").onChange(this::applyOptions));
    final SliderSetting strength = add(new SliderSetting("strength", "Сила блеска", 75, 0, 100, 5, " %").onChange(this::applyOptions));

    private double speedWas = -1, strengthWas = -1;

    public EnchantGlint() {
        super("enchant_glint", "Enchant Glint", "Свой цвет, скорость и сила блеска зачарований.", Category.VISUALS);
    }

    @Override
    protected void onEnable() {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            speedWas = mc.options.glintSpeed().get();
            strengthWas = mc.options.glintStrength().get();
            apply();
            applyOptions();
        });
    }

    @Override
    protected void onDisable() {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            var tm = mc.getTextureManager();
            tm.release(ITEM);
            tm.release(ARMOR);
            if (speedWas >= 0) mc.options.glintSpeed().set(speedWas);
            if (strengthWas >= 0) mc.options.glintStrength().set(strengthWas);
        });
    }

    private void applyOptions() {
        if (!isEnabled()) return;
        Minecraft mc = Minecraft.getInstance();
        mc.options.glintSpeed().set(speed.get() / 100.0);
        mc.options.glintStrength().set(strength.get() / 100.0);
    }

    private void apply() {
        if (!isEnabled()) return;
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            tint(mc, ITEM, item.get());
            tint(mc, ARMOR, armor.get());
        });
    }

    /** Ванильная текстура блеска → яркость каждого пикселя × выбранный цвет → своя текстура под тем же id. */
    private static void tint(Minecraft mc, Identifier id, int argb) {
        try (var in = mc.getResourceManager().open(id)) {
            NativeImage src = NativeImage.read(in);
            int w = src.getWidth(), h = src.getHeight();
            NativeImage out = new NativeImage(w, h, false);
            int cr = argb >> 16 & 255, cg = argb >> 8 & 255, cb = argb & 255;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int p = src.getPixel(x, y);
                    int r = p >> 16 & 255, g = p >> 8 & 255, b = p & 255;
                    float l = Math.max(r, Math.max(g, b)) / 255f;
                    out.setPixel(x, y, (p & 0xFF000000) | Math.round(cr * l) << 16 | Math.round(cg * l) << 8 | Math.round(cb * l));
                }
            }
            src.close();
            DynamicTexture tex = new DynamicTexture(() -> "mraz1visual glint", out);
            // как у ванили: повтор и сглаживание (блеск скользит по предмету)
            ((dev.mraz1visual.mixin.AbstractTextureAccessor) tex).mraz1visual$setSampler(
                    com.mojang.blaze3d.systems.RenderSystem.getSamplerCache().getRepeat(com.mojang.blaze3d.textures.FilterMode.LINEAR));
            mc.getTextureManager().register(id, tex);
        } catch (Exception e) {
            ModuleManager.LOG.warn("Enchant Glint: не удалось перекрасить {}", id, e);
        }
    }
}

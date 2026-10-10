package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.setting.BoolSetting;
import dev.mraz1visual.setting.ColorSetting;
import dev.mraz1visual.setting.EnumSetting;
import dev.mraz1visual.setting.SliderSetting;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Свой прицел вместо ванильного. Выключен — ванильный прицел как обычно. */
public final class Crosshair extends Module {
    private static Crosshair instance;

    final EnumSetting style = add(new EnumSetting("style", "Вид", 0, "cross", "Крест", "dot", "Точка", "corners", "Уголки"));
    final SliderSetting size = add(new SliderSetting("size", "Длина", 4, 1, 12, 1, " px"));
    final SliderSetting gap = add(new SliderSetting("gap", "Зазор", 2, 0, 8, 1, " px"));
    final SliderSetting thickness = add(new SliderSetting("thickness", "Толщина", 1, 1, 4, 1, " px"));
    final ColorSetting color = add(new ColorSetting("color", "Цвет", 0xFFFFFFFF));
    final BoolSetting outline = add(new BoolSetting("outline", "Обводка", true));
    final BoolSetting cooldown = add(new BoolSetting("cooldown", "Полоса заряда удара", true));

    public Crosshair() {
        super("crosshair", "Crosshair", "Свой прицел: крест, точка или уголки.", Category.VISUALS);
        instance = this;
    }

    public static void register() {
        HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, original -> (g, delta) -> {
            Crosshair c = instance;
            if (c != null && c.isEnabled()) c.render(g);
            else original.render(g, delta);
        });
    }

    private void render(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !mc.options.getCameraType().isFirstPerson() || mc.options.hideGui) return;
        int cx = g.guiWidth() / 2, cy = g.guiHeight() / 2;
        int len = size.getI(), gp = gap.getI(), t = thickness.getI();
        int col = color.get();
        int o = outline.get() ? 0xA0000000 : 0;
        switch (style.getId()) {
            case "dot" -> rect(g, cx - t, cy - t, t * 2, t * 2, col, o);
            case "corners" -> {
                int r = gp + len;
                corner(g, cx - r, cy - r, len, t, 1, 1, col, o);
                corner(g, cx + r, cy - r, len, t, -1, 1, col, o);
                corner(g, cx - r, cy + r, len, t, 1, -1, col, o);
                corner(g, cx + r, cy + r, len, t, -1, -1, col, o);
            }
            default -> {
                int h = t / 2;
                rect(g, cx - gp - len, cy - h, len, t, col, o);
                rect(g, cx + gp + 1, cy - h, len, t, col, o);
                rect(g, cx - h, cy - gp - len, t, len, col, o);
                rect(g, cx - h, cy + gp + 1, t, len, col, o);
            }
        }
        if (cooldown.get()) {
            float s = mc.player.getAttackStrengthScale(0f);
            if (s < 1f) {
                int w = 16;
                g.fill(cx - w / 2, cy + gp + len + 4, cx + w / 2, cy + gp + len + 5, 0x60000000);
                g.fill(cx - w / 2, cy + gp + len + 4, cx - w / 2 + Math.round(w * s), cy + gp + len + 5, col);
            }
        }
    }

    private static void rect(GuiGraphics g, int x, int y, int w, int h, int c, int outline) {
        if (outline != 0) g.fill(x - 1, y - 1, x + w + 1, y + h + 1, outline);
        g.fill(x, y, x + w, y + h, c);
    }

    private static void corner(GuiGraphics g, int x, int y, int len, int t, int dx, int dy, int c, int o) {
        int hx = dx > 0 ? x : x - len + 1, vy = dy > 0 ? y : y - len + 1;
        rect(g, hx, dy > 0 ? y : y - t + 1, len, t, c, o);
        rect(g, dx > 0 ? x : x - t + 1, vy, t, len, c, o);
    }
}

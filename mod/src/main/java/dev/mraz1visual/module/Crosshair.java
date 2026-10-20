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

    final EnumSetting style = add(new EnumSetting("style", "Вид", 0, "ios", "Точка и кольцо (iOS)", "cross", "Крест", "dot", "Точка", "corners", "Уголки"));
    final BoolSetting critGlow = add(new BoolSetting("crit_glow", "Подсветка, когда крит готов", true));
    final ColorSetting critColor = add(new ColorSetting("crit_color", "Цвет крита", 0xFFFFC266));
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
        if (style.getId().equals("ios")) {
            ios(g, mc);
            return;
        }
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

    // ── iOS: точка и тонкое кольцо, сглаженные (SDF); крит готов — кольцо светится и чуть сжимается, после крита — вспышка ──

    private float critK;
    private long lastNs;
    private static long critAt;

    /** Из Hit Marker: был крит — короткая вспышка прицела (как в CritGlow). */
    public static void critHappened() {
        critAt = System.currentTimeMillis();
    }

    private void ios(GuiGraphics g, Minecraft mc) {
        long now = System.nanoTime();
        float dt = lastNs == 0 ? 0 : Math.min(0.1f, (now - lastNs) / 1e9f);
        lastNs = now;
        boolean ready = critGlow.get() && CritIndicator.critReady(mc.player);
        critK += ((ready ? 1f : 0f) - critK) * Math.min(1f, dt * 10f);
        float flash = Math.max(0f, 1f - (System.currentTimeMillis() - critAt) / 420f);
        flash = flash * flash;
        float cx = g.guiWidth() / 2f, cy = g.guiHeight() / 2f;
        int col = color.get();
        int cc = critColor.get();
        float r = 4.5f + gap.getF() * 0.5f - critK * 0.8f + flash * 2.5f;
        float t = Math.max(0.8f, thickness.getF() * 0.9f);
        // мягкая тень — прицел читается на любом фоне
        if (outline.get()) {
            dev.mraz1visual.ui.Sdf.shape(g, cx - r - 1, cy - r - 1, (r + 1) * 2, (r + 1) * 2, r + 1, dev.mraz1visual.ui.Sdf.RING, t + 1.2f, 0x55000000, 0x55000000);
            dev.mraz1visual.ui.Sdf.fill(g, cx - 1.9f, cy - 1.9f, 3.8f, 3.8f, 1.9f, 0x66000000);
        }
        int ringC = dev.mraz1visual.ui.Colors.lerp(dev.mraz1visual.ui.Colors.mulAlpha(col, 0.75f), cc, Math.max(critK, flash));
        dev.mraz1visual.ui.Sdf.shape(g, cx - r, cy - r, r * 2, r * 2, r, dev.mraz1visual.ui.Sdf.RING, t, ringC, ringC);
        if (critK > 0.02f || flash > 0.02f) {
            // свечение вокруг кольца
            float k = Math.max(critK * 0.6f, flash);
            dev.mraz1visual.ui.Sdf.shape(g, cx - r, cy - r, r * 2, r * 2, r, dev.mraz1visual.ui.Sdf.SHADOW, 4f, dev.mraz1visual.ui.Colors.mulAlpha(cc, 0.45f * k), dev.mraz1visual.ui.Colors.mulAlpha(cc, 0.45f * k));
        }
        dev.mraz1visual.ui.Sdf.fill(g, cx - 1.25f, cy - 1.25f, 2.5f, 2.5f, 1.25f, col);
        if (cooldown.get()) {
            float s = mc.player.getAttackStrengthScale(0f);
            if (s < 1f) {
                float w = 14f, y = cy + r + 4f;
                dev.mraz1visual.ui.Sdf.fill(g, cx - w / 2, y, w, 2f, 1f, 0x50000000);
                dev.mraz1visual.ui.Sdf.fill(g, cx - w / 2, y, Math.max(2f, w * s), 2f, 1f, dev.mraz1visual.ui.Colors.mulAlpha(col, 0.9f));
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

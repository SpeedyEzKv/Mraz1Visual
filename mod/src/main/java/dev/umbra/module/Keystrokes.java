package dev.umbra.module;

import dev.umbra.core.Category;
import dev.umbra.core.HudLayer;
import dev.umbra.core.Module;
import dev.umbra.setting.EnumSetting;
import dev.umbra.ui.Anim;
import dev.umbra.ui.Colors;
import dev.umbra.ui.Fonts;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Нажатые клавиши движения и кнопки мыши с CPS. */
public final class Keystrokes extends Module implements HudLayer {
    private static final String[] CPS = new String[60];

    static {
        for (int i = 0; i < CPS.length; i++) CPS[i] = i + " CPS";
    }

    final EnumSetting corner = add(new EnumSetting("corner", "Угол", 0, "bl", "Слева снизу", "tl", "Слева сверху", "tr", "Справа сверху"));

    private final float[] anim = new float[7];
    private final long[] lmb = new long[64], rmb = new long[64];
    private int lHead, rHead;
    private boolean lWas, rWas;
    private long last;

    public Keystrokes() {
        super("keystrokes", "Keystrokes", "WASD, пробел и клики с CPS на экране.", Category.HUD);
    }

    @Override
    public void renderHud(GuiGraphics g, float pt) {
        Minecraft mc = Minecraft.getInstance();
        long now = System.nanoTime();
        float dt = last == 0 ? 0 : Math.min(0.1f, (now - last) / 1e9f);
        last = now;
        long ms = System.currentTimeMillis();

        boolean l = mc.options.keyAttack.isDown(), r = mc.options.keyUse.isDown();
        if (l && !lWas) lmb[lHead++ & 63] = ms;
        if (r && !rWas) rmb[rHead++ & 63] = ms;
        lWas = l;
        rWas = r;

        int s = 22, gap = 2, total = s * 3 + gap * 2, hgt = s * 2 + gap + 14 + gap + 18;
        int x, y;
        switch (corner.getId()) {
            case "tl" -> { x = 6; y = 6; }
            case "tr" -> { x = g.guiWidth() - total - 6; y = 30; }
            default -> { x = 6; y = g.guiHeight() - hgt - 6; }
        }
        key(g, mc, 0, "W", mc.options.keyUp, x + s + gap, y, s, s, dt);
        key(g, mc, 1, "A", mc.options.keyLeft, x, y + s + gap, s, s, dt);
        key(g, mc, 2, "S", mc.options.keyDown, x + s + gap, y + s + gap, s, s, dt);
        key(g, mc, 3, "D", mc.options.keyRight, x + (s + gap) * 2, y + s + gap, s, s, dt);
        int yy = y + (s + gap) * 2;
        key(g, mc, 4, "———", mc.options.keyJump, x, yy, total, 14, dt);
        yy += 14 + gap;
        int half = (total - gap) / 2;
        key(g, mc, 5, CPS[Math.min(CPS.length - 1, count(lmb, ms))], mc.options.keyAttack, x, yy, half, 18, dt);
        key(g, mc, 6, CPS[Math.min(CPS.length - 1, count(rmb, ms))], mc.options.keyUse, x + half + gap, yy, half, 18, dt);
    }

    private static int count(long[] buf, long now) {
        int n = 0;
        for (long t : buf) if (now - t < 1000) n++;
        return n;
    }

    private void key(GuiGraphics g, Minecraft mc, int i, String label, KeyMapping km, int x, int y, int w, int h, float dt) {
        anim[i] = Anim.to(anim[i], km.isDown() ? 1 : 0, dt);
        g.fill(x, y, x + w, y + h, Colors.lerp(0xA00C0C12, 0xD0E6E4FF, anim[i]));
        int tc = Colors.lerp(0xFFE9E9F1, 0xFF0C0C12, anim[i]);
        int tw = mc.font.width(Fonts.of(label, Fonts.Kind.BOLD));
        g.drawString(mc.font, Fonts.of(label, Fonts.Kind.BOLD), x + (w - tw) / 2, y + (h - 8) / 2, tc, false);
    }
}

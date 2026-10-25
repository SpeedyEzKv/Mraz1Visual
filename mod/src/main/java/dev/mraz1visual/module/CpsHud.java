package dev.mraz1visual.module;

import dev.mraz1visual.setting.BoolSetting;
import net.minecraft.client.Minecraft;

/** Клики в секунду: левая | правая кнопка мыши. */
public final class CpsHud extends TextHud {
    final BoolSetting right = add(new BoolSetting("right", "Показывать ПКМ", true));

    private final long[] lmb = new long[64], rmb = new long[64];
    private int lHead, rHead, last = -1;
    private boolean lWas, rWas;

    public CpsHud() {
        super("hud_cps", "CPS", "Клики в секунду левой и правой кнопкой.", 0.0, 0.14);
        labels[0] = "CPS";
    }

    @Override
    protected void update(Minecraft mc) {
        long now = System.currentTimeMillis();
        boolean l = mc.options.keyAttack.isDown(), r = mc.options.keyUse.isDown();
        if (l && !lWas) lmb[lHead++ & 63] = now;
        if (r && !rWas) rmb[rHead++ & 63] = now;
        lWas = l;
        rWas = r;
        int lc = count(lmb, now), rc = right.get() ? count(rmb, now) : -1;
        int key = lc * 1000 + rc;
        if (key != last) {
            last = key;
            values[0] = rc < 0 ? Integer.toString(lc) : lc + " | " + rc;
        }
    }

    static int count(long[] buf, long now) {
        int n = 0;
        for (long t : buf) if (now - t < 1000) n++;
        return n;
    }
}

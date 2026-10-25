package dev.mraz1visual.module;

import net.minecraft.client.Minecraft;

/** Активный ресурспак (верхний из подключённых). Список перечитывается раз в 2 секунды. */
public final class PackHud extends TextHud {
    private long at;
    private boolean has;

    public PackHud() {
        super("hud_pack", "Pack Display", "Какой ресурспак сейчас включён.", 1.0, 0.38);
        labels[0] = "Пак";
    }

    @Override
    protected void update(Minecraft mc) {
        long now = System.currentTimeMillis();
        if (now - at < 2000) return;
        at = now;
        String top = null;
        for (String id : mc.getResourcePackRepository().getSelectedIds()) {
            if (id.startsWith("file/")) top = id;
        }
        has = top != null;
        if (!has) return;
        String n = top.substring(5);
        if (n.endsWith(".zip")) n = n.substring(0, n.length() - 4);
        if (!n.equals(values[0])) values[0] = n;
    }

    @Override
    protected boolean visibleNow() { return has; }
}

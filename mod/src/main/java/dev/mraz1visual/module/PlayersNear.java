package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.HudLayer;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.render.HudKit;
import dev.mraz1visual.render.HudPos;
import dev.mraz1visual.setting.SliderSetting;
import dev.mraz1visual.ui.Draw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import java.util.ArrayList;
import java.util.Comparator;

public final class PlayersNear extends Module implements HudLayer {
    private record Row(String name, double distance) {}
    private final ArrayList<Row> rows = new ArrayList<>();
    private final SliderSetting radius = add(new SliderSetting("radius", "Радиус", 32, 4, 96, 1, " м"));
    private final HudPos position = hudPos(.06, .32);

    public PlayersNear() {
        super("players_near", "Players Near", "Игроки поблизости и расстояние до них.", Category.HUD);
    }

    @Override public void onTick(Minecraft mc) {
        rows.clear();
        if (mc.player == null || mc.level == null) return;
        for (var other : mc.level.players()) {
            if (other == mc.player || other.isSpectator() || other.isInvisibleTo(mc.player)) continue;
            double distance = mc.player.distanceTo(other);
            if (distance <= radius.get()) rows.add(new Row(StreamerMode.filter(other.getName().getString()), distance));
        }
        rows.sort(Comparator.comparingDouble(Row::distance).thenComparing(Row::name));
    }

    @Override public void renderHud(GuiGraphics g, float pt) {
        if (Minecraft.getInstance().player == null) return;
        int count = Math.min(8, rows.size());
        int width = 134, height = 19 + 13 * Math.max(1, count);
        position.begin(g, width, height);
        HudKit.card(g, 0, 0, width, height, 1);
        HudKit.title(g, "Players Near", 7, 5, 1);
        if (count == 0) HudKit.text(g, "Рядом никого", 7, 19, 0xFFAAAABD);
        for (int i = 0; i < count; i++) {
            Row row = rows.get(i);
            String distance = Math.round(row.distance()) + " м";
            int x = width - 7 - HudKit.width(distance);
            HudKit.text(g, Draw.ellipsize(row.name(), x - 13), 7, 19 + 13 * i, 0xFFF3F2FA);
            HudKit.text(g, distance, x, 19 + 13 * i, 0xFFBCACFF);
        }
        position.end(g);
    }
}

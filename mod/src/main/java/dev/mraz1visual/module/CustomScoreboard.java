package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.render.HudKit;
import dev.mraz1visual.setting.BoolSetting;
import dev.mraz1visual.setting.SliderSetting;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Табло справа в стиле клиента: тёмная карточка, без красных цифр, можно сдвинуть. */
public final class CustomScoreboard extends Module {
    private static CustomScoreboard instance;
    private static final Comparator<PlayerScoreEntry> ORDER = Comparator.<PlayerScoreEntry>comparingInt(PlayerScoreEntry::value)
        .reversed().thenComparing(PlayerScoreEntry::owner, String.CASE_INSENSITIVE_ORDER);

    final BoolSetting hideNumbers = add(new BoolSetting("hide_numbers", "Скрыть красные цифры", true));
    final BoolSetting card = add(new BoolSetting("card", "Карточка вместо серого фона", true));
    final SliderSetting opacity = add(new SliderSetting("opacity", "Прозрачность фона", 90, 0, 100, 5, " %"));
    final SliderSetting offsetY = add(new SliderSetting("offset_y", "Сдвиг по высоте", 0, -150, 150, 1, " px"));
    final BoolSetting shadow = add(new BoolSetting("shadow", "Тень текста", false));

    public CustomScoreboard() {
        super("custom_scoreboard", "Custom Scoreboard", "Красивое табло справа: без цифр, своя подложка.", Category.VISUALS);
        instance = this;
    }

    /** Вызывать после NoRender.register(): оборачивает уже заменённый элемент. */
    public static void register() {
        HudElementRegistry.replaceElement(VanillaHudElements.SCOREBOARD, original -> (g, delta) -> {
            CustomScoreboard c = instance;
            if (c == null || !c.isEnabled()) { original.render(g, delta); return; }
            if (NoRender.hideScoreboard()) return;
            c.render(g);
        });
    }

    private void render(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.options.hideGui) return;
        Scoreboard sb = mc.level.getScoreboard();
        Objective obj = null;
        PlayerTeam team = sb.getPlayersTeam(mc.player.getScoreboardName());
        if (team != null) {
            //? if >=26.2 {
            /*DisplaySlot ds = team.getColor().map(c -> {
                try {
                    return DisplaySlot.valueOf("TEAM_" + c.name());
                } catch (IllegalArgumentException e) {
                    return null;
                }
            }).orElse(null);
            *///?} else {
            DisplaySlot ds = DisplaySlot.teamColorToSlot(team.getColor());
            //?}
            if (ds != null) obj = sb.getDisplayObjective(ds);
        }
        if (obj == null) obj = sb.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (obj == null) return;

        NumberFormat fmt = obj.numberFormatOrDefault(StyledFormat.SIDEBAR_DEFAULT);
        List<PlayerScoreEntry> entries = new ArrayList<>();
        for (PlayerScoreEntry e : sb.listPlayerScores(obj)) if (!e.isHidden()) entries.add(e);
        entries.sort(ORDER);
        if (entries.size() > 15) entries = entries.subList(0, 15);

        Font font = mc.font;
        int n = entries.size();
        Component[] names = new Component[n];
        Component[] scores = new Component[n];
        Component title = obj.getDisplayName();
        int w = font.width(title);
        for (int i = 0; i < n; i++) {
            PlayerScoreEntry e = entries.get(i);
            names[i] = PlayerTeam.formatNameForTeam(sb.getPlayersTeam(e.owner()), e.ownerName());
            scores[i] = hideNumbers.get() ? null : e.formatValue(fmt);
            int lw = font.width(names[i]) + (scores[i] == null ? 0 : 6 + font.width(scores[i]));
            w = Math.max(w, lw);
        }

        int lineH = 9, pad = 5;
        int boxW = w + pad * 2;
        int boxH = pad * 2 + lineH + 3 + n * lineH;
        int x = g.guiWidth() - boxW - 3;
        int y = g.guiHeight() / 2 - boxH / 2 + offsetY.getI();
        y = Math.max(2, Math.min(g.guiHeight() - boxH - 2, y));

        float a = opacity.getF() / 100f;
        if (a > 0) {
            if (card.get()) HudKit.card(g, x, y, boxW, boxH, a);
            else g.fill(x, y, x + boxW, y + boxH, ((int) (a * 0x80) << 24));
        }

        boolean sh = shadow.get();
        g.drawString(font, title, x + (boxW - font.width(title)) / 2, y + pad, 0xFFFFFFFF, sh);
        int ty = y + pad + lineH + 3;
        g.fill(x + pad, ty - 2, x + boxW - pad, ty - 1, 0x22FFFFFF);
        for (int i = 0; i < n; i++) {
            int ly = ty + i * lineH;
            g.drawString(font, names[i], x + pad, ly, 0xFFFFFFFF, sh);
            if (scores[i] != null) g.drawString(font, scores[i], x + boxW - pad - font.width(scores[i]), ly, 0xFFFF5555, sh);
        }
    }
}

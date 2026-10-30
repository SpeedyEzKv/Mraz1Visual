package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.HudLayer;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.render.HudPos;
import dev.mraz1visual.render.HudStyle;
import dev.mraz1visual.setting.ActionSetting;
import dev.mraz1visual.ui.Colors;
import dev.mraz1visual.ui.Fonts;
import dev.mraz1visual.ui.Sdf;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AutoGPS: к сообщениям чата с координатами добавляется кнопка «[GPS]». Клик подставляет в поле чата
 * служебную строку «#gps x y z» — клиент перехватывает её сам (на сервер ничего не уходит), ставит точку,
 * и сверху появляется стрелка-компас с расстоянием.
 */
public final class AutoGps extends Module implements HudLayer {
    private static AutoGps instance;
    private static final String PREFIX = "#gps ";
    /** x y z или x, y, z (y — до трёх цифр, чтобы не ловить случайные числа). */
    private static final Pattern COORDS = Pattern.compile("(-?\\d{1,7})[ ,;/]+(-?\\d{1,3})[ ,;/]+(-?\\d{1,7})");

    final HudPos pos = hudPos(0.5, 0.0);
    final ActionSetting reset = add(new ActionSetting("reset", "Точка GPS", "Сбросить точку", AutoGps::clear));

    private static boolean has;
    private static int tx, ty, tz;
    private int lastDist = -1;
    private String distText = "";

    public AutoGps() {
        super("auto_gps", "AutoGPS", "Координаты в чате кликабельны: точка и стрелка-компас с расстоянием.", Category.MISC);
        instance = this;
        markNew();
    }

    private static void clear() {
        has = false;
    }

    /** Входящее сообщение: если есть координаты — дописать кликабельную кнопку. */
    public static Component decorate(Component message) {
        AutoGps g = instance;
        if (g == null || !g.isEnabled() || message == null) return message;
        Matcher m = COORDS.matcher(message.getString());
        if (!m.find()) return message;
        String cmd = PREFIX + m.group(1) + " " + m.group(2) + " " + m.group(3);
        MutableComponent chip = Component.literal(" [GPS]").withStyle(s -> s.withColor(ChatFormatting.AQUA)
                .withClickEvent(new ClickEvent.SuggestCommand(cmd))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("Поставить точку GPS: " + m.group(1) + " " + m.group(2) + " " + m.group(3)))));
        return message.copy().append(chip);
    }

    @Override
    public void onTick(Minecraft mc) {
        // клик по [GPS] кладёт «#gps x y z» в поле чата — забираем и очищаем
        if (!(mc.screen instanceof ChatScreen cs)) return;
        var input = ((dev.mraz1visual.mixin.ChatScreenAccessor) cs).mraz1visual$input();
        if (input == null) return;
        String v = input.getValue();
        if (!v.startsWith(PREFIX)) return;
        String[] p = v.substring(PREFIX.length()).trim().split(" ");
        if (p.length == 3) {
            try {
                tx = Integer.parseInt(p[0]);
                ty = Integer.parseInt(p[1]);
                tz = Integer.parseInt(p[2]);
                has = true;
                dev.mraz1visual.compat.Mc.message(mc.player, Component.literal("§bGPS: §f" + tx + " " + ty + " " + tz), true);
            } catch (NumberFormatException ignored) {
            }
        }
        input.setValue("");
        mc.setScreen(null);
    }

    @Override
    public void renderHud(GuiGraphics g, float pt) {
        if (!has) return;
        LocalPlayer p = Minecraft.getInstance().player;
        double dx = tx + 0.5 - p.getX(), dz = tz + 0.5 - p.getZ();
        int dist = (int) Math.round(Math.sqrt(dx * dx + dz * dz + (ty - p.getY()) * (ty - p.getY())));
        if (dist != lastDist) {
            lastDist = dist;
            distText = dist < 3 ? "Ты на месте" : dist + " м";
        }
        int w = Math.max(70, Fonts.width(distText, Fonts.Kind.BOLD) + 40), h = 22;
        pos.begin(g, w, h);
        HudStyle.panel(g, 0, 0, w, h, 11, 1f);
        // стрелка: угол к точке относительно взгляда
        float yaw = p.getViewYRot(pt);
        double ang = Math.atan2(dz, dx) - Math.toRadians(yaw + 90);
        g.pose().pushMatrix();
        g.pose().translate(13, 11);
        g.pose().rotate((float) ang);
        for (int i = 0; i < 6; i++) {
            float k = i / 5f;
            Sdf.fill(g, -5 + k * 10 - 1.2f, -(1 - k) * 4.5f - 1.2f, 2.4f, (1 - k) * 9 + 2.4f, 1.2f, Colors.lerp(HudStyle.GRAD_A, HudStyle.GRAD_B, k));
        }
        g.pose().popMatrix();
        Fonts.draw(g, distText, Fonts.Kind.BOLD, 26, 7, HudStyle.TEXT);
        pos.end(g);
    }
}

package dev.umbra.module;

import dev.umbra.core.Category;
import dev.umbra.core.HudLayer;
import dev.umbra.core.Module;
import dev.umbra.setting.BoolSetting;
import dev.umbra.setting.EnumSetting;
import dev.umbra.ui.Fonts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * Информационный HUD в стиле клиента: FPS и пинг сверху справа, координаты, броня.
 * Строки пересобираются только когда меняются числа.
 */
public final class Hud extends Module implements HudLayer {
    private static final int LABEL = 0xB4A0A0B8, VALUE = 0xFFE9E9F1;
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    final BoolSetting fps = add(new BoolSetting("fps", "Кадры", true).group("Показывать"));
    final BoolSetting ping = add(new BoolSetting("ping", "Пинг", true).group("Показывать"));
    final BoolSetting coords = add(new BoolSetting("coords", "Координаты", true).group("Показывать"));
    final BoolSetting armor = add(new BoolSetting("armor", "Броня", true).group("Показывать"));
    final EnumSetting armorSide = add(new EnumSetting("armor_side", "Броня", 0, "right", "Справа", "left", "Слева").group("Место"));

    private int lastFps = -1, lastPing = -2, lastX = Integer.MIN_VALUE, lastY, lastZ;
    private String fpsStr = "", pingStr = "", coordStr = "";

    public Hud() {
        super("hud", "HUD", "Кадры, пинг, координаты и броня на экране.", Category.HUD);
    }

    @Override
    public void renderHud(GuiGraphics g, float pt) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        int w = g.guiWidth(), h = g.guiHeight();

        // сверху справа: «кадров/с 144   LAT 32»
        int x = w - 6, y = 6;
        if (ping.get()) {
            int pv = latency(mc, p);
            if (pv != lastPing) {
                lastPing = pv;
                pingStr = pv < 0 ? "—" : Integer.toString(pv);
            }
            x = pair(g, "LAT", pingStr, x, y) - 12;
        }
        if (fps.get()) {
            int f = mc.getFps();
            if (f != lastFps) {
                lastFps = f;
                fpsStr = Integer.toString(f);
            }
            pair(g, "кадров/с", fpsStr, x, y);
        }

        if (coords.get()) {
            int bx = p.getBlockX(), by = p.getBlockY(), bz = p.getBlockZ();
            if (bx != lastX || by != lastY || bz != lastZ) {
                lastX = bx; lastY = by; lastZ = bz;
                coordStr = bx + "  " + by + "  " + bz;
            }
            pair(g, "XYZ", coordStr, w - 6, 18);
        }

        if (armor.get()) {
            boolean right = armorSide.get() == 0;
            int ax = right ? w / 2 + 98 : w / 2 - 98 - 18;
            int ay = h - 20;
            for (int i = ARMOR.length - 1; i >= 0; i--) {
                ItemStack s = p.getItemBySlot(ARMOR[i]);
                if (s.isEmpty()) continue;
                g.renderItem(s, ax, ay);
                g.renderItemDecorations(mc.font, s, ax, ay);
                ax += right ? 18 : -18;
            }
        }
    }

    /** Пара «подпись значение», выровненная по правому краю. Возвращает левый край. */
    private static int pair(GuiGraphics g, String label, String value, int right, int y) {
        Minecraft mc = Minecraft.getInstance();
        int vw = mc.font.width(Fonts.of(value, Fonts.Kind.BOLD));
        int lw = mc.font.width(Fonts.of(label, Fonts.Kind.TEXT));
        g.drawString(mc.font, Fonts.of(value, Fonts.Kind.BOLD), right - vw, y, VALUE, true);
        g.drawString(mc.font, Fonts.of(label, Fonts.Kind.TEXT), right - vw - 5 - lw, y, LABEL, true);
        return right - vw - 5 - lw;
    }

    private static int latency(Minecraft mc, LocalPlayer p) {
        if (mc.getConnection() == null) return -1;
        PlayerInfo info = mc.getConnection().getPlayerInfo(p.getUUID());
        return info == null ? -1 : info.getLatency();
    }
}

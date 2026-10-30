package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.setting.BoolSetting;
import dev.mraz1visual.setting.SliderSetting;
import dev.mraz1visual.ui.Mraz1VisualScreen;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/**
 * Плавные переходы интерфейса: инвентарь и сундуки «выплывают», чат выезжает снизу,
 * список игроков (Tab) раскрывается, меню клиента появляются мягко.
 * Фон экрана не трогаем — двигается только содержимое.
 */
public final class UiAnimations extends Module {
    private static UiAnimations instance;
    private static final int NONE = 0, ZOOM = 1, SLIDE = 2;

    final BoolSetting containers = add(new BoolSetting("containers", "Инвентарь и сундуки", true));
    final BoolSetting chat = add(new BoolSetting("chat", "Чат выезжает снизу", true));
    final BoolSetting tab = add(new BoolSetting("tab", "Tab раскрывается", true));
    final BoolSetting menus = add(new BoolSetting("menus", "Окна клиента (оверлей, стол, браузер)", true));
    final BoolSetting hud = add(new BoolSetting("hud", "HUD появляется после меню и входа в мир", true));
    final SliderSetting duration = add(new SliderSetting("duration", "Длительность", 0.22, 0.1, 0.6, 0.02, " с"));

    private static Screen lastScreen;
    private static long screenStart;
    private static boolean pushed;
    private static boolean tabWas;
    private static long tabStart;

    public UiAnimations() {
        super("ui_animations", "UI Animations", "Плавное появление инвентаря, чата, Tab и окон клиента.", Category.PERFORMANCE);
        instance = this;
        loadEnabled(true);
    }

    private int kindOf(Screen s) {
        if (s instanceof Mraz1VisualScreen) return NONE; // у главного окна своя анимация
        if (s instanceof AbstractContainerScreen<?>) return containers.get() ? ZOOM : NONE;
        if (s instanceof ChatScreen) return chat.get() ? SLIDE : NONE;
        String pkg = s.getClass().getName();
        if (pkg.startsWith("dev.mraz1visual.overlay") || pkg.startsWith("dev.mraz1visual.ui.desktop") || pkg.startsWith("dev.mraz1visual.browser")) {
            return menus.get() ? ZOOM : NONE;
        }
        return NONE;
    }

    private float progress(long start) {
        float t = (System.nanoTime() - start) / 1e9f / Math.max(0.05f, duration.getF());
        return Math.max(0f, Math.min(1f, t));
    }

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            // новый экран — анимация с начала; повторный init того же экрана (смена размера окна) — без неё
            if (screen != lastScreen) {
                lastScreen = screen;
                screenStart = System.nanoTime();
            }
            ScreenEvents.afterBackground(screen).register((s, g, mx, my, delta) -> push(s, g));
            ScreenEvents.afterRender(screen).register((s, g, mx, my, delta) -> pop(g));
        });

        HudElementRegistry.replaceElement(VanillaHudElements.PLAYER_LIST, original -> (g, delta) -> {
            UiAnimations u = instance;
            Minecraft mc = Minecraft.getInstance();
            boolean down = mc.options.keyPlayerList.isDown();
            if (down && !tabWas) tabStart = System.nanoTime();
            tabWas = down;
            if (u == null || !u.isEnabled() || !u.tab.get() || !down) {
                original.render(g, delta);
                return;
            }
            float t = u.progress(tabStart);
            if (t >= 1f) {
                original.render(g, delta);
                return;
            }
            float e = easeBack(t);
            float cx = g.guiWidth() / 2f, cy = 10f;
            g.pose().pushMatrix();
            g.pose().translate(cx, cy);
            g.pose().scale(0.9f + 0.1f * e, 0.55f + 0.45f * e);
            g.pose().translate(-cx, -cy);
            original.render(g, delta);
            g.pose().popMatrix();
        });
    }

    private static void push(Screen s, GuiGraphics g) {
        UiAnimations u = instance;
        pushed = false;
        if (u == null || !u.isEnabled() || s != lastScreen) return;
        int kind = u.kindOf(s);
        if (kind == NONE) return;
        float t = u.progress(screenStart);
        if (t >= 1f) return;
        g.pose().pushMatrix();
        pushed = true;
        if (kind == SLIDE) {
            g.pose().translate(0, (1f - easeOut(t)) * 16f);
        } else {
            float sc = 0.95f + 0.05f * easeOut(t);
            float cx = s.width / 2f, cy = s.height / 2f;
            g.pose().translate(cx, cy);
            g.pose().scale(sc, sc);
            g.pose().translate(-cx, -cy);
        }
    }

    private static boolean hadScreen = true;
    private static long hudStart;

    /** Масштаб HUD-слоя клиента: после закрытия экрана или входа в мир — 0.95 → 1 с ease-out. */
    public static float hudScale(Minecraft mc) {
        UiAnimations u = instance;
        boolean screen = mc.screen != null || mc.level == null;
        if (hadScreen && !screen) hudStart = System.nanoTime();
        hadScreen = screen;
        if (u == null || !u.isEnabled() || !u.hud.get()) return 1f;
        float t = u.progress(hudStart);
        return t >= 1f ? 1f : 0.95f + 0.05f * easeOut(t);
    }

    private static void pop(GuiGraphics g) {
        if (!pushed) return;
        pushed = false;
        g.pose().popMatrix();
    }

    private static float easeOut(float t) {
        return 1f - (float) Math.pow(1f - t, 3);
    }

    private static float easeBack(float t) {
        float c1 = 1.4f, c3 = c1 + 1f, x = t - 1f;
        return 1f + c3 * x * x * x + c1 * x * x;
    }
}

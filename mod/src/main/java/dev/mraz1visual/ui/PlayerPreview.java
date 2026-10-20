package dev.mraz1visual.ui;

import dev.mraz1visual.render.PlayerLayer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/**
 * Живое превью в меню клиента: модель своего игрока с крыльями, мечом в руке и питомцем.
 * Модель крутится мышкой (с инерцией), без мыши — медленно поворачивается сама.
 */
public final class PlayerPreview {
    /** Поворот для ближайшей отрисовки модели (действует только внутри {@link #render}). */
    private static boolean active;
    private static float activeYaw;

    /** Из миксина InventoryScreen: метка «меню» для слоя косметики и поворот модели превью. */
    public static EntityRenderState tag(EntityRenderState s) {
        s.setData(PlayerLayer.GUI, Boolean.TRUE);
        if (active && s instanceof LivingEntityRenderState l) l.bodyRot += activeYaw;
        return s;
    }

    // поворот модели (градусы), скорость после броска и состояние перетаскивания
    private float yaw = 25f, vel;
    private boolean dragging;
    private double lastX;
    private long lastDragAt;

    /**
     * Рисует модель в прямоугольнике (единицы окна меню, k — масштаб окна → GUI).
     * false — игрока нет (меню без мира), рисовать нечего. showModel = false — только обновить вращение
     * (во время анимации окна модель не рисуем: картинка-в-картинке не знает о масштабе окна).
     */
    public boolean render(GuiGraphics g, float k, int x, int y, int w, int h, boolean showModel, float dt) {
        if (!dragging) {
            if (Math.abs(vel) > 2f) {
                // бросок мышкой — плавно затухает
                yaw += vel * dt;
                vel *= (float) Math.exp(-dt * 2.5f);
            } else {
                vel = 0;
                yaw += dt * 12f; // само медленно поворачивается
            }
        }
        yaw %= 360f;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) return false;
        if (!showModel) return true;
        int x0 = Math.round(x * k), y0 = Math.round(y * k), x1 = Math.round((x + w) * k), y1 = Math.round((y + h) * k);
        int size = Math.max(10, Math.round((y1 - y0) * 0.36f));
        active = true;
        activeYaw = yaw;
        try {
            // мышь — в центр: модель смотрит прямо на зрителя, поворот — только наш
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, x0, y0, x1, y1, size, 0.0625f, (x0 + x1) / 2f, (y0 + y1) / 2f, p);
        } finally {
            active = false;
        }
        return true;
    }

    public void dragStart(double mx) {
        dragging = true;
        lastX = mx;
        lastDragAt = System.nanoTime();
        vel = 0;
    }

    public void drag(double mx) {
        long now = System.nanoTime();
        float d = (float) (mx - lastX) * 1.8f;
        yaw += d;
        float dt = Math.max(1e-3f, (now - lastDragAt) / 1e9f);
        vel = vel * 0.6f + d / dt * 0.4f;
        lastX = mx;
        lastDragAt = now;
    }

    public void dragEnd() {
        dragging = false;
        // отпустили, не двигая мышь — без броска
        if ((System.nanoTime() - lastDragAt) > 80_000_000L) vel = 0;
        vel = Math.max(-720f, Math.min(720f, vel));
    }
}

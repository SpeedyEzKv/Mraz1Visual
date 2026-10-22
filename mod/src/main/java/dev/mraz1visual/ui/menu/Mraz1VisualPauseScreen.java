package dev.mraz1visual.ui.menu;

import dev.mraz1visual.core.ModuleManager;
import dev.mraz1visual.music.MediaClient;
import dev.mraz1visual.music.MusicWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Меню паузы (Esc) в стиле клиента. */
public final class Mraz1VisualPauseScreen extends MenuBase {
    private final MusicWidget music = new MusicWidget();
    private Item lanItem;
    private int left;

    public Mraz1VisualPauseScreen() {
        super("Пауза");
        item("ВЕРНУТЬСЯ В ИГРУ", () -> minecraft.setScreen(null));
        itemAfterGap("ДОСТИЖЕНИЯ", () -> {
            if (minecraft.player != null) minecraft.setScreen(new AdvancementsScreen(minecraft.player.connection.getAdvancements(), this));
        });
        item("СТАТИСТИКА", () -> minecraft.setScreen(new Mraz1VisualStatsScreen(this)));
        item("НАСТРОЙКИ", () -> minecraft.setScreen(new Mraz1VisualOptionsScreen(this)));
        IntegratedServer sp = net.minecraft.client.Minecraft.getInstance().getSingleplayerServer();
        if (sp != null && !sp.isPublished()) lanItem = item("ОТКРЫТЬ ДЛЯ СЕТИ", () -> minecraft.setScreen(new Mraz1VisualLanScreen(this)));
        boolean local = net.minecraft.client.Minecraft.getInstance().isLocalServer();
        itemAfterGap(local ? "СОХРАНИТЬ И ВЫЙТИ" : "ОТКЛЮЧИТЬСЯ", this::quit);
    }

    @Override protected float sunScreenX() { return 0.66f; }
    @Override protected float sunScreenY() { return 0.40f; }

    @Override
    protected void onLayout() {
        left = Math.max(24, Math.round(vw * 0.056f));
        layoutItems(left, Math.round(vh * 0.19f));
    }

    private String place() {
        IntegratedServer sp = minecraft.getSingleplayerServer();
        if (sp != null) return sp.getWorldData().getLevelName();
        if (minecraft.getCurrentServer() != null) return minecraft.getCurrentServer().name;
        return "";
    }

    private int musicX() { return vw - 36 - MusicWidget.W; }
    private int musicY() { return vh - 36 - MusicWidget.H; }

    @Override
    protected void renderMenu(double mx, double my, float dt) {
        renderItems(mx, my, dt);
        renderBrand(left);
        renderAccount(place());
        if (dev.mraz1visual.music.NowPlaying.available()) music.render(d, musicX(), musicY(), fade, true, mx, my);
    }

    /**
     * Выход делаем руками ванильного меню паузы: оно само сохранит мир, закроет соединение
     * и спросит про неотправленную жалобу. Так поведение точно совпадает с игрой.
     */
    private void quit() {
        try {
            PauseScreen vanilla = new PauseScreen(true);
            vanilla.init(width, height);
            String toMenu = Component.translatable("menu.returnToMenu").getString();
            String disconnect = Component.translatable("menu.disconnect").getString();
            for (var child : vanilla.children()) {
                if (child instanceof Button b) {
                    String msg = b.getMessage().getString();
                    if (msg.equals(toMenu) || msg.equals(disconnect)) {
                        b.onPress(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0));
                        return;
                    }
                }
            }
        } catch (Throwable t) {
            ModuleManager.LOG.error("Mraz1Visual pause: quit failed", t);
        }
        // запасной путь — ванильное меню паузы
        Mraz1VisualPauseScreen.bypass = true;
        minecraft.setScreen(new PauseScreen(true));
        Mraz1VisualPauseScreen.bypass = false;
    }

    static boolean bypass;

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        if (e.button() != 0) return false;
        double mx = e.x() / k, my = e.y() / k;
        if (dev.mraz1visual.music.NowPlaying.available() && music.click(musicX(), musicY(), mx, my)) return true;
        return clickItems(mx, my);
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (e.key() == GLFW.GLFW_KEY_ESCAPE) {
            minecraft.setScreen(null);
            return true;
        }
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}

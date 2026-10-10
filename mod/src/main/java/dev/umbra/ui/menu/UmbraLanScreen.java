package dev.umbra.ui.menu;

import dev.umbra.setting.BoolSetting;
import dev.umbra.setting.EnumSetting;
import dev.umbra.ui.widget.CycleWidget;
import dev.umbra.ui.widget.TextInput;
import dev.umbra.ui.widget.ToggleWidget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.HttpUtil;
import net.minecraft.world.level.GameType;
import org.lwjgl.glfw.GLFW;

/** «Мир в локальной сети»: режим игры, команды, порт. */
public final class UmbraLanScreen extends MenuBase {
    private static final GameType[] MODES = {GameType.SURVIVAL, GameType.CREATIVE, GameType.ADVENTURE, GameType.SPECTATOR};

    private final Screen parent;
    private final MenuForm form = new MenuForm();
    private final EnumSetting mode = new EnumSetting("mode", "Режим игры", 0,
            "survival", "Выживание", "creative", "Творческий", "adventure", "Приключение", "spectator", "Наблюдатель");
    private final BoolSetting commands = new BoolSetting("commands", "Использование команд", false);
    private final TextInput port;
    private int left;

    public UmbraLanScreen(Screen parent) {
        super("Мир в локальной сети");
        this.parent = parent;
        item("МИР В ЛОКАЛЬНОЙ СЕТИ", () -> {}).selected = true;
        itemAfterGap("ОТМЕНА", this::onClose);
        form.add(new CycleWidget(mode, form));
        form.add(new ToggleWidget(commands, form));
        port = form.field("Порт", "", 5);
        form.submit("ОТКРЫТЬ МИР ДЛЯ СЕТИ");
    }

    @Override protected float itemScale() { return 1.0f; }
    @Override protected int itemLine() { return 26; }
    @Override protected float sunScreenX() { return 0.66f; }
    @Override protected float sunScreenY() { return 0.02f; }

    @Override
    protected void init() {
        if (port.value().isEmpty()) {
            port.set(Integer.toString(HttpUtil.getAvailablePort()));
            if (minecraft.gameMode != null) {
                GameType cur = minecraft.gameMode.getPlayerMode();
                for (int i = 0; i < MODES.length; i++) if (MODES[i] == cur) mode.setSilently(i);
            }
            commands.setSilently(minecraft.player != null && minecraft.player.isCreative());
        }
        super.init();
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
    }

    @Override
    protected void onLayout() {
        left = Math.max(24, Math.round(vw * 0.056f));
        layoutItems(left, Math.round(vh * 0.34f));
        int px = Math.max(left + 260, Math.round(vw * 0.29f));
        form.layout(px, Math.round(vh * 0.40f), Math.min(300, vw - px - 40));
    }

    @Override
    protected void renderMenu(double mx, double my, float dt) {
        d.gradientV(0, Math.round(vh * 0.25f), vw, vh - Math.round(vh * 0.25f), 0x00000000, 0xB4000000);
        renderItems(mx, my, dt);
        renderBrand(left);
        form.render(d, mx, my, dt, fade, vw - Math.max(24, Math.round(vw * 0.056f)), vh - 88);
    }

    private void publish() {
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (server == null) {
            onClose();
            return;
        }
        int p;
        try {
            p = Integer.parseInt(port.value().trim());
        } catch (NumberFormatException e) {
            form.error = "Неверный порт";
            return;
        }
        if (p < 1024 || p > 65535 || !HttpUtil.isPortAvailable(p)) {
            form.error = "Порт занят или неверный";
            return;
        }
        boolean ok = server.publishServer(MODES[mode.get()], commands.get(), p);
        minecraft.gui.getChat().addMessage(ok
                ? Component.translatable("commands.publish.started", p)
                : Component.translatable("commands.publish.failed"));
        minecraft.updateTitle();
        minecraft.setScreen(null);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        double mx = e.x() / k, my = e.y() / k;
        if (e.button() == 0 && clickItems(mx, my)) return true;
        if (form.click(mx, my, e.button()) == 1) publish();
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        int key = e.key();
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            publish();
            return true;
        }
        return form.key(key, (e.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0, minecraft.keyboardHandler::getClipboard);
    }

    @Override
    public boolean charTyped(CharacterEvent e) {
        int cp = e.codepoint();
        if (cp < '0' || cp > '9') return true;
        return form.chr(cp);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}

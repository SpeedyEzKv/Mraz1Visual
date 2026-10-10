package dev.mraz1visual.ui.menu;

import dev.mraz1visual.setting.EnumSetting;
import dev.mraz1visual.ui.widget.CycleWidget;
import dev.mraz1visual.ui.widget.TextInput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

/** «Добавление сервера» / «Изменение сервера»: название, адрес, наборы ресурсов. */
public final class Mraz1VisualAddServerScreen extends MenuBase {
    private static final ServerData.ServerPackStatus[] PACKS = {
            ServerData.ServerPackStatus.PROMPT, ServerData.ServerPackStatus.ENABLED, ServerData.ServerPackStatus.DISABLED};

    private final Screen parent;
    private final ServerData editing;
    private final Consumer<ServerData> done;
    private final MenuForm form = new MenuForm();
    private final TextInput name, addr;
    private final EnumSetting packs = new EnumSetting("packs", "Наборы ресурсов", 0,
            "prompt", "Запрашивать", "enabled", "Включены", "disabled", "Отключены");
    private int left;

    /** @param editing null — новый сервер. */
    public Mraz1VisualAddServerScreen(Screen parent, ServerData editing, Consumer<ServerData> done) {
        super("Сервер");
        this.parent = parent;
        this.editing = editing;
        this.done = done;
        Item head = item(editing == null ? "ДОБАВЛЕНИЕ СЕРВЕРА" : "ИЗМЕНЕНИЕ СЕРВЕРА", () -> {});
        head.selected = true;
        itemAfterGap("ОТМЕНА", this::onClose);
        name = form.field("Название сервера", "Сервер Minecraft", 48);
        addr = form.field("Адрес сервера", "", 128);
        form.add(new CycleWidget(packs, form));
        form.submit(editing == null ? "ДОБАВИТЬ СЕРВЕР" : "СОХРАНИТЬ");
        if (editing != null) {
            name.set(editing.name);
            addr.set(editing.ip);
            for (int i = 0; i < PACKS.length; i++) if (PACKS[i] == editing.getResourcePackStatus()) packs.setSilently(i);
        }
        addr.focused = editing == null;
    }

    @Override protected float itemScale() { return 1.0f; }
    @Override protected int itemLine() { return 26; }
    @Override protected float sunScreenX() { return 0.5f; }
    @Override protected float sunScreenY() { return -0.02f; }

    @Override
    protected void onLayout() {
        left = Math.max(24, Math.round(vw * 0.056f));
        layoutItems(left, Math.round(vh * 0.30f));
        int px = Math.max(left + 220, Math.round(vw * 0.30f));
        form.layout(px, Math.round(vh * 0.33f), Math.min(320, vw - px - 40));
    }

    @Override
    protected void renderMenu(double mx, double my, float dt) {
        d.gradientV(0, Math.round(vh * 0.25f), vw, vh - Math.round(vh * 0.25f), 0x00000000, 0xB4000000);
        renderItems(mx, my, dt);
        renderBrand(left);
        form.render(d, mx, my, dt, fade, vw - Math.max(24, Math.round(vw * 0.056f)), vh - 60);
    }

    private void submit() {
        String a = addr.value().trim();
        if (!ServerAddress.isValidAddress(a)) {
            form.error = "Проверь адрес";
            return;
        }
        String n = name.value().trim().isEmpty() ? "Сервер Minecraft" : name.value().trim();
        ServerData data = editing != null ? editing : new ServerData(n, a, ServerData.Type.OTHER);
        data.name = n;
        data.ip = a;
        data.setResourcePackStatus(PACKS[packs.get()]);
        done.accept(data);
        onClose();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        double mx = e.x() / k, my = e.y() / k;
        if (e.button() == 0 && clickItems(mx, my)) return true;
        if (form.click(mx, my, e.button()) == 1) submit();
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
            submit();
            return true;
        }
        if (key == GLFW.GLFW_KEY_TAB) {
            form.nextField();
            return true;
        }
        return form.key(key, (e.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0, minecraft.keyboardHandler::getClipboard);
    }

    @Override
    public boolean charTyped(CharacterEvent e) {
        return form.chr(e.codepoint());
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

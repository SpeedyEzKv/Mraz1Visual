package dev.mraz1visual.ui;

import dev.mraz1visual.setting.TextSetting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Маленькое окно для текстовой настройки: поле ввода и «Готово». */
public final class TextEditScreen extends Screen {
    private final Screen parent;
    private final TextSetting setting;
    private EditBox box;

    public TextEditScreen(Screen parent, TextSetting setting) {
        super(Component.literal(setting.name));
        this.parent = parent;
        this.setting = setting;
    }

    @Override
    protected void init() {
        int w = 200, x = width / 2 - w / 2, y = height / 2 - 20;
        box = new EditBox(font, x, y, w, 20, Component.literal(setting.name));
        box.setMaxLength(setting.maxLen);
        box.setValue(setting.get());
        addRenderableWidget(box);
        setInitialFocus(box);
        addRenderableWidget(Button.builder(Component.literal("Готово"), b -> onClose()).bounds(x, y + 28, w, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        super.render(g, mouseX, mouseY, pt);
        g.drawCenteredString(font, setting.name, width / 2, height / 2 - 36, 0xFFFFFFFF);
    }

    @Override
    public void onClose() {
        setting.set(box.getValue().trim());
        minecraft.setScreen(parent);
    }
}

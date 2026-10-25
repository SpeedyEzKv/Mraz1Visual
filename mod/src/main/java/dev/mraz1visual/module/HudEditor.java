package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.input.Keys;
import dev.mraz1visual.setting.ActionSetting;
import dev.mraz1visual.setting.BoolSetting;
import dev.mraz1visual.setting.KeySetting;
import dev.mraz1visual.ui.HudEditorScreen;
import dev.mraz1visual.ui.Lang;
import net.minecraft.client.Minecraft;

/** Редактор HUD: открыть кнопкой здесь или своей клавишей. Двигает все элементы со своим местом. */
public final class HudEditor extends Module {
    final ActionSetting open = add(new ActionSetting("open", "Расставить элементы", Lang.get("mraz1visual.hud.editor.open") + " ›", HudEditor::open));
    final BoolSetting grid = add(new BoolSetting("grid", "Сетка 8 px", false));
    final KeySetting key = add(new KeySetting("key", "Клавиша редактора", KeySetting.NONE));
    private boolean was;
    private static HudEditor instance;

    public HudEditor() {
        super("hud_editor", "HUD Editor", "Перетаскивание, масштаб и привязка элементов HUD к краям.", Category.HUD);
        instance = this;
    }

    @Override
    public boolean alwaysOn() { return true; }

    @Override
    public void onTick(Minecraft mc) {
        boolean down = mc.screen == null && Keys.isDown(key.get());
        if (down && !was) open();
        was = down;
    }

    public static void open() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        mc.setScreen(new HudEditorScreen(mc.screen, instance != null && instance.grid.get()));
    }
}

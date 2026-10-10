package dev.mraz1visual.ui.menu;

import dev.mraz1visual.core.ModuleManager;
import dev.mraz1visual.setting.BoolSetting;
import dev.mraz1visual.setting.EnumSetting;
import dev.mraz1visual.ui.widget.CycleWidget;
import dev.mraz1visual.ui.widget.TextInput;
import dev.mraz1visual.ui.widget.ToggleWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Difficulty;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * «Новый мир» в стиле клиента. Все значения пишутся в состояние ванильного экрана создания мира,
 * а мир создаёт сама игра (его кнопка «Создать новый мир») — так сохраняется вся логика ванили.
 * «Дополнительно» открывает ванильный экран: правила игры, датапаки, эксперименты.
 */
public final class Mraz1VisualCreateWorldScreen extends MenuBase {
    static boolean bypass;

    private static final WorldCreationUiState.SelectedGameMode[] MODES = {
            WorldCreationUiState.SelectedGameMode.SURVIVAL, WorldCreationUiState.SelectedGameMode.HARDCORE, WorldCreationUiState.SelectedGameMode.CREATIVE};
    private static final Difficulty[] DIFFS = {Difficulty.PEACEFUL, Difficulty.EASY, Difficulty.NORMAL, Difficulty.HARD};

    private final CreateWorldScreen vanilla;
    private final WorldCreationUiState ui;
    private final MenuForm form = new MenuForm();
    private final TextInput name, seed;
    private final EnumSetting mode = new EnumSetting("mode", "Режим игры", 0, "survival", "Выживание", "hardcore", "Хардкор", "creative", "Творческий");
    private final EnumSetting difficulty = new EnumSetting("difficulty", "Сложность", 2, "peaceful", "Мирная", "easy", "Лёгкая", "normal", "Нормальная", "hard", "Сложная");
    private final BoolSetting commands = new BoolSetting("commands", "Использование команд", false);
    private final EnumSetting worldType;
    private final List<WorldCreationUiState.WorldTypeEntry> types;
    private final BoolSetting structures = new BoolSetting("structures", "Генерация построек", true);
    private final BoolSetting bonusChest = new BoolSetting("bonus", "Бонусный сундук", false);
    private int left;
    private boolean inited;

    public Mraz1VisualCreateWorldScreen(CreateWorldScreen vanilla) {
        super("Новый мир");
        this.vanilla = vanilla;
        this.ui = vanilla.getUiState();
        item("НОВЫЙ МИР", () -> {}).selected = true;
        itemAfterGap("ДОПОЛНИТЕЛЬНО", this::openVanilla);
        item("ОТМЕНА", this::onClose);

        name = form.field("Название мира", "Новый мир", 64);
        name.set(ui.getName());
        form.add(new CycleWidget(mode, form));
        form.add(new CycleWidget(difficulty, form));
        form.add(new ToggleWidget(commands, form));
        types = ui.getNormalPresetList();
        String[] opts = new String[Math.max(1, types.size()) * 2];
        if (types.isEmpty()) {
            opts[0] = "default";
            opts[1] = "Обычный";
        }
        for (int i = 0; i < types.size(); i++) {
            opts[i * 2] = "t" + i;
            opts[i * 2 + 1] = types.get(i).describePreset().getString();
        }
        int cur = Math.max(0, types.indexOf(ui.getWorldType()));
        worldType = new EnumSetting("type", "Тип мира", cur, opts);
        form.add(new CycleWidget(worldType, form));
        seed = form.field("Ключ генератора (сид)", "Случайный", 64);
        seed.set(ui.getSeed());
        form.add(new ToggleWidget(structures, form));
        form.add(new ToggleWidget(bonusChest, form));
        form.submit("СОЗДАТЬ МИР");

        for (int i = 0; i < MODES.length; i++) if (MODES[i] == ui.getGameMode()) mode.setSilently(i);
        for (int i = 0; i < DIFFS.length; i++) if (DIFFS[i] == ui.getDifficulty()) difficulty.setSilently(i);
        commands.setSilently(ui.isAllowCommands());
        structures.setSilently(ui.isGenerateStructures());
        bonusChest.setSilently(ui.isBonusChest());
    }

    @Override protected float itemScale() { return 1.0f; }
    @Override protected int itemLine() { return 26; }
    @Override protected float sunScreenX() { return 0.66f; }
    @Override protected float sunScreenY() { return 0.02f; }

    @Override
    protected void init() {
        super.init();
        if (!inited) {
            inited = true;
            try {
                vanilla.init(width, height); // кнопки ванильного экрана нужны, чтобы создать мир его же кодом
            } catch (Throwable t) {
                ModuleManager.LOG.warn("Vanilla create-world init failed", t);
            }
        }
    }

    @Override
    protected void onLayout() {
        left = Math.max(24, Math.round(vw * 0.056f));
        layoutItems(left, Math.round(vh * 0.30f));
        int px = Math.max(left + 260, Math.round(vw * 0.29f));
        form.layout(px, Math.round(vh * 0.20f), Math.min(320, vw - px - 40));
    }

    @Override
    protected void renderMenu(double mx, double my, float dt) {
        renderItems(mx, my, dt);
        renderBrand(left);
        form.render(d, mx, my, dt, fade, vw - Math.max(24, Math.round(vw * 0.056f)), vh - 88);
    }

    /** Записать выбранное в состояние ванильного экрана. */
    private void apply() {
        ui.setName(name.value().isBlank() ? "Новый мир" : name.value().trim());
        ui.setGameMode(MODES[mode.get()]);
        if (MODES[mode.get()] != WorldCreationUiState.SelectedGameMode.HARDCORE) ui.setDifficulty(DIFFS[difficulty.get()]);
        ui.setAllowCommands(commands.get());
        if (!types.isEmpty()) ui.setWorldType(types.get(Math.min(worldType.get(), types.size() - 1)));
        ui.setSeed(seed.value().trim());
        ui.setGenerateStructures(structures.get());
        ui.setBonusChest(bonusChest.get());
    }

    private void create() {
        try {
            apply();
            String label = Component.translatable("selectWorld.create").getString();
            for (var c : vanilla.children()) {
                if (c instanceof Button b && b.getMessage().getString().equals(label)) {
                    bypass = true;
                    minecraft.setScreen(vanilla); // ванильный экран — родитель для экрана загрузки
                    bypass = false;
                    b.onPress(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0));
                    return;
                }
            }
            throw new IllegalStateException("create button not found");
        } catch (Throwable t) {
            ModuleManager.LOG.error("Custom create world failed", t);
            form.error = "Не получилось — открою обычный экран";
            openVanilla();
        }
    }

    private void openVanilla() {
        try {
            apply();
        } catch (Throwable ignored) {
        }
        bypass = true;
        minecraft.setScreen(vanilla);
        bypass = false;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        double mx = e.x() / k, my = e.y() / k;
        if (e.button() == 0 && clickItems(mx, my)) return true;
        if (form.click(mx, my, e.button()) == 1) create();
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
            create();
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
        // ваниль сама почистит временные датапаки и вернёт к списку миров
        vanilla.onClose();
    }
}

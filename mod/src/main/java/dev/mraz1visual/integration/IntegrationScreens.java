package dev.mraz1visual.integration;

import dev.mraz1visual.core.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.network.chat.Component;

/** Opens the real upstream screens; all their authentication and navigation stays intact. */
public final class IntegrationScreens {
    private IntegrationScreens() {}

    public static boolean themed(Screen screen) {
        if (screen == null) return false;
        if (dev.mraz1visual.core.Modules.client != null && dev.mraz1visual.core.Modules.client.customOptions.get()
                && (screen instanceof net.minecraft.client.gui.screens.options.OptionsSubScreen
                || screen instanceof net.minecraft.client.gui.screens.options.OptionsScreen
                || screen instanceof net.minecraft.client.gui.screens.ConfirmScreen
                || screen instanceof net.minecraft.client.gui.screens.DisconnectedScreen
                || screen instanceof net.minecraft.client.gui.screens.packs.PackSelectionScreen
                || screen instanceof AlertScreen)) return true;
        if (dev.mraz1visual.ui.menu.LoadingScreens.themed(screen)) return true;
        String name = screen.getClass().getName();
        if (dev.mraz1visual.core.Modules.client != null && dev.mraz1visual.core.Modules.client.customOptions.get()
                && name.startsWith("net.caffeinemc.mods.sodium.client.gui.")) return true;
        if (name.startsWith("ru.vidtu.ias.") || name.startsWith("com.terraformersmc.modmenu.")) return true;
        if (screen.getTitle().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents text) {
            return text.getKey().startsWith("ias.") || text.getKey().startsWith("modmenu.");
        }
        return false;
    }

    public static void accounts(Screen parent) { open("ru.vidtu.ias.screen.AccountScreen", parent); }
    public static void mods(Screen parent) { open("com.terraformersmc.modmenu.gui.ModsScreen", parent); }

    private static void open(String name, Screen parent) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && name.startsWith("ru.vidtu.ias.")) {
            mc.setScreen(new AlertScreen(() -> mc.setScreen(parent), Component.literal("Смена аккаунта"),
                    Component.literal("Сначала выйдите из мира или с сервера, затем переключите аккаунт.")));
            return;
        }
        try {
            mc.setScreen((Screen) Class.forName(name).getConstructor(Screen.class).newInstance(parent));
        } catch (ReflectiveOperationException | LinkageError e) {
            ModuleManager.LOG.error("Cannot open integration screen {} ({})", name, e.getClass().getSimpleName());
            mc.setScreen(new AlertScreen(() -> mc.setScreen(parent), Component.literal("Не удалось открыть меню"),
                    Component.literal("Проверьте установку совместимой версии " + (name.startsWith("ru.vidtu.ias.") ? "IAS" : "Mod Menu") + ".")));
        }
    }
}

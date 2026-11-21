package dev.mraz1visual.integration;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.mraz1visual.ui.ClientScreen;

public final class MrazModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> { ClientScreen screen = new ClientScreen(); screen.setParent(parent); return screen; };
    }
}

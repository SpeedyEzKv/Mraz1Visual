package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;

/** Выброшенные предметы лежат плашмя на земле, а не крутятся в воздухе. */
public final class ItemPhysic extends Module {
    private static ItemPhysic instance;

    public ItemPhysic() {
        super("item_physic", "Item Physic", "Выброшенные предметы лежат на земле плашмя.", Category.VISUALS);
        markNew();
        instance = this;
    }

    public static boolean on() {
        return instance != null && instance.isEnabled();
    }
}

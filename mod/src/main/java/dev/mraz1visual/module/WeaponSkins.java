package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.models.ModelEntry;
import dev.mraz1visual.models.ModelPrefs;
import dev.mraz1visual.models.ModelRegistry;
import dev.mraz1visual.setting.ActionSetting;
import dev.mraz1visual.setting.BoolSetting;
import dev.mraz1visual.setting.EnumSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Скины оружия из импортированных 3D-моделей: свой вид моего меча, топора, лука… только на моём экране.
 * Сам предмет, его поведение и то, что видят другие, не меняются — подменяется только картинка
 * при отрисовке (рука 1-го и 3-го лица, хотбар, инвентарь, превью в меню — на каждое место своя галочка).
 */
public final class WeaponSkins extends Module {
    /** Слоты привязки: id, подпись. «Все мечи» — запасной для любого меча без своего скина. */
    public static final String[][] SLOTS = {
            {"all_swords", "Все мечи"}, {"wooden_sword", "Деревянный меч"}, {"stone_sword", "Каменный меч"},
            {"iron_sword", "Железный меч"}, {"golden_sword", "Золотой меч"}, {"diamond_sword", "Алмазный меч"},
            {"netherite_sword", "Незеритовый меч"}, {"axes", "Топоры"}, {"mace", "Булава"}, {"trident", "Трезубец"},
            {"bow", "Лук"}, {"crossbow", "Арбалет"}, {"shield", "Щит"}, {"fishing_rod", "Удочка"},
    };

    final BoolSetting firstPerson = add(new BoolSetting("first_person", "Рука, 1-е лицо", true).group("Где подменять"));
    final BoolSetting thirdPerson = add(new BoolSetting("third_person", "Рука, 3-е лицо", true).group("Где подменять"));
    final BoolSetting hotbar = add(new BoolSetting("hotbar", "Хотбар", true).group("Где подменять"));
    final BoolSetting inventory = add(new BoolSetting("inventory", "Инвентарь", true).group("Где подменять"));
    final BoolSetting preview = add(new BoolSetting("preview", "Превью в меню", true).group("Где подменять"));
    public final EnumSetting material = add(new EnumSetting("material", "Материал", 0, "realistic", "Реалистичный", "original", "Оригинал")
            .hint("Реалистичный: световая кромка по краям (fresnel) и блик неба — металл и стекло читаются объёмно.")
            .onChange(WeaponSkins::applyMaterial));
    public final BoolSetting glow = add(new BoolSetting("glow", "Свечение", true)
            .hint("Эмиссивные текстуры (*_e) и яркие части плоских скинов светятся в темноте."));
    final ActionSetting gallery = add(new ActionSetting("gallery", "Галерея скинов", "Открыть",
            () -> dev.mraz1visual.ui.ModelGallery.open(dev.mraz1visual.ui.ModelGallery.Tab.SKINS)));
    final ActionSetting reload = add(new ActionSetting("reload", "Папка weapon_skins", "Обновить", ModelRegistry::reload));
    final ActionSetting folder = add(new ActionSetting("folder", "Открыть папку", "Открыть",
            () -> dev.mraz1visual.ui.ModelGallery.openFolder(ModelRegistry.WEAPONS)));

    private static WeaponSkins instance;

    public WeaponSkins() {
        super("weapon_skins", "Weapon Skins", "3D-скины оружия из своих паков (папка mraz1visual/weapon_skins). Только у тебя на экране.", Category.SWORDS);
        instance = this;
    }

    public static void applyMaterial() {
        WeaponSkins w = instance;
        dev.mraz1visual.models.ModelDraw.rimStrength = w != null && w.material.getId().equals("original") ? 0f : 0.38f;
    }

    public static WeaponSkins get() {
        return instance;
    }

    public static boolean active() {
        return instance != null && instance.isEnabled();
    }

    // ── выбор скина для предмета ─────────────────────────────

    /** Слот предмета (самый точный) или null — предмет не оружие из списка. */
    public static String slotOf(ItemStack stack) {
        Item it = stack.getItem();
        if (it == Items.WOODEN_SWORD) return "wooden_sword";
        if (it == Items.STONE_SWORD) return "stone_sword";
        if (it == Items.IRON_SWORD) return "iron_sword";
        if (it == Items.GOLDEN_SWORD) return "golden_sword";
        if (it == Items.DIAMOND_SWORD) return "diamond_sword";
        if (it == Items.NETHERITE_SWORD) return "netherite_sword";
        if (it == Items.MACE) return "mace";
        if (it == Items.TRIDENT) return "trident";
        if (it == Items.BOW) return "bow";
        if (it == Items.CROSSBOW) return "crossbow";
        if (it == Items.SHIELD) return "shield";
        if (it == Items.FISHING_ROD) return "fishing_rod";
        if (stack.is(ItemTags.AXES)) return "axes";
        if (stack.is(ItemTags.SWORDS)) return "all_swords";
        return null;
    }

    /** Скин для предмета: свой слот, для мечей — запасной «Все мечи». */
    public static ModelEntry skinFor(ItemStack stack) {
        String slot = slotOf(stack);
        if (slot == null) return null;
        ModelEntry e = ModelRegistry.get(ModelPrefs.weapons.get(slot));
        if (e == null && stack.is(ItemTags.SWORDS)) e = ModelRegistry.get(ModelPrefs.weapons.get("all_swords"));
        return e == null || e.broken() ? null : e;
    }

    /** Состояние предмета для модели: натяжение лука, заброшенная удочка, поднятый щит. */
    public static String stateFor(ItemStack stack) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return ModelEntry.DEFAULT;
        Item it = stack.getItem();
        boolean using = p.isUsingItem() && p.getUseItem() == stack;
        if ((it == Items.BOW || it == Items.CROSSBOW) && using) {
            float pull = (stack.getUseDuration(p) - p.getUseItemRemainingTicks()) / 20f;
            return pull >= 0.9f ? ModelEntry.PULL2 : pull >= 0.65f ? ModelEntry.PULL1 : ModelEntry.PULL0;
        }
        if (it == Items.CROSSBOW && net.minecraft.world.item.CrossbowItem.isCharged(stack)) return ModelEntry.PULL2;
        if (it == Items.SHIELD && using) return ModelEntry.BLOCKING;
        if (it == Items.FISHING_ROD && p.fishing != null) {
            boolean main = p.getMainHandItem() == stack;
            if (main || !p.getMainHandItem().is(Items.FISHING_ROD)) return ModelEntry.CAST;
        }
        return ModelEntry.DEFAULT;
    }

    /** Подменять ли в этом месте. gui — модель игрока в меню/инвентаре, screen — открыт экран (не хотбар). */
    public static boolean allowed(ItemDisplayContext ctx, boolean guiPass) {
        WeaponSkins w = instance;
        if (w == null || !w.isEnabled()) return false;
        return switch (ctx) {
            case FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND -> w.firstPerson.get();
            case THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND -> guiPass ? w.preview.get() : w.thirdPerson.get();
            case GUI -> Minecraft.getInstance().screen == null ? w.hotbar.get() : w.inventory.get();
            default -> false;
        };
    }
}

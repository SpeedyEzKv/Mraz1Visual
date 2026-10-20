package dev.mraz1visual.module;

import com.mojang.blaze3d.platform.NativeImage;
import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.setting.EnumSetting;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Свой скин из файла config/mraz1visual/skins/skin.png (64×64). Видишь его только ты —
 * серверу и другим игрокам ничего не уходит.
 */
public final class LocalSkin extends Module {
    private static LocalSkin instance;
    private static final Identifier TEX = Identifier.fromNamespaceAndPath("mraz1visual", "cosmetics/skin");
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("mraz1visual/skins/skin.png");

    final EnumSetting model = add(new EnumSetting("model", "Модель", 0, "wide", "Обычная (Стив)", "slim", "Тонкая (Алекс)"));

    private ClientAsset.Texture asset;
    private long stamp = -2;

    public LocalSkin() {
        super("local_skin", "Local Skin", "Свой скин из файла config/mraz1visual/skins/skin.png. Виден только тебе.", Category.COSMETICS);
        instance = this;
    }

    @Override
    public void onTick(Minecraft mc) {
        long m;
        try {
            Files.createDirectories(FILE.getParent());
            m = Files.exists(FILE) ? Files.getLastModifiedTime(FILE).toMillis() : -1;
        } catch (Exception e) {
            m = -1;
        }
        if (m == stamp) return;
        stamp = m;
        asset = null;
        if (m < 0) return;
        try (InputStream in = Files.newInputStream(FILE)) {
            NativeImage img = NativeImage.read(in);
            mc.getTextureManager().register(TEX, new DynamicTexture(() -> "mraz1visual skin", img));
            asset = new ClientAsset.ResourceTexture(TEX, TEX);
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onDisable() {
        stamp = -2;
    }

    /** Из миксина PlayerInfo.getSkin: подменяет скин только своему игроку. */
    public static PlayerSkin apply(PlayerInfo info, PlayerSkin skin) {
        LocalSkin l = instance;
        if (l == null || !l.isEnabled() || l.asset == null) return skin;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null || mc.getConnection().getPlayerInfo(mc.player.getUUID()) != info) return skin;
        var type = skin.model();
        String want = l.model.getId().equals("slim") ? "SLIM" : "WIDE";
        for (var t : type.getDeclaringClass().getEnumConstants()) if (t.name().equals(want)) type = t;
        return new PlayerSkin(l.asset, skin.cape(), skin.elytra(), type, skin.secure());
    }
}

package dev.mraz1visual.module;

import com.mojang.blaze3d.platform.NativeImage;
import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.setting.ColorSetting;
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
 * Плащ Mraz1Visual. Видишь его только ты (вид от третьего лица, инвентарь, F5) — серверу ничего не отправляется.
 * Свой рисунок: положи PNG 64×32 (как у обычных плащей) в config/mraz1visual/capes/cape.png и выбери «Свой файл».
 * В «Настройки → Настройки скина» должен быть включён «Плащ».
 */
public final class Cape extends Module {
    private static Cape instance;
    private static final Identifier TEX = Identifier.fromNamespaceAndPath("mraz1visual", "cosmetics/cape");

    final EnumSetting style = add(new EnumSetting("style", "Рисунок", 0,
            "eclipse", "Затмение", "gradient", "Градиент", "plain", "Однотонный", "file", "Свой файл"));
    final ColorSetting color = add(new ColorSetting("color", "Цвет", 0xFFB9A6FF));

    private boolean ready;
    private String builtFor = "";
    private ClientAsset.Texture asset;

    public Cape() {
        super("cape", "Cape", "Свой плащ. Виден только тебе.", Category.VISUALS);
        instance = this;
    }

    @Override
    public void onTick(Minecraft mc) {
        String key = style.getId() + color.get();
        if (!key.equals(builtFor)) {
            builtFor = key;
            rebuild();
        }
    }

    @Override
    protected void onDisable() {
        builtFor = "";
    }

    private void rebuild() {
        NativeImage img = style.getId().equals("file") ? loadFile() : null;
        if (img == null) img = paint(style.getId(), color.get());
        Minecraft.getInstance().getTextureManager().register(TEX, new DynamicTexture(() -> "mraz1visual cape", img));
        asset = new ClientAsset.ResourceTexture(TEX, TEX);
        ready = true;
    }

    private static NativeImage loadFile() {
        Path p = FabricLoader.getInstance().getConfigDir().resolve("mraz1visual/capes/cape.png");
        try {
            Files.createDirectories(p.getParent());
            if (!Files.exists(p)) return null;
            try (InputStream in = Files.newInputStream(p)) {
                return NativeImage.read(in);
            }
        } catch (Exception e) {
            return null;
        }
    }

    /** Рисует развёртку 64×32: лицевая сторона — x 1..11, y 1..17. */
    private static NativeImage paint(String kind, int argb) {
        int w = 64, h = 32;
        NativeImage img = new NativeImage(w, h, true);
        int r = argb >> 16 & 255, g = argb >> 8 & 255, b = argb & 255;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float t = switch (kind) {
                    case "plain" -> 0.55f;
                    default -> 0.25f + 0.6f * (1f - (y % 17) / 17f);
                };
                img.setPixel(x, y, argb(0xFF, (int) (r * t * 0.6f + 8), (int) (g * t * 0.6f + 8), (int) (b * t * 0.6f + 14)));
            }
        }
        if (kind.equals("eclipse")) {
            // кольцо затмения на лицевой и обратной стороне
            for (int side = 0; side < 2; side++) {
                float cx = side == 0 ? 6f : 17f, cy = 8f;
                for (int y = 1; y < 17; y++) {
                    for (int x = side == 0 ? 1 : 12; x < (side == 0 ? 11 : 22); x++) {
                        float d = (float) Math.hypot(x + 0.5f - cx, y + 0.5f - cy);
                        if (d < 2.6f) img.setPixel(x, y, argb(0xFF, 4, 4, 8));
                        else if (d < 3.6f) img.setPixel(x, y, argb(0xFF, Math.min(255, r + 40), Math.min(255, g + 40), Math.min(255, b + 40)));
                        else if (d < 4.6f) img.setPixel(x, y, argb(0xFF, r * 3 / 4, g * 3 / 4, b * 3 / 4));
                    }
                }
            }
        }
        return img;
    }

    private static int argb(int a, int r, int g, int b) {
        return a << 24 | (r & 255) << 16 | (g & 255) << 8 | (b & 255);
    }

    /** Вызывается из миксина PlayerInfo.getSkin: подменяет плащ только своему игроку. */
    public static PlayerSkin apply(PlayerInfo info, PlayerSkin skin) {
        Cape c = instance;
        if (c == null || !c.isEnabled() || !c.ready || c.asset == null) return skin;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) return skin;
        if (mc.getConnection().getPlayerInfo(mc.player.getUUID()) != info) return skin;
        return new PlayerSkin(skin.body(), c.asset, skin.elytra(), skin.model(), skin.secure());
    }
}

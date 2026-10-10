package dev.mraz1visual.module;

import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.input.Keys;
import dev.mraz1visual.setting.EnumSetting;
import dev.mraz1visual.setting.KeySetting;
import dev.mraz1visual.setting.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.LocalPlayer;
import org.lwjgl.glfw.GLFW;

/**
 * Эмоции для вида от третьего лица (F5) и в инвентаре.
 * Видно только тебе: модель меняется только у тебя в клиенте.
 */
public final class Emotes extends Module {
    private static Emotes instance;
    final EnumSetting emote = add(new EnumSetting("emote", "Эмоция", 0,
            "wave", "Помахать", "dance", "Танец", "clap", "Хлопки", "bow", "Поклон", "dab", "Дэб", "facepalm", "Фейспалм"));
    final KeySetting key = add(new KeySetting("key", "Клавиша", GLFW.GLFW_KEY_G));
    final SliderSetting duration = add(new SliderSetting("duration", "Длительность", 3, 1, 10, 0.5, " с"));

    private boolean was;
    private long startAt = -1;
    private double sx, sz;

    public Emotes() {
        super("emotes", "Emotes", "Эмоции от третьего лица. Видно только тебе.", Category.MISC);
        instance = this;
    }

    @Override
    public void onTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        boolean down = p != null && mc.screen == null && Keys.isDown(key.get());
        if (down && !was) {
            startAt = startAt < 0 ? System.currentTimeMillis() : -1;
            if (p != null) {
                sx = p.getX();
                sz = p.getZ();
            }
        }
        was = down;
        if (startAt >= 0 && p != null) {
            boolean moved = Math.abs(p.getX() - sx) > 0.3 || Math.abs(p.getZ() - sz) > 0.3;
            if (moved || System.currentTimeMillis() - startAt > duration.get() * 1000) startAt = -1;
        }
    }

    /** Из миксина HumanoidModel.setupAnim: x,y,z — позиция модели в мире. */
    public static void apply(double x, double y, double z, ModelPart head, ModelPart body, ModelPart rArm, ModelPart lArm, ModelPart rLeg, ModelPart lLeg) {
        Emotes e = instance;
        if (e == null || !e.isEnabled() || e.startAt < 0) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) return;
        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        double px = p.xo + (p.getX() - p.xo) * pt, py = p.yo + (p.getY() - p.yo) * pt, pz = p.zo + (p.getZ() - p.zo) * pt;
        if (Math.abs(px - x) > 0.05 || Math.abs(py - y) > 0.05 || Math.abs(pz - z) > 0.05) return;
        float t = (System.currentTimeMillis() - e.startAt) / 1000f;
        float s = (float) Math.sin(t * 8);
        switch (e.emote.getId()) {
            case "wave" -> {
                rArm.xRot = (float) Math.PI;
                rArm.zRot = 0.35f + 0.35f * s;
            }
            case "dance" -> {
                float b = (float) Math.sin(t * 6);
                body.yRot = 0.3f * b;
                head.yRot += 0.3f * b;
                rArm.xRot = -2.6f;
                lArm.xRot = -2.6f;
                rArm.zRot = 0.4f * b;
                lArm.zRot = 0.4f * b;
                rLeg.xRot = 0.3f * b;
                lLeg.xRot = -0.3f * b;
            }
            case "clap" -> {
                float c = 0.25f + 0.25f * Math.abs(s);
                rArm.xRot = -1.3f;
                lArm.xRot = -1.3f;
                rArm.yRot = -c;
                lArm.yRot = c;
            }
            case "bow" -> {
                float k = Math.min(1f, t * 3f);
                head.xRot = 0.6f * k;
                body.xRot = 0.5f * k;
                rArm.xRot = 0.2f * k;
                lArm.xRot = 0.2f * k;
            }
            case "dab" -> {
                head.xRot = 0.5f;
                head.yRot = 0.6f;
                rArm.xRot = -1.2f;
                rArm.yRot = 0.9f;
                lArm.xRot = -2.4f;
                lArm.zRot = 0.6f;
            }
            case "facepalm" -> {
                head.xRot = 0.35f;
                rArm.xRot = -2.1f;
                rArm.yRot = -0.55f;
            }
            default -> {}
        }
    }
}

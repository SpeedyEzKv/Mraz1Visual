package dev.mraz1visual.dev;

import com.google.gson.JsonPrimitive;
import dev.mraz1visual.Mraz1Visual;
import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.core.ModuleManager;
import dev.mraz1visual.setting.Setting;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Стенд для разработки: только в dev-клиенте и только если есть run/harness/script.txt.
 * Сам создаёт/открывает суперплоский мир, выполняет сценарий (команды, камера, модули, меню),
 * меряет FPS (средний, 1% low, время кадра) и делает скриншоты. В обычной игре не делает ничего.
 *
 * Команды сценария (по строке): wait сек · cmd команда · measure метка сек · shot имя · camera first|back|front ·
 * rot yaw pitch · module id on|off · set модуль настройка значение · gui open|close · gui cat КАТЕГОРИЯ ·
 * inventory · close · swing · key up|sprint|jump on|off · quit
 */
public final class Harness {
    private static final String WORLD = "harness";
    private static boolean active;
    private static Path dir;
    private static List<String> script = List.of();
    private static int pc;
    private static long waitUntil;
    private static boolean worldRequested;
    private static int inWorldTicks;

    // замер
    private static String measureLabel;
    private static long measureUntil, lastFrame;
    private static final float[] frames = new float[200_000];
    private static int frameCount;

    private Harness() {}

    public static void init() {
        if (!FabricLoader.getInstance().isDevelopmentEnvironment()) return;
        dir = FabricLoader.getInstance().getGameDir().resolve("harness");
        Path file = dir.resolve("script.txt");
        if (!Files.exists(file)) return;
        try {
            List<String> lines = new ArrayList<>();
            for (String l : Files.readAllLines(file)) {
                l = l.strip();
                if (!l.isEmpty() && !l.startsWith("#")) lines.add(l);
            }
            script = lines;
        } catch (IOException e) {
            ModuleManager.LOG.error("Harness: cannot read script", e);
            return;
        }
        active = true;
        try {
            Files.writeString(dir.resolve("results.txt"), "");
        } catch (IOException ignored) {
        }
        ClientTickEvents.END_CLIENT_TICK.register(Harness::tick);
        ModuleManager.LOG.info("Harness: {} steps", script.size());
    }

    /** Из миксина Minecraft.runTick: начало каждого кадра. */
    public static void frame() {
        if (!active || measureLabel == null) return;
        long now = System.nanoTime();
        if (lastFrame != 0 && frameCount < frames.length) frames[frameCount++] = (now - lastFrame) / 1e6f;
        lastFrame = now;
    }

    private static void tick(Minecraft mc) {
        // стенд не трогает мышь — без этого через минуту включается ванильный AFK-предел 30 FPS и портит замеры
        mc.getFramerateLimitTracker().onInputReceived();
        if (measureLabel != null) {
            if (System.nanoTime() < measureUntil) return;
            finishMeasure();
        }
        if (mc.level == null) {
            if (!worldRequested && mc.screen != null && mc.getOverlay() == null && inWorldTicks == 0) openWorld(mc);
            return;
        }
        if (mc.player == null) return;
        inWorldTicks++;
        if (inWorldTicks < 100) return; // мир догружается
        if (System.nanoTime() < waitUntil) return;
        while (pc < script.size()) {
            String line = script.get(pc++);
            log("> " + line);
            try {
                if (!run(mc, line.split("\\s+"))) return; // шаг занимает время
            } catch (Throwable t) {
                log("  ERROR " + t);
                ModuleManager.LOG.error("Harness step failed: {}", line, t);
            }
        }
    }

    private static void openWorld(Minecraft mc) {
        worldRequested = true;
        try {
            if (mc.getLevelSource().levelExists(WORLD)) {
                mc.createWorldOpenFlows().openWorld(WORLD, () -> mc.setScreen(new TitleScreen()));
            } else {
                GameRules rules = new GameRules(FeatureFlags.DEFAULT_FLAGS);
                LevelSettings settings = new LevelSettings(WORLD, GameType.CREATIVE, false, Difficulty.PEACEFUL, true, rules, WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(1234L, false, false),
                        WorldPresets::createFlatWorldDimensions, new TitleScreen());
            }
        } catch (Throwable t) {
            log("ERROR opening world: " + t);
            ModuleManager.LOG.error("Harness: world open failed", t);
        }
    }

    /** Модель по части id (без учёта регистра). */
    private static dev.mraz1visual.models.ModelEntry findModel(String q) {
        q = q.toLowerCase(java.util.Locale.ROOT);
        for (var e : dev.mraz1visual.models.ModelRegistry.all()) if (e.id.toLowerCase(java.util.Locale.ROOT).contains(q)) return e;
        return null;
    }

    /** @return true — шаг выполнен сразу, можно следующий; false — ждём (wait/measure). */
    private static boolean run(Minecraft mc, String[] a) throws Exception {
        switch (a[0]) {
            case "wait" -> {
                waitUntil = System.nanoTime() + (long) (Double.parseDouble(a[1]) * 1e9);
                return false;
            }
            case "cmd" -> mc.player.connection.sendCommand(String.join(" ", Arrays.copyOfRange(a, 1, a.length)));
            case "measure" -> {
                measureLabel = a[1];
                frameCount = 0;
                lastFrame = 0;
                measureUntil = System.nanoTime() + (long) (Double.parseDouble(a[2]) * 1e9);
                return false;
            }
            case "shot" -> Screenshot.grab(mc.gameDirectory, a[1] + ".png", mc.getMainRenderTarget(), 1, c -> log("  shot " + a[1]));
            case "camera" -> mc.options.setCameraType(switch (a[1]) {
                case "back" -> CameraType.THIRD_PERSON_BACK;
                case "front" -> CameraType.THIRD_PERSON_FRONT;
                default -> CameraType.FIRST_PERSON;
            });
            case "rot" -> {
                mc.player.setYRot(Float.parseFloat(a[1]));
                mc.player.setXRot(Float.parseFloat(a[2]));
                mc.player.yRotO = mc.player.getYRot();
                mc.player.xRotO = mc.player.getXRot();
                mc.player.setYHeadRot(mc.player.getYRot());
                mc.player.setYBodyRot(mc.player.getYRot());
            }
            case "module" -> {
                Module m = Mraz1Visual.MODULES.find(a[1]);
                if (m == null) log("  no module " + a[1]);
                else m.setEnabled(a[2].equals("on"));
            }
            case "set" -> {
                Module m = Mraz1Visual.MODULES.find(a[1]);
                Setting<?> s = null;
                if (m != null) for (Setting<?> x : m.settings()) if (x.id.equals(a[2])) s = x;
                String v = String.join(" ", Arrays.copyOfRange(a, 3, a.length));
                if (s == null) log("  no setting " + a[1] + "." + a[2]);
                else if (!s.fromJson(value(v))) log("  bad value " + v);
            }
            case "gui" -> {
                if (a[1].equals("open")) Mraz1Visual.openMenu(mc);
                else if (a[1].equals("close")) mc.setScreen(null);
                else if (a[1].equals("cat") && mc.screen instanceof dev.mraz1visual.ui.Mraz1VisualScreen s) s.harnessCategory(Category.valueOf(a[2]));
                else if (a[1].equals("reveal") && mc.screen instanceof dev.mraz1visual.ui.Mraz1VisualScreen s) s.harnessReveal(a[2]);
                else if (a[1].equals("yaw") && mc.screen instanceof dev.mraz1visual.ui.Mraz1VisualScreen s) s.harnessPreview(Float.parseFloat(a[2]));
                else if (a[1].equals("section") && mc.screen instanceof dev.mraz1visual.ui.Mraz1VisualScreen s) s.harnessSection(a[2]);
            }
            case "video" -> {
                // video <предел FPS> <vsync true|false> <дальность>
                mc.options.framerateLimit().set(Integer.parseInt(a[1]));
                mc.options.enableVsync().set(Boolean.parseBoolean(a[2]));
                mc.getWindow().updateVsync(Boolean.parseBoolean(a[2]));
                mc.options.renderDistance().set(Integer.parseInt(a[3]));
            }
            case "alloff" -> {
                for (Module m : Mraz1Visual.MODULES.all()) if (!m.alwaysOn()) m.setEnabled(false);
            }
            case "inventory" -> mc.setScreen(new InventoryScreen(mc.player));
            case "close" -> mc.setScreen(null);
            case "swing" -> mc.player.swing(InteractionHand.MAIN_HAND);
            case "key" -> {
                boolean on = a[2].equals("on");
                switch (a[1]) {
                    case "up" -> mc.options.keyUp.setDown(on);
                    case "sprint" -> mc.options.keySprint.setDown(on);
                    case "jump" -> mc.options.keyJump.setDown(on);
                    default -> log("  no key " + a[1]);
                }
            }
            // ── импортированные модели ──
            case "models" -> {
                if (dev.mraz1visual.models.ModelRegistry.loading()) {
                    waitUntil = System.nanoTime() + 500_000_000L;
                    pc--; // повторить шаг, когда загрузка закончится
                    return false;
                }
                java.util.Map<String, Integer> kinds = new java.util.TreeMap<>();
                for (var e : dev.mraz1visual.models.ModelRegistry.all()) {
                    kinds.merge(e.kind + (e.broken() ? " broken" : ""), 1, Integer::sum);
                    if (e.broken()) log("  BROKEN " + e.id + ": " + e.error);
                    else if (a.length > 1 && a[1].equals("all")) log("  " + e.kind + " " + e.id + " states=" + e.states.keySet()
                            + " anims=" + e.anims.size() + " fx=" + e.effects.size() + " quads=" + e.mesh().quads
                            + " atlas=" + e.mesh().atlas.w + "x" + e.mesh().atlas.h + (e.warnings.isEmpty() ? "" : " warn=" + e.warnings));
                }
                log("  models: " + kinds + " — " + dev.mraz1visual.models.ModelRegistry.status());
            }
            case "skin" -> {
                var e = findModel(a[2]);
                if (e == null) log("  no model " + a[2]);
                else dev.mraz1visual.models.ModelPrefs.weapons.put(a[1], e.id);
            }
            case "pet" -> {
                var e = findModel(a[1]);
                var mp = dev.mraz1visual.module.ModelPets.get();
                if (e == null || mp == null) log("  no pet " + a[1]);
                else mp.select(e);
            }
            case "petanim" -> {
                var mp = dev.mraz1visual.module.ModelPets.get();
                if (mp != null) mp.playNow(a[1]);
            }
            case "gallery" -> dev.mraz1visual.ui.ModelGallery.open(dev.mraz1visual.ui.ModelGallery.Tab.valueOf(a[1].toUpperCase(java.util.Locale.ROOT)));
            case "use" -> mc.options.keyUse.setDown(a[1].equals("on"));
            case "attack" -> {
                // ближайшая живая сущность в 6 блоках — настоящий удар (событие для визуалов)
                net.minecraft.world.entity.LivingEntity best = null;
                double bd = 36;
                for (var en : mc.level.entitiesForRendering()) {
                    if (en == mc.player || !(en instanceof net.minecraft.world.entity.LivingEntity le)) continue;
                    double dd = en.distanceToSqr(mc.player);
                    if (dd < bd) { bd = dd; best = le; }
                }
                if (best == null) log("  nothing to attack");
                else {
                    mc.gameMode.attack(mc.player, best);
                    mc.player.swing(InteractionHand.MAIN_HAND);
                }
            }
            case "jump" -> mc.player.jumpFromGround();
            case "cinematic" -> dev.mraz1visual.module.Cinematic.harnessToggle();
            case "preset" -> dev.mraz1visual.module.VisualPresets.apply(a[1]);
            case "quit" -> {
                log("done");
                active = false;
                mc.stop();
                return false;
            }
            default -> log("  unknown " + a[0]);
        }
        return true;
    }

    private static JsonPrimitive value(String v) {
        if (v.equals("true") || v.equals("false")) return new JsonPrimitive(Boolean.parseBoolean(v));
        try {
            return new JsonPrimitive(Double.parseDouble(v));
        } catch (NumberFormatException e) {
            return new JsonPrimitive(v);
        }
    }

    private static void finishMeasure() {
        String label = measureLabel;
        measureLabel = null;
        int n = frameCount;
        if (n == 0) {
            log("measure " + label + ": no frames");
            return;
        }
        float[] s = Arrays.copyOf(frames, n);
        double sum = 0;
        float max = 0;
        for (float v : s) { sum += v; max = Math.max(max, v); }
        Arrays.sort(s);
        int low = Math.max(1, n / 100);
        double lowSum = 0;
        for (int i = n - low; i < n; i++) lowSum += s[i];
        double avg = sum / n;
        log(String.format(Locale.ROOT, "RESULT %-24s fps %7.1f | 1%%low %7.1f | avg %6.2f ms | max %7.2f ms | frames %d",
                label, 1000 / avg, 1000 / (lowSum / low), avg, max, n));
    }

    private static void log(String s) {
        ModuleManager.LOG.info("[harness] {}", s);
        try {
            Files.writeString(dir.resolve("results.txt"), s + System.lineSeparator(), java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException ignored) {
        }
    }
}

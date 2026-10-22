package dev.mraz1visual.module;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.mraz1visual.core.Category;
import dev.mraz1visual.core.Module;
import dev.mraz1visual.core.ModuleManager;
import dev.mraz1visual.core.WorldLayer;
import dev.mraz1visual.input.Keys;
import dev.mraz1visual.render.WorldDraw;
import dev.mraz1visual.setting.BoolSetting;
import dev.mraz1visual.setting.ColorSetting;
import dev.mraz1visual.setting.KeySetting;
import dev.mraz1visual.ui.Colors;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Свои точки на карте: лучи-маяки там, где ты сам поставил точку или умер.
 * Отдельно для каждого сервера и измерения. Только твои координаты — никакой информации о других игроках.
 */
public final class Waypoints extends Module implements WorldLayer {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("mraz1visual").resolve("waypoints.json");

    final KeySetting addKey = add(new KeySetting("add_key", "Поставить точку", org.lwjgl.glfw.GLFW.GLFW_KEY_B));
    final KeySetting removeKey = add(new KeySetting("remove_key", "Убрать ближайшую", KeySetting.NONE));
    final ColorSetting color = add(new ColorSetting("color", "Цвет точек", 0xB0B8B4F2).group("Вид"));

    public static final class Point {
        public String name;
        public String dim;
        public double x, y, z;
        public boolean death;
    }

    /** server → точки */
    private Map<String, List<Point>> data = new HashMap<>();
    private boolean addWas, removeWas;
    private int counter = 1;

    public Waypoints() {
        super("waypoints", "Waypoints", "Свои точки-маяки по клавише.", Category.MISC);
        load();
    }

    private static String serverKey(Minecraft mc) {
        return mc.getCurrentServer() != null ? mc.getCurrentServer().ip : "local";
    }

    private List<Point> points(Minecraft mc) {
        return data.computeIfAbsent(serverKey(mc), k -> new ArrayList<>());
    }

    @Override
    public void onTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) return;
        String dim = mc.level.dimension().toString();

        boolean add = mc.screen == null && Keys.isDown(addKey.get());
        if (add && !addWas) {
            Point pt = new Point();
            pt.name = "Точка " + counter++;
            pt.dim = dim;
            pt.x = Math.floor(p.getX()) + 0.5;
            pt.y = Math.floor(p.getY());
            pt.z = Math.floor(p.getZ()) + 0.5;
            points(mc).add(pt);
            save();
            dev.mraz1visual.compat.Mc.message(p, Component.literal("§7Mraz1Visual: точка §f" + pt.name + "§7 — " + (int) pt.x + " " + (int) pt.y + " " + (int) pt.z), true);
        }
        addWas = add;

        boolean rem = mc.screen == null && Keys.isDown(removeKey.get());
        if (rem && !removeWas) {
            Point near = null;
            double best = Double.MAX_VALUE;
            for (Point pt : points(mc)) {
                if (!pt.dim.equals(dim)) continue;
                double d = p.distanceToSqr(pt.x, pt.y, pt.z);
                if (d < best) { best = d; near = pt; }
            }
            if (near != null) {
                points(mc).remove(near);
                save();
                dev.mraz1visual.compat.Mc.message(p, Component.literal("§7Mraz1Visual: убрана §f" + near.name), true);
            }
        }
        removeWas = rem;

    }

    @Override
    public void renderWorld(WorldDraw d, float pt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        String dim = mc.level.dimension().toString();
        List<Point> list = data.get(serverKey(mc));
        if (list == null) return;
        for (Point p : list) {
            if (p.death || !p.dim.equals(dim)) continue;
            double dist = Math.sqrt(mc.player.distanceToSqr(p.x, p.y, p.z));
            float fade = (float) Math.min(1, Math.max(0.25, dist / 24)); // вблизи луч не мешает обзору
            int c = Colors.mulAlpha(color.get(), fade);
            d.beam(p.x, p.y, p.z, 96, 0.22, c);
            d.ring(p.x, p.y + 0.02, p.z, 0.35, 0.5, 32, Colors.mulAlpha(c, 0.3f), c);
        }
    }

    private void load() {
        try {
            if (Files.exists(FILE)) {
                Map<String, List<Point>> m = GSON.fromJson(Files.readString(FILE, StandardCharsets.UTF_8),
                        new TypeToken<Map<String, List<Point>>>() {}.getType());
                if (m != null) data = m;
                for (List<Point> l : data.values()) counter += l.size();
            }
        } catch (Throwable t) {
            ModuleManager.LOG.warn("waypoints.json unreadable ({})", t.toString());
        }
    }

    private void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(data), StandardCharsets.UTF_8);
        } catch (Exception e) {
            ModuleManager.LOG.warn("Cannot save waypoints", e);
        }
    }
}

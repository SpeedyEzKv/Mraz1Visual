package dev.umbra.core;

import dev.umbra.input.Keys;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class ModuleManager {
    public static final Logger LOG = LoggerFactory.getLogger("Umbra");
    private static final Module[] EMPTY = new Module[0];

    private final List<Module> all = new ArrayList<>();
    private final List<Module> allView = Collections.unmodifiableList(all);
    private final Map<Category, List<Module>> byCategory = new EnumMap<>(Category.class);

    /** Включённые модули с переопределённым onTick. Пересобирается только при вкл/выкл. */
    private Module[] ticking = EMPTY;
    /** Включённые модули, рисующие в HUD / в мире. Пустые массивы = ноль работы. */
    private HudLayer[] hud = new HudLayer[0];
    private WorldLayer[] world = new WorldLayer[0];
    private AttackListener[] attack = new AttackListener[0];
    /** Модули с назначенной клавишей. Пересобирается при смене бинда. */
    private Module[] bound = EMPTY;
    private boolean bindsDirty = true;

    public ModuleManager() {
        for (Category c : Category.values()) byCategory.put(c, new ArrayList<>());
    }

    public void init() {
        Modules.registerAll(this);
    }

    public <M extends Module> M register(M m) {
        if (find(m.id) != null) throw new IllegalStateException("duplicate module id " + m.id);
        m.manager = this;
        m.bind.onChange(this::markBindsDirty);
        all.add(m);
        byCategory.get(m.category).add(m);
        return m;
    }

    public List<Module> all() { return allView; }

    public List<Module> in(Category c) { return byCategory.get(c); }

    public Module find(String id) {
        for (Module m : all) if (m.id.equals(id)) return m;
        return null;
    }

    /** После загрузки конфига: включаем то, что было включено. */
    public void afterConfigLoad() {
        for (Module m : all) {
            if (m.isEnabled()) {
                try { m.onEnable(); } catch (Throwable t) { LOG.error("Module {} failed on enable", m.id, t); }
            }
        }
        onModuleStateChanged();
        markBindsDirty();
    }

    void onModuleStateChanged() {
        int n = 0;
        for (Module m : all) if (m.isEnabled() && overridesTick(m)) n++;
        Module[] arr = n == 0 ? EMPTY : new Module[n];
        int i = 0;
        for (Module m : all) if (m.isEnabled() && overridesTick(m)) arr[i++] = m;
        ticking = arr;

        List<HudLayer> h = new ArrayList<>();
        List<WorldLayer> w = new ArrayList<>();
        List<AttackListener> a = new ArrayList<>();
        for (Module m : all) {
            if (!m.isEnabled()) continue;
            if (m instanceof HudLayer hl) h.add(hl);
            if (m instanceof WorldLayer wl) w.add(wl);
            if (m instanceof AttackListener al) a.add(al);
        }
        hud = h.toArray(new HudLayer[0]);
        world = w.toArray(new WorldLayer[0]);
        attack = a.toArray(new AttackListener[0]);
    }

    public HudLayer[] hudLayers() { return hud; }

    public WorldLayer[] worldLayers() { return world; }

    public AttackListener[] attackListeners() { return attack; }

    public void markBindsDirty() { bindsDirty = true; }

    private static boolean overridesTick(Module m) {
        try {
            return m.getClass().getMethod("onTick", Minecraft.class).getDeclaringClass() != Module.class;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    /** Раз в тик. Без аллокаций. */
    public void tick(Minecraft mc) {
        if (bindsDirty) rebuildBound();

        boolean canAct = mc.screen == null;
        Module[] b = bound;
        for (int i = 0; i < b.length; i++) {
            Module m = b[i];
            boolean down = Keys.isDown(m.bind.get());
            // Состояние обновляем всегда — иначе клавиша, которой закрыли меню, тут же откроет его снова.
            if (down && !m.bind.wasDown && canAct && !m.alwaysOn()) m.toggle();
            m.bind.wasDown = down;
        }

        Module[] t = ticking;
        for (int i = 0; i < t.length; i++) {
            try {
                t[i].onTick(mc);
            } catch (Throwable e) {
                LOG.error("Module {} crashed in tick, disabling", t[i].id, e);
                t[i].setEnabled(false);
            }
        }
    }

    private void rebuildBound() {
        bindsDirty = false;
        int n = 0;
        for (Module m : all) if (m.bind.isBound() && !m.alwaysOn()) n++;
        Module[] arr = n == 0 ? EMPTY : new Module[n];
        int i = 0;
        for (Module m : all) if (m.bind.isBound() && !m.alwaysOn()) arr[i++] = m;
        bound = arr;
    }
}

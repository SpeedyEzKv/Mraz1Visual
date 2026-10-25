package dev.mraz1visual.core;

import dev.mraz1visual.setting.KeySetting;
import dev.mraz1visual.setting.Setting;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Базовый класс визуала. Новый модуль = наследник этого класса
 * + одна строка в {@link Modules#registerAll}.
 *
 * Выключенный модуль не стоит ничего: менеджер не вызывает у него ни одного хука.
 */
public abstract class Module {
    public final String id;
    public final String name;
    public final String description;
    public final Category category;
    public final KeySetting bind;

    private final List<Setting<?>> settings = new ArrayList<>();
    private final List<Setting<?>> settingsView = Collections.unmodifiableList(settings);
    private boolean enabled;
    ModuleManager manager;

    protected Module(String id, String name, String description, Category category) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.category = category;
        this.bind = new KeySetting("bind", "Клавиша", KeySetting.NONE);
    }

    protected final <S extends Setting<?>> S add(S setting) {
        for (Setting<?> s : settings) {
            if (s.id.equals(setting.id)) throw new IllegalStateException(id + ": duplicate setting id " + setting.id);
        }
        settings.add(setting);
        return setting;
    }

    /** Место на экране для редактора HUD; fx, fy — 0..1 от левого верхнего до правого нижнего края. */
    protected final dev.mraz1visual.render.HudPos hudPos(double fx, double fy) {
        dev.mraz1visual.render.HudPos p = new dev.mraz1visual.render.HudPos(this, fx, fy);
        add(p.x);
        add(p.y);
        add(p.scale);
        return p;
    }

    public final List<Setting<?>> settings() { return settingsView; }

    public final boolean isEnabled() { return enabled; }

    /** Модуль без тумблера (например, настройки клиента) — всегда включён. */
    public boolean alwaysOn() { return false; }

    public final void setEnabled(boolean value) {
        if (alwaysOn()) value = true;
        if (value == enabled) return;
        enabled = value;
        try {
            if (value) onEnable(); else onDisable();
        } catch (Throwable t) {
            ModuleManager.LOG.error("Module {} failed on {}", id, value ? "enable" : "disable", t);
        }
        if (manager != null) manager.onModuleStateChanged();
        try {
            dev.mraz1visual.module.Notifications.post(this, value);
            dev.mraz1visual.module.UiSounds.toggled(this, value);
        } catch (Throwable ignored) {
        }
        Setting.globalChangeHook.run();
    }

    public final void toggle() { setEnabled(!enabled); }

    /** Только для загрузки конфига: без хуков onEnable — их вызовет менеджер после загрузки. */
    public final void loadEnabled(boolean value) { enabled = alwaysOn() || value; }

    protected void onEnable() {}
    protected void onDisable() {}

    /** Вызывается раз в тик, только пока модуль включён и только если метод переопределён. */
    public void onTick(Minecraft mc) {}
}

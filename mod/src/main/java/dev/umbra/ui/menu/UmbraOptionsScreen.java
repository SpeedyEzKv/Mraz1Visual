package dev.umbra.ui.menu;

import dev.umbra.setting.BoolSetting;
import dev.umbra.setting.ColorSetting;
import dev.umbra.setting.EnumSetting;
import dev.umbra.setting.KeySetting;
import dev.umbra.setting.Setting;
import dev.umbra.setting.SliderSetting;
import dev.umbra.ui.Anim;
import dev.umbra.ui.Colors;
import dev.umbra.ui.Draw;
import dev.umbra.ui.Fonts;
import dev.umbra.ui.Theme;
import dev.umbra.ui.widget.BindWidget;
import dev.umbra.ui.widget.ColorWidget;
import dev.umbra.ui.widget.EnumWidget;
import dev.umbra.ui.widget.Host;
import dev.umbra.ui.widget.Popup;
import dev.umbra.ui.widget.SettingWidget;
import dev.umbra.ui.widget.SliderWidget;
import dev.umbra.ui.widget.ToggleWidget;
import dev.umbra.ui.widget.Widget;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.ChatVisiblity;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Настройки игры в стиле клиента: разделы слева, параметры справа в две колонки,
 * подсказка к наведённому параметру внизу. Редкие настройки — «Все настройки» (ванильный экран).
 */
public final class UmbraOptionsScreen extends MenuBase implements Host {
    private enum Section { GRAPHICS, SOUND, CONTROLS, CHAT, SKIN, PACKS }

    /** Ванильный экран, открытый через «Все настройки», не перехватываем. */
    static boolean bypass;
    static java.lang.ref.WeakReference<Screen> vanillaInstance = new java.lang.ref.WeakReference<>(null);

    private static final int ROW_H = 22;
    private static final String[] GRAPHICS_TABS = {"ОСНОВНОЕ", "ДЕТАЛИ", "КАЧЕСТВО", "ЭФФЕКТЫ", "ОПТИМИЗАЦИЯ"};
    private int gTab;
    private final float[] gTabHover = new float[GRAPHICS_TABS.length];
    private float ulX = -1, ulW;

    private final Screen parent;
    private Options o;
    private Section section = Section.GRAPHICS;
    private final Item[] sectionItems = new Item[Section.values().length];

    private final List<OptionBinds.Sync> syncs = new ArrayList<>();
    private final List<SettingWidget> widgets = new ArrayList<>();
    private int px, py, pw, ph, colW;
    private float scroll, scrollTarget;
    private int contentH;
    private Popup popup;
    private BindWidget listening;
    private Widget active;
    private int initialGuiScale;
    private String extraLabel = "";
    private Runnable extraAction;
    private float extraHover;

    public UmbraOptionsScreen(Screen parent) {
        super("Настройки");
        this.parent = parent;
        sectionItems[0] = item("ГРАФИКА", () -> setSection(Section.GRAPHICS));
        sectionItems[1] = item("ЗВУК", () -> setSection(Section.SOUND));
        sectionItems[2] = item("УПРАВЛЕНИЕ", () -> setSection(Section.CONTROLS));
        sectionItems[3] = item("ЧАТ", () -> setSection(Section.CHAT));
        sectionItems[4] = item("СКИН", () -> setSection(Section.SKIN));
        sectionItems[5] = item("РЕСУРСПАКИ", () -> setSection(Section.PACKS));
        itemAfterGap("НАЗАД", this::onClose);
        sectionItems[0].selected = true;
    }

    @Override protected float itemScale() { return 1.0f; }
    @Override protected int itemLine() { return 26; }

    @Override protected float sunScreenX() { return 0.5f; }
    @Override protected float sunScreenY() { return -0.02f; }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (minecraft.level != null) {
            // в игре — размытый мир, как у ванили, плюс затемнение
            vanillaBackground(g, mouseX, mouseY, partialTick);
            g.fill(0, 0, width, height, 0x99000000);
        } else {
            super.renderBackground(g, mouseX, mouseY, partialTick);
        }
    }

    @Override
    protected void init() {
        o = minecraft.options;
        initialGuiScale = o.guiScale().get();
        super.init();
        for (OptionBinds.Sync s : syncs) s.pull();
    }

    @Override
    protected void onLayout() {
        int lx = Math.max(24, Math.round(vw * 0.056f));
        layoutItems(lx, Math.round(vh * 0.28f));
        int itemsRight = lx;
        for (Item it : items) itemsRight = Math.max(itemsRight, it.x + it.w);
        px = Math.max(itemsRight + 36, Math.round(vw * 0.25f));
        py = Math.round(vh * 0.30f);
        pw = vw - px - Math.max(24, Math.round(vw * 0.05f));
        ph = vh - py - 60;
        colW = (pw - 28) / 2;
        build();
    }

    private final PacksPanel packs = new PacksPanel();

    private void setSection(Section s) {
        if (section == Section.PACKS && s != Section.PACKS) packs.apply();
        if (s == Section.PACKS && section != Section.PACKS) packs.open();
        section = s;
        for (int i = 0; i < sectionItems.length; i++) sectionItems[i].selected = i == s.ordinal();
        scroll = scrollTarget = 0;
        build();
    }

    // ── содержимое разделов ──────────────────────────────────

    private void build() {
        if (o == null) return;
        widgets.clear();
        syncs.clear();
        popup = null;
        listening = null;
        extraLabel = "";
        extraAction = null;
        List<Setting<?>> list = new ArrayList<>();
        switch (section) {
            case GRAPHICS -> graphics(list);
            case SOUND -> sound(list);
            case CONTROLS -> controls(list);
            case CHAT -> chat(list);
            case SKIN -> skin(list);
            case PACKS -> { }
        }
        int rows = (list.size() + 1) / 2;
        for (int i = 0; i < list.size(); i++) {
            int col = i < rows ? 0 : 1;
            int row = i < rows ? i : i - rows;
            SettingWidget w = widgetFor(list.get(i));
            w.setBounds(px + col * (colW + 28), py + row * ROW_H, colW, ROW_H);
            widgets.add(w);
        }
        contentH = rows * ROW_H;
    }

    private void graphics(List<Setting<?>> l) {
        switch (gTab) {
            case 1 -> graphicsDetails(l);
            case 2 -> graphicsQuality(l);
            case 3 -> graphicsEffects(l);
            case 4 -> graphicsOptimization(l);
            default -> graphicsMain(l);
        }
    }

    private void graphicsMain(List<Setting<?>> l) {
        int maxGui = Math.max(1, minecraft.getWindow().calculateScale(0, minecraft.isEnforceUnicode()));
        l.add(OptionBinds.ints("Прорисовка", o.renderDistance(), 2, 32, 1, v -> (int) v + " чанков",
                "Сколько чанков видно вокруг. Главный ползунок FPS: меньше — быстрее.", syncs));
        l.add(OptionBinds.ints("Симуляция", o.simulationDistance(), 5, 32, 1, v -> (int) v + " чанков",
                "В каком радиусе работают мобы, растения и механизмы (в одиночной игре).", syncs));
        l.add(OptionBinds.ints("Частота кадров", o.framerateLimit(), 10, 260, 10, v -> v >= 260 ? "Без лимита" : (int) v + " FPS",
                "Ограничение FPS. Лимит под частоту монитора снижает нагрев и шум.", syncs));
        l.add(OptionBinds.bool("Вертикальная синхронизация", o.enableVsync(),
                "Убирает разрывы кадра, но добавляет задержку ввода.", syncs));
        l.add(OptionBinds.ints("Поле зрения", o.fov(), 30, 110, 1, v -> v == 70 ? "Обычное" : v >= 110 ? "Quake-про" : Integer.toString((int) v),
                "Угол обзора камеры.", syncs));
        l.add(OptionBinds.doubles("Яркость", o.gamma(), 0, 1, 0.01, v -> v <= 0 ? "Тускло" : v >= 1 ? "Ярко" : OptionBinds.pct(v),
                "Яркость тёмных мест. Ванильный максимум — то же, что в обычных настройках.", syncs));
        l.add(OptionBinds.ints("Масштаб интерфейса", o.guiScale(), 0, maxGui, 1, v -> v == 0 ? "Авто" : Integer.toString((int) v),
                "Размер ванильного интерфейса. Применится после закрытия настроек.", syncs));
        l.add(OptionBinds.bool("Полноэкранный режим", o.fullscreen(), "Переключить окно и полный экран.", syncs));
    }

    private void graphicsDetails(List<Setting<?>> l) {
        l.add(OptionBinds.choice("Облака", o.cloudStatus(), CloudStatus.values(), cloudLabels(),
                "Облака заметно влияют на FPS на слабых видеокартах.", syncs));
        l.add(OptionBinds.choice("Частицы", o.particles(), ParticleStatus.values(), new String[]{"Все", "Меньше", "Мин."},
                "Количество частиц: дым, искры, взрывы.", syncs));
        l.add(OptionBinds.bool("Тени сущностей", o.entityShadows(), "Круглые тени под мобами и игроками.", syncs));
        l.add(OptionBinds.doubles("Дальность сущностей", o.entityDistanceScaling(), 0.5, 5.0, 0.25, v -> OptionBinds.pct(v),
                "На каком расстоянии рисуются мобы и игроки.", syncs));
    }

    private void graphicsQuality(List<Setting<?>> l) {
        l.add(OptionBinds.bool("Мягкое освещение", o.ambientOcclusion(), "Плавные тени в углах блоков.", syncs));
        l.add(OptionBinds.ints("Смешивание биомов", o.biomeBlendRadius(), 0, 7, 1, v -> v == 0 ? "Выкл" : (int) (v * 2 + 1) + "×" + (int) (v * 2 + 1),
                "Плавный переход цветов травы и воды. Выше — дороже при загрузке чанков.", syncs));
        l.add(OptionBinds.ints("Мипмапы", o.mipmapLevels(), 0, 4, 1, v -> v == 0 ? "Выкл" : Integer.toString((int) v),
                "Сглаживание текстур вдали.", syncs));
    }

    private void graphicsEffects(List<Setting<?>> l) {
        l.add(OptionBinds.bool("Покачивание камеры", o.bobView(), "Камера покачивается при ходьбе.", syncs));
        l.add(OptionBinds.doubles("Эффекты поля зрения", o.fovEffectScale(), 0, 1, 0.01, OptionBinds::pct,
                "Насколько бег и зелья меняют угол обзора.", syncs));
        l.add(OptionBinds.doubles("Искажения экрана", o.screenEffectScale(), 0, 1, 0.01, OptionBinds::pct,
                "Тошнота и портал: сила искажения.", syncs));
        l.add(OptionBinds.doubles("Эффект темноты", o.darknessEffectScale(), 0, 1, 0.01, OptionBinds::pct,
                "Пульсация затемнения от варденов и скалк-крикунов.", syncs));
        l.add(OptionBinds.doubles("Блеск зачарований", o.glintStrength(), 0, 1, 0.01, OptionBinds::pct,
                "Яркость переливов на зачарованных предметах.", syncs));
        l.add(OptionBinds.doubles("Скорость блеска", o.glintSpeed(), 0, 1, 0.01, OptionBinds::pct,
                "Скорость переливов на зачарованных предметах.", syncs));
    }

    private void graphicsOptimization(List<Setting<?>> l) {
        boolean sodium = net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("sodium");
        l.add(OptionBinds.ints("Прорисовка", o.renderDistance(), 2, 32, 1, v -> (int) v + " чанков",
                "Главный ползунок FPS. 8–12 чанков — хороший баланс для PvP.", syncs));
        l.add(OptionBinds.ints("Частота кадров", o.framerateLimit(), 10, 260, 10, v -> v >= 260 ? "Без лимита" : (int) v + " FPS",
                "Лимит под частоту монитора снижает нагрев и шум.", syncs));
        l.add(OptionBinds.choice("Облака", o.cloudStatus(), CloudStatus.values(), cloudLabels(),
                "Облака заметно влияют на FPS на слабых видеокартах.", syncs));
        l.add(OptionBinds.choice("Частицы", o.particles(), ParticleStatus.values(), new String[]{"Все", "Меньше", "Мин."},
                "Количество частиц: дым, искры, взрывы.", syncs));
        l.add(OptionBinds.ints("Смешивание биомов", o.biomeBlendRadius(), 0, 7, 1, v -> v == 0 ? "Выкл" : (int) (v * 2 + 1) + "×" + (int) (v * 2 + 1),
                "Выключи на слабом ПК — быстрее грузятся чанки.", syncs));
        l.add(OptionBinds.doubles("Дальность сущностей", o.entityDistanceScaling(), 0.5, 5.0, 0.25, v -> OptionBinds.pct(v),
                "Меньше — меньше мобов рисуется вдали.", syncs));
        extraLabel = sodium ? "ВСЕ НАСТРОЙКИ SODIUM" : "ВАНИЛЬНЫЕ НАСТРОЙКИ ВИДЕО";
        extraAction = () -> minecraft.setScreen(sodium ? sodiumScreen() : new VideoSettingsScreen(this, minecraft, minecraft.options));
    }

    /** Экран настроек Sodium без жёсткой зависимости: ищем его по имени, иначе — ванильный. */
    private Screen sodiumScreen() {
        String[] candidates = {
                "net.caffeinemc.mods.sodium.client.gui.SodiumOptionsGUI",
                "net.caffeinemc.mods.sodium.client.gui.VideoSettingsScreen",
                "net.caffeinemc.mods.sodium.client.config.ConfigScreen"};
        for (String cls : candidates) {
            try {
                Object scr = Class.forName(cls).getMethod("createScreen", Screen.class).invoke(null, this);
                if (scr instanceof Screen sc) return sc;
            } catch (Throwable ignored) {
            }
        }
        return new VideoSettingsScreen(this, minecraft, minecraft.options);
    }

    private static String[] cloudLabels() {
        CloudStatus[] v = CloudStatus.values();
        String[] out = new String[v.length];
        for (int i = 0; i < v.length; i++) {
            out[i] = switch (v[i].name()) {
                case "OFF" -> "Выкл";
                case "FAST" -> "Быстро";
                case "FANCY" -> "Детально";
                default -> v[i].name();
            };
        }
        return out;
    }

    private void sound(List<Setting<?>> l) {
        for (SoundSource s : SoundSource.values()) {
            String name = switch (s.name()) {
                case "MASTER" -> "Общая громкость";
                case "MUSIC" -> "Музыка";
                case "RECORDS" -> "Проигрыватели";
                case "WEATHER" -> "Погода";
                case "BLOCKS" -> "Блоки";
                case "HOSTILE" -> "Враждебные";
                case "NEUTRAL" -> "Дружелюбные";
                case "PLAYERS" -> "Игроки";
                case "AMBIENT" -> "Окружение";
                case "VOICE" -> "Голос";
                case "UI" -> "Интерфейс";
                default -> s.name();
            };
            l.add(OptionBinds.doubles(name, o.getSoundSourceOptionInstance(s), 0, 1, 0.01, v -> v <= 0 ? "Выкл" : OptionBinds.pct(v),
                    "Громкость: " + name.toLowerCase() + ".", syncs));
        }
    }

    private void controls(List<Setting<?>> l) {
        l.add(OptionBinds.doubles("Чувствительность мыши", o.sensitivity(), 0, 1, 0.005, v -> Math.round(v * 200) + "%",
                "Скорость поворота камеры.", syncs));
        l.add(OptionBinds.bool("Автопрыжок", o.autoJump(), "Сам запрыгивает на блок высотой 1.", syncs));
        l.add(OptionBinds.bool("Бег: переключение", o.toggleSprint(), "Нажал — бежишь, нажал ещё — перестал. Ванильная опция.", syncs));
        l.add(OptionBinds.bool("Присед: переключение", o.toggleCrouch(), "Нажал — присел, нажал ещё — встал.", syncs));
        extraLabel = "НАЗНАЧИТЬ КЛАВИШИ";
        extraAction = () -> minecraft.setScreen(new KeyBindsScreen(this, minecraft.options));
    }

    private void chat(List<Setting<?>> l) {
        l.add(OptionBinds.choice("Чат", o.chatVisibility(), ChatVisiblity.values(), new String[]{"Весь", "Команды", "Скрыт"},
                "Что показывать в чате.", syncs));
        l.add(OptionBinds.bool("Цвета", o.chatColors(), "Цветные сообщения в чате.", syncs));
        l.add(OptionBinds.bool("Ссылки", o.chatLinks(), "Кликабельные ссылки.", syncs));
        l.add(OptionBinds.doubles("Непрозрачность", o.chatOpacity(), 0.1, 1, 0.01, OptionBinds::pct, "Непрозрачность текста чата.", syncs));
        l.add(OptionBinds.doubles("Фон текста", o.textBackgroundOpacity(), 0, 1, 0.01, OptionBinds::pct, "Тёмная подложка под сообщениями.", syncs));
        l.add(OptionBinds.doubles("Размер", o.chatScale(), 0, 1, 0.01, OptionBinds::pct, "Размер текста чата.", syncs));
        l.add(OptionBinds.doubles("Ширина", o.chatWidth(), 0, 1, 0.01, v -> Math.round(40 + v * 280) + " px", "Ширина окна чата.", syncs));
        l.add(OptionBinds.doubles("Межстрочный интервал", o.chatLineSpacing(), 0, 1, 0.01, OptionBinds::pct, "Расстояние между строками.", syncs));
    }

    private void skin(List<Setting<?>> l) {
        net.minecraft.world.entity.player.PlayerModelPart[] parts = {
                net.minecraft.world.entity.player.PlayerModelPart.CAPE, net.minecraft.world.entity.player.PlayerModelPart.JACKET,
                net.minecraft.world.entity.player.PlayerModelPart.LEFT_SLEEVE, net.minecraft.world.entity.player.PlayerModelPart.RIGHT_SLEEVE,
                net.minecraft.world.entity.player.PlayerModelPart.LEFT_PANTS_LEG, net.minecraft.world.entity.player.PlayerModelPart.RIGHT_PANTS_LEG,
                net.minecraft.world.entity.player.PlayerModelPart.HAT};
        String[] names = {"Плащ", "Куртка", "Левый рукав", "Правый рукав", "Левая штанина", "Правая штанина", "Головной убор"};
        for (int i = 0; i < parts.length; i++) {
            var part = parts[i];
            BoolSetting b = new BoolSetting(names[i], names[i], o.isModelPartEnabled(part));
            b.onChange(() -> o.setModelPart(part, b.get()));
            b.hint("Слой скина: " + names[i].toLowerCase() + ".");
            syncs.add(() -> b.setSilently(o.isModelPartEnabled(part)));
            l.add(b);
        }
        l.add(OptionBinds.choice("Ведущая рука", o.mainHand(), net.minecraft.world.entity.HumanoidArm.values(), new String[]{"Левая", "Правая"},
                "В какой руке персонаж держит предмет.", syncs));
    }

    private SettingWidget widgetFor(Setting<?> s) {
        if (s instanceof BoolSetting b) return new ToggleWidget(b, this);
        if (s instanceof SliderSetting sl) return new SliderWidget(sl, this);
        if (s instanceof EnumSetting en) return new EnumWidget(en, this, font);
        if (s instanceof ColorSetting c) return new ColorWidget(c, this);
        if (s instanceof KeySetting k) return new BindWidget(k, this, true);
        throw new IllegalArgumentException(s.getClass().getName());
    }

    // ── Host ─────────────────────────────────────────────────

    @Override public void openPopup(Popup p) { popup = p; }
    @Override public void startListening(BindWidget w) { listening = w; }
    @Override public float scrollOf(Widget w) { return scroll; }

    // ── рисование ────────────────────────────────────────────

    @Override
    protected void renderMenu(double mx, double my, float dt) {
        renderItems(mx, my, dt);
        renderBrand(Math.max(24, Math.round(vw * 0.056f)));

        scroll = Anim.to(scroll, scrollTarget, dt);
        boolean overPopup = popup != null && popup.hit(mx, my);
        boolean inArea = !overPopup && Widget.hit(mx, my, px - 4, py, pw + 8, ph);
        int sc = Math.round(scroll);
        double cmy = inArea ? my + sc : -1e6;

        if (section == Section.PACKS) {
            packs.render(d, mx, my, dt, px, py, pw, ph, fade);
            return;
        }
        if (section == Section.GRAPHICS) renderGraphicsTabs(mx, my, dt);
        else d.text(sectionTitle(), Fonts.Kind.HEAD, px, py - 22, Theme.TEXT_MUTED);
        d.rect(px, py - 8, pw, 1, Theme.DIVIDER);

        SettingWidget hovered = null;
        d.clip(px - 4, py, pw + 8, ph);
        d.g.pose().pushMatrix();
        d.g.pose().translate(0, -sc);
        for (SettingWidget w : widgets) {
            if (w.y + w.h < py + sc || w.y > py + ph + sc) continue;
            w.render(d, mx, cmy, dt);
            if (w.hit(mx, cmy)) hovered = w;
        }
        d.g.pose().popMatrix();
        d.unclip();

        // доп. кнопка раздела
        if (extraAction != null) {
            int ey = py + Math.min(contentH, ph) + 12;
            boolean hov = Widget.hit(mx, my, px, ey - 3, Fonts.width(extraLabel, Fonts.Kind.HEAD), 14);
            extraHover = Anim.to(extraHover, hov ? 1 : 0, dt);
            d.text(extraLabel, Fonts.Kind.HEAD, px, ey, Colors.lerp(Theme.TEXT_DIM, Theme.TEXT, extraHover));
        }

        // подсказка внизу
        int hy = vh - 40;
        if (hovered != null && !hovered.setting().hint().isEmpty()) {
            d.text(hovered.setting().name, Fonts.Kind.BOLD, px, hy, Theme.TEXT_DIM);
            d.text(hovered.setting().hint(), px + Fonts.width(hovered.setting().name, Fonts.Kind.BOLD) + 8, hy, Theme.TEXT_MUTED);
        }

        if (popup != null) {
            if (popup.isClosed()) popup = null;
            else {
                d.g.nextStratum();
                popup.render(d, mx, my, dt);
            }
        }
    }

    private int tabX(int i) {
        int x = px;
        for (int j = 0; j < i; j++) x += Fonts.width(GRAPHICS_TABS[j], Fonts.Kind.HEAD) + 18;
        return x;
    }

    private void renderGraphicsTabs(double mx, double my, float dt) {
        int y = py - 22;
        for (int i = 0; i < GRAPHICS_TABS.length; i++) {
            int x = tabX(i), w = Fonts.width(GRAPHICS_TABS[i], Fonts.Kind.HEAD);
            boolean hov = Widget.hit(mx, my, x - 4, y - 6, w + 8, 20);
            gTabHover[i] = Anim.to(gTabHover[i], hov ? 1 : 0, dt);
            int c = i == gTab ? Theme.TEXT : Colors.lerp(Theme.TEXT_MUTED, Theme.TEXT_DIM, gTabHover[i]);
            d.text(GRAPHICS_TABS[i], Fonts.Kind.HEAD, x, y, c);
        }
        int tx = tabX(gTab), tw = Fonts.width(GRAPHICS_TABS[gTab], Fonts.Kind.HEAD);
        if (ulX < 0) { ulX = tx; ulW = tw; }
        ulX = Anim.to(ulX, tx, dt);
        ulW = Anim.to(ulW, tw, dt);
        d.round(Math.round(ulX), py - 9, Math.round(ulW), 2, 1, Theme.accent());
    }

    private boolean clickGraphicsTabs(double mx, double my) {
        if (section != Section.GRAPHICS) return false;
        int y = py - 22;
        for (int i = 0; i < GRAPHICS_TABS.length; i++) {
            int x = tabX(i), w = Fonts.width(GRAPHICS_TABS[i], Fonts.Kind.HEAD);
            if (Widget.hit(mx, my, x - 4, y - 6, w + 8, 20)) {
                if (gTab != i) {
                    gTab = i;
                    scroll = scrollTarget = 0;
                    build();
                }
                return true;
            }
        }
        return false;
    }

    private String sectionTitle() {
        return switch (section) {
            case GRAPHICS -> "ГРАФИКА";
            case SOUND -> "ЗВУК";
            case CONTROLS -> "УПРАВЛЕНИЕ";
            case CHAT -> "ЧАТ";
            case SKIN -> "НАСТРОЙКИ СКИНА";
            case PACKS -> "РЕСУРСПАКИ";
        };
    }

    // ── ввод ─────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        double mx = e.x() / k, my = e.y() / k;
        int b = e.button();
        if (popup != null) {
            if (popup.mouseClicked(mx, my, b) && !popup.isClosed()) active = popup;
            else popup = null;
            return true;
        }
        if (listening != null) {
            listening.cancel();
            listening = null;
        }
        if (b == 0 && clickItems(mx, my)) return true;
        if (section == Section.PACKS) {
            if (b == 0) packs.click(mx, my, px, py, pw, ph);
            return true;
        }
        if (b == 0 && clickGraphicsTabs(mx, my)) return true;
        if (extraAction != null) {
            int ey = py + Math.min(contentH, ph) + 12;
            if (Widget.hit(mx, my, px, ey - 3, Fonts.width(extraLabel, Fonts.Kind.HEAD), 14)) {
                extraAction.run();
                return true;
            }
        }
        if (Widget.hit(mx, my, px - 4, py, pw + 8, ph)) {
            double cmy = my + scroll;
            for (SettingWidget w : widgets) {
                if (w.mouseClicked(mx, cmy, b)) {
                    active = w;
                    return true;
                }
            }
        }
        return false;
    }

    private double activeY(double my) {
        return active == popup ? my : my + scroll;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        if (active != null) {
            active.mouseReleased(e.x() / k, activeY(e.y() / k));
            active = null;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (active != null) active.mouseDragged(e.x() / k, activeY(e.y() / k));
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double sx, double sy) {
        if (section == Section.PACKS) {
            packs.scroll(sy, ph);
            return true;
        }
        int max = Math.max(0, contentH - ph);
        scrollTarget = (float) Math.max(0, Math.min(max, scrollTarget - sy * ROW_H));
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        int key = e.key();
        if (listening != null) {
            listening.onKey(key);
            listening = null;
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (popup != null) popup = null;
            else onClose();
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(CharacterEvent e) {
        return false;
    }

    @Override
    public void onClose() {
        if (section == Section.PACKS) packs.apply();
        minecraft.options.save();
        if (o != null && o.guiScale().get() != initialGuiScale) minecraft.resizeDisplay();
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}

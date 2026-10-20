package dev.mraz1visual.core;

/** Вкладки верхней панели. Порядок здесь = порядок в окне. */
public enum Category {
    PLAYER("Игрок", "Здесь появятся функции игрока."),
    VISUALS("Визуалы", "Здесь появятся визуалы."),
    COSMETICS("Косметика", "Здесь появится косметика."),
    SWORDS("Мечи", "Мечи и руки."),
    WINGS("Крылья", "Крылья и плащи."),
    PETS("Питомцы", "Питомцы рядом с тобой."),
    SKY("Небо", "Небо и атмосфера."),
    PERFORMANCE("Оптимизация", "Плавность и FPS."),
    MISC("Разное", "Здесь появятся мелочи."),
    MUSIC("Музыка", "Музыка с компьютера."),
    CONFIGS("Configs", "Профили настроек."),
    CLIENT("Настройки", "");

    public final String title;
    public final String emptyText;

    Category(String title, String emptyText) {
        this.title = title;
        this.emptyText = emptyText;
    }
}

package dev.umbra.core;

/** Вкладки верхней панели. Порядок здесь = порядок в окне. */
public enum Category {
    PLAYER("Player", "Здесь появятся визуалы игрока."),
    VISUALS("Visuals", "Здесь появятся визуалы."),
    MISC("Misc", "Здесь появятся мелочи."),
    MUSIC("Music", "Музыка с компьютера."),
    CONFIGS("Configs", "Профили настроек."),
    CLIENT("Настройки", "");

    public final String title;
    public final String emptyText;

    Category(String title, String emptyText) {
        this.title = title;
        this.emptyText = emptyText;
    }
}

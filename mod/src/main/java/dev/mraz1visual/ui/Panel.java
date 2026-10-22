package dev.mraz1visual.ui;

/** Особая вкладка меню (Market, Forum): рисует себя в области под шапкой и сама обрабатывает ввод. */
public interface Panel {
    void render(Draw d, double mx, double my, float dt, int x, int y, int w, int h);

    default boolean click(double mx, double my, int button) { return false; }

    default void release(double mx, double my) {}

    default void drag(double mx, double my) {}

    default boolean scroll(double mx, double my, double amount) { return false; }

    /** Поле ввода в фокусе — печать идёт сюда, а не в поиск меню. */
    default boolean typing() { return false; }

    default boolean key(int key, boolean ctrl) { return false; }

    default boolean chr(int codepoint) { return false; }

    /** Вкладка открыта (обновить данные, если нужно). */
    default void opened() {}
}

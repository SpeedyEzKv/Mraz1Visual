package dev.mraz1visual.ui;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Уведомления клиента для «колокольчика» меню: профиль сохранён, пак установлен, вышло обновление. Последние 20. */
public final class Notices {
    private Notices() {}

    public record Notice(String title, String text, long at) {}

    private static final ArrayDeque<Notice> LIST = new ArrayDeque<>();
    private static int unread;

    public static synchronized void add(String title, String text) {
        LIST.addFirst(new Notice(title, text, System.currentTimeMillis()));
        while (LIST.size() > 20) LIST.removeLast();
        unread++;
    }

    public static synchronized List<Notice> list() {
        return new ArrayList<>(LIST);
    }

    public static synchronized int unread() {
        return unread;
    }

    public static synchronized void markRead() {
        unread = 0;
    }

    /** «5 мин назад». */
    public static String ago(long at) {
        long s = (System.currentTimeMillis() - at) / 1000;
        if (s < 60) return "только что";
        if (s < 3600) return s / 60 + " мин назад";
        if (s < 86400) return s / 3600 + " ч назад";
        return s / 86400 + " дн назад";
    }
}

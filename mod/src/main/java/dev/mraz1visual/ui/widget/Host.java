package dev.mraz1visual.ui.widget;

/** То, что виджет может попросить у окна. */
public interface Host {
    void openPopup(Popup popup);
    void startListening(BindWidget widget);
    /** Сдвиг прокрутки области, где лежит виджет (для привязки попапов к экрану). */
    float scrollOf(Widget w);
}

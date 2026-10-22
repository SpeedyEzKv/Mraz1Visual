# План ночи `v2-multiversion` (рабочий файл — по нему продолжает страховочная задача)

Правила: только клиент и визуал, fair play; в `main` не пушить, без force-push; ничего не удалять вне проекта.
После каждого шага — сборка всех версий (`cd mod && JAVA_HOME=<JDK 25+> ./gradlew build`). Не выходит за 3–4 попытки — откатить, записать, дальше.
Маленькие коммиты, пуш после этапа и каждые ~30 минут. Не делаем: ESP, Skeleton ESP, Trajectories, Tape Mouse, Offhand Totem, Click Pearl, Elytra Flight, RP Spoofer.

Отметка: [x] — сделано и закоммичено.

## 1. Разведка
- [x] Версии 1.21.11 → 26.3, зависимости (VERSIONS.md)

## 2. Мультиверсия
- [x] Stonecutter 0.9.8 + loom-back-compat, 1.21.11 зелёная
- [ ] 26.1.2: замены и `//? if`, compat-слой
- [ ] 26.2, 26.3
- [ ] CI-матрица (`build.yml` на ветки/PR), release.yml собирает все версии, manifest: версия → файл → sha256 → loader → fabric api

## 3. GUI по референсу (iOS 26 / Liquid Glass)
- [ ] Вкладки сверху с «каплей» (Player, Visuals, Shaders, Misc, Staff, Cosmetics, Market, Forum, Music, Configs), справа поиск/настройки/друзья/уведомления/аватар
- [ ] Список модулей слева, баннер-превью (параллакс, «● ПРЕДПРОСМОТР», клавиша, переключатель), карточки настроек
- [ ] Скрыть исключённые модули

## 4. Превью модулей
- [ ] Offscreen-сцена (кирпич, фонари, ночь, манекены), сценарии 3–6 с, лимит 60 FPS; запасной вариант — анимированная иллюстрация

## 5. Гардероб
- [ ] Категории со счётчиками, фильтр цветов, сетка, 3D-витрина, примерка при наведении, поворот/анимации

## 6. Шейдеры
- [ ] Mraz1 PostFX: bloom (dual Kawase), LUT-пресеты, ACES/AgX, SSAO-lite, god rays, DOF, туман, виньетка, grain, CA, под водой, motion blur
- [ ] Пак Mraz1 Shaders для Iris + автоустановка; вкладка «Шейдеры»

## 7. Market
- [ ] Modrinth API: поиск, сортировки, фильтр, «только текущая версия», установка; вкладка «Mraz1»

## 8. Лаунчер
- [ ] Выбор версии с бейджами, автоустановка Fabric Loader/API, Sodium/Iris по галочке, sha256, профили, лента новостей

## 9. Прочее
- [ ] Forum (GitHub Discussions, Device Flow) или «Скоро»; Staff Wheel; Phrase Wheel; Minimap; Replays; Voice Chat

## 10. Финал
- [ ] Замеры FPS 1.21.11 и 26.3, `screenshots/v2/`, MORNING_REPORT.md, PR v2-multiversion → main (не мержить)

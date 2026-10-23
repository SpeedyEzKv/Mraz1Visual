# Mraz1Visual

Визуальный клиент для Minecraft (Fabric) и лаунчер к нему. Версии игры: 1.21.11 (рекомендуется), 26.1–26.2 (бета) и 26.3 (новая), см. `VERSIONS.md`.
Что умеет клиент — `FEATURES.md`.

| Папка | Что внутри |
|---|---|
| `mod/` | Мод (Java, Fabric). Подробности — `mod/README.md` |
| `launcher/` | Лаунчер (Tauri, Windows) |
| `release/manifest.json` | Список модов и «Что нового» для лаунчера |
| `.github/workflows/release.yml` | Автосборка и выпуск |

## Как выпустить новую версию
1. Залей изменения в ветку `main` (GitHub Desktop → Commit → Push).
2. Вкладка **Actions → Release** — ждём зелёную галочку (~10–15 минут).
3. Готово: во вкладке **Releases** появится `Mraz1Visual-setup.exe`, jar под каждую версию (`mraz1visual-<версия>+mc<версия игры>.jar`), `mraz1visual.jar` (1.21.11, для старых лаунчеров) и `manifest.json`.
   Лаунчер у всех сам скачает новый `mraz1visual.jar` при следующем «Играть».

Если сборка красная — открой упавший шаг, скопируй текст ошибки.

## Локально (необязательно)
- Мод: нужен JDK 25+ (Gradle и Loom 1.19). `cd mod` → `gradlew.bat build` → `build/libs/all/mraz1visual-*+mc*.jar` (все версии). Одна версия: `gradlew.bat :26.1.2:build`.
- Проверка миксинов по версиям: `gradlew.bat checkMixins` (отчёт — `versions/<версия>/build/mixincheck.txt`).
- Лаунчер: `cd launcher` → `npm install` → `npx tauri build`.

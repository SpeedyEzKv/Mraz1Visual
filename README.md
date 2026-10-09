# Umbra

Визуальный клиент для Minecraft 1.21.11 (Fabric) и лаунчер к нему.

| Папка | Что внутри |
|---|---|
| `mod/` | Мод (Java, Fabric). Подробности — `mod/README.md` |
| `launcher/` | Лаунчер (Tauri, Windows) |
| `release/manifest.json` | Список модов и «Что нового» для лаунчера |
| `.github/workflows/release.yml` | Автосборка и выпуск |

## Как выпустить новую версию
1. Залей изменения в ветку `main` (GitHub Desktop → Commit → Push).
2. Вкладка **Actions → Release** — ждём зелёную галочку (~10–15 минут).
3. Готово: во вкладке **Releases** появится `Umbra-setup.exe`, `umbra.jar`, `manifest.json`.
   Лаунчер у всех сам скачает новый `umbra.jar` при следующем «Играть».

Если сборка красная — открой упавший шаг, скопируй текст ошибки.

## Локально (необязательно)
- Мод: `cd mod` → `gradlew.bat build` → `build/libs/umbra-*.jar`.
- Лаунчер: `cd launcher` → `npm install` → `npx tauri build`.

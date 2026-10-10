# Mraz1Visual Launcher

Tauri 2 (Rust + HTML). Ставит Java 21, Minecraft 1.21.11, Fabric, Fabric API, Sodium, Iris и Mraz1Visual, потом запускает игру.

## Где лежит игра
`%APPDATA%\Mraz1Visual`: runtime (Java), libraries, assets, versions, game (mods, config, сохранения), logs, launcher.json.
Твои моды кладёшь в `game\mods` — лаунчер их не трогает, обновляет только свои (список в `game\.mraz1visual-managed.json`).

## Сборка exe
Вариант А — без установки чего-либо (рекомендую):
1. Залей папку на GitHub.
2. Actions → «Build launcher» → Run workflow.
3. Через ~10 минут в Artifacts будет `Mraz1Visual_0.1.0_x64-setup.exe` и голый `mraz1visual-launcher.exe`.

Вариант Б — у себя: Node 22, Rust (rustup), Visual Studio Build Tools (C++), затем
```
npm install
npx tauri build
```

## Обновления мода через GitHub Releases
1. В `src-tauri/src/manifest.rs` поменяй `GITHUB_REPO` на `твой-ник/mraz1visual`.
2. В каждый релиз репозитория прикладывай `mraz1visual.jar` и `manifest.json` (пример — `manifest.example.json`).
3. Лаунчер при старте читает `releases/latest/download/manifest.json`, качает новое и удаляет старые версии своих модов.

Пока репозиторий не указан: лаунчер ставит игру, Fabric API, Sodium, Iris, а `mraz1visual.jar` нужно один раз положить в `game\mods` руками.

## Вход
Пока без входа: игра запускается локальной сессией (одиночная игра). Вход через Microsoft — следующая итерация.

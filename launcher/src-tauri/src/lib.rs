//! Лаунчер Mraz1Visual: ставит Java, Minecraft 1.21.11, Fabric, моды и запускает игру.

mod install;
mod launch;
mod manifest;
mod market;
mod discord;
mod media;
mod mods;
mod net;
mod paths;
mod settings;

use manifest::Manifest;
use paths::Paths;
use serde::Serialize;
use settings::Settings;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Mutex;
use tauri::{AppHandle, Emitter, Manager, State};
use tokio::io::{AsyncBufReadExt, AsyncWriteExt, BufReader};

struct AppState {
    paths: Paths,
    settings: Mutex<Settings>,
    manifest: Mutex<Option<Manifest>>,
    busy: AtomicBool,
    media: tokio::sync::OnceCell<Option<media::Media>>,
}

#[derive(Serialize, Clone)]
struct ProgressEvent {
    percent: f32,
    label: String,
}

#[derive(Serialize)]
struct LauncherInfo {
    version: String,
    build: u32,
    minecraft: String,
    changelog: Vec<String>,
    releases: Vec<manifest::Release>,
    links: Vec<manifest::Link>,
    online: bool,
    ram_mb: u64,
    total_ram_mb: u64,
    free_ram_mb: u64,
    close_on_launch: bool,
    game_dir: String,
    account: String,
    has_mraz1visual: bool,
    /// Ссылка на новый установщик лаунчера, если он вышел.
    launcher_update: Option<String>,
    /// Версии Minecraft на выбор: (версия, статус recommended/new/beta).
    versions: Vec<(String, String)>,
    selected: String,
    sodium: bool,
    iris: bool,
    optimize: bool,
    gc: String,
    /// Моды профиля «Оптимизация» (для подсказки в настройках).
    optimization_mods: Vec<&'static str>,
}

/// Выбранная версия: из настроек, если она есть в manifest, иначе рекомендуемая.
fn selected_version(m: &Manifest, s: &Settings) -> String {
    let choices = m.choices();
    if !s.mc_version.is_empty() && choices.iter().any(|(v, _)| v == &s.mc_version) {
        return s.mc_version.clone();
    }
    m.recommended()
}

struct EmitProgress(AppHandle);

impl install::Progress for EmitProgress {
    fn report(&self, percent: f32, label: &str) {
        let _ = self.0.emit("progress", ProgressEvent { percent, label: label.to_string() });
    }
}

fn memory() -> (u64, u64) {
    let mut sys = sysinfo::System::new();
    sys.refresh_memory();
    (sys.total_memory() / 1024 / 1024, sys.available_memory() / 1024 / 1024)
}

#[tauri::command]
async fn info(state: State<'_, AppState>) -> Result<LauncherInfo, String> {
    let cached = state.manifest.lock().unwrap().clone();
    let (m, online) = match cached {
        Some(m) => (m, true),
        None => {
            let (m, online) = Manifest::fetch(&net::client()).await;
            *state.manifest.lock().unwrap() = Some(m.clone());
            discord::set_app_id(&m.discord_app_id);
            discord::launcher();
            (m, online)
        }
    };
    let s = state.settings.lock().unwrap().clone();
    let (total, free) = memory();
    Ok(LauncherInfo {
        version: m.client_version.clone(),
        build: m.build,
        minecraft: m.minecraft.clone(),
        changelog: m.changelog.clone(),
        releases: m.releases.clone(),
        links: m.links.clone(),
        online,
        ram_mb: s.ram_mb,
        total_ram_mb: total,
        free_ram_mb: free,
        close_on_launch: s.close_on_launch,
        game_dir: state.paths.root.to_string_lossy().to_string(),
        account: launch::current_account(&state.paths).name,
        has_mraz1visual: install::has_mraz1visual(&state.paths),
        launcher_update: (!m.launcher_url.is_empty() && manifest::newer(&m.launcher_version, env!("CARGO_PKG_VERSION")))
            .then(|| m.launcher_url.clone()),
        versions: m.choices(),
        selected: selected_version(&m, &s),
        sodium: s.sodium,
        iris: s.iris,
        optimize: s.optimize,
        gc: s.gc.clone(),
        optimization_mods: manifest::OPTIMIZATION.iter().map(|(_, n)| *n).collect(),
    })
}

#[tauri::command]
fn set_settings(state: State<'_, AppState>, ram_mb: u64, close_on_launch: bool) -> Result<(), String> {
    let (total, _) = memory();
    let mut s = state.settings.lock().unwrap();
    s.ram_mb = ram_mb.clamp(1024, total.saturating_sub(1024).max(2048));
    s.close_on_launch = close_on_launch;
    s.save(&state.paths.settings_file()).map_err(|e| e.to_string())
}

/// Выбор версии Minecraft и дополнений (Sodium, Iris).
#[tauri::command]
fn set_profile(state: State<'_, AppState>, mc_version: String, sodium: bool, iris: bool) -> Result<(), String> {
    let mut s = state.settings.lock().unwrap();
    s.mc_version = mc_version;
    s.sodium = sodium;
    s.iris = iris && sodium;
    s.save(&state.paths.settings_file()).map_err(|e| e.to_string())
}

/// Профиль «Оптимизация» и сборщик мусора.
#[tauri::command]
fn set_perf(state: State<'_, AppState>, optimize: bool, gc: String) -> Result<(), String> {
    let mut s = state.settings.lock().unwrap();
    s.optimize = optimize;
    s.gc = if gc == "zgc" { gc } else { "g1".into() };
    s.save(&state.paths.settings_file()).map_err(|e| e.to_string())
}

#[tauri::command]
fn open_folder(state: State<'_, AppState>) -> Result<(), String> {
    let _ = std::fs::create_dir_all(&state.paths.root);
    open::that(&state.paths.root).map_err(|e| e.to_string())
}

#[tauri::command]
fn open_url(url: String) -> Result<(), String> {
    if !(url.starts_with("https://") || url.starts_with("http://")) {
        return Err("bad url".into());
    }
    open::that(url).map_err(|e| e.to_string())
}

/// Установить/обновить всё и запустить игру. Прогресс — событиями "progress".
/// Потом события "game-started" (окно игры появилось) и "game-exited".
#[tauri::command]
async fn play(app: AppHandle, state: State<'_, AppState>) -> Result<(), String> {
    if state.busy.swap(true, Ordering::SeqCst) {
        return Err("уже запускается".into());
    }
    let result = play_inner(&app, &state).await;
    if result.is_err() {
        state.busy.store(false, Ordering::SeqCst);
    }
    result.map_err(|e| format!("{e:#}"))
}

async fn play_inner(app: &AppHandle, state: &State<'_, AppState>) -> anyhow::Result<()> {
    let manifest = {
        let cached = state.manifest.lock().unwrap().clone();
        match cached {
            Some(m) => m,
            None => Manifest::fetch(&net::client()).await.0,
        }
    };
    let progress = EmitProgress(app.clone());
    // профиль выбранной версии: свои моды и миры, общий конфиг клиента
    let (mc, sodium, iris, optimize) = {
        let s = state.settings.lock().unwrap();
        (selected_version(&manifest, &s), s.sodium, s.iris, s.optimize)
    };
    let mut manifest = manifest.resolve(&mc, sodium, iris);
    if optimize {
        manifest = manifest.with_optimization();
    }
    let paths = state.paths.for_version(&mc);
    let installed = install::install(&paths, &manifest, &progress).await?;
    install::sync_config(&state.paths.main_game(), &paths.game());
    if !install::has_mraz1visual(&paths) {
        anyhow::bail!("В папке mods нет mraz1visual.jar. Собери мод (gradlew build) и положи jar в папку игры → game/mods, либо укажи GitHub-репозиторий в лаунчере.");
    }

    let (ram, close, gc) = {
        let s = state.settings.lock().unwrap();
        (s.ram_mb, s.close_on_launch, s.gc.clone())
    };
    let account = launch::current_account(&state.paths);
    // мост «Сейчас играет»: порт и токен уходят в игру системными свойствами
    let mut extra = Vec::new();
    if !manifest.discord_app_id.is_empty() {
        extra.push(format!("-Dmraz1visual.discord={}", manifest.discord_app_id.trim()));
    }
    if !manifest.github_client_id.trim().is_empty() {
        extra.push(format!("-Dmraz1visual.github={}", manifest.github_client_id.trim()));
    }
    if let Some(m) = state.media.get_or_init(media::start).await {
        extra.push(format!("-Dmraz1visual.media.port={}", m.port));
        extra.push(format!("-Dmraz1visual.media.token={}", m.token));
    }
    let jvm = launch::JvmOptions { ram_mb: ram, java: manifest.java, zgc: gc == "zgc" };
    let mut cmd = launch::build_command(&paths, &installed, &jvm, &account, &extra)?;
    launch::ensure_logs(&state.paths)?;
    let mut child = cmd.spawn()?;
    discord::clear();
    let _ = app.emit("progress", ProgressEvent { percent: 100.0, label: "Запуск".into() });

    let stdout = child.stdout.take();
    let stderr = child.stderr.take();
    let log_path = launch::log_path(&state.paths);
    let app2 = app.clone();
    let app_handle = app.clone();
    let (game_dir, main_game) = (paths.game(), state.paths.main_game());

    // stderr читаем отдельно и параллельно, иначе переполненный канал подвесит игру
    if let Some(err) = stderr {
        let err_path = log_path.with_file_name("game-err.log");
        tokio::spawn(async move {
            let mut f = tokio::fs::File::create(&err_path).await.ok();
            let mut lines = BufReader::new(err).lines();
            while let Ok(Some(line)) = lines.next_line().await {
                if let Some(f) = f.as_mut() {
                    let _ = f.write_all(line.as_bytes()).await;
                    let _ = f.write_all(b"\n").await;
                }
            }
        });
    }

    tokio::spawn(async move {
        let mut log = tokio::fs::File::create(&log_path).await.ok();
        let mut started = false;
        if let Some(out) = stdout {
            let mut lines = BufReader::new(out).lines();
            while let Ok(Some(line)) = lines.next_line().await {
                if let Some(f) = log.as_mut() {
                    let _ = f.write_all(line.as_bytes()).await;
                    let _ = f.write_all(b"\n").await;
                }
                // окно игры создано — пора убирать заставку
                if !started && (line.contains("Backend library: LWJGL") || line.contains("OpenAL initialized") || line.contains("Sound engine started")) {
                    started = true;
                    let _ = app2.emit("game-started", ());
                    if let Some(w) = app2.get_webview_window("main") {
                        let _ = w.hide();
                    }
                }
            }
        }
        let code = child.wait().await.ok().and_then(|s| s.code()).unwrap_or(-1);
        // настройки, изменённые в этой версии, — обратно в общий конфиг
        install::sync_config(&game_dir, &main_game);
        discord::launcher();
        let state = app_handle.state::<AppState>();
        state.busy.store(false, Ordering::SeqCst);
        if close {
            app_handle.exit(0);
            return;
        }
        if let Some(w) = app_handle.get_webview_window("main") {
            let _ = w.show();
            let _ = w.set_focus();
        }
        let _ = app_handle.emit("game-exited", code);
    });
    Ok(())
}

fn mc_version(state: &State<'_, AppState>) -> String {
    let m = state.manifest.lock().unwrap().clone();
    let s = state.settings.lock().unwrap().clone();
    m.map(|m| selected_version(&m, &s)).unwrap_or_else(|| "1.21.11".into())
}

#[tauri::command]
async fn market_search(state: State<'_, AppState>, kind: String, query: String, sort: String, offset: u32) -> Result<Vec<market::Hit>, String> {
    let k = market::Kind::parse(&kind).map_err(|e| e.to_string())?;
    market::search(&state.paths.for_version(&mc_version(&state)), &mc_version(&state), k, &query, &sort, offset).await.map_err(|e| format!("{e:#}"))
}

#[tauri::command]
async fn market_install(state: State<'_, AppState>, kind: String, project: String) -> Result<String, String> {
    let k = market::Kind::parse(&kind).map_err(|e| e.to_string())?;
    market::install(&state.paths.for_version(&mc_version(&state)), &mc_version(&state), k, &project).await.map_err(|e| format!("{e:#}"))
}

#[tauri::command]
fn market_uninstall(state: State<'_, AppState>, kind: String, project: String) -> Result<(), String> {
    let k = market::Kind::parse(&kind).map_err(|e| e.to_string())?;
    market::uninstall(&state.paths.for_version(&mc_version(&state)), k, &project).map_err(|e| e.to_string())
}

#[tauri::command]
fn market_local(state: State<'_, AppState>, kind: String) -> Result<Vec<market::LocalItem>, String> {
    let k = market::Kind::parse(&kind).map_err(|e| e.to_string())?;
    Ok(market::list_local(&state.paths.for_version(&mc_version(&state)), k))
}

#[tauri::command]
fn market_delete_local(state: State<'_, AppState>, kind: String, file: String) -> Result<(), String> {
    let k = market::Kind::parse(&kind).map_err(|e| e.to_string())?;
    market::delete_local(&state.paths.for_version(&mc_version(&state)), k, &file).map_err(|e| e.to_string())
}

/// Папка игры выбранной версии и её Fabric Loader (для проверки модов).
fn selected_profile(state: &State<'_, AppState>) -> (Paths, String, String) {
    let mc = mc_version(state);
    let loader = state.manifest.lock().unwrap().as_ref().map(|m| m.resolve(&mc, false, false).fabric_loader).unwrap_or_default();
    (state.paths.for_version(&mc), mc, loader)
}

/// Кнопка «Моды»: папка mods выбранной версии в проводнике (нет — создаётся).
#[tauri::command]
fn open_mods_folder(state: State<'_, AppState>) -> Result<(), String> {
    let (paths, _, _) = selected_profile(&state);
    std::fs::create_dir_all(paths.mods()).map_err(|e| e.to_string())?;
    open::that(paths.mods()).map_err(|e| e.to_string())
}

#[derive(Serialize)]
struct ModsView {
    minecraft: String,
    folder: String,
    items: Vec<mods::ModInfo>,
}

#[tauri::command]
fn mods_list(state: State<'_, AppState>) -> ModsView {
    let (paths, mc, loader) = selected_profile(&state);
    ModsView { items: mods::list(&paths, &mc, &loader), folder: paths.mods().to_string_lossy().to_string(), minecraft: mc }
}

#[tauri::command]
fn mods_toggle(state: State<'_, AppState>, file: String, enabled: bool) -> Result<(), String> {
    mods::set_enabled(&selected_profile(&state).0, &file, enabled).map_err(|e| e.to_string())
}

#[tauri::command]
fn mods_delete(state: State<'_, AppState>, file: String) -> Result<(), String> {
    mods::delete(&selected_profile(&state).0, &file).map_err(|e| e.to_string())
}

#[tauri::command]
fn mods_add(state: State<'_, AppState>, files: Vec<String>) -> Result<Vec<String>, String> {
    mods::add(&selected_profile(&state).0, &files).map_err(|e| e.to_string())
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    let paths = Paths::new();
    let settings = Settings::load(&paths.settings_file());
    tauri::Builder::default()
        .manage(AppState {
            paths,
            settings: Mutex::new(settings),
            manifest: Mutex::new(None),
            busy: AtomicBool::new(false),
            media: tokio::sync::OnceCell::new(),
        })
        .invoke_handler(tauri::generate_handler![info, set_settings, set_profile, set_perf, open_folder, open_url, play, market_search, market_install, market_uninstall, market_local, market_delete_local,
            open_mods_folder, mods_list, mods_toggle, mods_delete, mods_add])
        .run(tauri::generate_context!())
        .expect("error while running Mraz1Visual launcher");
}

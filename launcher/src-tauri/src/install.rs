//! Установка и обновление: Java 21, Minecraft, Fabric, моды.
//! Всё скачанное проверяется; уже скачанное не качается повторно.

use crate::manifest::Manifest;
use crate::net::{self, Job};
use crate::paths::Paths;
use anyhow::{anyhow, bail, Context, Result};
use serde::{Deserialize, Serialize};
use serde_json::Value;
use std::collections::HashSet;
use std::path::{Path, PathBuf};

const VERSION_MANIFEST: &str = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
const ASSETS_BASE: &str = "https://resources.download.minecraft.net";
const FABRIC_META: &str = "https://meta.fabricmc.net/v2";

/// Этапы и их вес в общем прогрессе (в сумме 100).
#[derive(Clone, Copy)]
pub enum Stage {
    Java,
    Game,
    Assets,
    Mods,
}

impl Stage {
    fn range(self) -> (f32, f32) {
        match self {
            Stage::Java => (0.0, 25.0),
            Stage::Game => (25.0, 50.0),
            Stage::Assets => (50.0, 85.0),
            Stage::Mods => (85.0, 100.0),
        }
    }
    pub fn label(self) -> &'static str {
        match self {
            Stage::Java => "Java",
            Stage::Game => "Minecraft",
            Stage::Assets => "Ресурсы игры",
            Stage::Mods => "Моды",
        }
    }
}

pub trait Progress: Send + Sync {
    fn report(&self, percent: f32, label: &str);
}

fn report(p: &dyn Progress, stage: Stage, done: usize, total: usize) {
    let (a, b) = stage.range();
    let f = if total == 0 { 1.0 } else { done as f32 / total as f32 };
    p.report(a + (b - a) * f, stage.label());
}

/// Готовый к запуску набор.
#[derive(Serialize, Deserialize, Clone)]
pub struct Installed {
    pub java: PathBuf,
    pub main_class: String,
    pub classpath: Vec<PathBuf>,
    pub client_jar: PathBuf,
    pub version_json: PathBuf,
    pub fabric_profile: PathBuf,
    pub asset_index: String,
    pub version_name: String,
}

pub async fn install(paths: &Paths, manifest: &Manifest, progress: &dyn Progress) -> Result<Installed> {
    let client = net::client();
    let java = ensure_java(&client, paths, manifest.java.max(21), progress).await.context("Java")?;
    let (vjson_path, vjson) = ensure_version_json(&client, paths, &manifest.minecraft).await.context("версия Minecraft")?;
    let (fabric_path, fabric) = ensure_fabric_profile(&client, paths, manifest).await.context("Fabric")?;

    // клиент + библиотеки (ванильные и Fabric), с дедупликацией по group:artifact[:classifier]
    let mut jobs = Vec::new();
    let client_dl = &vjson["downloads"]["client"];
    let client_jar = paths.versions().join(&manifest.minecraft).join(format!("{}.jar", manifest.minecraft));
    jobs.push(Job {
        url: str_of(client_dl, "url")?,
        path: client_jar.clone(),
        sha1: client_dl["sha1"].as_str().map(String::from),
        size: client_dl["size"].as_u64(),
    });

    let mut classpath: Vec<PathBuf> = Vec::new();
    let mut seen: HashSet<String> = HashSet::new();
    // Fabric первым: его версии библиотек (asm и т.п.) главнее ванильных
    for lib in fabric["libraries"].as_array().cloned().unwrap_or_default() {
        let name = str_of(&lib, "name")?;
        let base = lib["url"].as_str().unwrap_or("https://maven.fabricmc.net/");
        let rel = maven_path(&name)?;
        let path = paths.libraries().join(&rel);
        seen.insert(lib_key(&name));
        jobs.push(Job {
            url: format!("{}/{}", base.trim_end_matches('/'), rel.replace('\\', "/")),
            path: path.clone(),
            sha1: lib["sha1"].as_str().map(String::from),
            size: lib["size"].as_u64(),
        });
        classpath.push(path);
    }
    for lib in vjson["libraries"].as_array().cloned().unwrap_or_default() {
        if !rules_allow(&lib["rules"]) {
            continue;
        }
        let name = str_of(&lib, "name")?;
        if !seen.insert(lib_key(&name)) {
            continue;
        }
        let art = &lib["downloads"]["artifact"];
        if art.is_null() {
            continue;
        }
        let path = paths.libraries().join(str_of(art, "path")?);
        jobs.push(Job {
            url: str_of(art, "url")?,
            path: path.clone(),
            sha1: art["sha1"].as_str().map(String::from),
            size: art["size"].as_u64(),
        });
        classpath.push(path);
    }
    net::fetch_all(&client, jobs, 12, |d, t| report(progress, Stage::Game, d, t)).await?;

    // ресурсы
    let asset_index = ensure_assets(&client, paths, &vjson, progress).await.context("ресурсы")?;

    // моды
    ensure_mods(&client, paths, manifest, progress).await.context("моды")?;

    // видео-фон меню: не критично — при ошибке игра запускается с заставкой
    if let Err(e) = ensure_video(&client, paths, manifest, progress).await {
        eprintln!("menu video: {e:#}");
    }

    let main_class = str_of(&fabric, "mainClass")?;
    let version_name = str_of(&fabric, "id").unwrap_or_else(|_| format!("fabric-{}", manifest.minecraft));
    Ok(Installed {
        java,
        main_class,
        classpath,
        client_jar,
        version_json: vjson_path,
        fabric_profile: fabric_path,
        asset_index,
        version_name,
    })
}

// ── Java ─────────────────────────────────────────────────────

fn java_exe(dir: &Path) -> Option<PathBuf> {
    // архив Adoptium распаковывается в папку вида jdk-21.0.x+y-jre
    let exe = if cfg!(windows) { "javaw.exe" } else { "java" };
    for entry in std::fs::read_dir(dir).ok()?.flatten() {
        let p = entry.path().join("bin").join(exe);
        if p.exists() {
            return Some(p);
        }
    }
    None
}

async fn ensure_java(client: &reqwest::Client, paths: &Paths, major: u32, progress: &dyn Progress) -> Result<PathBuf> {
    let dir = paths.runtime().join(format!("java{major}"));
    if let Some(p) = java_exe(&dir) {
        report(progress, Stage::Java, 1, 1);
        return Ok(p);
    }
    let (os, ext) = if cfg!(windows) { ("windows", "zip") } else if cfg!(target_os = "macos") { ("mac", "tar.gz") } else { ("linux", "tar.gz") };
    if ext != "zip" {
        bail!("автоустановка Java сейчас только для Windows");
    }
    let arch = if cfg!(target_arch = "aarch64") { "aarch64" } else { "x64" };
    let url = format!("https://api.adoptium.net/v3/binary/latest/{major}/ga/{os}/{arch}/jre/hotspot/normal/eclipse");
    let archive = paths.runtime().join(format!("java{major}.zip"));
    report(progress, Stage::Java, 0, 2);
    net::fetch(client, &Job { url, path: archive.clone(), sha1: None, size: None }).await?;
    report(progress, Stage::Java, 1, 2);
    let dir2 = dir.clone();
    let archive2 = archive.clone();
    tokio::task::spawn_blocking(move || unzip(&archive2, &dir2)).await??;
    let _ = std::fs::remove_file(&archive);
    report(progress, Stage::Java, 2, 2);
    java_exe(&dir).ok_or_else(|| anyhow!("java не найдена после распаковки"))
}

fn unzip(archive: &Path, dest: &Path) -> Result<()> {
    std::fs::create_dir_all(dest)?;
    let file = std::fs::File::open(archive)?;
    let mut zip = zip::ZipArchive::new(file)?;
    for i in 0..zip.len() {
        let mut entry = zip.by_index(i)?;
        let Some(rel) = entry.enclosed_name() else { continue };
        let out = dest.join(rel);
        if entry.is_dir() {
            std::fs::create_dir_all(&out)?;
        } else {
            if let Some(p) = out.parent() {
                std::fs::create_dir_all(p)?;
            }
            let mut f = std::fs::File::create(&out)?;
            std::io::copy(&mut entry, &mut f)?;
        }
    }
    Ok(())
}

/// Кадры видео-фона в game/mraz1visual/video (мод читает их оттуда).
async fn ensure_video(client: &reqwest::Client, paths: &Paths, m: &Manifest, progress: &dyn Progress) -> Result<()> {
    let Some(v) = &m.video else { return Ok(()) };
    if v.url.is_empty() {
        return Ok(());
    }
    let dir = paths.game().join("mraz1visual").join("video");
    let stamp = dir.join(".version");
    if std::fs::read_to_string(&stamp).map(|t| t.trim() == v.version).unwrap_or(false) && dir.join("video.json").exists() {
        return Ok(());
    }
    progress.report(99.0, "Видео-фон меню");
    let archive = paths.game().join("mraz1visual").join("menu-video.zip");
    std::fs::create_dir_all(archive.parent().unwrap())?;
    let _ = std::fs::remove_file(&archive);
    net::fetch(client, &Job { url: v.url.clone(), path: archive.clone(), sha1: None, size: None }).await?;
    let (dir2, archive2) = (dir.clone(), archive.clone());
    tokio::task::spawn_blocking(move || -> Result<()> {
        let _ = std::fs::remove_dir_all(&dir2);
        unzip(&archive2, &dir2)
    })
    .await??;
    let _ = std::fs::remove_file(&archive);
    std::fs::write(&stamp, &v.version)?;
    Ok(())
}

// ── Minecraft ────────────────────────────────────────────────

async fn ensure_version_json(client: &reqwest::Client, paths: &Paths, mc: &str) -> Result<(PathBuf, Value)> {
    let path = paths.versions().join(mc).join(format!("{mc}.json"));
    if let Ok(text) = std::fs::read_to_string(&path) {
        if let Ok(v) = serde_json::from_str::<Value>(&text) {
            return Ok((path, v));
        }
    }
    let manifest = net::get_json(client, VERSION_MANIFEST).await?;
    let entry = manifest["versions"]
        .as_array()
        .and_then(|a| a.iter().find(|v| v["id"].as_str() == Some(mc)))
        .ok_or_else(|| anyhow!("версия {mc} не найдена у Mojang"))?;
    let url = str_of(entry, "url")?;
    let v = net::get_json(client, &url).await?;
    write_json(&path, &v)?;
    Ok((path, v))
}

async fn ensure_assets(client: &reqwest::Client, paths: &Paths, vjson: &Value, progress: &dyn Progress) -> Result<String> {
    let idx = &vjson["assetIndex"];
    let id = str_of(idx, "id")?;
    let idx_path = paths.assets().join("indexes").join(format!("{id}.json"));
    let index: Value = match std::fs::read_to_string(&idx_path).ok().and_then(|t| serde_json::from_str(&t).ok()) {
        Some(v) => v,
        None => {
            let v = net::get_json(client, &str_of(idx, "url")?).await?;
            write_json(&idx_path, &v)?;
            v
        }
    };
    let mut jobs = Vec::new();
    let mut uniq = HashSet::new();
    if let Some(objs) = index["objects"].as_object() {
        for obj in objs.values() {
            let hash = str_of(obj, "hash")?;
            if !uniq.insert(hash.clone()) {
                continue;
            }
            let sub = &hash[..2];
            jobs.push(Job {
                url: format!("{ASSETS_BASE}/{sub}/{hash}"),
                path: paths.assets().join("objects").join(sub).join(&hash),
                sha1: Some(hash.clone()),
                size: obj["size"].as_u64(),
            });
        }
    }
    net::fetch_all(client, jobs, 24, |d, t| report(progress, Stage::Assets, d, t)).await?;
    Ok(id)
}

// ── Fabric ───────────────────────────────────────────────────

async fn ensure_fabric_profile(client: &reqwest::Client, paths: &Paths, m: &Manifest) -> Result<(PathBuf, Value)> {
    let loader = if m.fabric_loader.is_empty() {
        let list = net::get_json(client, &format!("{FABRIC_META}/versions/loader/{}", m.minecraft)).await;
        match list {
            Ok(v) => v
                .as_array()
                .and_then(|a| a.iter().find(|e| e["loader"]["stable"].as_bool() == Some(true)).or(a.first()))
                .and_then(|e| e["loader"]["version"].as_str().map(String::from))
                .ok_or_else(|| anyhow!("Fabric Loader для {} не найден", m.minecraft))?,
            Err(e) => {
                // без сети — берём последний сохранённый профиль
                if let Some(found) = find_cached_fabric(paths, &m.minecraft) {
                    return Ok(found);
                }
                return Err(e);
            }
        }
    } else {
        m.fabric_loader.clone()
    };
    let name = format!("fabric-loader-{loader}-{}", m.minecraft);
    let path = paths.versions().join(&name).join(format!("{name}.json"));
    if let Ok(text) = std::fs::read_to_string(&path) {
        if let Ok(v) = serde_json::from_str::<Value>(&text) {
            return Ok((path, v));
        }
    }
    let v = net::get_json(client, &format!("{FABRIC_META}/versions/loader/{}/{loader}/profile/json", m.minecraft)).await?;
    write_json(&path, &v)?;
    Ok((path, v))
}

fn find_cached_fabric(paths: &Paths, mc: &str) -> Option<(PathBuf, Value)> {
    let suffix = format!("-{mc}");
    for e in std::fs::read_dir(paths.versions()).ok()?.flatten() {
        let n = e.file_name().to_string_lossy().to_string();
        if n.starts_with("fabric-loader-") && n.ends_with(&suffix) {
            let p = e.path().join(format!("{n}.json"));
            if let Ok(t) = std::fs::read_to_string(&p) {
                if let Ok(v) = serde_json::from_str(&t) {
                    return Some((p, v));
                }
            }
        }
    }
    None
}

// ── моды ─────────────────────────────────────────────────────

#[derive(Serialize, Deserialize, Default)]
struct Managed {
    files: Vec<String>,
}

async fn ensure_mods(client: &reqwest::Client, paths: &Paths, m: &Manifest, progress: &dyn Progress) -> Result<()> {
    std::fs::create_dir_all(paths.mods())?;
    let mut jobs = Vec::new();
    let mut files = Vec::new();
    for entry in &m.mods {
        let (url, file, sha1) = if let Some((slug, ver)) = &entry.modrinth {
            let v = net::get_json(client, &format!("https://api.modrinth.com/v2/project/{slug}/version/{ver}")).await
                .with_context(|| format!("Modrinth {slug} {ver}"))?;
            let f = v["files"]
                .as_array()
                .and_then(|a| a.iter().find(|f| f["primary"].as_bool() == Some(true)).or(a.first()))
                .ok_or_else(|| anyhow!("у {slug} {ver} нет файлов"))?;
            (str_of(f, "url")?, str_of(f, "filename")?, f["hashes"]["sha1"].as_str().map(String::from))
        } else if let Some(url) = &entry.url {
            let file = entry.file.clone().unwrap_or_else(|| url.rsplit('/').next().unwrap_or("mod.jar").to_string());
            (url.clone(), file, entry.sha1.clone())
        } else {
            continue;
        };
        let job = Job { url, path: paths.mods().join(&file), sha1, size: None };
        if entry.url.is_some() {
            // свой мод из релиза: качаем только когда вышла новая сборка (номер build в manifest)
            let stamp = paths.mods().join(format!(".{file}.build"));
            let have = std::fs::read_to_string(&stamp).ok().and_then(|t| t.trim().parse::<u32>().ok());
            if have == Some(m.build) && paths.mods().join(&file).exists() {
                files.push(file);
                continue;
            }
            // если релиза нет, но jar уже лежит — играем с ним
            if let Err(e) = net::fetch(client, &job).await {
                if !has_mraz1visual(paths) {
                    return Err(e);
                }
                files.push(file);
                continue;
            }
            // убрать другие копии mraz1visual*.jar, иначе Fabric увидит два одинаковых мода
            if is_client_jar(&file.to_lowercase()) {
                if let Ok(rd) = std::fs::read_dir(paths.mods()) {
                    for e in rd.flatten() {
                        let n = e.file_name().to_string_lossy().to_string();
                        let low = n.to_lowercase();
                        if low != file.to_lowercase() && is_client_jar(&low) {
                            let _ = std::fs::remove_file(e.path());
                        }
                    }
                }
            }
            // проверка sha256 из manifest: битый или подменённый файл не запускаем
            if let Some(want) = &entry.sha256 {
                let got = sha256_file(&paths.mods().join(&file))?;
                if !got.eq_ignore_ascii_case(want) {
                    let _ = std::fs::remove_file(paths.mods().join(&file));
                    bail!("{file}: sha256 не совпадает (ожидался {want}, получен {got})");
                }
            }
            let _ = std::fs::write(&stamp, m.build.to_string());
            files.push(file);
            continue;
        }
        jobs.push(job);
        files.push(file);
    }
    net::fetch_all(client, jobs, 4, |d, t| report(progress, Stage::Mods, d, t)).await?;

    // удалить наши старые версии, которых больше нет в списке; чужие моды не трогаем
    let managed_path = paths.managed_mods_file();
    let old: Managed = std::fs::read_to_string(&managed_path).ok().and_then(|t| serde_json::from_str(&t).ok()).unwrap_or_default();
    let keep: HashSet<&String> = files.iter().collect();
    for f in &old.files {
        if !keep.contains(f) {
            let _ = std::fs::remove_file(paths.mods().join(f));
        }
    }
    std::fs::write(&managed_path, serde_json::to_vec_pretty(&Managed { files })?)?;
    report(progress, Stage::Mods, 1, 1);
    Ok(())
}

/// Jar нашего клиента: новое имя mraz1visual*.jar или старое umbra*.jar.
fn is_client_jar(low: &str) -> bool {
    (low.starts_with("mraz1visual") || low.starts_with(concat!("umb", "ra"))) && low.ends_with(".jar")
}

/// Есть ли в mods сам Mraz1Visual (на случай, когда репозиторий ещё не настроен).
pub fn has_mraz1visual(paths: &Paths) -> bool {
    std::fs::read_dir(paths.mods())
        .map(|rd| rd.flatten().any(|e| {
            let n = e.file_name().to_string_lossy().to_lowercase();
            is_client_jar(&n)
        }))
        .unwrap_or(false)
}

// ── помощники ────────────────────────────────────────────────

fn sha256_file(path: &Path) -> Result<String> {
    use sha2::{Digest, Sha256};
    let mut f = std::fs::File::open(path)?;
    let mut h = Sha256::new();
    std::io::copy(&mut f, &mut h)?;
    Ok(hex::encode(h.finalize()))
}

/// Общий конфиг клиента между профилями версий: config/mraz1visual.json и папка config/mraz1visual.
pub fn sync_config(from_game: &Path, to_game: &Path) {
    if from_game == to_game {
        return;
    }
    let (fc, tc) = (from_game.join("config"), to_game.join("config"));
    let _ = std::fs::create_dir_all(&tc);
    let _ = std::fs::copy(fc.join("mraz1visual.json"), tc.join("mraz1visual.json"));
    copy_dir(&fc.join("mraz1visual"), &tc.join("mraz1visual"));
}

fn copy_dir(from: &Path, to: &Path) {
    let Ok(rd) = std::fs::read_dir(from) else { return };
    let _ = std::fs::create_dir_all(to);
    for e in rd.flatten() {
        let p = e.path();
        let t = to.join(e.file_name());
        if p.is_dir() {
            copy_dir(&p, &t);
        } else {
            let _ = std::fs::copy(&p, &t);
        }
    }
}

pub fn str_of(v: &Value, key: &str) -> Result<String> {
    v[key].as_str().map(String::from).ok_or_else(|| anyhow!("нет поля {key}"))
}

fn write_json(path: &Path, v: &Value) -> Result<()> {
    if let Some(p) = path.parent() {
        std::fs::create_dir_all(p)?;
    }
    std::fs::write(path, serde_json::to_vec(v)?)?;
    Ok(())
}

/// group:artifact:version[:classifier] → group/path/artifact/version/artifact-version[-classifier].jar
pub fn maven_path(name: &str) -> Result<String> {
    let parts: Vec<&str> = name.split(':').collect();
    if parts.len() < 3 {
        bail!("bad maven name {name}");
    }
    let (g, a, v) = (parts[0].replace('.', "/"), parts[1], parts[2]);
    let file = match parts.get(3) {
        Some(c) => format!("{a}-{v}-{c}.jar"),
        None => format!("{a}-{v}.jar"),
    };
    Ok(format!("{g}/{a}/{v}/{file}"))
}

fn lib_key(name: &str) -> String {
    let p: Vec<&str> = name.split(':').collect();
    match p.len() {
        0..=2 => name.to_string(),
        3 => format!("{}:{}", p[0], p[1]),
        _ => format!("{}:{}:{}", p[0], p[1], p[3]),
    }
}

pub fn os_name() -> &'static str {
    if cfg!(windows) { "windows" } else if cfg!(target_os = "macos") { "osx" } else { "linux" }
}

/// Правила Mojang: allow/disallow по ОС и архитектуре. Правила с features — не применяем (false).
pub fn rules_allow(rules: &Value) -> bool {
    let Some(arr) = rules.as_array() else { return true };
    let mut allowed = false;
    for r in arr {
        if r.get("features").is_some() {
            if r["action"] == "allow" {
                continue;
            }
        }
        let os = &r["os"];
        let mut matches = true;
        if let Some(n) = os["name"].as_str() {
            matches &= n == os_name();
        }
        if let Some(a) = os["arch"].as_str() {
            matches &= (a == "x86") == cfg!(target_arch = "x86");
        }
        if matches {
            allowed = r["action"] == "allow";
        }
    }
    allowed
}

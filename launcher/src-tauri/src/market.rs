//! Маркет: моды, ресурспаки и шейдеры с Modrinth — поиск и установка в один клик.

use crate::net;
use crate::paths::Paths;
use anyhow::{anyhow, bail, Result};
use serde::{Deserialize, Serialize};
use serde_json::Value;
use std::collections::HashMap;
use std::path::PathBuf;

const API: &str = "https://api.modrinth.com/v2";

#[derive(Clone, Copy)]
pub enum Kind {
    Mod,
    ResourcePack,
    Shader,
}

impl Kind {
    pub fn parse(s: &str) -> Result<Self> {
        Ok(match s {
            "mod" => Kind::Mod,
            "resourcepack" => Kind::ResourcePack,
            "shader" => Kind::Shader,
            _ => bail!("unknown kind {s}"),
        })
    }
    fn project_type(self) -> &'static str {
        match self {
            Kind::Mod => "mod",
            Kind::ResourcePack => "resourcepack",
            Kind::Shader => "shader",
        }
    }
    fn loaders(self) -> &'static str {
        match self {
            Kind::Mod => r#"["fabric"]"#,
            Kind::ResourcePack => r#"["minecraft"]"#,
            Kind::Shader => r#"["iris"]"#,
        }
    }
    pub fn folder(self, paths: &Paths) -> PathBuf {
        match self {
            Kind::Mod => paths.mods(),
            Kind::ResourcePack => paths.game().join("resourcepacks"),
            Kind::Shader => paths.game().join("shaderpacks"),
        }
    }
}

#[derive(Serialize)]
pub struct Hit {
    pub id: String,
    pub title: String,
    pub description: String,
    pub author: String,
    pub icon: String,
    pub downloads: u64,
    pub installed: bool,
}

#[derive(Serialize, Deserialize, Default)]
struct Installed {
    /// project_id → имя файла
    files: HashMap<String, String>,
}

fn installed_path(paths: &Paths) -> PathBuf {
    paths.game().join(".mraz1visual-market.json")
}

fn load_installed(paths: &Paths) -> Installed {
    std::fs::read_to_string(installed_path(paths)).ok().and_then(|t| serde_json::from_str(&t).ok()).unwrap_or_default()
}

fn save_installed(paths: &Paths, i: &Installed) -> Result<()> {
    std::fs::create_dir_all(paths.game())?;
    std::fs::write(installed_path(paths), serde_json::to_vec_pretty(i)?)?;
    Ok(())
}

/// Сверить записи с диском: удалённые руками файлы забываем.
fn live_installed(paths: &Paths) -> Installed {
    let mut i = load_installed(paths);
    i.files.retain(|_, f| [paths.mods(), paths.game().join("resourcepacks"), paths.game().join("shaderpacks")]
        .iter()
        .any(|d| d.join(f.as_str()).exists()));
    i
}

pub async fn search(paths: &Paths, mc: &str, kind: Kind, query: &str, sort: &str, offset: u32) -> Result<Vec<Hit>> {
    let index = match sort {
        "downloads" | "newest" | "updated" | "follows" => sort,
        _ => "relevance",
    };
    let facets = format!(r#"[["project_type:{}"],["versions:{}"]]"#, kind.project_type(), mc);
    let client = net::client();
    let resp: Value = client
        .get(format!("{API}/search"))
        .query(&[("query", query), ("facets", &facets), ("index", index), ("limit", "24"), ("offset", &offset.to_string())])
        .send()
        .await?
        .error_for_status()?
        .json()
        .await?;
    let inst = live_installed(paths);
    let hits = resp["hits"].as_array().cloned().unwrap_or_default();
    Ok(hits
        .into_iter()
        .map(|h| {
            let id = h["project_id"].as_str().unwrap_or_default().to_string();
            Hit {
                installed: inst.files.contains_key(&id),
                title: h["title"].as_str().unwrap_or_default().to_string(),
                description: h["description"].as_str().unwrap_or_default().to_string(),
                author: h["author"].as_str().unwrap_or_default().to_string(),
                icon: h["icon_url"].as_str().unwrap_or_default().to_string(),
                downloads: h["downloads"].as_u64().unwrap_or(0),
                id,
            }
        })
        .collect())
}

/// Установить последнюю подходящую версию (+ обязательные зависимости для модов).
pub async fn install(paths: &Paths, mc: &str, kind: Kind, project: &str) -> Result<String> {
    let client = net::client();
    let mut inst = live_installed(paths);
    let mut queue = vec![project.to_string()];
    let mut first_name = String::new();
    let mut depth = 0;
    while let Some(id) = queue.pop() {
        if inst.files.contains_key(&id) {
            continue;
        }
        let versions: Value = client
            .get(format!("{API}/project/{id}/version"))
            .query(&[("game_versions", format!(r#"["{mc}"]"#)), ("loaders", kind.loaders().to_string())])
            .send()
            .await?
            .error_for_status()?
            .json()
            .await?;
        let v = versions
            .as_array()
            .and_then(|a| a.first())
            .ok_or_else(|| anyhow!("Нет версии для Minecraft {mc}"))?;
        let file = v["files"]
            .as_array()
            .and_then(|a| a.iter().find(|f| f["primary"].as_bool() == Some(true)).or(a.first()))
            .ok_or_else(|| anyhow!("У версии нет файлов"))?;
        let name = file["filename"].as_str().ok_or_else(|| anyhow!("нет имени файла"))?.to_string();
        if name.contains('/') || name.contains('\\') || name.contains("..") {
            bail!("подозрительное имя файла");
        }
        let url = file["url"].as_str().ok_or_else(|| anyhow!("нет ссылки"))?.to_string();
        let dir = kind.folder(paths);
        std::fs::create_dir_all(&dir)?;
        net::fetch(&client, &net::Job { url, path: dir.join(&name), sha1: file["hashes"]["sha1"].as_str().map(String::from), size: None }).await?;
        if first_name.is_empty() {
            first_name = name.clone();
        }
        inst.files.insert(id.clone(), name);
        // зависимости — только для модов и только обязательные, на 2 уровня
        if matches!(kind, Kind::Mod) && depth < 2 {
            depth += 1;
            for dep in v["dependencies"].as_array().cloned().unwrap_or_default() {
                if dep["dependency_type"] == "required" {
                    if let Some(pid) = dep["project_id"].as_str() {
                        // fabric-api, sodium, iris ставит сам лаунчер
                        if !["P7dR8mSH", "AANobbMI", "YL57xq9U"].contains(&pid) {
                            queue.push(pid.to_string());
                        }
                    }
                }
            }
        }
    }
    save_installed(paths, &inst)?;
    Ok(first_name)
}

pub fn uninstall(paths: &Paths, kind: Kind, project: &str) -> Result<()> {
    let mut inst = load_installed(paths);
    if let Some(name) = inst.files.remove(project) {
        let _ = std::fs::remove_file(kind.folder(paths).join(name));
    }
    save_installed(paths, &inst)
}

#[derive(Serialize)]
pub struct LocalItem {
    pub file: String,
    pub project: Option<String>,
}

/// Что лежит в папке (включая поставленное руками).
pub fn list_local(paths: &Paths, kind: Kind) -> Vec<LocalItem> {
    let inst = load_installed(paths);
    let by_file: HashMap<&String, &String> = inst.files.iter().map(|(k, v)| (v, k)).collect();
    let managed: Vec<String> = std::fs::read_to_string(paths.managed_mods_file())
        .ok()
        .and_then(|t| serde_json::from_str::<Value>(&t).ok())
        .and_then(|v| v["files"].as_array().map(|a| a.iter().filter_map(|x| x.as_str().map(String::from)).collect()))
        .unwrap_or_default();
    let mut out = Vec::new();
    if let Ok(rd) = std::fs::read_dir(kind.folder(paths)) {
        for e in rd.flatten() {
            let name = e.file_name().to_string_lossy().to_string();
            if name.starts_with('.') || managed.contains(&name) {
                continue;
            }
            out.push(LocalItem { project: by_file.get(&name).map(|s| s.to_string()), file: name });
        }
    }
    out.sort_by(|a, b| a.file.to_lowercase().cmp(&b.file.to_lowercase()));
    out
}

/// Удалить файл, поставленный руками (только имя файла внутри своей папки).
pub fn delete_local(paths: &Paths, kind: Kind, file: &str) -> Result<()> {
    if file.contains('/') || file.contains('\\') || file.contains("..") {
        bail!("bad file name");
    }
    let mut inst = load_installed(paths);
    inst.files.retain(|_, f| f != file);
    save_installed(paths, &inst)?;
    std::fs::remove_file(kind.folder(paths).join(file))?;
    Ok(())
}

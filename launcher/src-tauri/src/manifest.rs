//! Что ставить. Берётся из manifest.json последнего релиза на GitHub,
//! а если сети/репозитория нет — из встроенного по умолчанию.

use serde::{Deserialize, Serialize};

/// Репозиторий с релизами Umbra: "владелец/репозиторий". Поменяй на свой.
pub const GITHUB_REPO: &str = "SpeedyEzKv/Umbra";

#[derive(Serialize, Deserialize, Clone)]
pub struct Manifest {
    pub client_version: String,
    pub build: u32,
    pub minecraft: String,
    /// Пусто — последний стабильный Fabric Loader.
    #[serde(default)]
    pub fabric_loader: String,
    pub mods: Vec<ModEntry>,
    #[serde(default)]
    pub changelog: Vec<String>,
    #[serde(default)]
    pub links: Vec<Link>,
    /// Версия лаунчера в релизе и ссылка на установщик — для кнопки «Обновить лаунчер».
    #[serde(default)]
    pub launcher_version: String,
    #[serde(default)]
    pub launcher_url: String,
}

/// true, если `remote` новее `local` (сравнение по числам через точку).
pub fn newer(remote: &str, local: &str) -> bool {
    let parse = |v: &str| v.split('.').map(|p| p.trim().parse::<u64>().unwrap_or(0)).collect::<Vec<_>>();
    let (r, l) = (parse(remote), parse(local));
    for i in 0..r.len().max(l.len()) {
        let (a, b) = (*r.get(i).unwrap_or(&0), *l.get(i).unwrap_or(&0));
        if a != b { return a > b; }
    }
    false
}

#[derive(Serialize, Deserialize, Clone)]
pub struct ModEntry {
    /// Для Modrinth: slug проекта и номер версии.
    #[serde(default)]
    pub modrinth: Option<(String, String)>,
    /// Прямая ссылка (например, umbra.jar из релиза).
    #[serde(default)]
    pub url: Option<String>,
    #[serde(default)]
    pub file: Option<String>,
    #[serde(default)]
    pub sha1: Option<String>,
}

#[derive(Serialize, Deserialize, Clone)]
pub struct Link {
    pub title: String,
    pub url: String,
}

impl Manifest {
    pub fn builtin() -> Self {
        Self {
            client_version: "0.1.0".into(),
            build: 1,
            minecraft: "1.21.11".into(),
            fabric_loader: String::new(),
            mods: vec![
                ModEntry { modrinth: Some(("fabric-api".into(), "0.141.3+1.21.11".into())), url: None, file: None, sha1: None },
                ModEntry { modrinth: Some(("sodium".into(), "mc1.21.11-0.8.7-fabric".into())), url: None, file: None, sha1: None },
                ModEntry { modrinth: Some(("iris".into(), "1.10.7+1.21.11-fabric".into())), url: None, file: None, sha1: None },
                ModEntry { modrinth: Some(("mcef-modern".into(), "0.3.3+mc1.21.11.jcef146.0.10".into())), url: None, file: None, sha1: None },
                ModEntry {
                    modrinth: None,
                    url: Some(format!("https://github.com/{GITHUB_REPO}/releases/latest/download/umbra.jar")),
                    file: Some("umbra.jar".into()),
                    sha1: None,
                },
            ],
            changelog: vec!["Первая сборка лаунчера.".into()],
            links: vec![],
            launcher_version: String::new(),
            launcher_url: String::new(),
        }
    }

    /// manifest.json из последнего релиза; при любой ошибке — встроенный.
    pub async fn fetch(client: &reqwest::Client) -> (Self, bool) {
        if GITHUB_REPO.starts_with("OWNER/") {
            return (Self::builtin(), false);
        }
        let url = format!("https://github.com/{GITHUB_REPO}/releases/latest/download/manifest.json");
        match client.get(&url).send().await.and_then(|r| r.error_for_status()) {
            Ok(resp) => match resp.json::<Manifest>().await {
                Ok(m) => (m, true),
                Err(_) => (Self::builtin(), false),
            },
            Err(_) => (Self::builtin(), false),
        }
    }
}

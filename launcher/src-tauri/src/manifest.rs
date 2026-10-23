//! Что ставить. Берётся из manifest.json последнего релиза на GitHub,
//! а если сети/репозитория нет — из встроенного по умолчанию.

use serde::{Deserialize, Serialize};

/// Репозиторий с релизами Mraz1Visual: "владелец/репозиторий". Поменяй на свой.
pub const GITHUB_REPO: &str = "SpeedyEzKv/Mraz1Visual";

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
    /// История обновлений: версия, дата и что нового. Лаунчер показывает новые сверху.
    #[serde(default)]
    pub releases: Vec<Release>,
    #[serde(default)]
    pub links: Vec<Link>,
    /// Версия лаунчера в релизе и ссылка на установщик — для кнопки «Обновить лаунчер».
    #[serde(default)]
    pub launcher_version: String,
    #[serde(default)]
    pub launcher_url: String,
    /// Видео-фон меню мода (кадры 60 fps), качается один раз, пока не сменится version.
    #[serde(default)]
    pub video: Option<VideoAsset>,
    /// Application ID приложения «Mraz1Visual» в Discord (для статуса «Играет в Mraz1Visual»).
    #[serde(default)]
    pub discord_app_id: String,
    /// Версии Minecraft, под которые собран мод (manifest v2): файл, sha256, Fabric Loader/API, Sodium/Iris/MCEF.
    #[serde(default)]
    pub versions: Vec<VersionEntry>,
    /// client_id OAuth-приложения GitHub (Device Flow) для вкладки «Форум» в моде. Не секрет; токенов здесь нет.
    #[serde(default)]
    pub github_client_id: String,
    /// Нужная Java (21 для 1.21.11, 25 для 26.x).
    #[serde(default = "java21")]
    pub java: u32,
}

fn java21() -> u32 { 21 }

#[derive(Serialize, Deserialize, Clone, Debug, Default)]
pub struct VersionEntry {
    pub minecraft: String,
    /// Какие версии игры покрывает jar (26.1.2 → 26.1, 26.1.1, 26.1.2).
    #[serde(default)]
    pub covers: Vec<String>,
    /// recommended / new / beta — бейдж в лаунчере.
    #[serde(default)]
    pub status: String,
    pub file: String,
    pub url: String,
    #[serde(default)]
    pub sha256: String,
    #[serde(default = "java21")]
    pub java: u32,
    #[serde(default)]
    pub fabric_loader: String,
    #[serde(default)]
    pub fabric_api: String,
    #[serde(default)]
    pub optional: Optional,
}

#[derive(Serialize, Deserialize, Clone, Debug, Default)]
pub struct Optional {
    #[serde(default)]
    pub sodium: Option<Opt>,
    #[serde(default)]
    pub iris: Option<Opt>,
    #[serde(default)]
    pub mcef: Option<Opt>,
}

#[derive(Serialize, Deserialize, Clone, Debug, Default)]
pub struct Opt {
    #[serde(default)]
    pub project: String,
    #[serde(default)]
    pub version: String,
}

#[derive(Serialize, Deserialize, Clone, Debug, Default)]
pub struct Release {
    #[serde(default)]
    pub version: String,
    /// Дата в виде ГГГГ-ММ-ДД.
    #[serde(default)]
    pub date: String,
    #[serde(default)]
    pub items: Vec<String>,
}

#[derive(Serialize, Deserialize, Clone, Debug)]
pub struct VideoAsset {
    pub url: String,
    pub version: String,
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
    /// Прямая ссылка (например, mraz1visual.jar из релиза).
    #[serde(default)]
    pub url: Option<String>,
    #[serde(default)]
    pub file: Option<String>,
    #[serde(default)]
    pub sha1: Option<String>,
    /// Для своего jar из релиза: sha256 из manifest (проверяется после скачивания).
    #[serde(default)]
    pub sha256: Option<String>,
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
                ModEntry { modrinth: Some(("fabric-api".into(), "0.141.3+1.21.11".into())), url: None, file: None, sha1: None, sha256: None },
                ModEntry { modrinth: Some(("sodium".into(), "mc1.21.11-0.8.7-fabric".into())), url: None, file: None, sha1: None, sha256: None },
                ModEntry { modrinth: Some(("iris".into(), "1.10.7+1.21.11-fabric".into())), url: None, file: None, sha1: None, sha256: None },
                ModEntry { modrinth: Some(("mcef-modern".into(), "0.3.3+mc1.21.11.jcef146.0.10".into())), url: None, file: None, sha1: None, sha256: None },
                ModEntry {
                    modrinth: None,
                    url: Some(format!("https://github.com/{GITHUB_REPO}/releases/latest/download/mraz1visual.jar")),
                    file: Some("mraz1visual.jar".into()),
                    sha1: None,
                    sha256: None,
                },
            ],
            changelog: vec!["Первая сборка лаунчера.".into()],
            releases: vec![],
            links: vec![],
            launcher_version: String::new(),
            launcher_url: String::new(),
            video: None,
            discord_app_id: String::new(),
            versions: vec![],
            github_client_id: String::new(),
            java: 21,
        }
    }

    /// Версии для выбора в лаунчере: каждая версия игры отдельно (26.1, 26.1.1, 26.1.2 — один jar).
    pub fn choices(&self) -> Vec<(String, String)> {
        let mut out = Vec::new();
        for v in &self.versions {
            let covers = if v.covers.is_empty() { vec![v.minecraft.clone()] } else { v.covers.clone() };
            for c in covers {
                out.push((c, v.status.clone()));
            }
        }
        if out.is_empty() {
            out.push((self.minecraft.clone(), "recommended".into()));
        }
        out
    }

    /// Рекомендуемая версия (первая со статусом recommended).
    pub fn recommended(&self) -> String {
        self.versions.iter().find(|v| v.status == "recommended").map(|v| v.minecraft.clone()).unwrap_or_else(|| self.minecraft.clone())
    }

    /// Что ставить для выбранной версии игры: Fabric API, Sodium/Iris по галочкам, MCEF и свой jar с sha256.
    /// Для 1.21.11 берётся прежний список модов (с проверенными дополнениями), для 26.x — собирается из versions.
    pub fn resolve(&self, mc: &str, sodium: bool, iris: bool) -> Manifest {
        let mut out = self.clone();
        let Some(v) = self.versions.iter().find(|v| v.minecraft == mc || v.covers.iter().any(|c| c == mc)) else {
            return out;
        };
        out.minecraft = mc.to_string();
        out.fabric_loader = v.fabric_loader.clone();
        out.java = v.java;
        let jar = ModEntry { modrinth: None, url: Some(v.url.clone()), file: Some(v.file.clone()), sha1: None,
            sha256: (!v.sha256.is_empty()).then(|| v.sha256.clone()) };
        let opt = |o: &Option<Opt>, slug: &str| o.as_ref().filter(|o| !o.version.is_empty())
            .map(|o| ModEntry { modrinth: Some((slug.into(), o.version.clone())), url: None, file: None, sha1: None, sha256: None });
        if mc == "1.21.11" && !self.mods.is_empty() {
            // прежний набор: убрать Sodium/Iris по галочкам, свой jar — версионный
            out.mods = self.mods.iter().filter(|e| match &e.modrinth {
                Some((slug, _)) if slug == "sodium" => sodium,
                Some((slug, _)) if slug == "iris" => iris && sodium,
                _ => true,
            }).map(|e| if e.url.as_deref().map(|u| u.ends_with("/mraz1visual.jar")).unwrap_or(false) { jar.clone() } else { e.clone() }).collect();
            return out;
        }
        let mut mods = vec![ModEntry { modrinth: Some(("fabric-api".into(), v.fabric_api.clone())), url: None, file: None, sha1: None, sha256: None }];
        if sodium { mods.extend(opt(&v.optional.sodium, "sodium")); }
        if sodium && iris { mods.extend(opt(&v.optional.iris, "iris")); }
        mods.extend(opt(&v.optional.mcef, "mcef-modern"));
        mods.push(jar);
        out.mods = mods;
        out
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

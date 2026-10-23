//! Настройки лаунчера: память, поведение при запуске.

use serde::{Deserialize, Serialize};
use std::path::Path;

#[derive(Serialize, Deserialize, Clone)]
#[serde(default)]
pub struct Settings {
    pub ram_mb: u64,
    pub close_on_launch: bool,
    /// Выбранная версия Minecraft; пусто — рекомендуемая.
    pub mc_version: String,
    /// Ставить Sodium и Iris (по галочке).
    pub sodium: bool,
    pub iris: bool,
}

impl Default for Settings {
    fn default() -> Self {
        Self { ram_mb: 4096, close_on_launch: false, mc_version: String::new(), sodium: true, iris: true }
    }
}

impl Settings {
    /// Битый файл — значения по умолчанию, как и в моде.
    pub fn load(path: &Path) -> Self {
        std::fs::read_to_string(path)
            .ok()
            .and_then(|s| serde_json::from_str(&s).ok())
            .unwrap_or_default()
    }

    pub fn save(&self, path: &Path) -> anyhow::Result<()> {
        if let Some(p) = path.parent() {
            std::fs::create_dir_all(p)?;
        }
        let tmp = path.with_extension("json.tmp");
        std::fs::write(&tmp, serde_json::to_vec_pretty(self)?)?;
        std::fs::rename(tmp, path)?;
        Ok(())
    }
}

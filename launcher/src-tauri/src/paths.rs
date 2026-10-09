//! Где что лежит. Всё в одной папке: %APPDATA%\Umbra (Windows) / ~/.local/share/Umbra.

use std::path::PathBuf;

pub struct Paths {
    pub root: PathBuf,
}

impl Paths {
    pub fn new() -> Self {
        let base = dirs::data_dir().unwrap_or_else(|| PathBuf::from("."));
        Self { root: base.join("Umbra") }
    }
    pub fn runtime(&self) -> PathBuf { self.root.join("runtime") }
    pub fn libraries(&self) -> PathBuf { self.root.join("libraries") }
    pub fn assets(&self) -> PathBuf { self.root.join("assets") }
    pub fn versions(&self) -> PathBuf { self.root.join("versions") }
    pub fn game(&self) -> PathBuf { self.root.join("game") }
    pub fn mods(&self) -> PathBuf { self.game().join("mods") }
    pub fn logs(&self) -> PathBuf { self.root.join("logs") }
    pub fn settings_file(&self) -> PathBuf { self.root.join("launcher.json") }
    pub fn accounts_file(&self) -> PathBuf { self.root.join("accounts.json") }
    pub fn managed_mods_file(&self) -> PathBuf { self.game().join(".umbra-managed.json") }
}

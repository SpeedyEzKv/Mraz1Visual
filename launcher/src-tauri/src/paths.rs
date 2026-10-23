//! Где что лежит. Всё в одной папке: %APPDATA%\Mraz1Visual (Windows) / ~/.local/share/Mraz1Visual.

use std::path::PathBuf;

#[derive(Clone)]
pub struct Paths {
    pub root: PathBuf,
    /// Папка игры профиля: для 1.21.11 — game, для остальных версий — game-<версия> (свои моды и миры).
    game_dir: Option<PathBuf>,
}

impl Paths {
    pub fn new() -> Self {
        let base = dirs::data_dir().unwrap_or_else(|| PathBuf::from("."));
        let root = base.join("Mraz1Visual");
        // раньше проект назывался Umbra: переносим папку целиком (Java, Minecraft, аккаунты, миры)
        let old = base.join(concat!("Umb", "ra"));
        if old.is_dir() && !root.exists() {
            let _ = std::fs::rename(&old, &root);
        }
        let game = root.join("game");
        for (o, n) in [(concat!(".umb", "ra-managed.json"), ".mraz1visual-managed.json"), (concat!(".umb", "ra-market.json"), ".mraz1visual-market.json")] {
            let (op, np) = (game.join(o), game.join(n));
            if op.exists() && !np.exists() {
                let _ = std::fs::rename(op, np);
            }
        }
        Self { root, game_dir: None }
    }

    /// Профиль версии: своя папка игры (моды и миры не смешиваются), конфиг клиента общий.
    pub fn for_version(&self, mc: &str) -> Paths {
        let mut p = self.clone();
        if mc != "1.21.11" {
            p.game_dir = Some(self.root.join(format!("game-{mc}")));
        }
        p
    }

    /// Папка игры основного профиля (1.21.11) — там хранится общий конфиг.
    pub fn main_game(&self) -> PathBuf { self.root.join("game") }
    pub fn runtime(&self) -> PathBuf { self.root.join("runtime") }
    pub fn libraries(&self) -> PathBuf { self.root.join("libraries") }
    pub fn assets(&self) -> PathBuf { self.root.join("assets") }
    pub fn versions(&self) -> PathBuf { self.root.join("versions") }
    pub fn game(&self) -> PathBuf { self.game_dir.clone().unwrap_or_else(|| self.root.join("game")) }
    pub fn mods(&self) -> PathBuf { self.game().join("mods") }
    pub fn logs(&self) -> PathBuf { self.root.join("logs") }
    pub fn settings_file(&self) -> PathBuf { self.root.join("launcher.json") }
    pub fn accounts_file(&self) -> PathBuf { self.root.join("accounts.json") }
    pub fn managed_mods_file(&self) -> PathBuf { self.game().join(".mraz1visual-managed.json") }
}

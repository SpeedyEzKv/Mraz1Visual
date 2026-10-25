//! Папка mods выбранной версии: список, вкл/выкл (.jar ⇄ .jar.disabled), добавление перетаскиванием, удаление.
//! Версия Minecraft и Fabric Loader сверяется с fabric.mod.json каждого мода.

use crate::paths::Paths;
use anyhow::{bail, Result};
use serde::{Deserialize, Serialize};
use serde_json::Value;
use std::collections::HashSet;
use std::io::Read;
use std::path::Path;

const DISABLED: &str = ".disabled";

#[derive(Serialize)]
pub struct ModInfo {
    /// Имя файла без .disabled.
    pub file: String,
    pub enabled: bool,
    pub name: String,
    pub version: String,
    /// Мод ставит сам лаунчер (Fabric API, Mraz1Visual, профиль «Оптимизация») — выключать бесполезно.
    pub managed: bool,
    /// Почему мод может не запуститься (не та версия игры / Fabric); пусто — всё в порядке.
    pub warning: String,
}

#[derive(Deserialize, Default)]
struct Managed {
    files: Vec<String>,
}

fn managed(paths: &Paths) -> HashSet<String> {
    std::fs::read_to_string(paths.managed_mods_file())
        .ok()
        .and_then(|t| serde_json::from_str::<Managed>(&t).ok())
        .unwrap_or_default()
        .files
        .into_iter()
        .collect()
}

/// Только имя файла, без путей: из интерфейса нельзя выйти за папку mods.
fn safe_name(file: &str) -> Result<&str> {
    if file.is_empty() || file.contains(['/', '\\']) || file.contains("..") || !file.to_lowercase().ends_with(".jar") {
        bail!("недопустимое имя файла");
    }
    Ok(file)
}

pub fn list(paths: &Paths, mc: &str, loader: &str) -> Vec<ModInfo> {
    let own = managed(paths);
    let Ok(rd) = std::fs::read_dir(paths.mods()) else { return Vec::new() };
    let mut out: Vec<ModInfo> = rd
        .flatten()
        .filter_map(|e| {
            let n = e.file_name().to_string_lossy().to_string();
            let low = n.to_lowercase();
            let (file, enabled) = if low.ends_with(".jar") {
                (n.clone(), true)
            } else if low.ends_with(".jar.disabled") {
                (n[..n.len() - DISABLED.len()].to_string(), false)
            } else {
                return None;
            };
            let meta = read_meta(&e.path());
            let (name, version, warning) = match &meta {
                Some(m) => (
                    m["name"].as_str().unwrap_or(&file).to_string(),
                    m["version"].as_str().unwrap_or("").to_string(),
                    check(m, mc, loader),
                ),
                None => (file.clone(), String::new(), "не Fabric-мод (нет fabric.mod.json)".into()),
            };
            Some(ModInfo { managed: own.contains(&file), file, enabled, name, version, warning })
        })
        .collect();
    out.sort_by(|a, b| a.name.to_lowercase().cmp(&b.name.to_lowercase()));
    out
}

fn read_meta(path: &Path) -> Option<Value> {
    let f = std::fs::File::open(path).ok()?;
    let mut z = zip::ZipArchive::new(f).ok()?;
    let mut entry = z.by_name("fabric.mod.json").ok()?;
    let mut s = String::new();
    entry.read_to_string(&mut s).ok()?;
    // в fabric.mod.json бывают переводы строк внутри строк — serde их не любит
    serde_json::from_str(&s).or_else(|_| serde_json::from_str(&s.replace(['\n', '\r', '\t'], " "))).ok()
}

/// Предупреждение, если depends мода не пускает эту версию Minecraft или Fabric Loader.
fn check(meta: &Value, mc: &str, loader: &str) -> String {
    let deps = &meta["depends"];
    let mut w = Vec::new();
    if let Some(req) = deps.get("minecraft") {
        if !matches_any(req, mc) {
            w.push(format!("нужен Minecraft {}", show(req)));
        }
    }
    if !loader.is_empty() {
        if let Some(req) = deps.get("fabricloader") {
            if !matches_any(req, loader) {
                w.push(format!("нужен Fabric Loader {}", show(req)));
            }
        }
    }
    w.join(", ")
}

fn show(req: &Value) -> String {
    match req {
        Value::String(s) => s.clone(),
        Value::Array(a) => a.iter().filter_map(|v| v.as_str()).collect::<Vec<_>>().join(" или "),
        _ => "?".into(),
    }
}

/// Строка или массив строк (массив — «любое из»).
fn matches_any(req: &Value, have: &str) -> bool {
    match req {
        Value::String(s) => matches(s, have),
        Value::Array(a) => a.iter().filter_map(|v| v.as_str()).any(|s| matches(s, have)),
        _ => true,
    }
}

/// Упрощённые предикаты версий Fabric: "*", "1.21.11", ">=1.21.10 <1.22", "~1.21", "^1.21", "1.21.x".
/// Непонятная запись не мешает — считаем, что подходит.
fn matches(pred: &str, have: &str) -> bool {
    let pred = pred.trim();
    if pred.is_empty() || pred == "*" {
        return true;
    }
    pred.split_whitespace().all(|p| one(p, have))
}

fn one(p: &str, have: &str) -> bool {
    let (op, v) = [">=", "<=", ">", "<", "=", "~", "^"]
        .iter()
        .find(|o| p.starts_with(**o))
        .map(|o| (*o, &p[o.len()..]))
        .unwrap_or(("", p));
    let v = v.trim();
    let h = nums(have);
    if v.ends_with(".x") || v.ends_with(".*") || v.ends_with(".X") {
        let prefix = nums(&v[..v.len() - 2]);
        return h.len() >= prefix.len() && h[..prefix.len()] == prefix[..];
    }
    let w = nums(v);
    if w.is_empty() {
        return true;
    }
    let c = cmp(&h, &w);
    match op {
        ">=" => c >= 0,
        "<=" => c <= 0,
        ">" => c > 0,
        "<" => c < 0,
        // ~1.21 — та же минорная ветка; ^1.21 — та же мажорная
        "~" => c >= 0 && h.len() >= 2 && w.len() >= 1 && h[0] == w[0] && (w.len() < 2 || h[1] == w[1]),
        "^" => c >= 0 && !h.is_empty() && h[0] == w[0],
        _ => c == 0 || (have.starts_with(v) && pre_release(have, v)),
    }
}

/// «26.3» совпадает с «26.3-rc-1» не должно, а «1.21.11» с «1.21.11+build» — да.
fn pre_release(have: &str, v: &str) -> bool {
    have[v.len()..].starts_with('+')
}

fn nums(s: &str) -> Vec<u64> {
    s.split(|c: char| c == '.' || c == '-' || c == '+')
        .take_while(|p| !p.is_empty() && p.chars().all(|c| c.is_ascii_digit()))
        .filter_map(|p| p.parse().ok())
        .collect()
}

fn cmp(a: &[u64], b: &[u64]) -> i32 {
    for i in 0..a.len().max(b.len()) {
        let (x, y) = (a.get(i).copied().unwrap_or(0), b.get(i).copied().unwrap_or(0));
        if x != y {
            return if x > y { 1 } else { -1 };
        }
    }
    0
}

pub fn set_enabled(paths: &Paths, file: &str, enabled: bool) -> Result<()> {
    let file = safe_name(file)?;
    let on = paths.mods().join(file);
    let off = paths.mods().join(format!("{file}{DISABLED}"));
    if enabled && off.exists() {
        std::fs::rename(off, on)?;
    } else if !enabled && on.exists() {
        std::fs::rename(on, off)?;
    }
    Ok(())
}

pub fn delete(paths: &Paths, file: &str) -> Result<()> {
    let file = safe_name(file)?;
    for p in [paths.mods().join(file), paths.mods().join(format!("{file}{DISABLED}"))] {
        if p.exists() {
            std::fs::remove_file(p)?;
        }
    }
    Ok(())
}

/// Скопировать перетащенные .jar в mods. Возвращает имена добавленных; не .jar — пропускаются.
pub fn add(paths: &Paths, files: &[String]) -> Result<Vec<String>> {
    std::fs::create_dir_all(paths.mods())?;
    let mut added = Vec::new();
    for f in files {
        let src = Path::new(f);
        let Some(name) = src.file_name().map(|n| n.to_string_lossy().to_string()) else { continue };
        if !name.to_lowercase().ends_with(".jar") || !src.is_file() {
            continue;
        }
        std::fs::copy(src, paths.mods().join(&name))?;
        added.push(name);
    }
    Ok(added)
}

#[cfg(test)]
mod tests {
    use super::matches;

    #[test]
    fn predicates() {
        assert!(matches("*", "26.3"));
        assert!(matches("1.21.11", "1.21.11"));
        assert!(!matches("1.21.11", "26.3"));
        assert!(matches(">=1.21.10 <1.22", "1.21.11"));
        assert!(!matches(">=1.21.10 <1.22", "26.1.2"));
        assert!(matches("~26.1", "26.1.2"));
        assert!(!matches("~26.1", "26.2"));
        assert!(matches("1.21.x", "1.21.11"));
        assert!(matches(">=0.18.1", "0.19.5"));
        assert!(!matches(">=0.19.5", "0.18.1"));
    }
}

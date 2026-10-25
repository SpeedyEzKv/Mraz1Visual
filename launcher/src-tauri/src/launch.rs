//! Сборка командной строки и запуск игры.

use crate::install::{rules_allow, Installed};
use crate::paths::Paths;
use anyhow::{Context, Result};
use serde::Deserialize;
use serde_json::Value;
use std::collections::HashMap;
use std::process::Stdio;

/// Активный аккаунт. Пишется переключателем аккаунтов (Microsoft) в accounts.json.
#[derive(Deserialize, Clone)]
pub struct Account {
    pub name: String,
    pub uuid: String,
    pub token: String,
    #[serde(default = "default_type")]
    pub kind: String,
    #[serde(default)]
    pub xuid: String,
}

fn default_type() -> String { "msa".into() }

#[derive(Deserialize, Default)]
struct Accounts {
    active: Option<Account>,
}

/// Аккаунт для запуска. Пока вход не настроен — локальная сессия, как в dev-клиенте Fabric
/// (работает одиночная игра; сетевая требует входа через аккаунт Microsoft).
pub fn current_account(paths: &Paths) -> Account {
    let acc: Accounts = std::fs::read_to_string(paths.accounts_file())
        .ok()
        .and_then(|t| serde_json::from_str(&t).ok())
        .unwrap_or_default();
    acc.active.unwrap_or_else(|| local_account("Player"))
}

fn local_account(name: &str) -> Account {
    // UUID как у ванили для локальных сессий: MD5("OfflinePlayer:" + name), версия 3
    let mut b = md5::compute(format!("OfflinePlayer:{name}")).0;
    b[6] = (b[6] & 0x0f) | 0x30;
    b[8] = (b[8] & 0x3f) | 0x80;
    let h = hex::encode(b);
    let uuid = format!("{}-{}-{}-{}-{}", &h[0..8], &h[8..12], &h[12..16], &h[16..20], &h[20..32]);
    Account { name: name.into(), uuid, token: "0".into(), kind: "legacy".into(), xuid: String::new() }
}

/// Память и сборщик мусора для запуска.
pub struct JvmOptions {
    pub ram_mb: u64,
    /// Мажорная версия Java, под которую собрана выбранная версия игры (21, 25).
    pub java: u32,
    pub zgc: bool,
}

/// Флаги памяти и GC. Проверены на Java 21 и 25/26: ни одного «неизвестного» флага (с ним JVM не стартует).
pub fn gc_args(o: &JvmOptions) -> Vec<String> {
    let mut a = vec![format!("-Xmx{}M", o.ram_mb), format!("-Xms{}M", (o.ram_mb / 2).clamp(512, 2048))];
    if o.zgc && o.java >= 21 {
        a.push("-XX:+UseZGC".into());
        // в 21 поколенческий ZGC включается флагом, с 23 он единственный, а флаг в 24+ уже ошибка запуска
        if o.java < 23 {
            a.push("-XX:+ZGenerational".into());
        }
    } else {
        // G1 под клиент: короткие паузы (меньше фризов), побольше молодое поколение — в игре много короткоживущих объектов
        a.extend(["-XX:+UseG1GC", "-XX:MaxGCPauseMillis=37", "-XX:+UnlockExperimentalVMOptions", "-XX:G1NewSizePercent=20",
            "-XX:G1ReservePercent=20", "-XX:G1HeapRegionSize=16M", "-XX:+UseStringDeduplication"].map(String::from));
        if o.java < 26 {
            a.push("-XX:+ParallelRefProcEnabled".into());
        }
    }
    a.push("-XX:+DisableExplicitGC".into());
    a
}

pub fn build_command(paths: &Paths, inst: &Installed, jvm: &JvmOptions, account: &Account, extra_jvm: &[String]) -> Result<tokio::process::Command> {
    let vjson: Value = serde_json::from_str(&std::fs::read_to_string(&inst.version_json)?)?;
    let fabric: Value = serde_json::from_str(&std::fs::read_to_string(&inst.fabric_profile)?)?;

    let sep = if cfg!(windows) { ";" } else { ":" };
    let mut cp: Vec<String> = inst.classpath.iter().map(|p| p.to_string_lossy().to_string()).collect();
    cp.push(inst.client_jar.to_string_lossy().to_string());
    let classpath = cp.join(sep);

    let natives = paths.versions().join(&inst.version_name).join("natives");
    std::fs::create_dir_all(&natives)?;
    std::fs::create_dir_all(paths.game())?;

    let mut vars: HashMap<&str, String> = HashMap::new();
    vars.insert("natives_directory", natives.to_string_lossy().to_string());
    vars.insert("launcher_name", "mraz1visual".into());
    vars.insert("launcher_version", env!("CARGO_PKG_VERSION").into());
    vars.insert("classpath", classpath);
    vars.insert("classpath_separator", sep.into());
    vars.insert("library_directory", paths.libraries().to_string_lossy().to_string());
    vars.insert("version_name", inst.version_name.clone());
    vars.insert("version_type", "release".into());
    vars.insert("game_directory", paths.game().to_string_lossy().to_string());
    vars.insert("assets_root", paths.assets().to_string_lossy().to_string());
    vars.insert("assets_index_name", inst.asset_index.clone());
    vars.insert("auth_player_name", account.name.clone());
    vars.insert("auth_uuid", account.uuid.clone());
    vars.insert("auth_access_token", account.token.clone());
    vars.insert("auth_xuid", if account.xuid.is_empty() { "0".into() } else { account.xuid.clone() });
    vars.insert("clientid", "0".into());
    vars.insert("user_type", account.kind.clone());
    vars.insert("user_properties", "{}".into());

    let mut args: Vec<String> = Vec::new();
    args.extend(gc_args(jvm));
    args.extend(extra_jvm.iter().cloned());
    push_args(&mut args, &vjson["arguments"]["jvm"], &vars);
    push_args(&mut args, &fabric["arguments"]["jvm"], &vars);
    args.push(inst.main_class.clone());
    push_args(&mut args, &vjson["arguments"]["game"], &vars);
    push_args(&mut args, &fabric["arguments"]["game"], &vars);

    let mut cmd = tokio::process::Command::new(&inst.java);
    cmd.args(&args)
        .current_dir(paths.game())
        .stdout(Stdio::piped())
        .stderr(Stdio::piped())
        .kill_on_drop(false);
    #[cfg(windows)]
    {
        const CREATE_NO_WINDOW: u32 = 0x0800_0000;
        cmd.creation_flags(CREATE_NO_WINDOW);
    }
    Ok(cmd)
}

fn push_args(out: &mut Vec<String>, arr: &Value, vars: &HashMap<&str, String>) {
    let Some(list) = arr.as_array() else { return };
    for a in list {
        if let Some(s) = a.as_str() {
            out.push(subst(s, vars));
        } else if a.is_object() {
            if !rules_allow(&a["rules"]) {
                continue;
            }
            match &a["value"] {
                Value::String(s) => out.push(subst(s, vars)),
                Value::Array(vs) => {
                    for v in vs {
                        if let Some(s) = v.as_str() {
                            out.push(subst(s, vars));
                        }
                    }
                }
                _ => {}
            }
        }
    }
}

fn subst(s: &str, vars: &HashMap<&str, String>) -> String {
    let mut out = String::with_capacity(s.len());
    let mut rest = s;
    while let Some(i) = rest.find("${") {
        out.push_str(&rest[..i]);
        let after = &rest[i + 2..];
        match after.find('}') {
            Some(j) => {
                let key = &after[..j];
                match vars.get(key) {
                    Some(v) => out.push_str(v),
                    None => {
                        out.push_str("${");
                        out.push_str(key);
                        out.push('}');
                    }
                }
                rest = &after[j + 1..];
            }
            None => {
                out.push_str(&rest[i..]);
                rest = "";
            }
        }
    }
    out.push_str(rest);
    out
}

pub fn log_path(paths: &Paths) -> std::path::PathBuf {
    paths.logs().join("game.log")
}

pub fn ensure_logs(paths: &Paths) -> Result<()> {
    std::fs::create_dir_all(paths.logs()).context("logs")
}

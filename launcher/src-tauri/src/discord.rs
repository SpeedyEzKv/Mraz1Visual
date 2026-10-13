//! Статус в Discord, пока открыт лаунчер: «Mraz1Visual · В лаунчере».
//! Когда запускается игра, статус передаётся моду (он ставит свой), после выхода из игры — снова лаунчер.
//! Прямо через локальный канал discord-ipc, без библиотек.

use std::io::{Read, Write};
use std::sync::mpsc::{channel, Sender};
use std::sync::{Mutex, OnceLock};
use std::time::{Duration, SystemTime, UNIX_EPOCH};

/// ID приложения «Mraz1Visual» из Discord Developer Portal. Берётся из manifest.json релиза
/// (поле discord_app_id) — одно место для лаунчера и мода.
static APP_ID: Mutex<String> = Mutex::new(String::new());

pub fn set_app_id(id: &str) {
    let id = id.trim();
    if id.chars().all(|c| c.is_ascii_digit()) && id.len() >= 15 {
        *APP_ID.lock().unwrap() = id.to_string();
    }
}

pub fn app_id() -> String {
    APP_ID.lock().unwrap().clone()
}

enum Cmd {
    Launcher,
    Clear,
}

static TX: OnceLock<Mutex<Sender<Cmd>>> = OnceLock::new();

/// Показать «В лаунчере».
pub fn launcher() {
    send(Cmd::Launcher);
}

/// Убрать статус лаунчера (игра поставит свой).
pub fn clear() {
    send(Cmd::Clear);
}

fn send(c: Cmd) {
    if app_id().is_empty() {
        return;
    }
    let tx = TX.get_or_init(|| {
        let (tx, rx) = channel::<Cmd>();
        std::thread::spawn(move || worker(rx));
        Mutex::new(tx)
    });
    if let Ok(t) = tx.lock() {
        let _ = t.send(c);
    }
}

trait Pipe: Read + Write + Send {}
impl<T: Read + Write + Send> Pipe for T {}

fn connect() -> Option<Box<dyn Pipe>> {
    for i in 0..10 {
        #[cfg(windows)]
        {
            if let Ok(f) = std::fs::OpenOptions::new().read(true).write(true).open(format!(r"\\.\pipe\discord-ipc-{i}")) {
                return Some(Box::new(f));
            }
        }
        #[cfg(unix)]
        {
            let mut dirs = Vec::new();
            for env in ["XDG_RUNTIME_DIR", "TMPDIR", "TMP", "TEMP"] {
                if let Ok(v) = std::env::var(env) {
                    dirs.push(v.clone());
                    dirs.push(format!("{v}/app/com.discordapp.Discord"));
                    dirs.push(format!("{v}/snap.discord"));
                }
            }
            dirs.push("/tmp".into());
            for d in dirs {
                if let Ok(s) = std::os::unix::net::UnixStream::connect(format!("{d}/discord-ipc-{i}")) {
                    return Some(Box::new(s));
                }
            }
        }
    }
    None
}

fn frame(p: &mut Box<dyn Pipe>, op: u32, json: &str) -> std::io::Result<()> {
    let mut b = Vec::with_capacity(8 + json.len());
    b.extend_from_slice(&op.to_le_bytes());
    b.extend_from_slice(&(json.len() as u32).to_le_bytes());
    b.extend_from_slice(json.as_bytes());
    p.write_all(&b)?;
    p.flush()?;
    let mut head = [0u8; 8];
    p.read_exact(&mut head)?;
    let len = u32::from_le_bytes([head[4], head[5], head[6], head[7]]) as usize;
    if len > 1 << 20 {
        return Err(std::io::Error::other("bad frame"));
    }
    let mut body = vec![0u8; len];
    p.read_exact(&mut body)?;
    if u32::from_le_bytes([head[0], head[1], head[2], head[3]]) == 2 {
        return Err(std::io::Error::other("closed"));
    }
    Ok(())
}

fn activity(start: u64) -> String {
    serde_json::json!({
        "cmd": "SET_ACTIVITY",
        "nonce": format!("{}", SystemTime::now().duration_since(UNIX_EPOCH).map(|d| d.as_nanos()).unwrap_or(0)),
        "args": {
            "pid": std::process::id(),
            "activity": {
                "details": "В лаунчере",
                "state": "Готовится к игре",
                "timestamps": { "start": start },
                "assets": { "large_image": "logo", "large_text": "Mraz1Visual — визуальный клиент" },
                "buttons": [{ "label": "Скачать Mraz1Visual", "url": "https://github.com/SpeedyEzKv/Mraz1Visual/releases/latest" }]
            }
        }
    })
    .to_string()
}

fn worker(rx: std::sync::mpsc::Receiver<Cmd>) {
    let start = SystemTime::now().duration_since(UNIX_EPOCH).map(|d| d.as_secs()).unwrap_or(0);
    let mut want_on = false;
    let mut pipe: Option<Box<dyn Pipe>> = None;
    loop {
        // ждём команду; раз в 15 с пробуем переподключиться, если Discord запустили позже
        match rx.recv_timeout(Duration::from_secs(15)) {
            Ok(Cmd::Launcher) => want_on = true,
            Ok(Cmd::Clear) => want_on = false,
            Err(std::sync::mpsc::RecvTimeoutError::Timeout) => {}
            Err(_) => return,
        }
        if !want_on {
            // закрываем канал — статус лаунчера пропадает, игра ставит свой
            pipe = None;
            continue;
        }
        if pipe.is_none() {
            if let Some(mut p) = connect() {
                let hello = format!("{{\"v\":1,\"client_id\":\"{}\"}}", app_id());
                if frame(&mut p, 0, &hello).is_ok() {
                    pipe = Some(p);
                }
            }
        }
        if let Some(p) = pipe.as_mut() {
            if frame(p, 1, &activity(start)).is_err() {
                pipe = None;
            }
        }
    }
}

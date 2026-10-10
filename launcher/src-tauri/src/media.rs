//! «Сейчас играет»: читает медиа-сессию Windows (Spotify, Яндекс Музыка, браузер — всё,
//! что показывает себя в системном медиа-оверлее) и отдаёт игре по локальному HTTP.
//! Сервер слушает только 127.0.0.1 и требует одноразовый токен, который знает только игра.

use serde::Serialize;
use std::sync::{Arc, Mutex};
use tokio::io::{AsyncReadExt, AsyncWriteExt};
use tokio::net::TcpListener;

#[derive(Serialize, Clone, Default)]
pub struct NowPlaying {
    pub title: String,
    pub artist: String,
    pub app: String,
    pub playing: bool,
    /// миллисекунды
    pub position: u64,
    pub duration: u64,
    /// меняется вместе с обложкой
    pub cover_id: u64,
    #[serde(skip)]
    pub cover: Vec<u8>,
}

#[derive(Clone, Copy)]
pub enum Cmd {
    Toggle,
    Next,
    Prev,
}

pub struct Media {
    pub port: u16,
    pub token: String,
}

/// Запустить опрос медиа и HTTP-мост. Возвращает порт и токен для игры.
pub async fn start() -> Option<Media> {
    let state = Arc::new(Mutex::new(NowPlaying::default()));
    let (tx, rx) = std::sync::mpsc::channel::<Cmd>();
    platform::spawn_poller(state.clone(), rx);

    let listener = TcpListener::bind("127.0.0.1:0").await.ok()?;
    let port = listener.local_addr().ok()?.port();
    let token = format!("{:x}{:x}", std::process::id(), std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH).map(|d| d.as_nanos()).unwrap_or(0));
    let tok = token.clone();
    let tx = Arc::new(Mutex::new(tx));
    tokio::spawn(async move {
        loop {
            let Ok((mut sock, _)) = listener.accept().await else { continue };
            let state = state.clone();
            let tok = tok.clone();
            let tx = tx.clone();
            tokio::spawn(async move {
                let mut buf = vec![0u8; 2048];
                let n = match sock.read(&mut buf).await { Ok(n) => n, Err(_) => return };
                let req = String::from_utf8_lossy(&buf[..n]).to_string();
                let line = req.lines().next().unwrap_or("");
                let path = line.split_whitespace().nth(1).unwrap_or("/");
                let authorized = req.lines().any(|l| l.eq_ignore_ascii_case(&format!("x-mraz1visual-token: {tok}")));
                let (status, ctype, body): (&str, &str, Vec<u8>) = if !authorized {
                    ("403 Forbidden", "text/plain", b"no".to_vec())
                } else if path.starts_with("/now") {
                    let s = state.lock().unwrap().clone();
                    ("200 OK", "application/json", serde_json::to_vec(&s).unwrap_or_default())
                } else if path.starts_with("/cover") {
                    let c = state.lock().unwrap().cover.clone();
                    if c.is_empty() { ("404 Not Found", "text/plain", vec![]) } else { ("200 OK", "application/octet-stream", c) }
                } else if let Some(cmd) = path.strip_prefix("/cmd/") {
                    let c = match cmd { "toggle" => Some(Cmd::Toggle), "next" => Some(Cmd::Next), "prev" => Some(Cmd::Prev), _ => None };
                    if let Some(c) = c { let _ = tx.lock().unwrap().send(c); }
                    ("200 OK", "text/plain", b"ok".to_vec())
                } else {
                    ("404 Not Found", "text/plain", vec![])
                };
                let head = format!("HTTP/1.1 {status}\r\nContent-Type: {ctype}\r\nContent-Length: {}\r\nConnection: close\r\n\r\n", body.len());
                let _ = sock.write_all(head.as_bytes()).await;
                let _ = sock.write_all(&body).await;
            });
        }
    });
    Some(Media { port, token })
}

#[cfg(windows)]
mod platform {
    use super::{Cmd, NowPlaying};
    use std::sync::mpsc::Receiver;
    use std::sync::{Arc, Mutex};
    use std::time::Duration;
    use windows::Media::Control::{
        GlobalSystemMediaTransportControlsSession as Session,
        GlobalSystemMediaTransportControlsSessionManager as Manager,
        GlobalSystemMediaTransportControlsSessionPlaybackStatus as Status,
    };
    use windows::Storage::Streams::DataReader;

    pub fn spawn_poller(state: Arc<Mutex<NowPlaying>>, rx: Receiver<Cmd>) {
        std::thread::spawn(move || {
            unsafe {
                let _ = windows::Win32::System::WinRT::RoInitialize(windows::Win32::System::WinRT::RO_INIT_MULTITHREADED);
            }
            let Ok(op) = Manager::RequestAsync() else { return };
            let Ok(manager) = op.get() else { return };
            let mut last_key = String::new();
            let mut cover_id = 0u64;
            loop {
                while let Ok(cmd) = rx.try_recv() {
                    if let Ok(s) = manager.GetCurrentSession() {
                        let _ = match cmd {
                            Cmd::Toggle => s.TryTogglePlayPauseAsync().and_then(|o| o.get()),
                            Cmd::Next => s.TrySkipNextAsync().and_then(|o| o.get()),
                            Cmd::Prev => s.TrySkipPreviousAsync().and_then(|o| o.get()),
                        };
                    }
                }
                let snapshot = manager.GetCurrentSession().ok().and_then(|s| read(&s, &mut last_key, &mut cover_id));
                {
                    let mut st = state.lock().unwrap();
                    match snapshot {
                        Some(mut n) => {
                            if n.cover.is_empty() && n.cover_id == st.cover_id {
                                n.cover = std::mem::take(&mut st.cover);
                            }
                            *st = n;
                        }
                        None => *st = NowPlaying::default(),
                    }
                }
                std::thread::sleep(Duration::from_millis(700));
            }
        });
    }

    fn read(s: &Session, last_key: &mut String, cover_id: &mut u64) -> Option<NowPlaying> {
        let props = s.TryGetMediaPropertiesAsync().ok()?.get().ok()?;
        let title = props.Title().ok()?.to_string();
        if title.is_empty() {
            return None;
        }
        let artist = props.Artist().map(|h| h.to_string()).unwrap_or_default();
        let app = s.SourceAppUserModelId().map(|h| h.to_string()).unwrap_or_default();
        let playing = s.GetPlaybackInfo().and_then(|i| i.PlaybackStatus()).map(|st| st == Status::Playing).unwrap_or(false);
        let (position, duration) = s.GetTimelineProperties().map(|t| {
            let p = t.Position().map(|d| d.Duration / 10_000).unwrap_or(0).max(0) as u64;
            let e = t.EndTime().map(|d| d.Duration / 10_000).unwrap_or(0).max(0) as u64;
            (p, e)
        }).unwrap_or((0, 0));

        // обложку читаем только когда сменился трек
        let key = format!("{title}\u{1}{artist}");
        let mut cover = Vec::new();
        if key != *last_key {
            *last_key = key;
            *cover_id += 1;
            if let Ok(thumb) = props.Thumbnail() {
                if let Ok(stream) = thumb.OpenReadAsync().and_then(|o| o.get()) {
                    if let Ok(size) = stream.Size() {
                        let size = size.min(4 * 1024 * 1024) as u32;
                        if let Ok(input) = stream.GetInputStreamAt(0) {
                            if let Ok(reader) = DataReader::CreateDataReader(&input) {
                                if reader.LoadAsync(size).and_then(|o| o.get()).is_ok() {
                                    let mut buf = vec![0u8; size as usize];
                                    if reader.ReadBytes(&mut buf).is_ok() {
                                        cover = buf;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Some(NowPlaying { title, artist, app, playing, position, duration, cover_id: *cover_id, cover })
    }
}

#[cfg(not(windows))]
mod platform {
    use super::{Cmd, NowPlaying};
    use std::sync::mpsc::Receiver;
    use std::sync::{Arc, Mutex};

    /// Не Windows — медиа-сессий нет, мост отдаёт пустое состояние.
    pub fn spawn_poller(_state: Arc<Mutex<NowPlaying>>, _rx: Receiver<Cmd>) {}
}

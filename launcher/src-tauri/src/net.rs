//! Скачивание: проверка размера/sha1, параллельные загрузки, ретраи.

use anyhow::{anyhow, Context, Result};
use futures::StreamExt;
use sha1::{Digest, Sha1};
use std::path::{Path, PathBuf};
use tokio::io::AsyncWriteExt;

pub fn client() -> reqwest::Client {
    reqwest::Client::builder()
        .user_agent(concat!("Mraz1VisualLauncher/", env!("CARGO_PKG_VERSION")))
        .connect_timeout(std::time::Duration::from_secs(15))
        .timeout(std::time::Duration::from_secs(600))
        .build()
        .expect("http client")
}

#[derive(Clone)]
pub struct Job {
    pub url: String,
    pub path: PathBuf,
    pub sha1: Option<String>,
    pub size: Option<u64>,
}

/// Файл уже на месте? Сначала размер (быстро), потом sha1, если он известен.
pub fn is_valid(path: &Path, sha1: Option<&str>, size: Option<u64>) -> bool {
    let Ok(meta) = std::fs::metadata(path) else { return false };
    if let Some(s) = size {
        if meta.len() != s {
            return false;
        }
    }
    match sha1 {
        Some(expected) => match std::fs::read(path) {
            Ok(bytes) => hex::encode(Sha1::digest(&bytes)).eq_ignore_ascii_case(expected),
            Err(_) => false,
        },
        None => meta.len() > 0,
    }
}

/// Скачать один файл (3 попытки). Пишет во временный файл и переименовывает.
pub async fn fetch(client: &reqwest::Client, job: &Job) -> Result<()> {
    fetch_verified(client, job, None).await
}

/// Do not replace an installed file until the downloaded bytes match the manifest.
pub async fn fetch_verified(client: &reqwest::Client, job: &Job, sha256: Option<&str>) -> Result<()> {
    if let Some(parent) = job.path.parent() {
        tokio::fs::create_dir_all(parent).await?;
    }
    let mut last = anyhow!("no attempts");
    for attempt in 0..3 {
        match fetch_once(client, job, sha256).await {
            Ok(()) => return Ok(()),
            Err(e) => {
                last = e;
                tokio::time::sleep(std::time::Duration::from_millis(400 * (attempt + 1))).await;
            }
        }
    }
    Err(last).with_context(|| format!("download {}", job.url))
}

async fn fetch_once(client: &reqwest::Client, job: &Job, sha256: Option<&str>) -> Result<()> {
    if let Some(relative) = job.url.strip_prefix("bundle://") {
        let relative = Path::new(relative);
        if relative.as_os_str().is_empty() || !relative.components().all(|part| matches!(part, std::path::Component::Normal(_))) {
            return Err(anyhow!("invalid bundle asset path"));
        }
        let executable = std::env::current_exe()?;
        let source = executable.parent().context("launcher directory unavailable")?.join(relative);
        let bytes = tokio::fs::read(&source).await.with_context(|| format!("local bundle asset {}", source.display()))?;
        if let Some(expected) = &job.sha1 {
            if !hex::encode(Sha1::digest(&bytes)).eq_ignore_ascii_case(expected) {
                return Err(anyhow!("sha1 mismatch for local bundle asset"));
            }
        }
        let tmp = job.path.with_extension("part");
        tokio::fs::write(&tmp, bytes).await?;
        return verify_and_install(&tmp, job, sha256).await;
    }
    let resp = client.get(&job.url).send().await?.error_for_status()?;
    let tmp = job.path.with_extension("part");
    let mut file = tokio::fs::File::create(&tmp).await?;
    let mut hasher = Sha1::new();
    let mut stream = resp.bytes_stream();
    while let Some(chunk) = stream.next().await {
        let chunk = chunk?;
        hasher.update(&chunk);
        file.write_all(&chunk).await?;
    }
    file.flush().await?;
    drop(file);
    if let Some(expected) = &job.sha1 {
        let got = hex::encode(hasher.finalize());
        if !got.eq_ignore_ascii_case(expected) {
            let _ = tokio::fs::remove_file(&tmp).await;
            return Err(anyhow!("sha1 mismatch for {}", job.url));
        }
    }
    verify_and_install(&tmp, job, sha256).await
}

async fn verify_and_install(tmp: &Path, job: &Job, sha256: Option<&str>) -> Result<()> {
    if let Some(expected) = job.size {
        let actual = tokio::fs::metadata(tmp).await?.len();
        if actual != expected {
            let _ = tokio::fs::remove_file(tmp).await;
            return Err(anyhow!("Неполная загрузка: получено {actual} из {expected} байт. Повторите запуск."));
        }
    }
    install_staged(tmp, &job.path, sha256).await
}

async fn install_staged(tmp: &Path, path: &Path, sha256: Option<&str>) -> Result<()> {
    if let Some(expected) = sha256 {
        let bytes = tokio::fs::read(tmp).await?;
        if !hex::encode(sha2::Sha256::digest(bytes)).eq_ignore_ascii_case(expected) {
            let _ = tokio::fs::remove_file(tmp).await;
            return Err(anyhow!("{}: SHA-256 не совпадает с манифестом", path.display()));
        }
    }
    tokio::fs::rename(tmp, path).await?;
    Ok(())
}

/// Скачать пачку параллельно. `on_done` вызывается после каждого файла.
pub async fn fetch_all<F: FnMut(usize, usize)>(client: &reqwest::Client, jobs: Vec<Job>, parallel: usize, mut on_done: F) -> Result<()> {
    let todo: Vec<Job> = jobs
        .into_iter()
        // уже скачанное пропускаем: при известном размере сверяем только размер (быстро), иначе sha1
        .filter(|j| !is_valid(&j.path, if j.size.is_some() { None } else { j.sha1.as_deref() }, j.size))
        .collect();
    let total = todo.len();
    on_done(0, total);
    let mut stream = futures::stream::iter(todo.into_iter().map(|j| async move { fetch(client, &j).await }))
        .buffer_unordered(parallel);
    let mut done = 0;
    while let Some(r) = stream.next().await {
        r?;
        done += 1;
        on_done(done, total);
    }
    Ok(())
}

pub async fn get_json(client: &reqwest::Client, url: &str) -> Result<serde_json::Value> {
    Ok(client.get(url).send().await?.error_for_status()?.json().await?)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn truncated_download_preserves_installed_file() -> Result<()> {
        use tokio::io::AsyncReadExt;
        let listener = tokio::net::TcpListener::bind("127.0.0.1:0").await?;
        let address = listener.local_addr()?;
        let server = tokio::spawn(async move {
            let (mut socket, _) = listener.accept().await?;
            let mut request = [0; 1024];
            socket.read(&mut request).await?;
            socket.write_all(b"HTTP/1.1 200 OK\r\nContent-Length: 1\r\nConnection: close\r\n\r\nx").await
        });
        let dir = std::env::temp_dir().join(format!("Обновление клиента {}", std::process::id()));
        tokio::fs::create_dir_all(&dir).await?;
        let path = dir.join("client.jar");
        tokio::fs::write(&path, b"old").await?;
        let job = Job { url: format!("http://{address}/jar"), path: path.clone(), size: Some(12), sha1: None };
        let result = fetch_once(&client(), &job, None).await;
        server.await??;
        let contents = tokio::fs::read(&path).await?;
        tokio::fs::remove_file(path).await?;
        let _ = tokio::fs::remove_dir(dir).await;
        assert!(result.is_err(), "truncated response accepted");
        assert_eq!(contents, b"old");
        Ok(())
    }

    #[tokio::test]
    async fn invalid_update_preserves_installed_jar() -> Result<()> {
        let unique = std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH)?.as_nanos();
        let installed = std::env::temp_dir().join(format!("mraz1-install-test-{}-{unique}.jar", std::process::id()));
        let staged = installed.with_extension("part");
        let result: Result<()> = async {
            tokio::fs::write(&installed, b"previous build").await?;
            tokio::fs::write(&staged, b"wrong build").await?;
            let expected = hex::encode(sha2::Sha256::digest(b"next build"));
            assert!(install_staged(&staged, &installed, Some(&expected)).await.is_err());
            assert_eq!(tokio::fs::read(&installed).await?, b"previous build");
            tokio::fs::write(&staged, b"next build").await?;
            install_staged(&staged, &installed, Some(&expected)).await?;
            assert_eq!(tokio::fs::read(&installed).await?, b"next build");
            Ok(())
        }.await;
        let _ = tokio::fs::remove_file(staged).await;
        let _ = tokio::fs::remove_file(installed).await;
        result
    }
}

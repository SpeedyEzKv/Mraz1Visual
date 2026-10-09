//! Скачивание: проверка размера/sha1, параллельные загрузки, ретраи.

use anyhow::{anyhow, Context, Result};
use futures::StreamExt;
use sha1::{Digest, Sha1};
use std::path::{Path, PathBuf};
use tokio::io::AsyncWriteExt;

pub fn client() -> reqwest::Client {
    reqwest::Client::builder()
        .user_agent(concat!("UmbraLauncher/", env!("CARGO_PKG_VERSION")))
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
    if let Some(parent) = job.path.parent() {
        tokio::fs::create_dir_all(parent).await?;
    }
    let mut last = anyhow!("no attempts");
    for attempt in 0..3 {
        match fetch_once(client, job).await {
            Ok(()) => return Ok(()),
            Err(e) => {
                last = e;
                tokio::time::sleep(std::time::Duration::from_millis(400 * (attempt + 1))).await;
            }
        }
    }
    Err(last).with_context(|| format!("download {}", job.url))
}

async fn fetch_once(client: &reqwest::Client, job: &Job) -> Result<()> {
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
    tokio::fs::rename(&tmp, &job.path).await?;
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

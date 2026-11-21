#![cfg_attr(windows, windows_subsystem = "windows")]
#[path="../media.rs"] mod media;
use std::io::{Read, Write};
#[tokio::main]
async fn main() {
    let Some(bridge)=media::start().await else{return;};
    // Private process pipe consumed by the Java parent. Never forward to the game log.
    let handshake=serde_json::json!({"port":bridge.port,"token":bridge.token});
    let _=writeln!(std::io::stdout(),"{handshake}");let _=std::io::stdout().flush();
    // The parent keeps stdin open. Exit automatically on game shutdown/crash.
    tokio::task::spawn_blocking(||{let mut byte=[0];while std::io::stdin().read(&mut byte).unwrap_or(0)>0 {}}).await.ok();
}

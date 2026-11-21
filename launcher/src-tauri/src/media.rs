//! One authenticated loopback bridge shared by the launcher and the standalone game helper.
use serde::{Deserialize, Serialize};
use std::{collections::VecDeque, sync::{Arc, Mutex, mpsc}, time::{Duration, Instant}};
use tokio::{io::{AsyncReadExt, AsyncWriteExt}, net::TcpListener, sync::oneshot};

#[derive(Serialize, Deserialize, Clone, Default)]
#[serde(default)]
pub struct Capabilities { pub toggle: bool, pub next: bool, pub prev: bool, pub seek: bool, pub volume: bool }
#[derive(Serialize, Deserialize, Clone, Default)]
pub struct Source { pub id: String, pub name: String }
#[derive(Serialize, Deserialize, Clone, Default)]
#[serde(default)]
pub struct NowPlaying {
    pub title: String, pub artist: String, pub app: String, pub playing: bool,
    pub position: u64, pub duration: u64, pub updated_at: u64, pub cover_id: u64,
    pub caps: Capabilities, pub sources: Vec<Source>, pub selected: String, pub error: String,
    pub volume: f32,
    #[serde(skip)] pub cover: Vec<u8>,
}
#[derive(Serialize, Deserialize, Clone)]
pub struct Command { pub command: String, #[serde(default)] pub value: u64 }
impl Command {
    fn valid(&self) -> bool { matches!(self.command.as_str(), "toggle"|"next"|"prev"|"seek"|"volume") }
    fn allowed(&self, c: &Capabilities) -> bool { match self.command.as_str() { "toggle"=>c.toggle,"next"=>c.next,"prev"=>c.prev,"seek"=>c.seek,"volume"=>c.volume,_=>false } }
}
type Reply = oneshot::Sender<Result<(), String>>;
struct Request { command: Command, target: String, reply: Reply }
#[derive(Serialize)]
pub struct LocalCommand { id: u64, #[serde(flatten)] command: Command }
struct Pending { command: LocalCommand, reply: Reply, at: Instant, delivered: bool }
struct Shared {
    snapshot: NowPlaying, selected: String, local: NowPlaying, local_at: Option<Instant>,
    pending: VecDeque<Pending>, serial: u64,
}
impl Default for Shared {
    fn default() -> Self { Self { snapshot: NowPlaying::default(), selected:"auto".into(), local:NowPlaying::default(), local_at:None, pending:VecDeque::new(), serial:0 } }
}
pub struct Media { pub port: u16, pub token: String, state: Arc<Mutex<Shared>>, tx: mpsc::SyncSender<Request> }
impl Media {
    pub fn snapshot(&self) -> NowPlaying { let n=self.state.lock().unwrap().snapshot.clone();if n.updated_at>0&&now().saturating_sub(n.updated_at)>6000{return NowPlaying{sources:n.sources,selected:n.selected,error:"Источник перестал обновлять состояние".into(),..Default::default()};}n }
    pub fn select(&self, id: String) -> Result<(), String> {
        let mut s=self.state.lock().unwrap();
        if id!="auto" && !s.snapshot.sources.iter().any(|x| x.id==id) { return Err("Источник больше недоступен".into()); }
        s.selected=id; Ok(())
    }
    pub async fn command(&self, command: Command) -> Result<(), String> {
        if !command.valid() { return Err("Неизвестная команда".into()); }
        let (reply, rx)=oneshot::channel();
        {
            let mut s=self.state.lock().unwrap();
            if s.snapshot.updated_at>0&&now().saturating_sub(s.snapshot.updated_at)>6000{return Err("Источник перестал обновлять состояние".into());}
            if !command.allowed(&s.snapshot.caps) { return Err("Плеер не поддерживает эту команду".into()); }
            if s.selected=="local" {
                if s.pending.len()>=16 { return Err("Плеер занят".into()); }
                s.serial+=1; let id=s.serial;
                s.pending.push_back(Pending { command:LocalCommand{id,command},reply,at:Instant::now(),delivered:false });
            } else {
                self.tx.try_send(Request{command,target:s.selected.clone(),reply}).map_err(|_|"Медиасервис занят".to_string())?;
            }
        }
        tokio::time::timeout(Duration::from_secs(4),rx).await.map_err(|_|"Плеер не ответил".to_string())?.map_err(|_|"Медиасервис недоступен".to_string())?
    }
}
pub fn now() -> u64 { std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).unwrap_or_default().as_millis() as u64 }
fn token() -> Option<String> {
    let mut bytes=[0u8;32];
    #[cfg(windows)] unsafe {
        use windows::Win32::Security::Cryptography::*;
        if BCryptGenRandom(BCRYPT_ALG_HANDLE::default(), &mut bytes, BCRYPT_USE_SYSTEM_PREFERRED_RNG).0<0 { return None; }
    }
    #[cfg(not(windows))] { use std::io::Read; std::fs::File::open("/dev/urandom").ok()?.read_exact(&mut bytes).ok()?; }
    Some(hex::encode(bytes))
}
pub async fn start() -> Option<Media> {
    let state=Arc::new(Mutex::new(Shared::default())); let (tx,rx)=mpsc::sync_channel(32);
    let listener=TcpListener::bind("127.0.0.1:0").await.ok()?;
    let port=listener.local_addr().ok()?.port(); let token=token()?;
    platform::spawn(state.clone(),rx);
    let media=Arc::new(Media{port,token:token.clone(),state:state.clone(),tx:tx.clone()});
    tokio::spawn(async move { while let Ok((mut socket,_))=listener.accept().await {
        let media=media.clone();
        tokio::spawn(async move {
            let response=tokio::time::timeout(Duration::from_secs(6),request(&mut socket,&media)).await;
            let (status,kind,body)=response.ok().flatten().unwrap_or((400,"text/plain",b"Invalid request".to_vec()));
            let reason=match status{200=>"OK",403=>"Forbidden",404=>"Not Found",409=>"Conflict",_=>"Bad Request"};
            let header=format!("HTTP/1.1 {status} {reason}\r\nContent-Type: {kind}\r\nContent-Length: {}\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n",body.len());
            let _=socket.write_all(header.as_bytes()).await; let _=socket.write_all(&body).await;
        });
    }});
    Some(Media{port,token,state,tx})
}
async fn request(sock:&mut tokio::net::TcpStream,media:&Media)->Option<(u16,&'static str,Vec<u8>)> {
    let mut bytes=Vec::new(); let end;
    loop { let mut buf=[0;2048];let n=sock.read(&mut buf).await.ok()?;if n==0{return None;}bytes.extend_from_slice(&buf[..n]);
        if let Some(p)=bytes.windows(4).position(|b|b==b"\r\n\r\n"){end=p+4;break;}if bytes.len()>16384{return None;}}
    let head=std::str::from_utf8(&bytes[..end]).ok()?;
    let mut first=head.lines().next()?.split_whitespace();let method=first.next()?.to_string();let path=first.next()?.to_string();
    let header=|key:&str|head.lines().skip(1).filter_map(|l|l.split_once(':')).find(|(k,_)|k.eq_ignore_ascii_case(key)).map(|(_,v)|v.trim());
    if header("x-mraz1visual-token")!=Some(media.token.as_str()){return Some((403,"text/plain",vec![]));}
    let len=header("content-length").unwrap_or("0").parse::<usize>().ok()?;if len>4*1024*1024{return None;}
    while bytes.len()<end+len{let mut buf=[0;4096];let n=sock.read(&mut buf).await.ok()?;if n==0{return None;}bytes.extend_from_slice(&buf[..n]);}
    let body=&bytes[end..end+len];
    match (method.as_str(),path.as_str()) {
        ("GET","/now")=>Some((200,"application/json",serde_json::to_vec(&media.snapshot()).ok()?)),
        ("GET","/cover")=>{let c=media.snapshot().cover;Some((if c.is_empty(){404}else{200},"application/octet-stream",c))},
        ("POST","/command")=>{let c=serde_json::from_slice(body).ok()?;match media.command(c).await{Ok(())=>Some((200,"application/json",b"{\"ok\":true}".to_vec())),Err(e)=>Some((409,"application/json",serde_json::to_vec(&serde_json::json!({"error":e})).ok()?))}},
        ("POST","/select")=>{let j:serde_json::Value=serde_json::from_slice(body).ok()?;let result=media.select(j.get("id")?.as_str()?.to_string());Some((if result.is_ok(){200}else{409},"text/plain",vec![]))},
        ("POST","/local")=>{let mut n:NowPlaying=serde_json::from_slice(body).ok()?;if n.title.len()>2048||n.artist.len()>2048{return None;}n.app="Mraz1Visual".into();n.updated_at=now();
            let mut s=media.state.lock().unwrap();let source_id=n.cover_id;n.cover_id=source_id.saturating_mul(2);if source_id==s.local.cover_id/2{n.cover=std::mem::take(&mut s.local.cover);n.cover_id=s.local.cover_id;}s.local=n;s.local_at=Some(Instant::now());Some((200,"text/plain",vec![]))},
        ("POST","/local/cover")=>{let mut s=media.state.lock().unwrap();s.local.cover=body.to_vec();s.local.cover_id=(s.local.cover_id&!1)|u64::from(!body.is_empty());Some((200,"text/plain",vec![]))},
        ("GET","/local/commands")=>{let mut s=media.state.lock().unwrap();s.pending.retain(|p|p.at.elapsed()<Duration::from_secs(4));
            let commands:Vec<_>=s.pending.iter_mut().filter(|p|!p.delivered).map(|p|{p.delivered=true;&p.command}).collect();Some((200,"application/json",serde_json::to_vec(&commands).ok()?))},
        ("POST","/local/result")=>{let j:serde_json::Value=serde_json::from_slice(body).ok()?;let id=j.get("id")?.as_u64()?;let mut s=media.state.lock().unwrap();
            if let Some(i)=s.pending.iter().position(|p|p.command.id==id){let p=s.pending.remove(i)?;let _=p.reply.send(if j.get("ok").and_then(|x|x.as_bool())==Some(true){Ok(())}else{Err("Команда не выполнена".into())});}Some((200,"text/plain",vec![]))},
        _=>Some((404,"text/plain",vec![]))
    }
}

#[cfg(windows)]
mod platform {
    use super::*;
    use windows::{core::Interface, Media::Control::{GlobalSystemMediaTransportControlsSession as Session,GlobalSystemMediaTransportControlsSessionManager as Manager,GlobalSystemMediaTransportControlsSessionPlaybackStatus as Status},Storage::Streams::DataReader};
    fn id(s:&Session)->String { use sha2::{Sha256,Digest};hex::encode(Sha256::digest(format!("{}:{:p}",s.SourceAppUserModelId().unwrap_or_default(),s.as_raw()).as_bytes()))[..20].into() }
    fn sessions(m:&Manager)->Vec<Session>{m.GetSessions().map(|s|(0..s.Size().unwrap_or(0)).filter_map(|i|s.GetAt(i).ok()).collect()).unwrap_or_default()}
    fn selected(m:&Manager,target:&str)->Option<Session>{if target=="auto"{m.GetCurrentSession().ok()}else{sessions(m).into_iter().find(|s|id(s)==target)}}
    fn app_name(app:&str)->String{windows::ApplicationModel::AppInfo::GetFromAppUserModelId(&windows::core::HSTRING::from(app)).and_then(|i|i.DisplayInfo()).and_then(|d|d.DisplayName()).map(|n|n.to_string()).ok().filter(|n|!n.is_empty()).unwrap_or_else(||if app.len()>=12&&app.chars().all(|c|c.is_ascii_hexdigit()){"Приложение Windows".into()}else{app.into()})}
    #[derive(Default)]struct CoverState{key:String,serial:u64,ready:bool,attempt:Option<Instant>}
    pub fn spawn(state:Arc<Mutex<Shared>>,rx:mpsc::Receiver<Request>){std::thread::spawn(move||{
        unsafe{let _=windows::Win32::System::WinRT::RoInitialize(windows::Win32::System::WinRT::RO_INIT_MULTITHREADED);}
        let manager=Manager::RequestAsync().and_then(|o|o.get());
        let mut art=CoverState::default();
        loop {
            while let Ok(r)=rx.try_recv(){if r.reply.is_closed(){continue;}let result=manager.as_ref().ok().and_then(|m|selected(m,&r.target)).ok_or_else(||"Источник недоступен".to_string()).and_then(|s|execute(&s,&r.command));let _=r.reply.send(result);}
            let target=state.lock().unwrap().selected.clone();
            let sources=manager.as_ref().map(|m|sessions(m).iter().map(|s|{let raw=s.SourceAppUserModelId().unwrap_or_default().to_string();let mut name=app_name(&raw);if name=="Приложение Windows"{if let Ok(p)=s.TryGetMediaPropertiesAsync().and_then(|o|o.get()){if let Ok(t)=p.Title(){name=format!("{name} · {}",t.to_string().chars().take(65).collect::<String>());}}}Source{id:id(s),name}}).collect::<Vec<_>>()).unwrap_or_default();
            let snapshot=manager.as_ref().ok().and_then(|m|selected(m,&target)).and_then(|s|read(&s,&mut art));
            if snapshot.is_none(){art.key.clear();}
            let mut s=state.lock().unwrap();let old=std::mem::take(&mut s.snapshot);
            let local_alive=s.local_at.map(|t|t.elapsed()<Duration::from_secs(5)).unwrap_or(false)&&!s.local.title.is_empty();
            let mut n=if target=="local"&&local_alive{s.local.clone()}else if target=="local"{NowPlaying::default()}else{snapshot.unwrap_or_default()};
            if n.cover_id==old.cover_id&&n.app==old.app&&n.title==old.title&&n.cover.is_empty(){n.cover=old.cover;}
            n.sources=sources;if local_alive{n.sources.push(Source{id:"local".into(),name:"Плеер Mraz1Visual".into()});}
            n.selected=target;if n.title.is_empty(){n.error=if manager.is_err(){"Windows не предоставила медиасессии"}else if n.selected!="auto"{"Выбранный источник недоступен"}else{"Запусти музыку в плеере или браузере"}.into();}
            s.snapshot=n;s.pending.retain(|p|p.at.elapsed()<Duration::from_secs(4));drop(s);std::thread::sleep(Duration::from_millis(350));
        }
    });}
    fn execute(s:&Session,c:&Command)->Result<(),String>{
        let info=s.GetPlaybackInfo().map_err(|_|"Источник недоступен".to_string())?;let controls=info.Controls().map_err(|_|"Нет управления".to_string())?;
        let playing=info.PlaybackStatus().ok()==Some(Status::Playing);
        let result=match c.command.as_str(){
            "toggle" if controls.IsPlayPauseToggleEnabled().unwrap_or(false)=>s.TryTogglePlayPauseAsync().and_then(|o|o.get()),
            "toggle" if playing&&controls.IsPauseEnabled().unwrap_or(false)=>s.TryPauseAsync().and_then(|o|o.get()),
            "toggle" if !playing&&controls.IsPlayEnabled().unwrap_or(false)=>s.TryPlayAsync().and_then(|o|o.get()),
            "next" if controls.IsNextEnabled().unwrap_or(false)=>s.TrySkipNextAsync().and_then(|o|o.get()),
            "prev" if controls.IsPreviousEnabled().unwrap_or(false)=>s.TrySkipPreviousAsync().and_then(|o|o.get()),
            "seek" if controls.IsPlaybackPositionEnabled().unwrap_or(false)=>{let start=s.GetTimelineProperties().and_then(|t|t.StartTime()).map(|t|t.Duration.max(0) as u64/10000).unwrap_or(0);s.TryChangePlaybackPositionAsync(c.value.saturating_add(start).saturating_mul(10000).min(i64::MAX as u64) as i64).and_then(|o|o.get())},
            _=>return Err("Плеер не поддерживает эту команду".into())};
        match result{Ok(true)=>Ok(()),_=>Err("Плеер отклонил команду".into())}
    }
    fn read(s:&Session,art:&mut CoverState)->Option<NowPlaying>{
        let props=s.TryGetMediaPropertiesAsync().ok()?.get().ok()?;let title=props.Title().ok()?.to_string();if title.is_empty(){return None;}
        let artist=props.Artist().unwrap_or_default().to_string();let app=s.SourceAppUserModelId().unwrap_or_default().to_string();
        let info=s.GetPlaybackInfo().ok()?;let playing=info.PlaybackStatus().ok()==Some(Status::Playing);
        let controls=info.Controls().ok()?;
        let (position,duration)=s.GetTimelineProperties().map(|t|{let start=t.StartTime().map(|d|d.Duration/10000).unwrap_or(0).max(0) as u64;
            let mut p=(t.Position().map(|d|d.Duration/10000).unwrap_or(0).max(0) as u64).saturating_sub(start);
            let duration=(t.EndTime().map(|d|d.Duration/10000).unwrap_or(0).max(0) as u64).saturating_sub(start);
            let updated=t.LastUpdatedTime().map(|d|d.UniversalTime/10000-11644473600000).unwrap_or(now() as i64).max(0) as u64;
            if playing{p=p.saturating_add(now().saturating_sub(updated).min(86400000));}if duration>0{p=p.min(duration);} (p,duration)
        }).unwrap_or((0,0));
        let caps=Capabilities{toggle:controls.IsPlayPauseToggleEnabled().unwrap_or(false)||if playing{controls.IsPauseEnabled().unwrap_or(false)}else{controls.IsPlayEnabled().unwrap_or(false)},next:controls.IsNextEnabled().unwrap_or(false),prev:controls.IsPreviousEnabled().unwrap_or(false),seek:duration>0&&controls.IsPlaybackPositionEnabled().unwrap_or(false),volume:false};
        let track=format!("{}\u{1}{title}\u{1}{artist}",id(s));let mut cover=Vec::new();
        if art.key!=track{art.key=track;art.serial+=1;art.ready=false;art.attempt=None;}
        if !art.ready&&art.attempt.map(|t|t.elapsed()>=Duration::from_secs(2)).unwrap_or(true){art.attempt=Some(Instant::now());if let Ok(stream)=props.Thumbnail().and_then(|t|t.OpenReadAsync()).and_then(|o|o.get()){
            if let Ok(input)=stream.GetInputStreamAt(0){if let Ok(reader)=DataReader::CreateDataReader(&input){let size=stream.Size().unwrap_or(0).min(4*1024*1024) as u32;if reader.LoadAsync(size).and_then(|o|o.get()).is_ok(){cover.resize(size as usize,0);if reader.ReadBytes(&mut cover).is_err(){cover.clear();}}}}}}
        if !cover.is_empty(){art.ready=true;art.serial+=1;}
        Some(NowPlaying{title,artist,app:app_name(&app),playing,position,duration,updated_at:now(),cover_id:art.serial,caps,cover,..Default::default()})
    }
}
#[cfg(not(windows))]
mod platform { use super::*;pub fn spawn(_:Arc<Mutex<Shared>>,_:mpsc::Receiver<Request>){} }

#[cfg(test)]
mod tests {
    use super::*;
    #[test] fn command_capabilities_are_enforced(){let c=Command{command:"seek".into(),value:20};assert!(c.valid());assert!(!c.allowed(&Capabilities::default()));assert!(!Command{command:"shell".into(),value:0}.valid());}
    #[test] fn credentials_are_random(){let a=token().unwrap();let b=token().unwrap();assert_eq!(a.len(),64);assert_ne!(a,b);}
    #[test] fn expired_metadata_never_keeps_artwork_or_timeline(){
        let mut shared=Shared::default();shared.snapshot=NowPlaying{title:"old".into(),app:"old app".into(),duration:10000,position:1000,updated_at:now()-7000,cover:vec![1,2,3],selected:"auto".into(),sources:vec![Source{id:"keep".into(),name:"source".into()}],caps:Capabilities{seek:true,..Default::default()},..Default::default()};
        let(tx,_rx)=mpsc::sync_channel(1);let media=Media{port:0,token:String::new(),state:Arc::new(Mutex::new(shared)),tx};let n=media.snapshot();assert!(n.title.is_empty()&&n.app.is_empty()&&n.cover.is_empty());assert_eq!(n.duration,0);assert_eq!(n.position,0);assert!(!n.caps.seek);assert_eq!(n.sources.len(),1);
    }
}

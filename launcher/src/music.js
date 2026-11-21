/* Only renders native metadata; no cookies, downloads, fake audio levels or optimistic play state. */
(() => {
  const el=id=>document.getElementById(id), call=(command,args={})=>window.__TAURI__.core.invoke(command,args);
  let current=null, polling=false, pending=false, coverKey="", coverUrl="", sampledAt=0, errorUntil=0;
  const label=app=>{const a=(app||"").toLowerCase();return a.includes("soundcloud")?"SoundCloud":a.includes("zen")?"Zen · браузер":a.includes("spotify")?"Spotify":a.includes("yandex")?(a.includes("music")?"Яндекс Музыка":"Яндекс Браузер"):a.includes("chrome")?"Chrome":a.includes("msedge")?"Edge":a.includes("firefox")?"Firefox":app==="Mraz1Visual"?"Плеер Mraz1Visual":app?app.split(/[!\\/]/).pop().slice(0,60):"Windows Media";};
  const time=ms=>{const n=Math.floor(Math.max(0,ms)/1000);return Math.floor(n/60)+":"+String(n%60).padStart(2,"0");};
  function hint(text,error=false){el("musicHint").textContent=text;if(error)errorUntil=Date.now()+6000;}
  function capabilities(){document.querySelectorAll("[data-media]").forEach(b=>{const supported=!!current?.caps?.[b.dataset.media];b.disabled=pending||!supported;b.title=supported?({toggle:"Воспроизведение / пауза",next:"Следующий трек",prev:"Предыдущий трек"}[b.dataset.media]):"Источник не поддерживает это действие";if(b.dataset.media==="toggle"){b.textContent=current?.playing?"Ⅱ":"▶";b.setAttribute("aria-label",current?.playing?"Пауза":"Воспроизвести");}});el("musicSeek").disabled=pending||!current?.caps?.seek;el("musicVolume").disabled=pending||!current?.caps?.volume;el("musicSource").disabled=pending;}
  function resetOffline(){current=null;coverKey="";if(coverUrl)URL.revokeObjectURL(coverUrl);coverUrl="";
    for(const id of ["miniCover","fullCover"]){el(id).hidden=true;el(id).removeAttribute("src");}
    el("miniTitle").textContent="Связь с плеером потеряна";el("miniTitle").title="";el("miniArtist").textContent="Попробую подключиться снова";el("miniState").textContent="НЕТ СВЯЗИ";
    el("fullTitle").textContent="Плеер недоступен";el("fullArtist").textContent="Соединение восстановится автоматически";el("fullState").textContent="НЕТ СВЯЗИ";el("fullApp").textContent="Windows Media";
    el("musicSeek").disabled=el("musicVolume").disabled=true;el("musicSeek").value=0;el("musicPosition").textContent=el("musicDuration").textContent="—:—";el("musicVolumeValue").textContent="—";capabilities();
  }
  function progress(){if(!current)return;const known=current.duration>0;const pos=known?Math.min(current.duration,current.position+(current.playing?Math.max(0,performance.now()-sampledAt):0)):0;
    if(document.activeElement!==el("musicSeek"))el("musicSeek").value=pos;
    el("musicPosition").textContent=known?time(pos):"—:—";el("musicDuration").textContent=known?time(current.duration):"—:—";
  }
  async function update(){if(polling)return;polling=true;try{
    const n=await call("media_snapshot");current=n;sampledAt=performance.now();
    const has=!!n.title;el("miniTitle").textContent=n.title||"Сейчас ничего не играет";el("miniTitle").title=n.title||"";
    el("miniArtist").textContent=n.artist||label(n.app);el("miniState").textContent=has?(n.playing?"СЕЙЧАС ИГРАЕТ":"НА ПАУЗЕ"):"МУЗЫКА";
    el("fullTitle").textContent=n.title||"Твоя музыка — здесь";el("fullArtist").textContent=n.artist||(!has?"Запусти плеер или видео в браузере":"Исполнитель не указан источником");
    el("fullApp").textContent=label(n.app);el("fullState").textContent=has?(n.playing?"ВОСПРОИЗВЕДЕНИЕ":"НА ПАУЗЕ"):"ОЖИДАНИЕ";
    const select=el("musicSource"), sources=[{id:"auto",name:"Авто · текущий источник Windows"},...(n.sources||[]).map(s=>({id:s.id,name:label(s.name)}))];
    if(n.selected!=="auto"&&!sources.some(s=>s.id===n.selected))sources.push({id:n.selected,name:"Выбранный источник закрыт"});
    const signature=JSON.stringify(sources);if(select.dataset.signature!==signature){select.replaceChildren(...sources.map(s=>{const o=document.createElement("option");o.value=s.id;o.textContent=s.name;return o;}));select.dataset.signature=signature;}
    select.value=n.selected||"auto";
    el("musicSeek").max=n.duration||1;el("musicSeek").disabled=pending||!n.caps.seek||!has;el("musicSeek").title=n.caps.seek?"Перемотать":"Источник не предоставляет перемотку";
    el("musicVolume").disabled=pending||!n.caps.volume;el("musicVolume").title=n.caps.volume?"Громкость встроенного плеера":"У этого источника нет управления громкостью плеера";
    if(document.activeElement!==el("musicVolume"))el("musicVolume").value=Math.round((n.volume||0)*100);el("musicVolumeValue").textContent=n.caps.volume?Math.round((n.volume||0)*100)+"%":"—";
    capabilities();progress();
    if(Date.now()>errorUntil)hint(n.error||(n.selected==="local"?"Встроенный плеер игры. Лаунчер управляет воспроизведением в Minecraft.":"Внешний плеер. Обложка, время и кнопки доступны в пределах возможностей источника."));
    const key=n.app+"|"+n.cover_id+"|"+n.title;if(key!==coverKey){for(const id of ["miniCover","fullCover"])el(id).hidden=true;
      try{const bytes=has?await call("media_cover"):[];if(current===n){coverKey=key;if(coverUrl)URL.revokeObjectURL(coverUrl);coverUrl=bytes.length?URL.createObjectURL(new Blob([new Uint8Array(bytes)])):"";
        for(const id of ["miniCover","fullCover"]){el(id).src=coverUrl;el(id).hidden=!coverUrl;}}}catch{coverKey="";hint("Обложка недоступна, повторю загрузку",true);}}
  }catch(e){resetOffline();hint("Медиасервис недоступен: "+String(e),true);}finally{polling=false;}}
  async function command(command,value){if(pending)return;pending=true;capabilities();try{await call("media_command",{command,value:value==null?null:Math.round(value)});}catch(e){hint(String(e),true);}finally{pending=false;capabilities();await update();}}
  document.querySelectorAll("[data-media]").forEach(b=>b.onclick=()=>command(b.dataset.media));
  el("musicSeek").oninput=()=>el("musicPosition").textContent=time(+el("musicSeek").value);el("musicSeek").onchange=()=>command("seek",+el("musicSeek").value);
  el("musicVolume").oninput=()=>el("musicVolumeValue").textContent=el("musicVolume").value+"%";el("musicVolume").onchange=()=>command("volume",+el("musicVolume").value);
  el("musicSource").onchange=async()=>{try{await call("media_select",{id:el("musicSource").value});await new Promise(r=>setTimeout(r,400));await update();}catch(e){hint(String(e),true);}};
  function open(show){el("musicPanel").hidden=el("musicShade").hidden=!show;el("musicExpand").setAttribute("aria-expanded",String(show));(show?el("musicClose"):el("musicExpand")).focus();}
  el("musicExpand").onclick=()=>open(true);el("musicClose").onclick=el("musicShade").onclick=()=>open(false);
  el("musicPanel").onkeydown=e=>{if(e.key==="Escape"){open(false);e.stopPropagation();}if(e.key==="Tab"){const items=[...el("musicPanel").querySelectorAll("button:not(:disabled),select,input:not(:disabled)")].filter(x=>x.getClientRects().length);const first=items[0],last=items.at(-1);if(e.shiftKey&&document.activeElement===first){e.preventDefault();last.focus();}else if(!e.shiftKey&&document.activeElement===last){e.preventDefault();first.focus();}}};
  let bookmarks=[];try{bookmarks=JSON.parse(localStorage.getItem("mraz.music.links")||"[]").filter(x=>typeof x.name==="string"&&/^https?:\/\//i.test(x.url)).slice(0,20);}catch{}
  function renderLinks(){el("musicLinks").replaceChildren(...bookmarks.map((item,index)=>{const row=document.createElement("div");row.className="musicBookmark";const link=document.createElement("button");link.textContent=item.name;link.title="Открыть в браузере: "+item.url;link.onclick=()=>call("open_url",{url:item.url}).catch(e=>hint(String(e),true));const remove=document.createElement("button");remove.textContent="×";remove.setAttribute("aria-label","Удалить ссылку "+item.name);remove.onclick=()=>{bookmarks.splice(index,1);saveLinks();};row.append(link,remove);return row;}));}
  function saveLinks(){localStorage.setItem("mraz.music.links",JSON.stringify(bookmarks));renderLinks();}
  el("musicAddLink").onclick=()=>{el("musicLinkForm").hidden=!el("musicLinkForm").hidden;if(!el("musicLinkForm").hidden)el("musicLinkName").focus();};
  el("musicLinkForm").onsubmit=e=>{e.preventDefault();try{const url=new URL(el("musicLinkUrl").value);if(!["https:","http:"].includes(url.protocol))throw Error("Нужна http(s)-ссылка");if(bookmarks.length>=20)throw Error("Можно сохранить до 20 ссылок");bookmarks.push({name:el("musicLinkName").value.trim()||url.hostname,url:url.href});saveLinks();el("musicLinkForm").reset();el("musicLinkForm").hidden=true;}catch(e){hint(String(e),true);}};
  for(const id of ["miniCover","fullCover"])el(id).onerror=()=>el(id).hidden=true;
  renderLinks();update();setInterval(update,900);setInterval(progress,250);
})();

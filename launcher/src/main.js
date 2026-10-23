// Лаунчер Mraz1Visual: интерфейс. Вся работа — в Rust (invoke), сюда приходят события.
const { invoke } = window.__TAURI__.core;
const { listen } = window.__TAURI__.event;
const win = window.__TAURI__.window.getCurrentWindow();

const $ = (id) => document.getElementById(id);

let info = null;
let busy = false;

// ── фон: видео на весь экран, диск с процентами — над деревом ──────
function layout() {
  const w = innerWidth, h = innerHeight;
  const dx = w * 0.6, dy = h * 0.36, dr = Math.min(w, h) * 0.17;
  const disc = $("disc");
  Object.assign(disc.style, { left: dx - dr + "px", top: dy - dr + "px", width: dr * 2 + "px", height: dr * 2 + "px" });
  const st = $("status");
  st.style.left = dx - 210 + "px";
  st.style.top = Math.min(h - 70, dy + dr + 42) + "px";
}
function startVideo() {
  const v = $("bg");
  const show = () => v.classList.add("ready");
  if (v.readyState >= 3) show(); else v.addEventListener("canplay", show, { once: true });
  v.play().catch(() => {});
}

// ── заставка ─────────────────────────────────────────────────
const GLYPHS = "%§Σ#&@ΔΞ∆¥ЖЯ";
function scramble(el, text, ms) {
  return new Promise((done) => {
    const start = performance.now();
    el.classList.add("glitch");
    const tick = (t) => {
      const p = Math.min(1, (t - start) / ms);
      const fixed = Math.floor(p * text.length);
      let out = "";
      for (let i = 0; i < text.length; i++) out += i < fixed ? text[i] : GLYPHS[(Math.random() * GLYPHS.length) | 0];
      el.textContent = out;
      el.dataset.text = out;
      if (p < 1) requestAnimationFrame(tick);
      else {
        setTimeout(() => el.classList.remove("glitch"), 350);
        done();
      }
    };
    requestAnimationFrame(tick);
  });
}
async function splash(show) {
  const sp = $("splash");
  if (!show) {
    sp.classList.remove("on");
    $("presents").classList.remove("on");
    return;
  }
  sp.classList.add("on");
  await new Promise((r) => setTimeout(r, 500));
  await scramble($("logo"), "MRAZ1VISUAL", 900);
  $("presents").classList.add("on");
}

// ── статус и прогресс ────────────────────────────────────────
function status(main, sub = "", error = false) {
  $("statusMain").textContent = main;
  $("statusSub").textContent = sub;
  $("status").classList.toggle("error", error);
}
function percent(p) {
  const el = $("percent");
  if (p == null) { el.classList.remove("on"); return; }
  el.innerHTML = Math.floor(p) + "<sup>%</sup>";
  el.classList.add("on");
}
function idleStatus() {
  if (!info) return;
  if (!info.has_mraz1visual && !info.online) status("Mraz1Visual ещё не установлен", "Положи mraz1visual.jar в папку игры → game/mods");
  else status(`Версия ${info.version} · Minecraft ${info.selected || info.minecraft}`, "Нажми «Играть» — потом игра запустится сама");
}

// ── данные ───────────────────────────────────────────────────
function gb(mb) { return (mb / 1024).toFixed(1).replace(".", ","); }
function renderInfo() {
  $("build").textContent = `${info.version} · сборка ${info.build}`;
  $("newsVer").textContent = info.version;
  $("account").textContent = info.account;
  renderChangelog();
  const links = $("links");
  links.innerHTML = "";
  if (info.launcher_update) {
    const b = document.createElement("button");
    b.textContent = "Обновить лаунчер";
    b.className = "update";
    b.onclick = () => invoke("open_url", { url: info.launcher_update });
    links.appendChild(b);
  }
  info.links.forEach((l) => {
    const b = document.createElement("button");
    b.textContent = l.title;
    b.onclick = () => invoke("open_url", { url: l.url });
    links.appendChild(b);
  });
  const ram = $("ram");
  ram.max = Math.max(2048, Math.floor((info.total_ram_mb - 1024) / 512) * 512);
  ram.value = info.ram_mb;
  $("ramValue").textContent = Math.round(info.ram_mb / 1024) + " ГБ";
  $("ramHint").textContent = `Свободно ${gb(info.free_ram_mb)} из ${gb(info.total_ram_mb)} ГБ · советуем 4–6`;
  $("folder").textContent = info.game_dir;
  renderVersions();
  document.querySelectorAll("#sodiumSeg button").forEach((b) => b.classList.toggle("on", (b.dataset.v === "1") === info.sodium));
  document.querySelectorAll("#irisSeg button").forEach((b) => b.classList.toggle("on", (b.dataset.v === "1") === (info.iris && info.sodium)));
  document.querySelectorAll("#closeSeg button").forEach((b) => b.classList.toggle("on", (b.dataset.v === "1") === info.close_on_launch));
  idleStatus();
}
// ── версии Minecraft: бейджи «Рекомендуется / Новая / Бета» ──
const BADGE = { recommended: "Рекомендуется", new: "Новая", beta: "Бета" };
function renderVersions() {
  const box = $("versions");
  box.innerHTML = "";
  (info.versions || []).forEach(([v, st]) => {
    const b = document.createElement("button");
    b.className = "ver" + (v === info.selected ? " on" : "");
    b.innerHTML = `<span class="vn"></span><span class="vb ${st}"></span>`;
    b.querySelector(".vn").textContent = v;
    b.querySelector(".vb").textContent = BADGE[st] || st;
    b.onclick = async (e) => {
      e.stopPropagation();
      info.selected = v;
      await saveProfile();
      renderInfo();
    };
    box.appendChild(b);
  });
}
async function saveProfile() {
  await invoke("set_profile", { mcVersion: info.selected, sodium: info.sodium, iris: info.iris });
}
// ── что нового: новые обновления сверху ──────────────────────
function verKey(v) {
  return String(v || "").split(/[^0-9]+/).filter(Boolean).map(Number);
}
function cmpVer(a, b) {
  const x = verKey(a), y = verKey(b);
  for (let i = 0; i < Math.max(x.length, y.length); i++) {
    const d = (x[i] || 0) - (y[i] || 0);
    if (d) return d;
  }
  return 0;
}
function fmtDate(d) {
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(d || "");
  return m ? `${m[3]}.${m[2]}.${m[1]}` : (d || "");
}
function renderChangelog() {
  const ul = $("changelog");
  ul.innerHTML = "";
  let releases = (info.releases || []).filter((r) => r && r.items && r.items.length);
  if (!releases.length) {
    // старый формат: просто список строк, новые дописывают в конец — показываем их сверху
    releases = [{ version: info.version, date: "", items: (info.changelog || []).slice().reverse() }];
  }
  // сортировка: по дате, потом по версии — по убыванию (новые сверху)
  releases.sort((a, b) => (b.date || "").localeCompare(a.date || "") || cmpVer(b.version, a.version));
  releases.forEach((r, i) => {
    const li = document.createElement("li");
    li.className = "release" + (i === 0 ? " latest open" : "");
    const head = document.createElement("button");
    head.className = "release-head";
    head.innerHTML = `<span class="rv"></span><span class="rd"></span>${i === 0 ? '<span class="badge">новое</span>' : ""}<span class="chev">›</span>`;
    head.querySelector(".rv").textContent = r.version ? "v" + r.version : "Обновление";
    head.querySelector(".rd").textContent = fmtDate(r.date);
    head.onclick = () => li.classList.toggle("open");
    const items = document.createElement("ul");
    items.className = "release-items";
    r.items.forEach((t) => {
      const it = document.createElement("li");
      it.textContent = t;
      items.appendChild(it);
    });
    li.appendChild(head);
    li.appendChild(items);
    ul.appendChild(li);
  });
}

async function saveSettings() {
  await invoke("set_settings", { ramMb: Number($("ram").value), closeOnLaunch: info.close_on_launch });
}

// ── действия ─────────────────────────────────────────────────
async function play() {
  if (busy) return;
  busy = true;
  $("play").disabled = true;
  percent(0);
  status("Проверяю файлы", "Первый запуск скачивает около 600 МБ");
  try {
    await invoke("play");
    percent(null);
    status("Запускаю игру…");
    splash(true);
  } catch (e) {
    percent(null);
    status("Не удалось запустить", String(e), true);
    busy = false;
    $("play").disabled = false;
  }
}

function show(view) {
  document.querySelectorAll(".view").forEach((v) => v.classList.toggle("active", v.id === view));
}

// ── маркет ───────────────────────────────────────────────────
const market = { kind: "mod", src: "modrinth", sort: "downloads", query: "", offset: 0, timer: 0 };
function fmtDl(n) {
  if (n >= 1e6) return (n / 1e6).toFixed(1).replace(".", ",") + " млн";
  if (n >= 1e3) return Math.round(n / 1e3) + " тыс";
  return String(n);
}
function el(tag, cls, text) {
  const e = document.createElement(tag);
  if (cls) e.className = cls;
  if (text != null) e.textContent = text;
  return e;
}
async function marketLoad(append = false) {
  const body = $("mBody");
  if (!append) { market.offset = 0; body.innerHTML = ""; body.appendChild(el("div", "m-empty", "Загрузка…")); }
  $("mSort").style.visibility = market.src === "local" ? "hidden" : "visible";
  $("mQuery").style.visibility = market.src === "local" ? "hidden" : "visible";
  try {
    if (market.src === "local") {
      const items = await invoke("market_local", { kind: market.kind });
      body.innerHTML = "";
      if (!items.length) body.appendChild(el("div", "m-empty", "Здесь пока пусто. Установи что-нибудь из Modrinth или положи файлы в папку игры."));
      items.forEach((it) => {
        const row = el("div", "local");
        const name = el("span", null, it.file);
        if (it.project) name.appendChild(el("small", null, "Modrinth"));
        const del = el("button", "btn ghost", "Удалить");
        del.onclick = async () => { await invoke("market_delete_local", { kind: market.kind, file: it.file }); marketLoad(); };
        row.append(name, del);
        body.appendChild(row);
      });
      return;
    }
    const hits = await invoke("market_search", { kind: market.kind, query: market.query, sort: market.query ? "relevance" : market.sort, offset: market.offset });
    if (!append) body.innerHTML = "";
    body.querySelector(".m-more")?.remove();
    if (!hits.length && !append) body.appendChild(el("div", "m-empty", "Ничего не нашлось для 1.21.11."));
    hits.forEach((h) => body.appendChild(card(h)));
    if (hits.length === 24) {
      const more = el("button", "btn ghost m-more", "Показать ещё");
      more.onclick = () => { market.offset += 24; marketLoad(true); };
      body.appendChild(more);
    }
  } catch (e) {
    body.innerHTML = "";
    body.appendChild(el("div", "m-empty", "Нет связи с Modrinth: " + e));
  }
}
function card(h) {
  const c = el("div", "card");
  let icon;
  if (h.icon) { icon = document.createElement("img"); icon.src = h.icon; icon.loading = "lazy"; }
  else icon = el("div", "noicon");
  const head = el("div");
  head.append(el("div", "t", h.title), el("div", "a", h.author));
  const row = el("div", "row2");
  const btn = el("button", h.installed ? "btn ghost" : "btn", h.installed ? "Удалить" : "Установить");
  btn.onclick = async () => {
    btn.disabled = true;
    try {
      if (h.installed) { await invoke("market_uninstall", { kind: market.kind, project: h.id }); h.installed = false; }
      else { btn.textContent = "Ставлю…"; await invoke("market_install", { kind: market.kind, project: h.id }); h.installed = true; }
      btn.className = h.installed ? "btn ghost" : "btn";
      btn.textContent = h.installed ? "Удалить" : "Установить";
    } catch (e) {
      btn.textContent = "Ошибка";
      btn.title = String(e);
    }
    btn.disabled = false;
  };
  row.append(el("span", "dl", "↓ " + fmtDl(h.downloads)), btn);
  c.append(icon, head, el("div", "d", h.description), row);
  return c;
}
function seg(id, attr, key) {
  document.querySelectorAll(`#${id} button`).forEach((b) => (b.onclick = () => {
    document.querySelectorAll(`#${id} button`).forEach((x) => x.classList.toggle("on", x === b));
    market[key] = b.dataset[attr];
    marketLoad();
  }));
}
seg("mKinds", "k", "kind");
seg("mSrc", "s", "src");
seg("mSort", "s", "sort");
$("mQuery").oninput = () => {
  clearTimeout(market.timer);
  market.timer = setTimeout(() => { market.query = $("mQuery").value.trim(); marketLoad(); }, 350);
};
$("toMarket").onclick = () => { $("market").classList.add("open"); marketLoad(); };
$("mClose").onclick = () => $("market").classList.remove("open");

// ── старт ────────────────────────────────────────────────────
window.addEventListener("resize", layout);
document.addEventListener("contextmenu", (e) => e.preventDefault());
$("min").onclick = () => win.minimize();
$("close").onclick = () => win.close();
$("play").onclick = play;
$("toSettings").onclick = () => show("settings");
$("back").onclick = () => show("home");
$("openFolder").onclick = () => invoke("open_folder");
$("ram").oninput = () => { $("ramValue").textContent = Math.round($("ram").value / 1024) + " ГБ"; };
$("ram").onchange = saveSettings;
document.querySelectorAll("#sodiumSeg button").forEach((b) => (b.onclick = () => {
  info.sodium = b.dataset.v === "1";
  if (!info.sodium) info.iris = false;
  renderInfo();
  saveProfile();
}));
document.querySelectorAll("#irisSeg button").forEach((b) => (b.onclick = () => {
  info.iris = b.dataset.v === "1";
  if (info.iris) info.sodium = true;
  renderInfo();
  saveProfile();
}));
document.querySelectorAll("#closeSeg button").forEach((b) => (b.onclick = () => {
  info.close_on_launch = b.dataset.v === "1";
  renderInfo();
  saveSettings();
}));

listen("progress", (e) => {
  const { percent: p, label } = e.payload;
  percent(p);
  status(label, p >= 100 ? "Запуск" : "Скачиваю и проверяю");
});
listen("game-started", () => {
  // окно скрывает Rust; подготовим вид к возвращению
  splash(false);
  status("Игра запущена");
});
listen("game-exited", (e) => {
  busy = false;
  $("play").disabled = false;
  splash(false);
  if (e.payload !== 0) status("Игра закрылась с ошибкой", `Код ${e.payload}. Лог: папка клиента → logs`, true);
  else idleStatus();
});

startVideo();
layout();
(async () => {
  splash(true);
  try {
    info = await invoke("info");
    renderInfo();
  } catch (e) {
    status("Ошибка", String(e), true);
  }
  setTimeout(() => splash(false), 2200);
})();

// ── заставка «нажмите, чтобы продолжить» → стекло с меню ─────
function enter() {
  const app = $("app");
  if (!app.classList.contains("intro")) return;
  app.classList.add("entering");
  app.classList.remove("intro");
  setTimeout(() => app.classList.remove("entering"), 1800);
}
$("app").addEventListener("click", (e) => {
  if (e.target.closest(".win")) return;
  enter();
});
addEventListener("keydown", (e) => { if (e.key === "Enter" || e.key === " " || e.key === "Escape") enter(); });

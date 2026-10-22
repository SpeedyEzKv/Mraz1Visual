// Список версий для manifest.json: Minecraft → файл → sha256 → Fabric Loader → Fabric API → зависимости.
// node tools/release/versions-manifest.js <папка с jar> <базовый URL релиза>  → JSON в stdout
// Версии берутся из mod/settings.gradle (что реально собирается), зависимости — из mod/versions/<версия>/gradle.properties.
const fs = require("fs"), path = require("path"), crypto = require("crypto");
const [dir, baseUrl] = process.argv.slice(2);
const root = path.join(__dirname, "..", "..");
const settings = fs.readFileSync(path.join(root, "mod", "settings.gradle"), "utf8");
const built = (settings.match(/^\s*versions\s+(.+)$/m) || [, ""])[1].match(/'([^']+)'/g).map(s => s.slice(1, -1));

// Статус для лаунчера: бейдж «Рекомендуется / Новая / Бета» (VERSIONS.md)
const STATUS = { "1.21.11": "recommended", "26.1.2": "beta", "26.2": "beta", "26.3": "new" };
// Какие версии игры покрывает jar (диапазон из fabric.mod.json)
const COVERS = { "1.21.11": ["1.21.11"], "26.1.2": ["26.1", "26.1.1", "26.1.2"], "26.2": ["26.2"], "26.3": ["26.3"] };
// id проектов на Modrinth для автоустановки
const MODRINTH = { fabric_api: "P7dR8mSH", sodium: "AANobbMI", iris: "YL57xq9U", mcef: "mcef-modern" };

const props = f => Object.fromEntries(fs.readFileSync(f, "utf8").split(/\r?\n/)
  .filter(l => l && !l.startsWith("#") && l.includes("=")).map(l => [l.slice(0, l.indexOf("=")).trim(), l.slice(l.indexOf("=") + 1).trim()]));

const out = [];
for (const mc of built) {
  const p = props(path.join(root, "mod", "versions", mc, "gradle.properties"));
  const file = fs.readdirSync(dir).find(f => f.startsWith("mraz1visual-") && f.endsWith(`+mc${mc}.jar`));
  if (!file) { console.error(`нет jar для ${mc}`); process.exit(1); }
  const buf = fs.readFileSync(path.join(dir, file));
  out.push({
    minecraft: mc,
    covers: COVERS[mc] || [mc],
    status: STATUS[mc] || "beta",
    file,
    url: `${baseUrl}/${file}`,
    size: buf.length,
    sha256: crypto.createHash("sha256").update(buf).digest("hex"),
    java: Number(p["deps.java"]),
    loader: "fabric",
    fabric_loader: p["deps.fabric_loader"],
    fabric_api: p["deps.fabric_api"],
    optional: {
      sodium: { project: MODRINTH.sodium, version: p["deps.sodium"] },
      iris: { project: MODRINTH.iris, version: p["deps.iris"] },
      mcef: { project: MODRINTH.mcef, version: p["deps.mcef"] },
    },
    fabric_api_project: MODRINTH.fabric_api,
  });
}
process.stdout.write(JSON.stringify(out, null, 2));

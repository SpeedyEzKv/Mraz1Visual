// Генератор встроенного набора «Mraz1Visual»: свои модели оружия (item JSON) и текстуры 64×64, нарисованные кодом.
// Запуск: node tools/cosmetics/gen-mraz1.js  → mod/src/main/resources/assets/mraz1visual/cosmetics/mraz1/
// Всё своё: геометрия по пропорциям настоящих клинков, текстуры — шум, градиенты, узоры; сторонних ассетов нет.
const fs = require("fs"), path = require("path"), zlib = require("zlib");
const OUT = path.join(__dirname, "../../mod/src/main/resources/assets/mraz1visual/cosmetics/mraz1");

// ── PNG ──
const CRC = []; for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xEDB88320 ^ (c >>> 1) : c >>> 1; CRC[n] = c >>> 0; }
const crc = b => { let c = 0xFFFFFFFF; for (const x of b) c = CRC[(c ^ x) & 255] ^ (c >>> 8); return (c ^ 0xFFFFFFFF) >>> 0; };
function png(W, H, px) {
  const raw = Buffer.alloc((W * 4 + 1) * H);
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) { const o = y * (W * 4 + 1) + 1 + x * 4, c = px[y * W + x]; raw[o] = c[0]; raw[o + 1] = c[1]; raw[o + 2] = c[2]; raw[o + 3] = c[3]; }
  const chunk = (t, d) => { const l = Buffer.alloc(4); l.writeUInt32BE(d.length); const td = Buffer.concat([Buffer.from(t), d]); const c = Buffer.alloc(4); c.writeUInt32BE(crc(td)); return Buffer.concat([l, td, c]); };
  const ih = Buffer.alloc(13); ih.writeUInt32BE(W, 0); ih.writeUInt32BE(H, 4); ih[8] = 8; ih[9] = 6;
  return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk("IHDR", ih), chunk("IDAT", zlib.deflateSync(raw, { level: 9 })), chunk("IEND", Buffer.alloc(0))]);
}

// ── шум ──
function hash(x, y, s) { let h = (x * 374761393 + y * 668265263 + s * 1442695041) | 0; h = Math.imul(h ^ (h >>> 13), 1274126177); h ^= h >>> 16; return (h >>> 0) / 4294967296; }
function noise(x, y, s) { const xi = Math.floor(x), yi = Math.floor(y), fx = x - xi, fy = y - yi; const e = t => t * t * (3 - 2 * t);
  const a = hash(xi, yi, s), b = hash(xi + 1, yi, s), c = hash(xi, yi + 1, s), d = hash(xi + 1, yi + 1, s);
  return (a + (b - a) * e(fx)) + ((c + (d - c) * e(fx)) - (a + (b - a) * e(fx))) * e(fy); }
function fbm(x, y, s, o = 4) { let v = 0, a = 0.5, n = 0; for (let i = 0; i < o; i++) { v += noise(x, y, s + i * 17) * a; n += a; x *= 2; y *= 2; a *= 0.5; } return v / n; }
const mix = (a, b, t) => a.map((v, i) => Math.round(v + (b[i] - v) * Math.max(0, Math.min(1, t))));
const clamp = v => Math.max(0, Math.min(255, Math.round(v)));
const shade = (c, k) => [clamp(c[0] * k), clamp(c[1] * k), clamp(c[2] * k), c[3]];

// ── текстура 64×64: области деталей ──
// клинок — столбец x 0..15, y 0..63 (по высоте — от гарды к острию); гарда — x 16..31, y 0..15; рукоять — x 16..31, y 16..47; навершие — x 32..47, y 0..15;
// голова топора/булавы — x 32..63, y 16..63
const W = 64;
const R = { blade: [0, 0, 16, 64], guard: [16, 0, 16, 16], grip: [16, 16, 16, 32], pommel: [32, 0, 16, 16], head: [32, 16, 32, 48] };

function paint(fn, glowFn) {
  const px = new Array(W * W).fill([0, 0, 0, 0]), gl = new Array(W * W).fill([0, 0, 0, 0]);
  for (const [part, r] of Object.entries(R)) {
    for (let y = r[1]; y < r[1] + r[3]; y++) for (let x = r[0]; x < r[0] + r[2]; x++) {
      const u = (x - r[0] + 0.5) / r[2], v = (y - r[1] + 0.5) / r[3];
      px[y * W + x] = fn(part, u, v, x, y);
      if (glowFn) { const g = glowFn(part, u, v, x, y); if (g) gl[y * W + x] = g; }
    }
  }
  return { px, gl: glowFn ? gl : null };
}

// общие материалы
const steelBlade = (u, v, base, edge) => {
  // поперёк клинка: светлая кромка по краям, тёмный дол в середине, мягкий вертикальный градиент («полировка»)
  const across = Math.abs(u - 0.5) * 2;
  let c = mix(base, edge, Math.pow(across, 3));
  c = shade(c, 0.88 + 0.18 * (1 - v) + 0.06 * Math.sin(v * 30));
  if (across < 0.18) c = shade(c, 0.82);
  return [...c.slice(0, 3), 255];
};
const leatherGrip = (u, v, a, b) => {
  const d = ((Math.floor(u * 6) + Math.floor(v * 16)) % 2);
  const c = mix(a, b, d * 0.6 + 0.2 * noise(u * 8, v * 30, 3));
  return [...shade(c, 0.85 + 0.25 * Math.sin(u * Math.PI)).slice(0, 3), 255];
};
const metal = (u, v, c, k = 1) => [...shade(c, k * (0.8 + 0.35 * (1 - v) + 0.1 * noise(u * 10, v * 10, 5))).slice(0, 3), 255];

const STYLES = {
  katana: {
    name: "Катана с хамоном", bind: "sword",
    paint: (p, u, v) => {
      if (p === "blade") {
        let c = steelBlade(u, v, [188, 194, 205], [236, 240, 246]);
        // хамон: волнистая молочная линия закалки вдоль режущей кромки
        const h = 0.68 + 0.08 * Math.sin(v * 38) + 0.04 * Math.sin(v * 91);
        if (u > h) c = mix(c, [250, 251, 252, 255], 0.6 + 0.3 * fbm(u * 6, v * 40, 2));
        return c;
      }
      if (p === "guard") return metal(u, v, [52, 46, 42]);
      if (p === "grip") return leatherGrip(u, v, [24, 22, 26], [210, 205, 196]);
      return metal(u, v, [70, 60, 48]);
    },
  },
  damascus: {
    name: "Дамасская сталь", bind: "sword",
    paint: (p, u, v) => {
      if (p === "blade") {
        const w = Math.sin((u * 3 + fbm(u * 3, v * 6, 9) * 4 + v * 2) * 9);
        let c = mix([108, 112, 120, 255], [214, 218, 226, 255], 0.5 + 0.5 * w);
        return steelBlade(u, v, c, [236, 238, 242]);
      }
      if (p === "guard") return metal(u, v, [150, 120, 70]);
      if (p === "grip") return leatherGrip(u, v, [74, 44, 28], [52, 30, 18]);
      return metal(u, v, [150, 120, 70]);
    },
  },
  obsidian: {
    name: "Обсидиановый клинок", bind: "sword",
    paint: (p, u, v) => {
      if (p === "blade") {
        const vein = Math.abs(fbm(u * 4, v * 9, 11) - 0.5);
        let c = steelBlade(u, v, [22, 18, 30], [70, 62, 92]);
        if (vein < 0.022) c = mix(c, [255, 120, 40, 255], 1 - vein / 0.022);
        return c;
      }
      if (p === "guard") return metal(u, v, [30, 26, 36]);
      if (p === "grip") return leatherGrip(u, v, [20, 18, 22], [60, 30, 22]);
      return metal(u, v, [40, 34, 46]);
    },
    glow: (p, u, v) => {
      if (p !== "blade") return null;
      const vein = Math.abs(fbm(u * 4, v * 9, 11) - 0.5);
      return vein < 0.022 ? [255, 140, 50, Math.round(255 * (1 - vein / 0.022))] : null;
    },
  },
  crystal: {
    name: "Кристальный клинок", bind: "sword",
    paint: (p, u, v) => {
      if (p === "blade") {
        const facet = Math.floor(u * 4 + v * 6 + fbm(u * 2, v * 2, 4) * 2) % 3;
        const c = mix([196, 176, 255, 170], [246, 240, 255, 200], facet / 2);
        return [...shade(c, 0.9 + 0.2 * (1 - v)).slice(0, 3), 150 + facet * 30];
      }
      if (p === "guard") return metal(u, v, [210, 214, 222]);
      if (p === "grip") return leatherGrip(u, v, [46, 40, 70], [90, 80, 140]);
      return [200, 180, 255, 200];
    },
    glow: (p, u, v) => p === "blade" && Math.abs(u - 0.5) > 0.42 ? [220, 200, 255, 120] : null,
  },
  ice: {
    name: "Ледяной клинок", bind: "sword",
    paint: (p, u, v) => {
      if (p === "blade") {
        const crack = Math.abs(fbm(u * 5, v * 7, 21) - 0.5) < 0.02;
        const depth = fbm(u * 2, v * 3, 22);
        let c = mix([150, 205, 240, 150], [225, 245, 255, 190], depth);
        if (crack) c = [255, 255, 255, 230];
        return c;
      }
      if (p === "guard") return metal(u, v, [176, 196, 214]);
      if (p === "grip") return leatherGrip(u, v, [40, 52, 70], [120, 150, 180]);
      return [190, 230, 255, 210];
    },
  },
  liquid_glass: {
    name: "Liquid Glass", bind: "sword",
    paint: (p, u, v) => {
      if (p === "blade") {
        const edge = Math.pow(Math.abs(u - 0.5) * 2, 4);
        const c = mix([150, 210, 255, 90], [250, 252, 255, 220], edge + 0.25 * (1 - v));
        return c;
      }
      if (p === "guard") return [235, 240, 248, 210];
      if (p === "grip") return [60, 90, 120, 200];
      return [220, 236, 255, 200];
    },
    glow: (p, u, v) => p === "blade" && Math.abs(u - 0.5) > 0.4 ? [190, 225, 255, 140] : null,
  },
  ceremonial: {
    name: "Золотой церемониальный", bind: "sword",
    paint: (p, u, v, x, y) => {
      if (p === "blade") {
        let c = steelBlade(u, v, [226, 222, 214], [250, 248, 244]);
        // гравировка: тонкий вьющийся узор по долу
        if (Math.abs(u - 0.5) < 0.16 && Math.abs(Math.sin(v * 44 + u * 9)) > 0.86) c = mix(c, [170, 128, 50, 255], 0.8);
        return c;
      }
      if (p === "guard" || p === "pommel") {
        const engr = Math.abs(Math.sin((u + v) * 20)) > 0.9;
        return metal(u, v, engr ? [150, 104, 34] : [232, 186, 88]);
      }
      return leatherGrip(u, v, [120, 28, 40], [220, 170, 80]);
    },
  },
  knight: {
    name: "Двуручник тёмного рыцаря", bind: "sword", great: true,
    paint: (p, u, v) => {
      if (p === "blade") return steelBlade(u, v, [62, 66, 76], [150, 156, 168]);
      if (p === "guard") return metal(u, v, [44, 44, 50]);
      if (p === "grip") return leatherGrip(u, v, [26, 22, 22], [48, 40, 38]);
      // навершие с камнем
      const d = Math.hypot(u - 0.5, v - 0.5);
      return d < 0.28 ? [190, 30, 50, 255] : metal(u, v, [44, 44, 50]);
    },
    glow: (p, u, v) => p === "pommel" && Math.hypot(u - 0.5, v - 0.5) < 0.28 ? [255, 50, 70, 220] : null,
  },
  battle_axe: {
    name: "Боевой топор", bind: "axe", axe: true,
    paint: (p, u, v) => {
      if (p === "head") return steelBlade(v, u, [140, 146, 156], [228, 232, 238]);
      if (p === "grip" || p === "blade") return [...shade([96, 64, 40], 0.8 + 0.3 * fbm(u * 3, v * 30, 7)).slice(0, 3), 255];
      return metal(u, v, [70, 70, 76]);
    },
  },
  obsidian_axe: {
    name: "Обсидиановый топор", bind: "axe", axe: true,
    paint: (p, u, v) => {
      if (p === "head") {
        const vein = Math.abs(fbm(u * 5, v * 5, 31) - 0.5);
        let c = steelBlade(v, u, [24, 20, 32], [74, 64, 96]);
        if (vein < 0.03) c = mix(c, [255, 120, 40, 255], 1 - vein / 0.03);
        return c;
      }
      if (p === "grip" || p === "blade") return [...shade([40, 34, 40], 0.8 + 0.3 * fbm(u * 3, v * 30, 8)).slice(0, 3), 255];
      return metal(u, v, [36, 30, 42]);
    },
    glow: (p, u, v) => {
      if (p !== "head") return null;
      const vein = Math.abs(fbm(u * 5, v * 5, 31) - 0.5);
      return vein < 0.03 ? [255, 140, 50, Math.round(255 * (1 - vein / 0.03))] : null;
    },
  },
  flanged_mace: {
    name: "Булава с перьями", bind: "mace", mace: true,
    paint: (p, u, v) => {
      if (p === "head") return metal(u, v, [176, 180, 190], 1.05);
      if (p === "grip" || p === "blade") return leatherGrip(u, v, [70, 44, 30], [40, 26, 18]);
      return metal(u, v, [176, 180, 190]);
    },
  },
};

// ── геометрия (пиксели 0..16, клинок вертикально, затем −45° по Z — как спрайт ванильного меча) ──
const uvOf = (r) => [r[0] / 4, r[1] / 4, (r[0] + r[2]) / 4, (r[1] + r[3]) / 4]; // 64 px → 16 единиц UV
function box(from, to, region, v0 = 0, v1 = 1, skip = []) {
  const r = R[region];
  const uv = [r[0] / 4, (r[1] + r[3] * (1 - v1)) / 4, (r[0] + r[2]) / 4, (r[1] + r[3] * (1 - v0)) / 4];
  const f = {};
  for (const d of ["north", "south", "east", "west", "up", "down"]) if (!skip.includes(d)) f[d] = { uv, texture: "#0" };
  return { from, to, faces: f, rotation: { angle: -45, axis: "z", origin: [8, 8, 8] } };
}
function sword(st) {
  const els = [];
  const great = !!st.great, len = great ? 13.2 : 11, w = great ? 1.35 : 0.95, t = 0.26;
  const y0 = great ? 4.6 : 4.2;
  // клинок сегментами (катана — с изгибом), к острию сужается
  // прямой клинок — один сегмент; у катаны — 4 со смещением (изгиб); внутренние грани убраны (без «ступенек» у стекла)
  const seg = st === STYLES.katana ? 4 : 1;
  for (let i = 0; i < seg; i++) {
    const a = i / seg, b = (i + 1) / seg;
    const curve = st === STYLES.katana ? 0.7 * a * a : 0;
    const skip = [...(i > 0 ? ["down"] : []), ...(i < seg - 1 ? ["up"] : [])];
    els.push(box([8 - w + curve, y0 + len * a, 8 - t], [8 + w + curve, y0 + len * b, 8 + t], "blade", a, b, skip));
  }
  // острие: три ступеньки
  const curveTip = st === STYLES.katana ? 0.7 : 0, tipY = y0 + len;
  els.push(box([8 - w * 0.6 + curveTip, tipY, 8 - t * 0.85], [8 + w * 0.6 + curveTip, tipY + 0.9, 8 + t * 0.85], "blade", 0.95, 1, ["down"]));
  els.push(box([8 - w * 0.25 + curveTip, tipY + 0.9, 8 - t * 0.6], [8 + w * 0.25 + curveTip, tipY + 1.5, 8 + t * 0.6], "blade", 0.97, 1, ["down"]));
  // гарда, рукоять, навершие
  const gw = great ? 3.6 : st === STYLES.katana ? 1.6 : 2.8;
  if (st === STYLES.katana) els.push(box([8 - gw, y0 - 0.5, 8 - gw], [8 + gw, y0, 8 + gw], "guard")); // цуба — диск
  else els.push(box([8 - gw, y0 - 0.7, 8 - 0.7], [8 + gw, y0, 8 + 0.7], "guard"));
  const gl = great ? 5.6 : 4.2;
  els.push(box([7.45, y0 - 0.7 - gl, 7.45], [8.55, y0 - 0.7, 8.55], "grip"));
  els.push(box([7.2, y0 - 1.6 - gl, 7.2], [8.8, y0 - 0.7 - gl, 8.8], "pommel"));
  return els;
}
function axe(st) {
  const els = [box([7.4, -1.5, 7.4], [8.6, 16.5, 8.6], "grip")];
  // лезвие: несколько слоёв, расширяется к режущей кромке
  els.push(box([8.6, 11.2, 7.65], [10.4, 15, 8.35], "head", 0.2, 0.8));
  els.push(box([10.4, 10.2, 7.75], [12.6, 16, 8.25], "head", 0, 1));
  els.push(box([12.6, 9.6, 7.85], [13.4, 16.6, 8.15], "head", 0, 1));
  els.push(box([6.2, 12.4, 7.7], [7.4, 14.2, 8.3], "head", 0.3, 0.7)); // обух
  els.push(box([7.2, 16.5, 7.2], [8.8, 17.3, 8.8], "pommel"));
  return els;
}
function mace() {
  const els = [box([7.4, -1.5, 7.4], [8.6, 13, 8.6], "grip")];
  els.push(box([6.6, 13, 6.6], [9.4, 16.6, 9.4], "head"));
  for (const [dx, dz] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
    const x0 = 8 + dx * 1.4 - (dx === 0 ? 0.25 : 0), x1 = 8 + dx * 2.6 + (dx === 0 ? 0.25 : 0);
    const z0 = 8 + dz * 1.4 - (dz === 0 ? 0.25 : 0), z1 = 8 + dz * 2.6 + (dz === 0 ? 0.25 : 0);
    els.push(box([Math.min(x0, x1), 13.4, Math.min(z0, z1)], [Math.max(x0, x1), 16.2, Math.max(z0, z1)], "head"));
  }
  els.push(box([7.6, 16.6, 7.6], [8.4, 17.6, 8.4], "pommel"));
  return els;
}

fs.rmSync(OUT, { recursive: true, force: true });
const MODELS = path.join(OUT, "assets/mraz1visual/models/item"), TEX = path.join(OUT, "assets/mraz1visual/textures/item");
fs.mkdirSync(MODELS, { recursive: true });
fs.mkdirSync(TEX, { recursive: true });
let total = 0;
for (const [id, st] of Object.entries(STYLES)) {
  const { px, gl } = paint(st.paint, st.glow);
  const tex = png(W, W, px);
  fs.writeFileSync(path.join(TEX, id + ".png"), tex);
  total += tex.length;
  if (gl) { const e = png(W, W, gl); fs.writeFileSync(path.join(TEX, id + "_e.png"), e); total += e.length; }
  const elements = st.axe ? axe(st) : st.mace ? mace(st) : sword(st);
  const model = { parent: "minecraft:item/handheld", textures: { "0": "mraz1visual:item/" + id, particle: "mraz1visual:item/" + id }, elements, mraz1: { name: st.name, bind: st.bind } };
  const js = JSON.stringify(model);
  fs.writeFileSync(path.join(MODELS, id + ".json"), js);
  total += js.length;
}
fs.writeFileSync(path.join(OUT, "names.json"), JSON.stringify(Object.fromEntries(Object.entries(STYLES).map(([k, s]) => [k, s.name])), null, 1));
console.log("mraz1:", Object.keys(STYLES).length, "моделей,", Math.round(total / 1024), "КБ");

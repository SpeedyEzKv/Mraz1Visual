"""
Umbra preview renderer v3 — «Minecraft с шейдерами».
Свои 16×16 текстуры, трава-спрайты, мягкие тени, небо с облаками, лучи, ACES-тонмаппинг.
Скины игроков — стандартная развёртка 64×64 с внешним слоем.
"""
import numpy as np, math
from PIL import Image, ImageFilter

W, H, D = 112, 44, 112
RNG = np.random.default_rng(7)

# ================================================================ textures (sRGB 0..1)
def nz(seed, size=16):
    return np.random.default_rng(seed).random((size, size))

def blobs(seed, n, rmin, rmax, size=16):
    r = np.random.default_rng(seed); m = np.zeros((size, size))
    yy, xx = np.mgrid[0:size, 0:size]
    for _ in range(n):
        cx, cy, rr = r.random() * size, r.random() * size, rmin + r.random() * (rmax - rmin)
        dx = np.minimum(abs(xx - cx), size - abs(xx - cx)); dy = np.minimum(abs(yy - cy), size - abs(yy - cy))
        m = np.maximum(m, (dx * dx + dy * dy < rr * rr) * (0.5 + 0.5 * r.random()))
    return m

def colorize(base, shade):
    return np.clip(np.array(base)[None, None] * shade[..., None], 0, 1)

def t_grass_top():
    s = 0.78 + 0.22 * nz(1) + 0.12 * blobs(2, 7, 1, 2.2) - 0.18 * (nz(3) > 0.86)
    return colorize((0.42, 0.64, 0.25), s)

def t_dirt():
    s = 0.80 + 0.2 * nz(11) - 0.25 * (nz(12) > 0.85) + 0.15 * (nz(13) > 0.92)
    return colorize((0.53, 0.38, 0.26), s)

def t_grass_side():
    t = t_dirt().copy(); g = t_grass_top()
    r = np.random.default_rng(5)
    for x in range(16):
        e = 3 + r.integers(0, 3)
        t[:e, x] = g[:e, x] * 0.92
    return t

def t_stone():
    s = 0.74 + 0.22 * nz(21) - 0.15 * blobs(22, 5, 0.6, 1.6) + 0.08 * blobs(23, 4, 0.5, 1.2)
    return colorize((0.52, 0.52, 0.53), s)

def t_cobble():
    base = 0.55 + 0.1 * nz(31)
    stones = blobs(32, 11, 1.6, 3.2)
    s = base + 0.32 * stones - 0.15 * (nz(33) > 0.8) * stones
    return colorize((0.56, 0.56, 0.57), s)

def t_bricks():
    s = 0.80 + 0.18 * nz(41) - 0.12 * (nz(42) > 0.85)
    t = colorize((0.50, 0.50, 0.52), s)
    m = (0.33, 0.33, 0.35)
    for row in (3, 7, 11, 15):
        t[row, :] = m
    for row0, xs in ((0, (0, 8)), (4, (4, 12)), (8, (0, 8)), (12, (4, 12))):
        for x in xs:
            t[row0:row0 + 3, x] = m
    t[0:3, 1] *= 1.08; t[4:7, 5] *= 1.08  # light edges
    return np.clip(t, 0, 1)

def t_chiseled():
    t = t_bricks().copy()
    t[1:15, 1:15] = colorize((0.53, 0.53, 0.55), 0.85 + 0.15 * nz(51))[1:15, 1:15]
    d, l = (0.32, 0.32, 0.34), (0.66, 0.66, 0.68)
    t[2, 2:14] = l; t[2:14, 2] = l; t[13, 2:14] = d; t[2:14, 13] = d
    t[5, 5:11] = d; t[5:11, 5] = d; t[10, 5:11] = l; t[5:11, 10] = l
    t[7:9, 7:9] = d
    return t

def t_log_side():
    s = 0.75 + 0.15 * nz(61)
    for x in range(16):
        s[:, x] *= 0.85 + 0.15 * math.sin(x * 1.7 + 1) ** 2
    return colorize((0.42, 0.32, 0.20), s)

def t_log_top():
    yy, xx = np.mgrid[0:16, 0:16]
    rr = np.sqrt((xx - 7.5) ** 2 + (yy - 7.5) ** 2)
    s = 0.8 + 0.1 * np.cos(rr * 2.2) + 0.08 * nz(62)
    t = colorize((0.64, 0.50, 0.32), s)
    t[rr > 6.6] = (0.40, 0.30, 0.19)
    return t

def t_leaves():
    s = 0.65 + 0.35 * nz(71) + 0.25 * blobs(72, 9, 0.8, 1.8)
    t = colorize((0.22, 0.46, 0.17), s)
    a = (nz(73) > 0.18).astype(float)
    return t, a

def t_planks():
    s = 0.82 + 0.12 * nz(81)
    for r in (3, 7, 11, 15): s[r, :] *= 0.72
    return colorize((0.62, 0.48, 0.30), s)

def t_amethyst():
    s = 0.72 + 0.28 * nz(91) + 0.2 * blobs(92, 6, 1, 2)
    t = colorize((0.45, 0.34, 0.68), s)
    t[0, :] *= 1.15; t[:, 0] *= 1.12; t[15, :] *= 0.8; t[:, 15] *= 0.82
    return np.clip(t, 0, 1)

def t_tallgrass():
    t = np.zeros((16, 16, 3)); a = np.zeros((16, 16))
    r = np.random.default_rng(101)
    for b in range(7):
        x = r.integers(1, 15); h = r.integers(4, 11); lean = r.uniform(-0.25, 0.25)
        for k in range(h):
            xx = int(round(x + lean * k)); yy = 15 - k
            if 0 <= xx < 16:
                t[yy, xx] = np.array((0.38, 0.62, 0.22)) * (0.75 + 0.35 * k / h); a[yy, xx] = 1
    return t, a

def t_flower(color, seed):
    t = np.zeros((16, 16, 3)); a = np.zeros((16, 16))
    t[7:16, 7:9] = (0.30, 0.55, 0.20); a[7:16, 7:9] = 1
    t[10, 5:7] = (0.30, 0.55, 0.2); a[10, 5:7] = 1
    yy, xx = np.mgrid[0:16, 0:16]
    pet = (xx - 7.5) ** 2 + (yy - 5) ** 2 < 7
    t[pet] = color; a[pet] = 1
    t[4:6, 7:9] = (0.98, 0.85, 0.3)
    return t, a

AIR, GRASS, DIRT, STONE, LOG, LEAVES, PLANKS, AMETHYST, BRICKS, CHISELED, COBBLE, TGRASS, FLOWER_R, FLOWER_Y = range(14)
TEX = {
    GRASS: (t_grass_top(), t_grass_side(), t_dirt()),
    DIRT: (t_dirt(),) * 3, STONE: (t_stone(),) * 3,
    LOG: (t_log_top(), t_log_side(), t_log_top()),
    PLANKS: (t_planks(),) * 3, AMETHYST: (t_amethyst(),) * 3,
    BRICKS: (t_bricks(),) * 3, CHISELED: (t_chiseled(),) * 3, COBBLE: (t_cobble(),) * 3,
}
LEAF, LEAF_A = t_leaves(); TEX[LEAVES] = (LEAF,) * 3
TG, TG_A = t_tallgrass(); FR, FR_A = t_flower((0.85, 0.15, 0.2), 1); FY, FY_A = t_flower((0.98, 0.82, 0.2), 2)
PLANTS = {TGRASS: (TG, TG_A), FLOWER_R: (FR, FR_A), FLOWER_Y: (FY, FY_A)}
ALPHA_FACE = {LEAVES: LEAF_A}
# linear albedo
LIN = {k: tuple(np.clip(t, 0, 1) ** 2.2 for t in v) for k, v in TEX.items()}
PLIN = {k: (t ** 2.2, a) for k, (t, a) in PLANTS.items()}

# ================================================================ worlds
def value_noise(x, z, scale, seed):
    g = np.random.default_rng(seed).random((W // scale + 3, D // scale + 3))
    xi, zi = x / scale, z / scale
    x0, z0 = np.floor(xi).astype(int), np.floor(zi).astype(int)
    fx, fz = xi - x0, zi - z0
    fx = fx * fx * (3 - 2 * fx); fz = fz * fz * (3 - 2 * fz)
    return (g[x0, z0] * (1 - fx) * (1 - fz) + g[x0 + 1, z0] * fx * (1 - fz) + g[x0, z0 + 1] * (1 - fx) * fz + g[x0 + 1, z0 + 1] * fx * fz)

CX, CZ = 56, 56

def build_forest(seed=1, clearing=12):
    world = np.zeros((W, H, D), np.uint8)
    xs, zs = np.mgrid[0:W, 0:D]
    h = 13 + value_noise(xs, zs, 18, seed) * 6 + value_noise(xs, zs, 7, seed + 1) * 2
    dist = np.sqrt((xs - CX) ** 2 + (zs - CZ) ** 2)
    k = np.clip((dist - clearing) / 10.0, 0, 1)
    h = np.floor(16.0 * (1 - k) + h * k).astype(int)
    for x in range(W):
        for z in range(D):
            t = h[x, z]
            world[x, :t - 3, z] = STONE; world[x, t - 3:t, z] = DIRT; world[x, t, z] = GRASS
    r = np.random.default_rng(seed + 5)
    trees = []
    for _ in range(170):
        x, z = r.integers(4, W - 4), r.integers(4, D - 4)
        if math.hypot(x - CX, z - CZ) < clearing + 3 or any(abs(x - a) < 6 and abs(z - b) < 6 for a, b in trees):
            continue
        trees.append((x, z)); top = h[x, z] + 1; th = r.integers(4, 7)
        world[x, top:top + th, z] = LOG
        cy = top + th
        for dx in range(-3, 4):
            for dy in range(-2, 3):
                for dz in range(-3, 4):
                    if dx * dx + dz * dz + (dy * 1.6) ** 2 <= 8.5 + r.random() * 2:
                        X, Y, Z = x + dx, cy + dy, z + dz
                        if 0 <= X < W and 0 <= Z < D and 0 <= Y < H and world[X, Y, Z] == AIR:
                            world[X, Y, Z] = LEAVES
    for x in range(1, W - 1):
        for z in range(1, D - 1):
            y = h[x, z] + 1
            if world[x, y, z] != AIR or math.hypot(x - CX, z - CZ) < 2.2:
                continue
            q = r.random()
            if q < 0.20: world[x, y, z] = TGRASS
            elif q < 0.215: world[x, y, z] = FLOWER_R
            elif q < 0.23: world[x, y, z] = FLOWER_Y
    return world, h

def build_castle():
    world = np.zeros((W, H, D), np.uint8)
    base = 16
    world[:, :base, :] = STONE
    world[:, base - 1, :] = BRICKS
    r = np.random.default_rng(9)
    m = r.random((W, D)) < 0.25
    world[:, base - 1, :][m] = COBBLE
    for x in range(W):
        for y in range(base, base + 2):
            world[x, y, 64] = BRICKS; world[x, y, 65] = BRICKS
        if x % 2 == 0:
            world[x, base + 2, 65] = BRICKS
    lights = []
    for x in range(1, W, 7):
        world[x, base:base + 3, 63] = CHISELED
        lights.append(np.array([x + 0.5, base + 3.45, 63.5]))
    return world, base, lights

def ground_y(h, x, z):
    return h[int(math.floor(x)), int(math.floor(z))] + 1

# ================================================================ camera
class Camera:
    def __init__(self, pos, look, vfov, w, h):
        self.pos = np.array(pos, float)
        f = np.array(look, float) - self.pos; f /= np.linalg.norm(f)
        r = np.cross(f, [0, 1, 0]); r /= np.linalg.norm(r)
        self.f, self.r, self.u = f, r, np.cross(r, f)
        self.w, self.h = w, h
        self.th = math.tan(math.radians(vfov) / 2); self.tw = self.th * w / h

    def rays(self):
        ys, xs = np.mgrid[0:self.h, 0:self.w]
        sx = ((xs + 0.5) / self.w * 2 - 1) * self.tw
        sy = (1 - (ys + 0.5) / self.h * 2) * self.th
        d = self.f[None, None] + sx[..., None] * self.r[None, None] + sy[..., None] * self.u[None, None]
        d /= np.linalg.norm(d, axis=-1, keepdims=True)
        return d

    def project(self, p):
        v = np.array(p, float) - self.pos
        z = v @ self.f
        return ((v @ self.r) / z / self.tw + 1) / 2 * self.w, (1 - (v @ self.u) / z / self.th) / 2 * self.h, z

# ================================================================ voxel tracing
def face_uv(p, axis):
    fx, fy, fz = (p - np.floor(p)).T
    u = np.where(axis == 0, fz, fx); v = np.where(axis == 1, fz, 1 - fy)
    return np.clip(u, 0, 0.999), np.clip(v, 0, 0.999)

def trace(world, o, d, max_steps=260, plants=True):
    """Returns t, axis (0..2 faces, 3/4 plant planes), block, plant uv."""
    n = d.shape[0]
    pos = np.broadcast_to(o, d.shape).astype(float)
    ix = np.floor(pos).astype(int)
    step = np.where(d >= 0, 1, -1)
    with np.errstate(divide='ignore', invalid='ignore'):
        inv = np.where(np.abs(d) > 1e-9, 1.0 / d, 1e30)
    tmax = (ix + (step > 0) - pos) * inv
    tdelta = np.abs(inv)
    hit_t = np.full(n, np.inf); hit_ax = np.zeros(n, int); hit_b = np.zeros(n, np.uint8); puv = np.zeros((n, 2))
    active = np.arange(n); t_cur = np.zeros(n)
    # the starting cell may contain a plant: ignore
    for _ in range(max_steps):
        if active.size == 0: break
        a = active
        tm = tmax[a]; axis = np.argmin(tm, axis=1)
        t_enter = tm[np.arange(a.size), axis]
        ix[a, axis] += step[a, axis]; tmax[a, axis] += tdelta[a, axis]
        t_exit = tmax[a].min(axis=1)
        x, y, z = ix[a].T
        inb = (x >= 0) & (x < W) & (y >= 0) & (y < H) & (z >= 0) & (z < D)
        out = (~inb) & ((y >= H) | (y < 0) | (x < -1) | (x > W) | (z < -1) | (z > D))
        b = np.zeros(a.size, np.uint8); b[inb] = world[x[inb], y[inb], z[inb]]
        hit = np.zeros(a.size, bool); ht = t_enter.copy(); hax = axis.copy(); pu = np.zeros((a.size, 2))
        solid = (b > 0)
        pl = np.isin(b, list(PLANTS.keys()))
        face = solid & ~pl
        # leaves alpha
        lm = face & (b == LEAVES)
        if lm.any():
            ii = np.where(lm)[0]
            p = pos[a[ii]] + d[a[ii]] * t_enter[ii, None]
            u, v = face_uv(p, axis[ii])
            keep = LEAF_A[(v * 16).astype(int), (u * 16).astype(int)] > 0.5
            face[ii[~keep]] = False
        hit |= face
        if plants and pl.any():
            ii = np.where(pl)[0]
            P = pos[a[ii]] - ix[a[ii]]; dd = d[a[ii]]
            best = np.full(ii.size, np.inf); bu = np.zeros(ii.size); bv = np.zeros(ii.size); bax = np.zeros(ii.size, int)
            for k, (num, den) in enumerate(((-(P[:, 0] - P[:, 2]), dd[:, 0] - dd[:, 2]), (1 - P[:, 0] - P[:, 2], dd[:, 0] + dd[:, 2]))):
                with np.errstate(divide='ignore', invalid='ignore'):
                    tt = np.where(np.abs(den) > 1e-9, num / den, np.inf)
                ok = (tt >= t_enter[ii] - 1e-6) & (tt <= t_exit[ii] + 1e-6) & (tt < best)
                q = P + dd * tt[:, None]
                ok &= (q[:, 0] >= 0) & (q[:, 0] < 1) & (q[:, 1] >= 0) & (q[:, 1] < 1)
                u = np.clip(q[:, 0], 0, 0.999); v = np.clip(1 - q[:, 1], 0, 0.999)
                bb = b[ii]
                alpha = np.zeros(ii.size)
                for blk, (_, am) in PLIN.items():
                    mm = bb == blk
                    alpha[mm] = am[(v[mm] * 16).astype(int), (u[mm] * 16).astype(int)]
                ok &= alpha > 0.5
                best = np.where(ok, tt, best); bu = np.where(ok, u, bu); bv = np.where(ok, v, bv); bax = np.where(ok, 3 + k, bax)
            got = np.isfinite(best)
            hit[ii[got]] = True; ht[ii[got]] = best[got]; hax[ii[got]] = bax[got]
            pu[ii[got], 0] = bu[got]; pu[ii[got], 1] = bv[got]
        hit_t[a[hit]] = ht[hit]; hit_ax[a[hit]] = hax[hit]; hit_b[a[hit]] = b[hit]; puv[a[hit]] = pu[hit]
        active = a[~hit & ~out]
    return hit_t, hit_ax, hit_b, puv

# ================================================================ characters (64×64 skins with outer layer)
UV = {  # part: (inner uv origin, outer uv origin, size w,h,d in px)
    'head': ((0, 0), (32, 0), (8, 8, 8)),
    'body': ((16, 16), (16, 32), (8, 12, 4)),
    'rarm': ((40, 16), (40, 32), (4, 12, 4)),
    'larm': ((32, 48), (48, 48), (4, 12, 4)),
    'rleg': ((0, 16), (0, 32), (4, 12, 4)),
    'lleg': ((16, 48), (0, 48), (4, 12, 4)),
}

def face_rect(origin, size, face):
    (u0, v0), (w, h, d) = origin, size
    return {'top': (u0 + d, v0, w, d), 'bottom': (u0 + d + w, v0, w, d), 'right': (u0, v0 + d, d, h),
            'front': (u0 + d, v0 + d, w, h), 'left': (u0 + d + w, v0 + d, d, h), 'back': (u0 + 2 * d + w, v0 + d, w, h)}[face]

def rot_y(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])

def rot_x(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])

def load_skin(path):
    im = Image.open(path).convert('RGBA')
    if im.size[1] == 32:  # старый формат: достроить левые руку/ногу
        n = Image.new('RGBA', (64, 64)); n.paste(im, (0, 0))
        n.paste(im.crop((0, 16, 16, 32)), (16, 48)); n.paste(im.crop((40, 16, 56, 32)), (32, 48)); im = n
    a = np.asarray(im).astype(float) / 255
    return a

def humanoid(pos, yaw, skin, arm_r=0.0, arm_l=0.0, leg=0.0, head_pitch=0.0, head_yaw=0.0, tint=None):
    px = 1 / 16
    Rm = rot_y(yaw)
    out = []
    def add(part, center, pivot, pitch, extra_yaw=0.0):
        (inner, outer, size) = UV[part]
        R2 = Rm @ rot_y(extra_yaw) if extra_yaw else Rm
        for layer, origin, grow in ((0, inner, 0.0), (1, outer, 0.5 if part == 'head' else 0.25)):
            half = (np.array(size) / 2 + grow) * px
            out.append(dict(kind='skin', part=part, origin=origin, size=size, skin=skin, outer=layer == 1, half=half,
                            center=np.array(center) * px, pivot=np.array(pivot) * px, pitch=pitch, R=R2,
                            pos=np.array(pos, float), tint=tint))
    add('rleg', (-2, 6, 0), (-2, 12, 0), leg)
    add('lleg', (2, 6, 0), (2, 12, 0), -leg)
    add('body', (0, 18, 0), (0, 18, 0), 0)
    add('rarm', (-6, 18, 0), (-6, 22, 0), arm_r)
    add('larm', (6, 18, 0), (6, 22, 0), arm_l)
    add('head', (0, 28, 0), (0, 24, 0), head_pitch, head_yaw)
    return out

def flat_box(pos, half, color, emit=False, frame=None):
    return dict(kind='flat', color=np.array(color, float), emit=emit, frame=frame, half=np.array(half, float),
                center=np.zeros(3), pivot=np.zeros(3), pitch=0.0, R=np.eye(3), pos=np.array(pos, float))

def lantern(pos):
    p = np.array(pos, float)
    return [flat_box(p + [0, -0.12, 0], [0.19, 0.22, 0.19], (1.0, 0.70, 0.30), emit=True, frame=(0.16, 0.14, 0.13)),
            flat_box(p + [0, 0.15, 0], [0.13, 0.05, 0.13], (0.16, 0.14, 0.13))]

def box_world(b):
    Rp = rot_x(b.get('pitch', 0.0)); M = b['R'] @ Rp
    cw = b['pos'] + b['R'] @ (b['pivot'] + Rp @ (b['center'] - b['pivot']))
    return M, cw

def trace_boxes(boxes, o, d):
    n = d.shape[0]
    o = np.broadcast_to(o, d.shape)
    best = np.full(n, np.inf); col = np.zeros((n, 3)); nrm = np.zeros((n, 3)); emit = np.zeros(n, bool)
    for b in boxes:
        M, cw = box_world(b)
        lo = (o - cw) @ M; ld = d @ M; half = b['half']
        with np.errstate(divide='ignore', invalid='ignore'):
            inv = np.where(np.abs(ld) > 1e-9, 1 / ld, 1e30)
        t1 = (-half - lo) * inv; t2 = (half - lo) * inv
        tmin = np.minimum(t1, t2); tmax = np.maximum(t1, t2)
        tn = tmin.max(axis=1); tf = tmax.min(axis=1); ax = tmin.argmax(axis=1)
        hit = (tn <= tf) & (tf > 0) & (tn > 0) & (tn < best)
        if not hit.any(): continue
        idx = np.where(hit)[0]
        p = lo[idx] + ld[idx] * tn[idx, None]; a = ax[idx]; sgn = -np.sign(ld[idx, a])
        alpha = np.ones(idx.size)
        if b['kind'] == 'flat':
            cols = np.broadcast_to(b['color'], (idx.size, 3)).copy()
            if b.get('frame') is not None:
                q = np.abs(p) / half[None]; q[np.arange(q.shape[0]), a] = 0
                cols[q.max(axis=1) > 0.72] = b['frame']
        else:
            cols = np.zeros((idx.size, 3)); skin = b['skin']
            for face in ('front', 'back', 'left', 'right', 'top', 'bottom'):
                m = {'front': (a == 2) & (sgn > 0), 'back': (a == 2) & (sgn < 0), 'left': (a == 0) & (sgn > 0),
                     'right': (a == 0) & (sgn < 0), 'top': (a == 1) & (sgn > 0), 'bottom': (a == 1) & (sgn < 0)}[face]
                if not m.any(): continue
                u0, v0, fw, fh = face_rect(b['origin'], b['size'], face)
                pp = p[m]
                if face in ('front', 'back'):
                    u = (pp[:, 0] + half[0]) / (2 * half[0]); v = (half[1] - pp[:, 1]) / (2 * half[1])
                    if face == 'back': u = 1 - u
                elif face in ('left', 'right'):
                    u = (pp[:, 2] + half[2]) / (2 * half[2]); v = (half[1] - pp[:, 1]) / (2 * half[1])
                    if face == 'left': u = 1 - u
                else:
                    u = (pp[:, 0] + half[0]) / (2 * half[0]); v = (pp[:, 2] + half[2]) / (2 * half[2])
                    if face == 'bottom': v = 1 - v
                tx = np.clip((u0 + np.clip(u, 0, 0.999) * fw).astype(int), 0, 63)
                ty = np.clip((v0 + np.clip(v, 0, 0.999) * fh).astype(int), 0, 63)
                texel = skin[ty, tx]
                cols[m] = texel[:, :3]; alpha[m] = texel[:, 3]
            if b.get('tint') is not None:
                cols = np.clip(cols * np.array(b['tint']), 0, 1)
        ok = alpha > 0.5
        idx = idx[ok]
        if idx.size == 0: continue
        nl = np.zeros((idx.size, 3)); nl[np.arange(idx.size), a[ok]] = sgn[ok]
        best[idx] = tn[idx]; col[idx] = cols[ok]; nrm[idx] = nl @ M.T; emit[idx] = bool(b.get('emit', False))
    return best, col, nrm, emit

# ================================================================ lighting
def solid(world, c):
    x, y, z = c.T
    inb = (x >= 0) & (x < W) & (y >= 0) & (y < H) & (z >= 0) & (z < D)
    b = world[np.clip(x, 0, W - 1), np.clip(y, 0, H - 1), np.clip(z, 0, D - 1)]
    return inb & (b > 0) & ~np.isin(b, list(PLANTS.keys()))

def ao(world, p, ax, normal):
    n = p.shape[0]; idx = np.arange(n)
    cell = np.floor(p - normal * 0.01).astype(int) + normal.astype(int)
    t1 = np.where(ax == 0, 1, 0); t2 = np.where(ax == 2, 1, 2); t1 = np.where(ax == 1, 0, t1)
    fr = p - np.floor(p); u = fr[idx, t1]; v = fr[idx, t2]
    occ = np.zeros(n)
    def off(a1, s1, a2=None, s2=0):
        c = cell.copy(); c[idx, a1] += s1
        if a2 is not None: c[idx, a2] += s2
        return solid(world, c)
    for a1, s1, w in ((t1, -1, 1 - u), (t1, 1, u), (t2, -1, 1 - v), (t2, 1, v)):
        occ += off(a1, s1) * np.clip((w - 0.3) / 0.7, 0, 1) ** 1.8 * 0.6
    for s1, w1 in ((-1, 1 - u), (1, u)):
        for s2, w2 in ((-1, 1 - v), (1, v)):
            occ += off(t1, s1, t2, s2) * (np.clip((w1 - 0.3) / 0.7, 0, 1) * np.clip((w2 - 0.3) / 0.7, 0, 1)) ** 1.4 * 0.4
    return np.clip(1 - occ * 0.55, 0.3, 1)

MODES = {
    'day':   dict(sun=np.array([1.00, 0.93, 0.80]) * 2.7, sky=np.array([0.42, 0.58, 0.95]) * 0.75, fog=np.array([0.62, 0.74, 0.93]), fogd=95, exp=1.0),
    'dusk':  dict(sun=np.array([1.00, 0.62, 0.36]) * 2.6, sky=np.array([0.45, 0.42, 0.62]) * 0.55, fog=np.array([0.85, 0.58, 0.46]), fogd=75, exp=1.1),
    'night': dict(sun=np.array([0.45, 0.55, 1.00]) * 0.45, sky=np.array([0.10, 0.13, 0.28]) * 0.55, fog=np.array([0.05, 0.06, 0.12]), fogd=45, exp=1.35),
}

def fbm2(x, y, seed=5, octaves=4):
    tot = np.zeros_like(x); amp = 0.5
    for o in range(octaves):
        g = np.random.default_rng(seed + o).random((64, 64))
        xi, yi = x * (2 ** o), y * (2 ** o)
        x0, y0 = np.floor(xi).astype(int), np.floor(yi).astype(int)
        fx, fy = xi - x0, yi - y0; fx = fx * fx * (3 - 2 * fx); fy = fy * fy * (3 - 2 * fy)
        a = g[x0 % 64, y0 % 64]; b = g[(x0 + 1) % 64, y0 % 64]; c = g[x0 % 64, (y0 + 1) % 64]; e = g[(x0 + 1) % 64, (y0 + 1) % 64]
        tot += amp * (a * (1 - fx) * (1 - fy) + b * fx * (1 - fy) + c * (1 - fx) * fy + e * fx * fy); amp *= 0.5
    return tot

def sky(d, mode, sun):
    up = np.clip(d[:, 1], -0.1, 1)
    sd = np.clip(d @ sun, -1, 1)
    if mode == 'night':
        top, hor = np.array([0.004, 0.006, 0.02]), np.array([0.03, 0.04, 0.09])
    elif mode == 'dusk':
        top, hor = np.array([0.08, 0.10, 0.30]), np.array([1.10, 0.48, 0.26])
    else:
        top, hor = np.array([0.10, 0.25, 0.75]), np.array([0.62, 0.76, 0.98])
    k = np.clip(up * 2.2, 0, 1)[:, None] ** 0.6
    c = hor * (1 - k) + top * k
    if mode != 'night':
        c += MODES[mode]['sun'][None] * (0.05 * np.clip(sd, 0, 1) ** 8 + 0.6 * np.clip(sd - 0.9995, 0, 1) * 2000)[:, None]
        # облака на плоскости
        m = d[:, 1] > 0.02
        cl = np.zeros(d.shape[0])
        tt = 40.0 / np.maximum(d[m, 1], 0.02)
        cx, cz = d[m, 0] * tt / 60, d[m, 2] * tt / 60
        f = fbm2(cx + 7.3, cz + 3.1)
        cl[m] = np.clip((f - 0.52) * 4.5, 0, 1) * np.clip(d[m, 1] * 8, 0, 1)
        ccol = np.array([1.0, 0.98, 0.95]) if mode == 'day' else np.array([1.0, 0.62, 0.48])
        c = c * (1 - cl[:, None] * 0.85) + ccol[None] * cl[:, None] * (0.9 if mode == 'day' else 0.7)
    else:
        r = np.random.default_rng(3); n = d.shape[0]
        st = (r.random(n) > 0.9975) & (d[:, 1] > 0.05)
        c[st] += np.array([0.7, 0.75, 0.9]) * r.uniform(0.2, 1, st.sum())[:, None]
    return c

def shade(world, cam, boxes, mode, sun, lights=(), region=None, soft=3):
    dall = cam.rays()
    if region:
        x0, y0, x1, y1 = region; dall = dall[y0:y1, x0:x1]
    hh, ww = dall.shape[:2]
    d = dall.reshape(-1, 3); n = d.shape[0]; o = cam.pos
    P = MODES[mode]
    sun = np.array(sun, float); sun /= np.linalg.norm(sun)
    vt, vax, vb, puv = trace(world, o, d)
    bt, bcol, bn, bemit = trace_boxes(boxes, o, d)
    out = sky(d, mode, sun)
    depth = np.full(n, 300.0)
    r = np.random.default_rng(17)
    jit = [sun] + [sun + r.normal(0, 0.035, 3) for _ in range(soft - 1)]
    jit = [j / np.linalg.norm(j) for j in jit]
    shadow_boxes = [b for b in boxes if not b.get('emit')]

    def lights_at(p, nrm):
        acc = np.zeros((p.shape[0], 3))
        for L in lights:
            v = L - p; dist = np.linalg.norm(v, axis=1)
            ndl = np.clip((nrm * v).sum(axis=1) / np.maximum(dist, 1e-6), 0, 1) * 0.85 + 0.15
            acc += np.array([1.0, 0.55, 0.22])[None] * (16.0 * ndl / (1 + 4.0 * dist * dist))[:, None]
        return acc

    def visibility(p, nrm):
        v = np.zeros(p.shape[0])
        for j in jit:
            so = p + nrm * 0.02
            dj = np.broadcast_to(j, so.shape).copy()
            st, _, _, _ = trace(world, so, dj, max_steps=60, plants=False)
            sb, _, _, _ = trace_boxes(shadow_boxes, so, dj)
            v += (~np.isfinite(st)) & (~np.isfinite(sb))
        return v / len(jit)

    use_box = bt < vt
    vm = np.isfinite(vt) & ~use_box
    if vm.any():
        i = np.where(vm)[0]
        p = o + d[i] * vt[i, None]; ax = vax[i]; blk = vb[i]
        plant = ax >= 3
        normal = np.zeros((i.size, 3))
        fa = ~plant
        normal[np.where(fa)[0], ax[fa]] = -np.sign(d[i[fa], ax[fa]])
        normal[plant] = (0, 1, 0)
        top = (ax == 1) & (normal[:, 1] > 0); bot = (ax == 1) & (normal[:, 1] < 0)
        u, v = face_uv(p, np.minimum(ax, 2))
        tx = (u * 16).astype(int); ty = (v * 16).astype(int)
        c = np.zeros((i.size, 3))
        for blkid, (tt, ts, tb) in LIN.items():
            m = (blk == blkid) & fa
            if not m.any(): continue
            mt, ms, mb = m & top, m & ~top & ~bot, m & bot
            c[mt] = tt[ty[mt], tx[mt]]; c[ms] = ts[ty[ms], tx[ms]]; c[mb] = tb[ty[mb], tx[mb]]
        for blkid, (tex, _) in PLIN.items():
            m = (blk == blkid) & plant
            if m.any():
                c[m] = tex[(puv[i[m], 1] * 16).astype(int), (puv[i[m], 0] * 16).astype(int)]
        cellv = np.floor(p - normal * 0.01).astype(int)
        hsh = ((cellv[:, 0] * 73856093) ^ (cellv[:, 1] * 19349663) ^ (cellv[:, 2] * 83492791)) & 1023
        c *= (0.9 + 0.1 * hsh / 1023.0)[:, None]
        occ = np.ones(i.size)
        occ[fa] = ao(world, p[fa], ax[fa], normal[fa])
        diff = np.clip(normal @ sun, 0, 1)
        diff[plant] = 0.30 + 0.25 * np.clip(sun[1], 0, 1)
        vis = visibility(p, normal)
        skyk = np.where(top, 1.0, np.where(bot, 0.35, 0.7)); skyk[plant] = 0.85
        light = P['sky'][None] * (occ * skyk)[:, None] + P['sun'][None] * (diff * vis)[:, None]
        light += lights_at(p, normal) * occ[:, None]
        col = c * light
        dist = vt[i]
        f = 1 - np.exp(-np.maximum(dist - 4, 0) / P['fogd'])
        fogc = P['fog'] ** 2.2
        col = col * (1 - f[:, None]) + fogc[None] * f[:, None] * (1.0 if mode != 'night' else 0.6)
        out[i] = col; depth[i] = dist
    if use_box.any():
        i = np.where(use_box)[0]
        nn = bn[i]; p = o + d[i] * bt[i, None]
        c = np.clip(bcol[i], 0, 1) ** 2.2
        diff = np.clip(nn @ sun, 0, 1)
        vis = visibility(p, nn)
        light = P['sky'][None] * (0.55 + 0.45 * np.clip(nn[:, 1], 0, 1))[:, None] + P['sun'][None] * (diff * vis)[:, None] + lights_at(p, nn)
        col = c * light
        em = bemit[i]; col[em] = c[em] * 9.0
        out[i] = col; depth[i] = bt[i]
    return out.reshape(hh, ww, 3), depth.reshape(hh, ww)

# ================================================================ post
def aces(x):
    a, b, c, d, e = 2.51, 0.03, 2.43, 0.59, 0.14
    return np.clip((x * (a * x + b)) / (x * (c * x + d) + e), 0, 1)

def blur(img, r):
    im = Image.fromarray(np.clip(img * 255 / 4, 0, 255).astype(np.uint8))
    return np.asarray(im.filter(ImageFilter.GaussianBlur(r))).astype(float) / 255 * 4

def post(rgb, depth, mode, focus, sun_px=None):
    h, w = depth.shape
    x = rgb * MODES[mode]['exp']
    # bloom (HDR)
    br = np.clip(x - 1.3, 0, 3)
    x = x + blur(br, w / 220) * 0.45 + blur(br, w / 70) * 0.55
    # god rays from the sun position
    if sun_px is not None and mode != 'night':
        sx, sy = sun_px
        skymask = (depth >= 299)[..., None] * np.clip(x - 0.8, 0, 2)
        acc = np.zeros_like(x); cur = skymask.copy()
        yy, xx = np.mgrid[0:h, 0:w]
        for k in range(24):
            s = 1 - k * 0.025
            mx = np.clip((sx + (xx - sx) * s).astype(int), 0, w - 1); my = np.clip((sy + (yy - sy) * s).astype(int), 0, h - 1)
            acc += skymask[my, mx] * (0.96 ** k)
        x = x + acc / 24 * 0.35 * MODES[mode]['sun'][None, None] / 3
    # tonemap
    t = aces(x)
    t = t ** (1 / 2.2)
    # DOF (far)
    base = Image.fromarray((t * 255).astype(np.uint8))
    bl = np.asarray(base.filter(ImageFilter.GaussianBlur(w / 950))).astype(float) / 255
    k = np.clip((depth - focus * 2.5) / (focus * 5), 0, 0.8)[..., None]
    t = t * (1 - k) + bl * k
    # grading: тёплые светы, холодные тени, насыщенность
    l = t.mean(axis=2, keepdims=True)
    t = l + (t - l) * 1.15
    shadow_tint = np.array([0.97, 1.0, 1.06]) if mode != 'dusk' else np.array([0.96, 0.95, 1.05])
    hi_tint = np.array([1.04, 1.0, 0.95])
    t = t * (shadow_tint * (1 - l) + hi_tint * l)
    yy, xx = np.mgrid[0:h, 0:w]
    vig = np.clip(1 - (((xx / w - 0.5) * 1.5) ** 2 + ((yy / h - 0.5) * 1.15) ** 2) * 0.6, 0.4, 1)[..., None]
    return np.clip(t * vig, 0, 1)


def make_skin(shirt, pants, hair, skin=(0.84, 0.64, 0.50), hood=False, seed=11):
    """Своя 64×64 скин-развёртка (внутренний слой + капюшон на внешнем слое)."""
    import render as R0
    rgb = R0.make_skin(shirt, pants, hair, skin=skin, logo=False, hood=False)
    s = np.zeros((64, 64, 4)); s[..., :3] = rgb; s[..., 3] = 0
    # inner layer regions opaque
    for (x0, y0, x1, y1) in ((0, 0, 32, 16), (0, 16, 56, 32), (16, 48, 48, 64)):
        s[y0:y1, x0:x1, 3] = 1
    # left arm/leg = copies of right
    s[48:64, 16:32, :3] = rgb[16:32, 0:16]; s[48:64, 32:48, :3] = rgb[16:32, 40:56]
    r = np.random.default_rng(seed)
    # folds / shading on shirt
    for y in range(20, 32):
        s[y, 16:56, :3] *= 0.9 + 0.1 * math.sin(y * 0.9)
    s[..., :3] *= (0.94 + 0.06 * r.random((64, 64)))[..., None]
    if hood:
        # hood on outer head layer (32,0): all faces except the face area
        s[0:16, 32:64, :3] = np.array(shirt) * 0.9; s[0:16, 32:64, 3] = 1
        s[8:16, 40:48, 3] = 0  # open face
        s[8:9, 40:48, 3] = 1; s[8:16, 40:41, 3] = 1; s[8:16, 47:48, 3] = 1
    return np.clip(s, 0, 1)

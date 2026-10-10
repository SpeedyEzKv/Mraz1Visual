"""
Voxel scene renderer for Umbra module previews.
Own procedural textures and skins (no Mojang assets). Outputs PNG + anchor json.
"""
import numpy as np, json, math, sys, os
from PIL import Image

RNG = np.random.default_rng(7)

# ---------------------------------------------------------------- textures
def noise16(seed, base, var, size=16):
    r = np.random.default_rng(seed)
    n = r.random((size, size))
    return np.clip(np.array(base)[None, None, :] + (n[..., None] - 0.5) * np.array(var)[None, None, :], 0, 1)

def tex_grass_top():
    t = noise16(1, (0.33, 0.52, 0.22), (0.08, 0.12, 0.07))
    return t

def tex_dirt():
    t = noise16(2, (0.47, 0.33, 0.22), (0.12, 0.10, 0.08))
    r = np.random.default_rng(22).random((16, 16)) > 0.9
    t[r] *= 0.75
    return t

def tex_grass_side():
    t = tex_dirt().copy()
    g = tex_grass_top()
    edge = 3 + (np.random.default_rng(3).random(16) * 2).astype(int)
    for x in range(16):
        t[: edge[x], x] = g[: edge[x], x]
    return t

def tex_stone():
    return noise16(4, (0.50, 0.50, 0.52), (0.14, 0.14, 0.14))

def tex_log_side():
    t = noise16(5, (0.40, 0.29, 0.18), (0.06, 0.05, 0.04))
    for x in range(0, 16, 4):
        t[:, x] *= 0.78
    return t

def tex_log_top():
    t = noise16(6, (0.62, 0.48, 0.30), (0.06, 0.05, 0.04))
    yy, xx = np.mgrid[0:16, 0:16]
    rr = np.sqrt((xx - 7.5) ** 2 + (yy - 7.5) ** 2)
    t[(rr.astype(int) % 3) == 0] *= 0.85
    t[rr > 6.8] = (0.40, 0.29, 0.18)
    return t

def tex_leaves():
    t = noise16(7, (0.20, 0.47, 0.16), (0.12, 0.18, 0.10))
    alpha = (np.random.default_rng(8).random((16, 16)) > 0.22).astype(float)
    return t, alpha

def tex_planks():
    t = noise16(9, (0.58, 0.44, 0.27), (0.05, 0.04, 0.03))
    t[3::4, :] *= 0.8
    return t

def tex_purple():  # block used for "placed blocks" demos
    t = noise16(10, (0.36, 0.29, 0.55), (0.06, 0.05, 0.08))
    t[0, :] *= 1.15; t[:, 0] *= 1.15; t[15, :] *= 0.8; t[:, 15] *= 0.8
    return np.clip(t, 0, 1)

AIR, GRASS, DIRT, STONE, LOG, LEAVES, PLANKS, PURPLE, FLOWER_R, FLOWER_Y = range(10)
TEX = {
    GRASS: (tex_grass_top(), tex_grass_side(), tex_dirt()),
    DIRT: (tex_dirt(),) * 3,
    STONE: (tex_stone(),) * 3,
    LOG: (tex_log_top(), tex_log_side(), tex_log_top()),
    PLANKS: (tex_planks(),) * 3,
    PURPLE: (tex_purple(),) * 3,
}
LEAF_RGB, LEAF_A = tex_leaves()
TEX[LEAVES] = (LEAF_RGB,) * 3

# flowers: cross sprites approximated as tiny colored blocks with alpha mask
def tex_flower(color):
    t = np.zeros((16, 16, 3)); a = np.zeros((16, 16))
    t[8:16, 7:9] = (0.25, 0.55, 0.2); a[8:16, 7:9] = 1
    t[4:8, 5:11] = color; a[4:8, 5:11] = 1
    t[5:7, 7:9] = (1.0, 0.9, 0.3); return t, a
FR_RGB, FR_A = tex_flower((0.85, 0.2, 0.25))
FY_RGB, FY_A = tex_flower((0.95, 0.85, 0.25))
TEX[FLOWER_R] = (FR_RGB,) * 3
TEX[FLOWER_Y] = (FY_RGB,) * 3
ALPHA = {LEAVES: LEAF_A, FLOWER_R: FR_A, FLOWER_Y: FY_A}

# ---------------------------------------------------------------- world
W, H, D = 112, 40, 112
CX, CZ = 56, 56

def value_noise(x, z, scale, seed):
    r = np.random.default_rng(seed)
    g = r.random((W // scale + 3, D // scale + 3))
    xi, zi = x / scale, z / scale
    x0, z0 = np.floor(xi).astype(int), np.floor(zi).astype(int)
    fx, fz = xi - x0, zi - z0
    fx = fx * fx * (3 - 2 * fx); fz = fz * fz * (3 - 2 * fz)
    a = g[x0, z0]; b = g[x0 + 1, z0]; c = g[x0, z0 + 1]; d = g[x0 + 1, z0 + 1]
    return a * (1 - fx) * (1 - fz) + b * fx * (1 - fz) + c * (1 - fx) * fz + d * fx * fz

def build_world(seed=1, clearing=12):
    world = np.zeros((W, H, D), dtype=np.uint8)
    xs, zs = np.mgrid[0:W, 0:D]
    h = 13 + value_noise(xs, zs, 18, seed) * 6 + value_noise(xs, zs, 7, seed + 1) * 2
    dist = np.sqrt((xs - CX) ** 2 + (zs - CZ) ** 2)
    flat = 16.0
    k = np.clip((dist - clearing) / 10.0, 0, 1)
    h = flat * (1 - k) + h * k
    h = np.floor(h).astype(int)
    for x in range(W):
        for z in range(D):
            top = h[x, z]
            world[x, :top - 3, z] = STONE
            world[x, top - 3:top, z] = DIRT
            world[x, top, z] = GRASS
    r = np.random.default_rng(seed + 5)
    # trees
    trees = []
    for _ in range(170):
        x, z = r.integers(4, W - 4), r.integers(4, D - 4)
        if math.hypot(x - CX, z - CZ) < clearing + 3:
            continue
        if any(abs(x - a) < 6 and abs(z - b) < 6 for a, b in trees):
            continue
        trees.append((x, z))
        top = h[x, z] + 1
        th = r.integers(4, 7)
        for y in range(top, top + th):
            world[x, y, z] = LOG
        cy = top + th
        for dx in range(-3, 4):
            for dy in range(-2, 3):
                for dz in range(-3, 4):
                    if dx * dx + dz * dz + (dy * 1.6) ** 2 <= 8.5 + r.random() * 2:
                        xx, yy, zz = x + dx, cy + dy, z + dz
                        if 0 <= xx < W and 0 <= zz < D and 0 <= yy < H and world[xx, yy, zz] == AIR:
                            world[xx, yy, zz] = LEAVES
    # flowers
    for _ in range(120):
        x, z = r.integers(2, W - 2), r.integers(2, D - 2)
        if math.hypot(x - CX, z - CZ) < 3:
            continue
        y = h[x, z] + 1
        if world[x, y, z] == AIR:
            world[x, y, z] = FLOWER_R if r.random() < 0.5 else FLOWER_Y
    return world, h

# ---------------------------------------------------------------- skins
def make_skin(shirt, pants, hair, skin=(0.80, 0.62, 0.48), logo=True, hood=False):
    s = np.zeros((64, 64, 3)); s[:] = skin
    def rect(x, y, w, hh, c): s[y:y + hh, x:x + w] = c
    # head (cube 8): top 8,0 bottom 16,0 right 0,8 front 8,8 left 16,8 back 24,8
    rect(8, 0, 8, 8, hair); rect(24, 8, 8, 8, hair)
    for fx in (0, 16):
        rect(fx, 8, 8, 3, hair)
    rect(8, 8, 8, 2, hair)
    rect(9, 12, 2, 1, (0.95, 0.95, 0.95)); rect(13, 12, 2, 1, (0.95, 0.95, 0.95))
    rect(10, 12, 1, 1, (0.25, 0.22, 0.45)); rect(13, 12, 1, 1, (0.25, 0.22, 0.45))
    rect(11, 14, 2, 1, tuple(c * 0.7 for c in skin))
    if hood:
        rect(8, 0, 8, 8, shirt); rect(24, 8, 8, 8, shirt); rect(0, 8, 8, 8, shirt); rect(16, 8, 8, 8, shirt)
        rect(8, 8, 8, 1, shirt); rect(8, 8, 1, 8, shirt); rect(15, 8, 1, 8, shirt)
    # body 16,16: top 20,16(8x4) front 20,20(8x12) right 16,20(4x12) left 28,20 back 32,20
    rect(16, 16, 24, 16, shirt)
    if logo:
        cx, cy = 23.5, 25
        for y in range(20, 32):
            for x in range(20, 28):
                d = math.hypot(x + 0.5 - cx - 0.5, y + 0.5 - cy)
                if d < 1.6: s[y, x] = (0.03, 0.03, 0.05)
                elif d < 2.6: s[y, x] = (0.92, 0.9, 1.0)
    # arms 40,16 (4x12 faces): sleeves + hands
    rect(40, 16, 16, 16, shirt)
    rect(40, 28, 16, 4, skin)
    # legs 0,16
    rect(0, 16, 16, 16, pants)
    rect(0, 29, 16, 3, (0.15, 0.15, 0.18))
    noise = np.random.default_rng(11).random((64, 64, 1)) * 0.06 - 0.03
    return np.clip(s + noise, 0, 1)

# part: name -> (size (w,h,d) in pixels, uv origin)
PARTS = {
    'head': ((8, 8, 8), (0, 0)),
    'body': ((8, 12, 4), (16, 16)),
    'arm': ((4, 12, 4), (40, 16)),
    'leg': ((4, 12, 4), (0, 16)),
}

def face_uv(part, face):
    (w, h, d), (u0, v0) = PARTS[part]
    # MC layout: top (u0+d, v0, w x d), bottom (u0+d+w, v0), right (u0, v0+d, d x h), front (u0+d, v0+d, w x h), left (u0+d+w, v0+d), back (u0+2d+w, v0+d)
    if face == 'top': return (u0 + d, v0, w, d)
    if face == 'bottom': return (u0 + d + w, v0, w, d)
    if face == 'right': return (u0, v0 + d, d, h)
    if face == 'front': return (u0 + d, v0 + d, w, h)
    if face == 'left': return (u0 + d + w, v0 + d, d, h)
    return (u0 + 2 * d + w, v0 + d, w, h)

def rot_y(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])

def rot_x(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])

def humanoid(pos, yaw, skin, arm_r=0.0, arm_l=0.0, leg=0.0, head_pitch=0.0):
    """Returns list of boxes. Model faces +z local; yaw rotates around y. Units: blocks (1px=1/16)."""
    px = 1 / 16
    R = rot_y(yaw)
    boxes = []
    def add(part, center, pivot, pitch):
        (w, h, d), _ = PARTS[part]
        boxes.append(dict(part=part, skin=skin, half=np.array([w, h, d]) * px / 2,
                          center=np.array(center) * px, pivot=np.array(pivot) * px, pitch=pitch,
                          R=R, pos=np.array(pos, dtype=float)))
    add('leg', (-2, 6, 0), (-2, 12, 0), leg)
    add('leg', (2, 6, 0), (2, 12, 0), -leg)
    add('body', (0, 18, 0), (0, 18, 0), 0)
    add('arm', (-6, 18, 0), (-6, 22, 0), arm_r)
    add('arm', (6, 18, 0), (6, 22, 0), arm_l)
    add('head', (0, 28, 0), (0, 24, 0), head_pitch)
    return boxes

# ---------------------------------------------------------------- camera / rays
class Camera:
    def __init__(self, pos, look, vfov, w, h):
        self.pos = np.array(pos, float)
        f = np.array(look, float) - self.pos; f /= np.linalg.norm(f)
        r = np.cross(f, [0, 1, 0]); r /= np.linalg.norm(r)
        u = np.cross(r, f)
        self.f, self.r, self.u = f, r, u
        self.w, self.h = w, h
        self.th = math.tan(math.radians(vfov) / 2)
        self.tw = self.th * w / h

    def rays(self):
        ys, xs = np.mgrid[0:self.h, 0:self.w]
        sx = ((xs + 0.5) / self.w * 2 - 1) * self.tw
        sy = (1 - (ys + 0.5) / self.h * 2) * self.th
        d = self.f[None, None] + sx[..., None] * self.r[None, None] + sy[..., None] * self.u[None, None]
        d /= np.linalg.norm(d, axis=-1, keepdims=True)
        return d.reshape(-1, 3)

    def project(self, p):
        v = np.array(p, float) - self.pos
        z = v @ self.f
        x = (v @ self.r) / z / self.tw
        y = (v @ self.u) / z / self.th
        return ((x + 1) / 2 * self.w, (1 - y) / 2 * self.h, z)

# ---------------------------------------------------------------- voxel DDA
def trace_voxels(world, o, d, max_steps=260, want_tex=True):
    n = d.shape[0]
    pos = np.broadcast_to(o, d.shape).astype(float) if o.ndim == 1 else o.astype(float)
    ix = np.floor(pos).astype(int)
    step = np.where(d >= 0, 1, -1)
    with np.errstate(divide='ignore', invalid='ignore'):
        inv = np.where(np.abs(d) > 1e-9, 1.0 / d, 1e30)
    nxt = ix + (step > 0)
    tmax = (nxt - pos) * inv
    tdelta = np.abs(inv)
    hit_t = np.full(n, np.inf); hit_axis = np.zeros(n, int); hit_block = np.zeros(n, np.uint8)
    active = np.arange(n)
    last_axis = np.zeros(n, int)
    t_cur = np.zeros(n)
    for it in range(max_steps):
        if active.size == 0:
            break
        a = active
        tm = tmax[a]
        axis = np.argmin(tm, axis=1)
        t_cur[a] = tm[np.arange(a.size), axis]
        ix[a, axis] += step[a, axis]
        tmax[a, axis] += tdelta[a, axis]
        last_axis[a] = axis
        x, y, z = ix[a, 0], ix[a, 1], ix[a, 2]
        inb = (x >= 0) & (x < W) & (y >= 0) & (y < H) & (z >= 0) & (z < D)
        out = (~inb) & ((y >= H) | (x < -1) | (x > W) | (z < -1) | (z > D) | (y < 0))
        b = np.zeros(a.size, np.uint8)
        b[inb] = world[x[inb], y[inb], z[inb]]
        solid = b > 0
        # alpha test for leaves / flowers
        if solid.any():
            for blk, am in ALPHA.items():
                m = solid & (b == blk)
                if m.any():
                    am_idx = a[m]
                    p = pos[am_idx] + d[am_idx] * t_cur[am_idx, None]
                    u, v = face_uv_coords(p, last_axis[am_idx], d[am_idx])
                    tx = np.clip((u * 16).astype(int), 0, 15); ty = np.clip((v * 16).astype(int), 0, 15)
                    keep = am[ty, tx] > 0.5
                    mm = np.where(m)[0]
                    solid[mm[~keep]] = False
        hs = solid
        hit_t[a[hs]] = t_cur[a[hs]]
        hit_axis[a[hs]] = last_axis[a[hs]]
        hit_block[a[hs]] = b[hs]
        active = a[~hs & ~out]
    return hit_t, hit_axis, hit_block

def face_uv_coords(p, axis, d):
    fx, fy, fz = p[:, 0] - np.floor(p[:, 0]), p[:, 1] - np.floor(p[:, 1]), p[:, 2] - np.floor(p[:, 2])
    u = np.where(axis == 0, fz, fx)
    v = np.where(axis == 1, fz, 1 - fy)
    # fix exact boundaries
    return np.clip(u, 0, 0.999), np.clip(v, 0, 0.999)

# ---------------------------------------------------------------- boxes (characters)
def trace_boxes(boxes, o, d):
    n = d.shape[0]
    o = np.broadcast_to(o, d.shape)
    best_t = np.full(n, np.inf); col = np.zeros((n, 3)); nrm = np.zeros((n, 3))
    for b in boxes:
        # world -> model space: pos + R @ (pivot + Rx(pitch) @ (local - pivot))
        R = b['R'] @ np.eye(3)
        Rp = rot_x(b['pitch'])
        M = R @ Rp  # rotation of box relative to world
        center_world = b['pos'] + R @ (b['pivot'] + Rp @ (b['center'] - b['pivot']))
        lo = (o - center_world) @ M  # into box local (M orthonormal -> inverse = transpose)
        ld = d @ M
        half = b['half']
        with np.errstate(divide='ignore', invalid='ignore'):
            inv = np.where(np.abs(ld) > 1e-9, 1 / ld, 1e30)
        t1 = (-half - lo) * inv; t2 = (half - lo) * inv
        tmin = np.minimum(t1, t2); tmax = np.maximum(t1, t2)
        tn = tmin.max(axis=1); tf = tmax.min(axis=1)
        ax = tmin.argmax(axis=1)
        hit = (tn <= tf) & (tf > 0) & (tn > 0) & (tn < best_t)
        if not hit.any():
            continue
        idx = np.where(hit)[0]
        p = lo[idx] + ld[idx] * tn[idx, None]
        a = ax[idx]
        sgn = -np.sign(ld[idx, a])  # outward normal sign
        w, h, dd = (half * 2 * 16)
        cols = np.zeros((idx.size, 3))
        skin = b['skin']
        for face in ('front', 'back', 'left', 'right', 'top', 'bottom'):
            if face == 'front': m = (a == 2) & (sgn > 0)
            elif face == 'back': m = (a == 2) & (sgn < 0)
            elif face == 'left': m = (a == 0) & (sgn > 0)
            elif face == 'right': m = (a == 0) & (sgn < 0)
            elif face == 'top': m = (a == 1) & (sgn > 0)
            else: m = (a == 1) & (sgn < 0)
            if not m.any(): continue
            uu0, vv0, fw, fh = face_uv(b['part'], face)
            pp = p[m]
            if face in ('front', 'back'):
                u = (pp[:, 0] + half[0]) / (2 * half[0]); v = (half[1] - pp[:, 1]) / (2 * half[1])
                if face == 'front': u = 1 - u
            elif face in ('left', 'right'):
                u = (pp[:, 2] + half[2]) / (2 * half[2]); v = (half[1] - pp[:, 1]) / (2 * half[1])
                if face == 'right': u = 1 - u
            else:
                u = (pp[:, 0] + half[0]) / (2 * half[0]); v = (pp[:, 2] + half[2]) / (2 * half[2])
            tx = np.clip((uu0 + u * fw).astype(int), 0, 63); ty = np.clip((vv0 + v * fh).astype(int), 0, 63)
            cols[m] = skin[ty, tx]
        nl = np.zeros((idx.size, 3)); nl[np.arange(idx.size), a] = sgn
        best_t[idx] = tn[idx]; col[idx] = cols; nrm[idx] = nl @ M.T
    return best_t, col, nrm

# ---------------------------------------------------------------- shading
def sky(d, night):
    t = np.clip(d[:, 1], -0.2, 1)
    if night:
        top = np.array([0.03, 0.03, 0.08]); hor = np.array([0.12, 0.10, 0.22])
    else:
        top = np.array([0.36, 0.56, 0.92]); hor = np.array([0.78, 0.86, 0.97])
    k = np.clip(t * 2.2, 0, 1)[:, None] ** 0.8
    return hor * (1 - k) + top * k

def render(world, cam, boxes, night=False, sun=(0.45, 0.8, 0.35), ss=2, fog=55.0):
    W2, H2 = cam.w, cam.h
    d = cam.rays()
    o = cam.pos
    n = d.shape[0]
    vt, vax, vb = trace_voxels(world, o, d)
    bt, bcol, bn = trace_boxes(boxes, o, d)
    sun = np.array(sun) / np.linalg.norm(sun)
    out = sky(d, night)
    if night:
        # stars + moon
        r = np.random.default_rng(3)
        stars = (r.random(n) > 0.9985) & (d[:, 1] > 0.05)
        out[stars] = 0.9
        md = d @ (np.array(sun) / np.linalg.norm(sun))
        out[md > 0.9992] = np.array([0.93, 0.93, 1.0])
        out += np.clip((md - 0.97) * 8, 0, 1)[:, None] * np.array([0.10, 0.10, 0.18])
    else:
        sd = d @ sun
        out += np.clip((sd - 0.995) * 300, 0, 1)[:, None] * np.array([1, 0.95, 0.8])
    # voxels
    use_box = bt < vt
    vm = np.isfinite(vt) & ~use_box
    if vm.any():
        i = np.where(vm)[0]
        p = o + d[i] * vt[i, None]
        ax = vax[i]
        u, v = face_uv_coords(p, ax, d[i])
        tx = np.clip((u * 16).astype(int), 0, 15); ty = np.clip((v * 16).astype(int), 0, 15)
        blk = vb[i]
        normal = np.zeros((i.size, 3)); normal[np.arange(i.size), ax] = -np.sign(d[i, ax])
        top = (ax == 1) & (normal[:, 1] > 0)
        bot = (ax == 1) & (normal[:, 1] < 0)
        c = np.zeros((i.size, 3))
        for blkid, (tt, ts, tb) in TEX.items():
            m = blk == blkid
            if not m.any(): continue
            mt = m & top; ms = m & ~top & ~bot; mb = m & bot
            c[mt] = tt[ty[mt], tx[mt]]; c[ms] = ts[ty[ms], tx[ms]]; c[mb] = tb[ty[mb], tx[mb]]
        # flowers: unlit-ish
        diff = np.clip(normal @ sun, 0, 1)
        # shadows
        so = p + normal * 0.01
        st, _, _ = trace_voxels(world, so, np.broadcast_to(sun, so.shape).copy(), max_steps=60)
        sbt, _, _ = trace_boxes(boxes, so, np.broadcast_to(sun, so.shape).copy())
        lit = (~np.isfinite(st)) & (~np.isfinite(sbt))
        amb = 0.42 if not night else 0.30
        sunk = 0.75 if not night else 0.30
        face_k = np.where(ax == 0, 0.92, np.where(ax == 2, 0.84, 1.0))
        light = (amb + sunk * diff * lit) * face_k
        col = c * light[:, None]
        if night:
            col = col * np.array([0.55, 0.6, 1.0])
        dist = vt[i]
        f = 1 - np.exp(-np.maximum(dist - 8, 0) / fog)
        col = col * (1 - f[:, None]) + sky(d[i], night) * f[:, None]
        out[i] = col
    if use_box.any():
        i = np.where(use_box)[0]
        nn = bn[i]
        diff = np.clip(nn @ sun, 0, 1)
        so = o + d[i] * bt[i, None] + nn * 0.01
        st, _, _ = trace_voxels(world, so, np.broadcast_to(sun, so.shape).copy(), max_steps=60)
        lit = ~np.isfinite(st)
        amb = 0.5 if not night else 0.34
        light = amb + (0.6 if not night else 0.2) * diff * lit
        col = bcol[i] * light[:, None]
        if night: col = col * np.array([0.6, 0.65, 1.0])
        out[i] = col
    img = np.clip(out.reshape(H2, W2, 3), 0, 1)
    # gamma-ish
    img = img ** 0.92
    im = Image.fromarray((img * 255).astype(np.uint8))
    return im

def ground_y(h, x, z):
    return h[int(math.floor(x)), int(math.floor(z))] + 1

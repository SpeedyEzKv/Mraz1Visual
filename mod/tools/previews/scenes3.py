"""Animated preview scenes: background PNG + character frame strip + anchors per frame."""
import sys, math, json, os, time
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from render3 import *

OUT = sys.argv[1]
ONLY = sys.argv[2].split(',') if len(sys.argv) > 2 else None
os.makedirs(OUT, exist_ok=True)
FW, FH, SS = 1056, 300, 2
NF = 16
anchors = json.load(open(f'{OUT}/scenes.json')) if os.path.exists(f'{OUT}/scenes.json') else {}

ME = load_skin(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'skins', os.environ.get('UMBRA_SKIN', 'player.png')))
FOE = make_skin((0.85, 0.42, 0.28), (0.70, 0.28, 0.52), (0.85, 0.40, 0.28))

def cam(pos, look, vfov):
    return Camera(pos, look, vfov, FW * SS, FH * SS)

def proj(c, p):
    x, y, z = c.project(p)
    return [round(x / SS, 1), round(y / SS, 1)]

def entity(c, pos, height=1.8):
    pos = np.array(pos, float)
    corners = [pos + [dx, dy, dz] for dy in (0, height) for dx in (-0.3, 0.3) for dz in (-0.3, 0.3)]
    ring = [pos + [math.cos(a), 0.02, math.sin(a)] for a in np.linspace(0, 2 * math.pi, 25)]
    headring = [pos + [0.55 * math.cos(a), height + 0.02, 0.55 * math.sin(a)] for a in np.linspace(0, 2 * math.pi, 25)]
    return dict(feet=proj(c, pos), chest=proj(c, pos + [0, 1.25, 0]), head=proj(c, pos + [0, height, 0]),
                tip=proj(c, pos + [0, height + 0.55, 0]), box=[proj(c, p) for p in corners],
                ring=[proj(c, p) for p in ring], headring=[proj(c, p) for p in headring])

def box_corners(b):
    Rm = b['R']; Rp = rot_x(b.get('pitch', 0.0)); M = Rm @ Rp
    cw = b['pos'] + Rm @ (b['pivot'] + Rp @ (b['center'] - b['pivot']))
    h = b['half']
    return [cw + M @ (np.array([sx, sy, sz]) * h) for sx in (-1, 1) for sy in (-1, 1) for sz in (-1, 1)]

def to_img(rgb, depth, mode, focus, sun_px=None):
    img = post(rgb, depth, mode, focus, sun_px)
    return Image.fromarray((img * 255).astype(np.uint8)).resize((FW, FH), Image.LANCZOS)

def shoot(name, world, c, static_boxes, frame_boxes, mode, sun, lights=(), focus=6.0, extra=None, shadow_pad=(40, 60), frame_world=None):
    """frame_boxes: list (per frame) of box lists (animated part); static_boxes always present.
    frame_world: per-frame list of (x, y, z, block) placed into the world for that frame."""
    t0 = time.time()
    n = len(frame_boxes)
    sv = np.array(sun, float); sv /= np.linalg.norm(sv)
    sx, sy, sz = c.project(c.pos + sv * 500)
    sun_px = (sx, sy) if sz > 0 else None
    # background without animated part
    rgb, dep = shade(world, c, static_boxes, mode, sun, lights)
    to_img(rgb, dep, mode, focus, sun_px).save(f'{OUT}/{name}.png', optimize=True)
    a = dict(w=FW, h=FH, frames=n)
    if n > 1 or frame_boxes[0]:
        xs, ys = [], []
        for bl in frame_boxes:
            for b in bl:
                for p in box_corners(b):
                    x, y, z = c.project(p)
                    if z > 0:
                        xs.append(x); ys.append(y)
        for fwl in (frame_world or []):
            for (bx, by, bz, _) in fwl:
                for dx in (0, 1):
                    for dy in (0, 1):
                        for dz in (0, 1):
                            x, y, z = c.project([bx + dx, by + dy, bz + dz])
                            if z > 0:
                                xs.append(x); ys.append(y)
        px, py = shadow_pad
        x0 = max(0, int(min(xs)) - px); x1 = min(c.w, int(max(xs)) + px)
        y0 = max(0, int(min(ys)) - 30); y1 = min(c.h, int(max(ys)) + py)
        x0 -= x0 % 2; y0 -= y0 % 2; x1 += x1 % 2; y1 += y1 % 2
        x1 = min(c.w, x1); y1 = min(c.h, y1)
        fw, fh = (x1 - x0) // SS, (y1 - y0) // SS
        sheet = Image.new('RGB', (fw, fh * n))
        for f, bl in enumerate(frame_boxes):
            wf = world
            if frame_world:
                wf = world.copy()
                for (bx, by, bz, blk) in frame_world[f]:
                    wf[bx, by, bz] = blk
            r2, d2 = shade(wf, c, static_boxes + bl, mode, sun, lights, region=(x0, y0, x1, y1))
            full = rgb.copy(); fd = dep.copy()
            full[y0:y1, x0:x1] = r2; fd[y0:y1, x0:x1] = d2
            im = to_img(full, fd, mode, focus, sun_px)
            sheet.paste(im.crop((x0 // SS, y0 // SS, x0 // SS + fw, y0 // SS + fh)), (0, f * fh))
        sheet.save(f'{OUT}/{name}_anim.png', optimize=True)
        a['anim'] = [x0 // SS, y0 // SS, fw, fh]
    if extra:
        a.update(extra(c))
    anchors[name] = a
    json.dump(anchors, open(f'{OUT}/scenes.json', 'w'))
    print(name, n, 'frames', round(time.time() - t0), 's', flush=True)

def want(n):
    return ONLY is None or n in ONLY

def ease(x):
    return 0.5 - 0.5 * math.cos(math.pi * max(0, min(1, x)))

# ---------------------------------------------------------------- castle duel (night)
if want('duel') or want('night'):
    castle, base, lights = build_castle()
    lant = []
    for L in lights: lant += lantern(L)
    lights_f = [L + [0, 0, -0.55] for L in lights]
    MOON = (-0.30, 0.62, 0.72)
    A = np.array([55.6, base, 58.5]); B0 = A + [1.45, 0, 0.05]
    c1 = cam(A + [-0.4, 2.1, -6.4], A + [1.0, 1.45, 3.0], 36)

if want('duel'):
    frames, info = [], []
    for f in range(NF):
        t = f / NF
        swing = math.sin(min(1, t / 0.30) * math.pi) if t < 0.30 else 0      # удар 0..0.30
        hit = 0.18 <= t < 0.42
        kb = (ease((t - 0.18) / 0.15) if t < 0.33 else 1 - ease((t - 0.33) / 0.6)) if t >= 0.18 else 0
        B = B0 + np.array([1, 0, 0.1]) * 0.35 * kb
        bob = 0.04 * math.sin(t * 2 * math.pi * 2)
        a_boxes = humanoid(A + [0, 0, 0], math.radians(84), ME, arm_r=-0.4 - 1.25 * swing, arm_l=0.15, leg=0.25 + 0.1 * swing)
        b_boxes = humanoid(B + [0, bob * 0.5, 0], math.radians(-96), FOE, arm_r=0.25 + 0.3 * kb, arm_l=-0.35 - 0.3 * kb,
                           leg=-0.22, head_pitch=-0.15 * kb)
        if hit:
            for b in b_boxes: b['tint'] = (1.30, 0.62, 0.62)
        frames.append(a_boxes + b_boxes)
        info.append(dict(player=entity(c1, A), target=entity(c1, B)))
    shoot('duel', castle, c1, lant, frames, 'night', MOON, lights_f, focus=6.5, shadow_pad=(170, 300),
          extra=lambda c: dict(per_frame=info, hit_frame=3))

if want('night'):
    c6 = cam(A + [0.2, 1.9, -5.6], A + [0.6, 1.3, 3.0], 38)
    P6 = A + [0.6, 0, 0.4]
    frames, info = [], []
    for f in range(NF):
        t = f / NF
        sway = math.sin(t * 2 * math.pi)
        frames.append(humanoid(P6, math.radians(190), ME, arm_r=0.08 * sway, arm_l=-0.08 * sway, head_pitch=0.05 * sway))
        info.append(dict(player=entity(c6, P6)))
    shoot('night', castle, c6, lant, frames, 'night', MOON, lights_f, focus=5.5, shadow_pad=(170, 300),
          extra=lambda c: dict(per_frame=info))

# ---------------------------------------------------------------- forest
if want('front') or want('back') or want('fp') or want('blocks'):
    forest, hmap = build_forest(1)
    gy = ground_y(hmap, CX, CZ)
    P = np.array([CX + 0.5, gy, CZ + 0.5])
    SUN = (-0.55, 0.62, -0.45)

if want('front'):
    c1 = cam(P + [-0.9, 2.2, -5.0], P + [0.7, 1.2, 0], 40)
    frames, info = [], []
    for f in range(NF):
        t = f / NF
        # прыжок: присед 0..0.1, полёт 0.1..0.55, приземление
        jump = math.sin((t - 0.1) / 0.45 * math.pi) * 1.05 if 0.1 <= t < 0.55 else 0
        crouch = 0.12 * math.sin(t / 0.1 * math.pi) if t < 0.1 else (0.12 * math.sin((t - 0.55) / 0.12 * math.pi) if 0.55 <= t < 0.67 else 0)
        pos = P + [0, jump - crouch * 0.4, 0]
        arms = 0.5 * jump
        frames.append(humanoid(pos, math.radians(195), ME, arm_r=0.12 - arms, arm_l=-0.12 - arms, leg=0.3 * jump))
        info.append(dict(player=entity(c1, P)))
    shoot('front', forest, c1, [], frames, 'day', SUN, focus=5.5,
          extra=lambda c: dict(per_frame=info, land_frame=9))

if want('back'):
    c2 = cam(P + [0.9, 2.3, -4.6], P + [-0.4, 1.3, 2.0], 40)
    frames, info = [], []
    for f in range(NF):
        t = f / NF
        s = math.sin(t * 2 * math.pi * 2)
        frames.append(humanoid(P + [0, abs(s) * 0.05, 0], math.radians(10), ME, arm_r=0.6 * s, arm_l=-0.6 * s, leg=0.55 * s))
        info.append(dict(player=entity(c2, P)))
    def back_extra(c):
        sh = [P + [0.36, 1.45, -0.18], P + [-0.36, 1.45, -0.18]]
        b1 = [P + [0.42, 0.35, -0.30], P + [-0.42, 0.35, -0.30]]
        b2 = [P + [0.46, 0.55, -0.70], P + [-0.46, 0.55, -0.70]]
        return dict(per_frame=info, cape_top=[proj(c, p) for p in sh], cape_bot_a=[proj(c, p) for p in b1], cape_bot_b=[proj(c, p) for p in b2])
    shoot('back', forest, c2, [], frames, 'dusk', (0.25, 0.16, 0.95), focus=5.0, extra=back_extra)

def fp_arm(c, swing):
    Rm = np.column_stack([c.r, c.u, c.f])
    sh = c.pos + c.r * 0.75 - c.u * 0.50 + c.f * 0.35
    out = []
    (inner, outer, size) = UV['rarm']
    for origin, grow in ((inner, 0.0), (outer, 0.25)):
        out.append(dict(kind='skin', part='rarm', origin=origin, size=size, skin=ME, half=(np.array(size) / 2 + grow) / 16,
                        center=np.array([0, -6, 0]) / 16, pivot=np.zeros(3), pitch=-2.0 + swing, R=Rm, pos=sh, tint=None))
    return out

def swing_curve(t, start, length=0.3):
    x = (t - start) / length
    return math.sin(x * math.pi) * 0.55 if 0 <= x < 1 else 0

if want('fp'):
    eye = P + [0, 1.62, -4]
    c4 = cam(eye, eye + [0.25, -0.05, 1], 42)
    frames = [fp_arm(c4, swing_curve(f / NF, 0.0)) for f in range(NF)]
    def fp_extra(c):
        g = np.array([0, -0.03, 0]); v0 = c.f + np.array([0, 0.25, 0]) + c.r * 0.18; v = v0 / np.linalg.norm(v0) * 1.2
        p = c.pos + c.r * 0.30 - c.u * 0.22 + c.f * 0.6; path = []
        for i in range(400):
            if i % 2 == 0: path.append(p.copy())
            p = p + v * 0.25; v = v * (0.99 ** 0.25) + g * 0.25
            if p[1] < ground_y(hmap, p[0], p[2]): break
        path.append(p.copy())
        land = path[-1]
        ring = [land + [0.7 * math.cos(a), 0.05, 0.7 * math.sin(a)] for a in np.linspace(0, 2 * math.pi, 25)]
        far = P + [-5, 0, 22]; far[1] = ground_y(hmap, far[0], far[2])
        return dict(path=[proj(c, q) for q in path], land=[proj(c, q) for q in ring], waypoint=[proj(c, far), proj(c, far + [0, 30, 0])])
    shoot('fp', forest, c4, [], frames, 'day', SUN, focus=8.0, extra=fp_extra, shadow_pad=(10, 10))

if want('blocks'):
    eye6 = P + [0, 1.62, -3.2]
    row = []
    for i in range(5):
        bx, bz = int(P[0]) - 2 + i, int(P[2]) + 1
        row.append((bx, hmap[bx, bz] + 1, bz))
    c6 = cam(eye6, np.array([P[0] + 0.5, row[0][1] + 0.3, P[2] + 1.5]), 44)
    frames, fworld = [], []
    for f in range(NF):
        t = f / NF
        k = min(5, int(t * 6.5) + 1)
        sw = swing_curve((t * 6.5) % 1, 0.0, 0.6)
        frames.append(fp_arm(c6, sw))
        fworld.append([(b[0], b[1], b[2], AMETHYST) for b in row[:k]])
    def blocks_extra(c):
        allb = [[proj(c, [b[0] + dx, b[1] + dy, b[2] + dz]) for dy in (0, 1) for dx in (0, 1) for dz in (0, 1)] for b in row]
        return dict(blocks=allb, place_rate=6.5)
    shoot('blocks', forest, c6, [], frames, 'day', SUN, focus=5.0, extra=blocks_extra, frame_world=fworld)

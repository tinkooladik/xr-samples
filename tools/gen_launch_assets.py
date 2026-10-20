"""Builds the Android XR launch-scene assets from Tim's visionOS sources.

Inputs (from dtv-rbtv-apple branch tbarrett/DPSSTPL-9075-spatial-launch):
  RBTV_Launch_Logo_Paths.json  final-frame vector outlines, 900x397 px, y down
  RBTV_Launch_Sun_NN.png       61 swoosh frames painted on the 900x397 canvas

Outputs:
  models/launch_<part>.glb     extruded, mesh-centred on the logo plane (z in [-d/2, d/2])
  textures/sun_NN.png          unique swoosh frames unwrapped onto a 512x256 equirect map
  stdout                       the frame -> texture index table to paste into Kotlin
"""
import json
import math
import struct
import sys
from pathlib import Path

import mapbox_earcut as earcut
import numpy as np
from PIL import Image

LOGO_WIDTH = 0.6  # metres; Tim's depths below were tuned for a logo about this wide
SEGMENTS_PER_SPAN = 10
SUN_ART_CENTER = (475.0, 198.5)
SUN_ART_RADIUS = 100.0
SUN_MAP_SIZE = 512


def srgb_to_linear(c):
    return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4


def linear_rgba(rgb):
    return [srgb_to_linear(c) for c in rgb[:3]] + [1.0]


# --- outlines -------------------------------------------------------------

def flatten(bezier, to_point):
    """One closed ring as a list of (x, y) in metres."""
    v, i, o = bezier["v"], bezier["i"], bezier["o"]
    n = len(v)
    pts = []
    spans = n if bezier["closed"] else n - 1
    for s in range(spans):
        a, b = v[s], v[(s + 1) % n]
        c1 = (a[0] + o[s][0], a[1] + o[s][1])
        c2 = (b[0] + i[(s + 1) % n][0], b[1] + i[(s + 1) % n][1])
        for k in range(SEGMENTS_PER_SPAN):
            t = k / SEGMENTS_PER_SPAN
            u = 1 - t
            x = u ** 3 * a[0] + 3 * u * u * t * c1[0] + 3 * u * t * t * c2[0] + t ** 3 * b[0]
            y = u ** 3 * a[1] + 3 * u * u * t * c1[1] + 3 * u * t * t * c2[1] + t ** 3 * b[1]
            pts.append(to_point(x, y))
    return dedupe(pts)


def dedupe(pts, eps=1e-6):
    out = []
    for p in pts:
        if not out or abs(p[0] - out[-1][0]) > eps or abs(p[1] - out[-1][1]) > eps:
            out.append(p)
    if len(out) > 1 and abs(out[0][0] - out[-1][0]) < eps and abs(out[0][1] - out[-1][1]) < eps:
        out.pop()
    return out


def signed_area(ring):
    return 0.5 * sum(ring[k][0] * ring[(k + 1) % len(ring)][1] - ring[(k + 1) % len(ring)][0] * ring[k][1]
                     for k in range(len(ring)))


def contains(ring, p):
    inside = False
    n = len(ring)
    for k in range(n):
        (x1, y1), (x2, y2) = ring[k], ring[(k + 1) % n]
        if (y1 > p[1]) != (y2 > p[1]):
            x = x1 + (p[1] - y1) * (x2 - x1) / (y2 - y1)
            if x > p[0]:
                inside = not inside
    return inside


def group_rings(rings):
    """Splits rings into (outer CCW, [holes CW]) by nesting parity."""
    depth = [sum(1 for other in rings if other is not r and contains(other, r[0])) for r in rings]
    outers = [k for k, d in enumerate(depth) if d % 2 == 0]
    polygons = []
    for k in outers:
        outer = rings[k] if signed_area(rings[k]) > 0 else rings[k][::-1]
        holes = []
        for j, d in enumerate(depth):
            if d % 2 == 1 and contains(rings[k], rings[j][0]) and depth[j] == depth[k] + 1:
                holes.append(rings[j] if signed_area(rings[j]) < 0 else rings[j][::-1])
        polygons.append((outer, holes))
    return polygons


# --- meshes ---------------------------------------------------------------

class MeshBuilder:
    def __init__(self):
        self.prims = {}  # material index -> dict(pos, nrm, uv, idx)

    def prim(self, material):
        return self.prims.setdefault(material, {"pos": [], "nrm": [], "uv": [], "idx": []})

    def tri(self, material, a, b, c, n, uvs=None):
        p = self.prim(material)
        base = len(p["pos"])
        p["pos"] += [a, b, c]
        p["nrm"] += [n, n, n]
        p["uv"] += uvs or [(0, 0)] * 3
        p["idx"] += [base, base + 1, base + 2]

    def quad(self, material, a, b, c, d, n):
        self.tri(material, a, b, c, n)
        self.tri(material, a, c, d, n)


def cap(mb, material, polygons, z, flip):
    for outer, holes in polygons:
        verts = outer + [p for h in holes for p in h]
        ring_ends = []
        count = len(outer)
        ring_ends.append(count)
        for h in holes:
            count += len(h)
            ring_ends.append(count)
        idx = earcut.triangulate_float64(np.array(verts, dtype=np.float64), np.array(ring_ends, dtype=np.uint32))
        for t in range(0, len(idx), 3):
            a, b, c = (verts[idx[t]], verts[idx[t + 1]], verts[idx[t + 2]])
            area = (b[0] - a[0]) * (c[1] - a[1]) - (c[0] - a[0]) * (b[1] - a[1])
            if (area < 0) != flip:
                b, c = c, b
            n = (0, 0, -1 if flip else 1)
            mb.tri(material, (a[0], a[1], z), (b[0], b[1], z), (c[0], c[1], z), n)


def walls(mb, material, rings, z_top, z_bottom):
    for ring in rings:
        n = len(ring)
        for k in range(n):
            p, q = ring[k], ring[(k + 1) % n]
            dx, dy = q[0] - p[0], q[1] - p[1]
            length = math.hypot(dx, dy) or 1
            nrm = (dy / length, -dx / length, 0)
            mb.quad(material, (p[0], p[1], z_top), (p[0], p[1], z_bottom), (q[0], q[1], z_bottom), (q[0], q[1], z_top), nrm)


def extrude(polygons, depth, face_material, side_material):
    mb = MeshBuilder()
    h = depth / 2
    cap(mb, face_material, polygons, h, flip=False)
    cap(mb, face_material, polygons, -h, flip=True)
    walls(mb, side_material, [r for outer, holes in polygons for r in [outer] + holes], h, -h)
    return mb


def inset_convex(ring, c):
    """Offsets a convex CCW ring inward by c (line intersection of shifted edges)."""
    n = len(ring)
    lines = []
    for k in range(n):
        p, q = ring[k], ring[(k + 1) % n]
        dx, dy = q[0] - p[0], q[1] - p[1]
        length = math.hypot(dx, dy)
        nx, ny = dy / length, -dx / length  # outward
        lines.append(((p[0] - nx * c, p[1] - ny * c), (dx, dy)))
    out = []
    for k in range(n):
        (p1, d1), (p2, d2) = lines[k - 1], lines[k]
        det = d1[0] * d2[1] - d1[1] * d2[0]
        t = ((p2[0] - p1[0]) * d2[1] - (p2[1] - p1[1]) * d2[0]) / det
        out.append((p1[0] + d1[0] * t, p1[1] + d1[1] * t))
    return out


def extrude_beveled(ring, depth, chamfer, face_material, side_material, bevel_material):
    """Convex ring only: flat faces, 45-degree chamfer on both edges, straight sides."""
    mb = MeshBuilder()
    h = depth / 2
    inner = inset_convex(ring, chamfer)
    cap(mb, face_material, [(inner, [])], h, flip=False)
    cap(mb, face_material, [(inner, [])], -h, flip=True)
    walls(mb, side_material, [ring], h - chamfer, -h + chamfer)
    n = len(ring)
    for k in range(n):
        p, q, pi, qi = ring[k], ring[(k + 1) % n], inner[k], inner[(k + 1) % n]
        dx, dy = q[0] - p[0], q[1] - p[1]
        length = math.hypot(dx, dy)
        nx, ny = dy / length, -dx / length
        s = math.sqrt(0.5)
        mb.quad(bevel_material, (pi[0], pi[1], h), (p[0], p[1], h - chamfer), (q[0], q[1], h - chamfer), (qi[0], qi[1], h), (nx * s, ny * s, s))
        mb.quad(bevel_material, (p[0], p[1], -h + chamfer), (pi[0], pi[1], -h), (qi[0], qi[1], -h), (q[0], q[1], -h + chamfer), (nx * s, ny * s, -s))
    return mb


def uv_sphere(radius, material, stacks=48, slices=96):
    mb = MeshBuilder()
    p = mb.prim(material)
    for stack in range(stacks + 1):
        lat = math.pi / 2 - math.pi * stack / stacks
        for slice_ in range(slices + 1):
            lon = 2 * math.pi * slice_ / slices - math.pi
            d = (math.cos(lat) * math.sin(lon), math.sin(lat), math.cos(lat) * math.cos(lon))
            p["pos"].append(tuple(c * radius for c in d))
            p["nrm"].append(d)
            p["uv"].append((slice_ / slices, stack / stacks))
    stride = slices + 1
    for stack in range(stacks):
        for slice_ in range(slices):
            tl = stack * stride + slice_
            bl = (stack + 1) * stride + slice_
            p["idx"] += [tl, bl, tl + 1, tl + 1, bl, bl + 1]
    return mb


# --- glb ------------------------------------------------------------------

def write_glb(path, name, mb, materials, image_png=None):
    blob = bytearray()
    views, accessors = [], []

    def view(data, target=None):
        while len(blob) % 4:
            blob.append(0)
        views.append({"buffer": 0, "byteOffset": len(blob), "byteLength": len(data), **({"target": target} if target else {})})
        blob.extend(data)
        return len(views) - 1

    def accessor(data, comp_type, type_, count, target, minmax=None):
        acc = {"bufferView": view(data, target), "componentType": comp_type, "count": count, "type": type_}
        if minmax:
            acc["min"], acc["max"] = minmax
        accessors.append(acc)
        return len(accessors) - 1

    primitives = []
    for material, p in sorted(mb.prims.items()):
        pos = np.array(p["pos"], dtype=np.float32)
        attrs = {
            "POSITION": accessor(pos.tobytes(), 5126, "VEC3", len(pos), 34962, (pos.min(0).tolist(), pos.max(0).tolist())),
            "NORMAL": accessor(np.array(p["nrm"], dtype=np.float32).tobytes(), 5126, "VEC3", len(pos), 34962),
        }
        if image_png is not None:
            attrs["TEXCOORD_0"] = accessor(np.array(p["uv"], dtype=np.float32).tobytes(), 5126, "VEC2", len(pos), 34962)
        idx = np.array(p["idx"], dtype=np.uint32)
        primitives.append({"attributes": attrs, "indices": accessor(idx.tobytes(), 5125, "SCALAR", len(idx), 34963), "material": material})

    gltf = {
        "asset": {"version": "2.0", "generator": "gen_launch_assets.py"},
        "extensionsUsed": ["KHR_materials_unlit"],
        "scene": 0,
        "scenes": [{"nodes": [0]}],
        "nodes": [{"name": name, "mesh": 0}],
        "meshes": [{"name": name, "primitives": primitives}],
        "materials": materials,
        "accessors": accessors,
        "bufferViews": views,
    }
    if image_png is not None:
        gltf["images"] = [{"bufferView": view(image_png), "mimeType": "image/png"}]
        gltf["samplers"] = [{"magFilter": 9729, "minFilter": 9729, "wrapS": 33071, "wrapT": 33071}]
        gltf["textures"] = [{"source": 0, "sampler": 0}]
    gltf["buffers"] = [{"byteLength": len(blob)}]

    json_bytes = json.dumps(gltf, separators=(",", ":")).encode()
    json_bytes += b" " * (-len(json_bytes) % 4)
    while len(blob) % 4:
        blob.append(0)
    out = bytearray(struct.pack("<III", 0x46546C67, 2, 12 + 8 + len(json_bytes) + 8 + len(blob)))
    out += struct.pack("<II", len(json_bytes), 0x4E4F534A) + json_bytes
    out += struct.pack("<II", len(blob), 0x004E4942) + blob
    path.write_bytes(out)
    tris = sum(len(p["idx"]) // 3 for p in mb.prims.values())
    print(f"{path.name}: {tris} tris, {len(out)} bytes")


def unlit(name, rgb):
    return {"name": name, "pbrMetallicRoughness": {"baseColorFactor": linear_rgba(rgb), "metallicFactor": 0, "roughnessFactor": 1},
            "extensions": {"KHR_materials_unlit": {}}}


def glossy(name, rgb, roughness, self_lit=0.0):
    m = {"name": name, "pbrMetallicRoughness": {"baseColorFactor": linear_rgba(rgb), "metallicFactor": 0, "roughnessFactor": roughness}}
    if self_lit:
        m["emissiveFactor"] = [min(1.0, srgb_to_linear(c) * self_lit) for c in rgb[:3]]
    return m


# --- sun ------------------------------------------------------------------

def unwrap_table():
    w, h = SUN_MAP_SIZE, SUN_MAP_SIZE // 2
    rows = (np.arange(h) + 0.5) / h
    cols = (np.arange(w) + 0.5) / w
    lat = math.pi / 2 - math.pi * rows[:, None]
    lon = 2 * math.pi * cols[None, :] - math.pi
    east = np.cos(lat) * np.sin(lon)
    north = np.sin(lat) * np.ones_like(lon)
    toward = np.cos(lat) * np.cos(lon)
    across = np.rint(SUN_ART_CENTER[0] + east * SUN_ART_RADIUS).astype(int)
    down = np.rint(SUN_ART_CENTER[1] - north * SUN_ART_RADIUS).astype(int)
    return toward >= 0, across, down


def unwrap(art_rgba, table):
    front, across, down = table
    ah, aw = art_rgba.shape[:2]
    ok = front & (across >= 0) & (across < aw) & (down >= 0) & (down < ah)
    out = np.zeros((SUN_MAP_SIZE // 2, SUN_MAP_SIZE, 4), dtype=np.uint8)
    out[ok] = art_rgba[down[ok], across[ok]]
    return out


def main(assets_dir, out_dir):
    assets_dir, out_dir = Path(assets_dir), Path(out_dir)
    models, textures = out_dir / "models", out_dir / "textures"
    models.mkdir(parents=True, exist_ok=True)
    textures.mkdir(parents=True, exist_ok=True)

    data = json.loads((assets_dir / "RBTV_Launch_Logo_Paths.json").read_text())
    space_w, space_h = data["space"]["w"], data["space"]["h"]
    mpp = LOGO_WIDTH / space_w
    logo_height = LOGO_WIDTH * space_h / space_w

    def to_point(x, y):
        return ((x - space_w / 2) * mpp, (space_h / 2 - y) * mpp)

    def rings_for(pred):
        return [flatten(b, to_point) for e in data["elements"] if pred(e["name"]) for b in e["paths"]]

    def color_for(pred):
        return next(e["color"] for e in data["elements"] if pred(e["name"]))

    is_outline = lambda n: n.startswith("bull/Group 7")
    is_body = lambda n: n.startswith("bull/") and not is_outline(n)
    is_letters = lambda n: n.startswith("tv/")

    white, red = color_for(is_outline), color_for(is_body)
    write_glb(models / "launch_silhouette.glb", "silhouette",
              extrude(group_rings(rings_for(is_outline)), 0.015, 0, 1),
              [unlit("face", white), glossy("side", white, 0.45, 0.2)])
    write_glb(models / "launch_bull.glb", "bull",
              extrude(group_rings(rings_for(is_body)), 0.028, 0, 1),
              [unlit("face", red), glossy("side", red, 0.26, 0.22)])
    write_glb(models / "launch_letters.glb", "letters",
              extrude(group_rings(rings_for(is_letters)), 0.014, 0, 1),
              [unlit("face", [1, 1, 1]), glossy("side", [1, 1, 1], 0.3, 1.0)])

    # Tim's rhombus, authored in a 468 px tall space, y up.
    s = logo_height / 468
    rhombus = [(98 * s, 170 * s), (52 * s, -170 * s), (-97 * s, -170 * s), (-51 * s, 170 * s)]
    if signed_area(rhombus) < 0:
        rhombus = rhombus[::-1]
    slab_red, slab_orange, slab_yellow = [0.86, 0.04, 0.25], [0.95, 0.58, 0.04], [0.98, 0.84, 0.04]
    write_glb(models / "launch_slab.glb", "slab",
              extrude_beveled(rhombus, 0.045, 0.005, 0, 1, 2),
              [glossy("front", slab_red, 0.3, 0.3), glossy("side", slab_orange, 0.3), glossy("bevel", slab_yellow, 0.3)])

    table = unwrap_table()
    frames = sorted(assets_dir.glob("RBTV_Launch_Sun_*.png"))
    unique, mapping, seen = [], [], {}
    for frame in frames:
        art = np.array(Image.open(frame).convert("RGBA"))
        if not art[..., 3].any():
            mapping.append(-1)
            continue
        png = unwrap(art, table)
        key = png.tobytes()
        if key not in seen:
            seen[key] = len(unique)
            unique.append(png)
        mapping.append(seen[key])
    for k, png in enumerate(unique):
        Image.fromarray(png, "RGBA").save(textures / f"sun_{k:02d}.png", optimize=True)
    print(f"sun: {len(frames)} frames, {len(unique)} unique textures")
    print("sunFrameTexture = intArrayOf(" + ", ".join(map(str, mapping)) + ")")

    last = Image.open(textures / f"sun_{len(unique) - 1:02d}.png")
    import io
    buf = io.BytesIO()
    last.save(buf, format="PNG")
    sun_material = {"name": "swoosh", "pbrMetallicRoughness": {"baseColorFactor": [1, 1, 1, 1], "baseColorTexture": {"index": 0}, "metallicFactor": 0, "roughnessFactor": 1},
                    "alphaMode": "BLEND", "doubleSided": False, "extensions": {"KHR_materials_unlit": {}}}
    write_glb(models / "launch_sun.glb", "sun", uv_sphere(LOGO_WIDTH * SUN_ART_RADIUS / space_w, 0), [sun_material], buf.getvalue())


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])

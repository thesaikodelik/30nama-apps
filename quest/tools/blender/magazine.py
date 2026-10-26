# The 30nama magazine as a 3D object: closed (for the pile and the row) and open (for reading),
# exported as glTF for the Quest app, plus preview renders. Run:
#   blender --background --factory-startup --python quest/tools/blender/magazine.py -- <out dir> [cover.png page-a.png page-b.png]
# Sizes are metres and match Layout in Stage.kt. Blender is Z-up; glTF export turns that into
# Y-up, so the cover faces +Y in the app.
import bpy, bmesh, math, sys, os
from mathutils import Vector

args = sys.argv[sys.argv.index("--") + 1:]
OUT = args[0]
SAMPLES = args[1:4] if len(args) >= 4 else []
os.makedirs(OUT, exist_ok=True)

# Closed magazine (Layout.COVER_W/H) and an open spread's page (Layout.PAGE_W/H).
CW, CH, CT = 0.21, 0.28, 0.009      # closed: width, height, thickness (a thick monthly)
PW, PH, PT = 0.34, 0.45, 0.007      # open: one half's page, and its stack of pages
GUTTER = 0.022                      # how far from the spine the pages curve down into it
COVER = 0.0004                      # cover board
OVERHANG = 0.0015                   # the cover sticks out past the pages


def reset():
    bpy.ops.wm.read_factory_settings(use_empty=True)


def paper_edge_image():
    """Stacked sheets seen edge-on: fine light lines with a little variation."""
    w, h = 64, 512
    img = bpy.data.images.new("paper_edge", w, h)
    px = []
    import random
    random.seed(7)
    for y in range(h):
        v = 0.86 + 0.08 * random.random() if y % 3 else 0.72 + 0.06 * random.random()
        for _ in range(w):
            px += [v, v * 0.985, v * 0.955, 1.0]
    img.pixels = px
    img.pack()
    return img


def material(name, color, rough, image=None, gloss=False):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    b = m.node_tree.nodes["Principled BSDF"]
    b.inputs["Base Color"].default_value = (*color, 1)
    b.inputs["Roughness"].default_value = rough
    if gloss:
        b.inputs["Coat Weight"].default_value = 0.6
        b.inputs["Coat Roughness"].default_value = 0.15
    if image is not None:
        t = m.node_tree.nodes.new("ShaderNodeTexImage")
        t.image = image
        m.node_tree.links.new(t.outputs["Color"], b.inputs["Base Color"])
    return m


def box(name, x0, y0, z0, x1, y1, z1, mat, edge_uv=False):
    """An axis-aligned box; with edge_uv its sides map the paper-edge lines across the thickness."""
    me = bpy.data.meshes.new(name)
    bm = bmesh.new()
    v = [bm.verts.new(p) for p in [(x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0), (x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)]]
    faces = [(0, 3, 2, 1), (4, 5, 6, 7), (0, 1, 5, 4), (1, 2, 6, 5), (2, 3, 7, 6), (3, 0, 4, 7)]
    uv = bm.loops.layers.uv.new()
    for f in faces:
        face = bm.faces.new([v[i] for i in f])
        for loop in face.loops:
            co = loop.vert.co
            if edge_uv:
                # u along the edge, v across the stack: the lines run along the edge.
                along = co.x if abs(face.normal.y) > 0.5 else co.y
                loop[uv].uv = (along * 4, (co.z - z0) / max(z1 - z0, 1e-6))
            else:
                loop[uv].uv = ((co.x - x0) / (x1 - x0), (co.y - y0) / (y1 - y0))
    bm.to_mesh(me)
    ob = bpy.data.objects.new(name, me)
    bpy.context.scene.collection.objects.link(ob)
    ob.data.materials.append(mat)
    return ob


def spine(name, length, radius, x, z, mat, side):
    """The rounded spine: half a cylinder along the magazine's height."""
    bpy.ops.mesh.primitive_cylinder_add(vertices=24, radius=radius, depth=length, location=(x, 0, z), rotation=(math.pi / 2, 0, 0))
    ob = bpy.context.object
    ob.name = name
    # The inner half hides inside the pages; only the rounded outer half shows.
    bpy.ops.object.transform_apply(location=False, rotation=True, scale=False)
    ob.data.materials.append(mat)
    return ob


def join(objs, name):
    bpy.ops.object.select_all(action="DESELECT")
    for o in objs:
        o.select_set(True)
    bpy.context.view_layer.objects.active = objs[0]
    bpy.ops.object.join()
    ob = bpy.context.object
    ob.name = name
    bpy.ops.object.shade_smooth()
    return ob


def closed(edge_img, cover_mat, side):
    """A closed magazine lying cover up, its centre at the origin. side=-1: spine on the left (LTR)."""
    paper = material("paper_edge", (1, 1, 1), 0.85, edge_img)
    back = material("back_cover", (0.93, 0.92, 0.9), 0.35, gloss=True)
    hw, hh = CW / 2, CH / 2
    sx = side * hw  # the spine edge
    pages = box("pages", -hw + OVERHANG if side > 0 else -hw + 0.001, -hh + OVERHANG, COVER, hw - 0.001 if side > 0 else hw - OVERHANG, hh - OVERHANG, CT - COVER, paper, edge_uv=True)
    bottom = box("back", -hw, -hh, 0, hw, hh, COVER, back)
    top = box("front", -hw, -hh, CT - COVER, hw, hh, CT, cover_mat)
    sp = spine("spine", CH, CT / 2, sx, CT / 2, back, side)
    ob = join([pages, bottom, top, sp], "magazine_closed")
    ob.location.z = -CT / 2  # centred on its thickness
    bpy.ops.object.transform_apply(location=True)
    return ob


def open_half(edge_img, side, page_mat):
    """One half of an open magazine: a page stack whose top curves down into the spine at x=0."""
    paper = material("paper_edge_o", (1, 1, 1), 0.85, edge_img)
    back = material("cover_out", (0.93, 0.92, 0.9), 0.35, gloss=True)
    me = bpy.data.meshes.new("half")
    bm = bmesh.new()
    uv = bm.loops.layers.uv.new()
    n = 24
    xs = [side * PW * (i / n) ** 1.0 for i in range(n + 1)]

    def top(x):
        d = abs(x)
        # Flat page, dipping smoothly into the gutter over its last GUTTER metres.
        return PT + COVER - (PT * 0.95) * (max(0.0, 1 - d / GUTTER) ** 2)

    hh = PH / 2
    tv, bv = [], []
    for x in xs:
        tv.append((bm.verts.new((x, -hh, top(x))), bm.verts.new((x, hh, top(x)))))
        bv.append((bm.verts.new((x, -hh, 0)), bm.verts.new((x, hh, 0))))
    for i in range(n):
        a, b = (i, i + 1) if side > 0 else (i + 1, i)
        f = bm.faces.new([tv[a][0], tv[b][0], tv[b][1], tv[a][1]])
        for l in f.loops:
            # u runs left to right across the half, whichever side of the spine it is on.
            l[uv].uv = ((l.vert.co.x - (-PW if side < 0 else 0)) / PW, (l.vert.co.y + hh) / PH)
        f.material_index = 0
        g = bm.faces.new([bv[a][1], bv[b][1], bv[b][0], bv[a][0]])
        g.material_index = 2
        # The two long sides show the stack's edge.
        for (y, k) in ((0, 0), (1, 1)):
            q = [tv[a][k], tv[b][k], bv[b][k], bv[a][k]] if k == 0 else [tv[b][k], tv[a][k], bv[a][k], bv[b][k]]
            e = bm.faces.new(q)
            e.material_index = 1
            for l in e.loops:
                l[uv].uv = (abs(l.vert.co.x) * 4, l.vert.co.z / (PT + COVER))
    # Fore edge.
    o = tv[n]
    ob_ = bv[n]
    q = [o[0], o[1], ob_[1], ob_[0]] if side > 0 else [o[1], o[0], ob_[0], ob_[1]]
    e = bm.faces.new(q)
    e.material_index = 1
    for l in e.loops:
        l[uv].uv = ((l.vert.co.y + hh) * 4, l.vert.co.z / (PT + COVER))
    bm.normal_update()
    bm.to_mesh(me)
    ob = bpy.data.objects.new("half", me)
    bpy.context.scene.collection.objects.link(ob)
    for m in (page_mat, paper, back):
        ob.data.materials.append(m)
    return ob


def export(objs, path):
    bpy.ops.object.select_all(action="DESELECT")
    for o in objs:
        o.select_set(True)
    bpy.ops.export_scene.gltf(filepath=path, use_selection=True, export_format="GLB", export_apply=True)


def image_or_none(path):
    return bpy.data.images.load(path) if path and os.path.exists(path) else None


# ---- assets for the app: no page content, the app draws it on top ----
for side, tag in ((-1, "ltr"), (1, "rtl")):
    reset()
    edge = paper_edge_image()
    ob = closed(edge, material("cover_face", (0.95, 0.95, 0.95), 0.25, gloss=True), side)
    export([ob], f"{OUT}/magazine_closed_{tag}.glb")
reset()
edge = paper_edge_image()
blank = material("page_face", (0.957, 0.941, 0.91), 0.9)
halves = [open_half(edge, s, blank) for s in (-1, 1)]
export(halves, f"{OUT}/magazine_open.glb")

# ---- a preview render: the pile on a table, and the spread in front of the eye ----
reset()
edge = paper_edge_image()
cover_img, page_a, page_b = (image_or_none(p) for p in (SAMPLES + [None, None, None])[:3])
scn = bpy.context.scene
scn.render.engine = "BLENDER_EEVEE"
scn.render.resolution_x, scn.render.resolution_y = 1600, 1000
scn.world = bpy.data.worlds.new("w")
scn.world.use_nodes = True
scn.world.node_tree.nodes["Background"].inputs["Color"].default_value = (0.05, 0.045, 0.045, 1)
scn.view_settings.view_transform = "AgX"

wood = material("wood", (0.32, 0.2, 0.12), 0.45)
bpy.ops.mesh.primitive_plane_add(size=1, location=(0, 0.1, -0.004))
tbl = bpy.context.object
tbl.scale = (1.2, 0.8, 1)
tbl.data.materials.append(wood)

# The pile: six covers, each a little off the one under it.
offs = [(0, 0, -3), (0.012, -0.008, 5), (-0.01, 0.01, -7), (0.016, 0.006, 9), (-0.006, -0.012, 2), (0.008, 0.014, -10)]
tints = [(0.55, 0.25, 0.2), (0.2, 0.35, 0.5), (0.45, 0.42, 0.18), (0.38, 0.2, 0.5), (0.18, 0.45, 0.35), (0.5, 0.2, 0.32)]
for i, (dx, dy, rot) in enumerate(offs):
    cm = material(f"c{i}", tints[i], 0.25, cover_img if i == 0 else None, gloss=True)
    ob = closed(edge, cm, 1)
    ob.location = (0.25 + dx, 0.12 + dy, CT / 2 + (len(offs) - i) * CT)
    ob.rotation_euler = (0, 0, math.radians(rot))

# The open magazine, as if held at reading distance.
pa = material("pa", (0.96, 0.94, 0.91), 0.85, page_a)
pb = material("pb", (0.96, 0.94, 0.91), 0.85, page_b)
left = open_half(edge, -1, pb)
right = open_half(edge, 1, pa)
for o in (left, right):
    o.location = (-0.2, 0.0, 0.02)
    o.rotation_euler = (math.radians(0), 0, 0)
left.rotation_euler = (0, math.radians(8), 0)
right.rotation_euler = (0, math.radians(-8), 0)

bpy.ops.object.light_add(type="AREA", location=(0.3, -0.4, 0.9))
k = bpy.context.object
k.data.energy = 90
k.data.size = 0.8
bpy.ops.object.light_add(type="AREA", location=(-0.6, 0.6, 0.6))
f = bpy.context.object
f.data.energy = 25
f.data.size = 1.2

cam = bpy.data.cameras.new("eye")
cam.lens = 24
co = bpy.data.objects.new("eye", cam)
scn.collection.objects.link(co)
co.location = (0, -0.55, 0.45)
d = Vector((0, 0.05, 0)) - co.location
co.rotation_euler = d.to_track_quat("-Z", "Y").to_euler()
scn.camera = co
scn.render.filepath = f"{OUT}/preview.png"
bpy.ops.render.render(write_still=True)
print("done", OUT)

# Renders the scenes SceneSim wrote (quest/app/src/test/.../SceneSim.kt) from the eye, one PNG
# each, so the magazines can be checked on the computer. Run:
#   blender --background --factory-startup --python quest/tools/blender/sim_render.py -- quest/build/sim
#
# Conventions, as on the headset:
#   - The Spatial SDK's world is left-handed: x right, y up, z forward. Blender's is right-handed
#     (x right, y forward, z up), so a point (x, y, z) is drawn at (x, z, y), and a rotation R as
#     S R S with S that swap.
#   - glTF models are taken as their raw numbers (the SDK loads them as they are).
#   - A panel shows its picture toward local -z, the picture's right along +x, its top along +y.
#   - Paper textures count v from the picture's top (v = 0 is the top row).
import bpy, bmesh, json, math, os, sys
from mathutils import Matrix, Quaternion, Vector

ROOT = os.path.abspath(sys.argv[sys.argv.index("--") + 1])
MODELS = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "app", "src", "main", "assets", "models")
FOV = float(os.environ.get("NAMA_FOV", "76"))
VFOV = float(os.environ.get("NAMA_VFOV", "89"))
SHIFT = tuple(float(v) for v in os.environ.get("NAMA_SHIFT", "0,-0.05").split(","))
S = Matrix(((1, 0, 0), (0, 0, 1), (0, 1, 0)))


def to_b(v):
    return Vector((v[0], v[2], v[1]))


def rot_b(q):
    r = Quaternion((q[0], q[1], q[2], q[3])).to_matrix()
    return S @ r @ S


def world(item, scale=1.0):
    m = rot_b(item["quat"]).to_4x4()
    m.translation = to_b(item["pos"])
    return m @ Matrix.Diagonal((scale, scale, scale, 1))


def unlit(m, color_socket=None, rgb=None):
    """A panel: the SDK draws its picture as it is, with no light on it."""
    nt = m.node_tree
    e = nt.nodes.new("ShaderNodeEmission")
    e.inputs["Strength"].default_value = 1.0
    if color_socket is not None:
        nt.links.new(color_socket, e.inputs["Color"])
    else:
        e.inputs["Color"].default_value = (*rgb, 1)
    nt.links.new(e.outputs["Emission"], nt.nodes["Material Output"].inputs["Surface"])


def image_material(name, path, panel=False):
    m = bpy.data.materials.get(name + ("#panel" if panel else ""))
    if m:
        return m
    m = bpy.data.materials.new(name + ("#panel" if panel else ""))
    m.use_nodes = True
    nt = m.node_tree
    b = nt.nodes["Principled BSDF"]
    t = nt.nodes.new("ShaderNodeTexImage")
    t.image = bpy.data.images.load(path, check_existing=True)
    nt.links.new(t.outputs["Color"], b.inputs["Base Color"])
    # Paper.material: roughness 0.8, not metal.
    b.inputs["Roughness"].default_value = 0.8
    if panel:
        unlit(m, t.outputs["Color"])
    m.use_backface_culling = False
    return m


def srgb(c):
    c /= 255
    return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4


def wood_material():
    # The virtual table is a panel too; its colour as the headset shows it.
    m = bpy.data.materials.get("wood")
    if m:
        return m
    m = bpy.data.materials.new("wood")
    m.use_nodes = True
    unlit(m, rgb=tuple(srgb(c) for c in (43, 30, 24)))
    return m


def headset_lighting(scn):
    """NamaActivity.setLightingEnvironment as Blender light. The SDK's shader divides diffuse by pi
    (Filament's convention, as Blender's sun does): measured on the headset, paper (F4F0E8) facing
    away from the sun shows 94, which is ambient 0.4 / pi, and facing it 148, with the sun's 1.6."""
    scn.view_settings.view_transform = "Standard"
    scn.view_settings.look = "None"
    scn.world = bpy.data.worlds.new("headset")
    scn.world.use_nodes = True
    nt = scn.world.node_tree
    bg = nt.nodes["Background"]
    bg.inputs["Color"].default_value = (0.4 / math.pi,) * 3 + (1,)
    bg.inputs["Strength"].default_value = 1.0
    # The ambient light lights the scene; the eye sees black behind it, as with the room hidden.
    black = nt.nodes.new("ShaderNodeBackground")
    black.inputs["Color"].default_value = (0, 0, 0, 1)
    path = nt.nodes.new("ShaderNodeLightPath")
    mix = nt.nodes.new("ShaderNodeMixShader")
    nt.links.new(path.outputs["Is Camera Ray"], mix.inputs["Fac"])
    nt.links.new(bg.outputs["Background"], mix.inputs[1])
    nt.links.new(black.outputs["Background"], mix.inputs[2])
    nt.links.new(mix.outputs["Shader"], nt.nodes["World Output"].inputs["Surface"])
    sun = bpy.data.lights.new("sun", "SUN")
    sun.energy = 1.6
    sun.angle = math.radians(0.5)
    ob = bpy.data.objects.new("sun", sun)
    scn.collection.objects.link(ob)
    # sunDirection = -(1, 3, -2), the way the light travels; a Blender sun shines along its -Z.
    ob.rotation_euler = to_b((-1, -3, 2)).normalized().to_track_quat("-Z", "Y").to_euler()


def panel(item):
    w, h = item["w"], item["h"]
    me = bpy.data.meshes.new("panel")
    bm = bmesh.new()
    uv = bm.loops.layers.uv.new()
    corners = [(-w / 2, -h / 2), (w / 2, -h / 2), (w / 2, h / 2), (-w / 2, h / 2)]
    verts = [bm.verts.new(to_b((x, y, 0))) for x, y in corners]
    f = bm.faces.new(verts)
    for loop in f.loops:
        co = loop.vert.co
        loop[uv].uv = ((co.x + w / 2) / w, (co.z + h / 2) / h)
    bm.to_mesh(me)
    ob = bpy.data.objects.new("panel", me)
    bpy.context.scene.collection.objects.link(ob)
    tex = item["tex"]
    if tex == "wood":
        ob.data.materials.append(wood_material())
    else:
        ob.data.materials.append(image_material(tex, os.path.join(ROOT, tex), panel=True))
    ob.matrix_world = world(item)


_glb_cache = {}


def glb(item):
    path = os.path.join(MODELS, item["file"])
    before = set(bpy.data.objects)
    bpy.ops.import_scene.gltf(filepath=path)
    new = [o for o in bpy.data.objects if o not in before and o.type == "MESH"]
    for o in new:
        # Undo the importer's turn to Blender's axes, then draw the raw numbers the SDK way.
        o.matrix_world = Matrix.Identity(4)
        o.data.transform(Matrix.Scale(-1, 4, (0, 1, 0)))
        o.data.flip_normals()
        o.matrix_world = world(item, item.get("scale", 1.0))
        for mat in o.data.materials:
            if mat:
                mat.use_backface_culling = False


def sheet(item):
    path = os.path.join(ROOT, item["obj"])
    vs, vts, faces = [], [], {"front": [], "back": []}
    group = "front"
    for line in open(path):
        p = line.split()
        if not p:
            continue
        if p[0] == "v":
            vs.append(to_b((float(p[1]), float(p[2]), float(p[3]))))
        elif p[0] == "vt":
            vts.append((float(p[1]), 1.0 - float(p[2])))
        elif p[0] == "g":
            group = p[1]
        elif p[0] == "f":
            faces[group].append([int(x.split("/")[0]) - 1 for x in p[1:]])
    me = bpy.data.meshes.new("sheet")
    me.from_pydata(vs, [], faces["front"] + faces["back"])
    uvl = me.uv_layers.new()
    for poly in me.polygons:
        poly.material_index = 0 if poly.index < len(faces["front"]) else 1
        for li in poly.loop_indices:
            uvl.data[li].uv = vts[me.loops[li].vertex_index]
    ob = bpy.data.objects.new("sheet", me)
    bpy.context.scene.collection.objects.link(ob)
    ob.data.materials.append(image_material(item["front"], os.path.join(ROOT, item["front"])))
    ob.data.materials.append(image_material(item["back"], os.path.join(ROOT, item["back"])))
    ob.matrix_world = world(item)


def build(scene_file):
    """The scene into the current Blender scene, lit as on the headset, with its camera at the eye.
    Also used in the open Blender over MCP, to look at the headset's scene before sending models."""
    scn = bpy.context.scene
    scn.render.engine = "BLENDER_EEVEE"
    scn.render.resolution_x, scn.render.resolution_y = 1400, 1000
    data = json.load(open(scene_file))
    for it in data["items"]:
        {"panel": panel, "glb": glb, "sheet": sheet}[it["kind"]](it)
    headset_lighting(scn)
    cam = bpy.data.cameras.new("eye")
    cam.lens = 16
    co = bpy.data.objects.new("eye", cam)
    scn.collection.objects.link(co)
    eye, target = to_b(data["eye"]), to_b(data["target"])
    fwd = (target - eye).normalized()
    up = to_b(data["up"]).normalized() if "up" in data else Vector((0, 0, 1))
    right = fwd.cross(up).normalized()
    up = right.cross(fwd).normalized()
    if "up" in data:
        # A dump from the headset: drawn like the headset's own screenshot (the share button, or
        # the metacam service), from the middle of the head, square, about 90 degrees wide.
        cam.sensor_fit = "HORIZONTAL"
        cam.angle = math.radians(FOV)
        scn.render.resolution_x = 1440
        scn.render.resolution_y = round(1440 * math.tan(math.radians(VFOV) / 2) / math.tan(math.radians(FOV) / 2))
        cam.shift_x, cam.shift_y = SHIFT
    co.location = eye
    co.rotation_euler = Matrix((right, up, -fwd)).transposed().to_euler()
    scn.camera = co


def render(scene_file):
    bpy.ops.wm.read_factory_settings(use_empty=True)
    build(scene_file)
    scn = bpy.context.scene
    scn.render.filepath = scene_file.replace(".json", ".png")
    bpy.ops.render.render(write_still=True)


if __name__ == "__main__":
    for f in sorted(os.listdir(ROOT)):
        if f.endswith(".json") and f != "textures.json" and f != "art.json":
            render(os.path.join(ROOT, f))
    print("rendered", ROOT)

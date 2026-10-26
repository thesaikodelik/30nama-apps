# Draws SceneSim's test pictures (textures.json in the sim folder): label, an arrow to the top,
# and L and R at the sides, so a picture upside down or mirrored shows at once.
import json, os, sys
from PIL import Image, ImageDraw, ImageFont

root = sys.argv[1]
spec = json.load(open(os.path.join(root, "textures.json")))
try:
    big = ImageFont.truetype("arialbd.ttf", 70)
    mid = ImageFont.truetype("arialbd.ttf", 44)
except OSError:
    big = mid = ImageFont.load_default()
for name, t in spec.items():
    w, h = 512, 680
    rgb = t["rgb"]
    img = Image.new("RGB", (w, h), ((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255))
    d = ImageDraw.Draw(img)
    if t["label"]:
        d.line([(w / 2, 60), (w / 2, 260)], fill="black", width=10)
        d.line([(w / 2, 60), (w / 2 - 50, 130)], fill="black", width=10)
        d.line([(w / 2, 60), (w / 2 + 50, 130)], fill="black", width=10)
        d.text((w / 2, 300), "TOP", fill="black", font=mid, anchor="mm")
        d.text((w / 2, 440), t["label"], fill="black", font=big, anchor="mm")
        d.text((40, h - 120), "L", fill="black", font=big, anchor="mm")
        d.text((w - 40, h - 120), "R", fill="black", font=big, anchor="mm")
    img.save(os.path.join(root, name))
print(len(spec), "textures")

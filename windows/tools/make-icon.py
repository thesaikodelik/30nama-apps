"""Writes windows/src/Nama.App/Assets/30nama.ico from the Mac app's icon.

The Mac icon leaves the transparent margin macOS asks for; Windows icons fill more of their
square, so the picture is cut to what is drawn, with a little air kept. Needs Pillow.

    python windows/tools/make-icon.py
"""
import pathlib
from PIL import Image

root = pathlib.Path(__file__).resolve().parents[2]
source = Image.open(root / "30nama/Mac/Assets.xcassets/AppIcon.appiconset/icon_512@2x.png").convert("RGBA")
left, top, right, bottom = source.getchannel("A").point(lambda a: 255 if a > 8 else 0).getbbox()
air = (right - left) // 40
square = source.crop((left - air, top - air, right + air, bottom + air))
target = root / "windows/src/Nama.App/Assets/30nama.ico"
square.save(target, sizes=[(16, 16), (20, 20), (24, 24), (32, 32), (40, 40), (48, 48), (64, 64), (256, 256)])
print("wrote", target.relative_to(root), square.size)

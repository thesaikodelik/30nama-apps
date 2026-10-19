"""The installer's pictures, drawn from the app's own logo and icon, in the app's colours: a dark
page with the red glow the app's pages have, the logo on the Welcome and Finished pages, and the
icon in the corner. Each at the sizes Inno Setup picks from for the screen's DPI.

    python windows/tools/make-installer-art.py

Writes windows/installer/art/*.png. Needs Pillow.
"""
import math
import os

from PIL import Image, ImageDraw, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.normpath(os.path.join(HERE, '..', '..'))
OUT = os.path.join(REPO, 'windows', 'installer', 'art')
LOGO = os.path.join(REPO, '30nama', 'Mac', 'Assets.xcassets', 'Logo.imageset', 'logo@2x.png')
ICON = os.path.join(REPO, 'windows', 'src', 'Nama.App', 'Assets', '30nama-256.png')

INK = (11, 10, 14)
RED = (214, 52, 60)


def glow(size, centre, radius, colour, strength):
    """A soft round light: strongest at its centre, gone at its radius."""
    w, h = size
    layer = Image.new('RGBA', size, (0, 0, 0, 0))
    small = (max(1, w // 4), max(1, h // 4))
    mask = Image.new('L', small, 0)
    pixels = mask.load()
    cx, cy, r = centre[0] / 4, centre[1] / 4, radius / 4
    for y in range(small[1]):
        for x in range(small[0]):
            d = math.hypot(x - cx, y - cy) / r
            if d < 1:
                pixels[x, y] = int(255 * strength * (1 - d) ** 2)
    mask = mask.resize(size, Image.BICUBIC).filter(ImageFilter.GaussianBlur(radius / 12))
    layer.paste(Image.new('RGBA', size, colour + (255,)), (0, 0), mask)
    return layer


def base(size):
    """The dark of the app's pages, a little lighter at the top."""
    w, h = size
    page = Image.new('RGBA', size, INK + (255,))
    top = Image.new('RGBA', size, (24, 20, 28, 255))
    fade = Image.linear_gradient('L').resize(size).transpose(Image.FLIP_TOP_BOTTOM)
    page.paste(top, (0, 0), fade.point(lambda v: v * 0.55))
    return page


def cube():
    """The cube of the logo, without the name."""
    logo = Image.open(LOGO).convert('RGBA')
    return logo.crop((0, 0, 340, logo.height)).crop(logo.crop((0, 0, 340, logo.height)).getbbox())


def back(size):
    """Behind every page: the dark, a red glow rising from the lower start corner, and the cube faint at the far end."""
    w, h = size
    page = base(size)
    page.alpha_composite(glow(size, (w * 0.08, h * 0.98), w * 0.8, RED, 0.62))
    page.alpha_composite(glow(size, (w * 0.98, h * 0.02), w * 0.5, (120, 40, 70), 0.3))
    mark = cube()
    side = int(h * 0.62)
    mark = mark.resize((int(mark.width * side / mark.height), side), Image.LANCZOS)
    faint = mark.copy()
    faint.putalpha(mark.getchannel('A').point(lambda v: int(v * 0.055)))
    page.alpha_composite(faint, (int(w - faint.width * 0.78), int(h - faint.height * 0.86)))
    return page.convert('RGB')


def side(size):
    """The Welcome and Finished pages' picture: the whole logo over the glow."""
    w, h = size
    page = base(size)
    page.alpha_composite(glow(size, (w * 0.5, h * 0.4), w * 1.15, RED, 0.85))
    page.alpha_composite(glow(size, (w * 0.5, h * 1.0), w * 0.95, (120, 40, 70), 0.45))
    mark = cube()
    mw = int(w * 0.62)
    mark = mark.resize((mw, int(mark.height * mw / mark.width)), Image.LANCZOS)
    # A shadow under the cube, so it stands off the glow.
    shadow = Image.new('RGBA', size, (0, 0, 0, 0))
    shadow.paste((0, 0, 0, 150), (int((w - mw) / 2), int(h * 0.27) + int(w * 0.03)), mark.getchannel('A'))
    page.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(w * 0.04)))
    page.alpha_composite(mark, (int((w - mw) / 2), int(h * 0.27)))
    name = Image.open(LOGO).convert('RGBA')
    name = name.crop((360, 0, name.width, name.height))
    name = name.crop(name.getbbox())
    nw = int(w * 0.6)
    name = name.resize((nw, int(name.height * nw / name.width)), Image.LANCZOS)
    page.alpha_composite(name, (int((w - nw) / 2), int(h * 0.27) + mark.height + int(h * 0.06)))
    # A thin red line under the name, as under the opening title of the app.
    line = Image.new('RGBA', size, (0, 0, 0, 0))
    y = int(h * 0.27) + mark.height + int(h * 0.06) + name.height + int(h * 0.05)
    ImageDraw.Draw(line).rounded_rectangle((w * 0.36, y, w * 0.64, y + max(2, h // 160)), radius=h // 300 + 1, fill=RED + (255,))
    page.alpha_composite(line.filter(ImageFilter.GaussianBlur(max(1, h // 400))))
    return page.convert('RGB')


def small(px):
    """The app's icon, for the corner of the other pages."""
    icon = Image.open(ICON).convert('RGBA')
    return icon.resize((px, px), Image.LANCZOS)


def main():
    os.makedirs(OUT, exist_ok=True)
    for name in os.listdir(OUT):
        os.remove(os.path.join(OUT, name))
    # The sizes Inno Setup 6.7 shows them at, from 100% to 250% DPI (497:360, 164:314, square).
    for w, h in [(596, 432), (796, 576), (994, 720), (1192, 864), (1490, 1080)]:
        back((w, h)).save(os.path.join(OUT, f'back-{w}.png'))
    for w, h in [(202, 386), (269, 515), (336, 642), (404, 772), (505, 965)]:
        side((w, h)).save(os.path.join(OUT, f'side-{w}.png'))
    for px in [58, 77, 97, 116, 124, 143, 159]:
        small(px).save(os.path.join(OUT, f'small-{px}.png'))
    print('wrote', OUT)


if __name__ == '__main__':
    main()

#!/usr/bin/env python3
"""Generates all launcher mipmaps from art/app-icon.jpg with Pillow.

Deletes the legacy .webp icons first so there are no duplicate resources,
writes PNGs for every density, computes the adaptive-icon background color
from the artwork edge, and drops preview PNGs into .icon-preview/ so builds
can be verified visually.
"""
import os
from PIL import Image, ImageDraw, ImageOps

BASE = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
RES = os.path.join(BASE, "app", "src", "main", "res")
SRC = os.path.join(BASE, "art", "app-icon.jpg")
DPI = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
FG_FACTOR = 2.25  # 108dp adaptive canvas at 48dp base density

img = Image.open(SRC).convert("RGB")


def shaped(im, size, radius_ratio=0.0, circle=False):
    im2 = ImageOps.fit(im, (size, size), Image.LANCZOS)
    mask = Image.new("L", (size, size), 0)
    d = ImageDraw.Draw(mask)
    if circle:
        d.ellipse((0, 0, size - 1, size - 1), fill=255)
    else:
        d.rounded_rectangle((0, 0, size - 1, size - 1), radius=int(size * radius_ratio), fill=255)
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(im2, (0, 0), mask)
    return out


# Adaptive background color = average of the top-left corner (artwork edge)
corner = img.crop((0, 0, 16, 16)).resize((1, 1), Image.LANCZOS).getpixel((0, 0))
bg_hex = "#%02X%02X%02X" % corner[:3] if len(corner) >= 3 else "#FFFFFF"

for dpi, size in DPI.items():
    dirp = os.path.join(RES, "mipmap-" + dpi)
    os.makedirs(dirp, exist_ok=True)
    for legacy in ("ic_launcher.webp", "ic_launcher_round.webp", "ic_launcher_foreground.webp"):
        p = os.path.join(dirp, legacy)
        if os.path.exists(p):
            os.remove(p)
    shaped(img, size, 0.22).save(os.path.join(dirp, "ic_launcher.png"))
    shaped(img, size, circle=True).save(os.path.join(dirp, "ic_launcher_round.png"))
    fg = Image.new("RGBA", (int(size * FG_FACTOR), int(size * FG_FACTOR)), (0, 0, 0, 0))
    fg.paste(ImageOps.fit(img, fg.size, Image.LANCZOS), (0, 0))
    fg.save(os.path.join(dirp, "ic_launcher_foreground.png"))

# Play Store listing icon (referenced under app/src/main)
ps = shaped(img, 512, 0.16)
ps.save(os.path.join(BASE, "app", "src", "main", "ic_launcher-playstore.png"))

# Preview montage for visual verification
prev = os.path.join(BASE, "icon-preview")
os.makedirs(prev, exist_ok=True)
shaped(img, 192, 0.22).save(os.path.join(prev, "ic_launcher_192.png"))
fg432 = Image.new("RGBA", (432, 432), (0, 0, 0, 0))
fg432.paste(ImageOps.fit(img, (432, 432), Image.LANCZOS), (0, 0))
fg432.save(os.path.join(prev, "foreground_432.png"))
ps.save(os.path.join(prev, "playstore_512.png"))

with open(os.path.join(RES, "values", "gs_launcher_colors.xml"), "w", encoding="utf-8") as fh:
    fh.write("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
    fh.write("<resources>\n")
    fh.write(f"    <color name=\"gs_launcher_bg\">{bg_hex}</color>\n")
    fh.write("</resources>\n")

print("ICON GEN OK — adaptive background:", bg_hex)

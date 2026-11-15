#!/usr/bin/env python3
"""
Generate 32x32 grayscale texture PNGs for antenna and horn models.
Run with: python tools/gen_antenna_horn_textures.py
"""
import os
from pathlib import Path
try:
    from PIL import Image, ImageDraw
except ImportError:
    print("Pillow not installed. Install with: pip install Pillow")
    exit(1)

OUT_DIR_ANTENNA = Path(__file__).parent.parent / "src/main/resources/assets/powerlevelling/textures/entity/antenna"
OUT_DIR_HORN = Path(__file__).parent.parent / "src/main/resources/assets/powerlevelling/textures/entity/horn"
OUT_DIR_ANTENNA.mkdir(parents=True, exist_ok=True)
OUT_DIR_HORN.mkdir(parents=True, exist_ok=True)

def draw_antenna_short(draw):
    """Short antenna: base cylinder + bulb"""
    # Main shaft (grayscale)
    draw.rectangle([14, 8, 18, 24], fill=180)  # light gray shaft
    draw.rectangle([14, 8, 18, 24], outline=100, width=1)  # darker edge
    # Top bulb
    draw.ellipse([12, 6, 20, 14], fill=200)
    draw.ellipse([13, 7, 19, 13], fill=220)
    # Base
    draw.rectangle([13, 24, 19, 26], fill=120)

def draw_antenna_long(draw):
    """Long antenna: longer shaft + bulb"""
    # Main shaft
    draw.rectangle([14, 4, 18, 28], fill=180)
    draw.rectangle([14, 4, 18, 28], outline=100, width=1)
    # Top bulb
    draw.ellipse([12, 2, 20, 10], fill=200)
    draw.ellipse([13, 3, 19, 9], fill=220)
    # Base
    draw.rectangle([13, 28, 19, 30], fill=120)

def draw_horn_small(draw):
    """Small horn: cone shape"""
    # Horn base (wider)
    draw.polygon([(10, 24), (22, 24), (16, 10)], fill=160)  # medium gray
    draw.polygon([(10, 24), (22, 24), (16, 10)], outline=100, width=1)
    # Horn tip highlight
    draw.polygon([(14, 10), (18, 10), (16, 6)], fill=200)
    # Base ring
    draw.rectangle([9, 24, 23, 26], fill=100)

def main():
    # antenna_short.png (32x32)
    img = Image.new("L", (32, 32), 0)  # grayscale
    draw = ImageDraw.Draw(img)
    draw_antenna_short(draw)
    img.save(OUT_DIR_ANTENNA / "antenna_short.png")
    print(f"Created {OUT_DIR_ANTENNA / 'antenna_short.png'}")

    # antenna_long.png (32x32)
    img = Image.new("L", (32, 32), 0)
    draw = ImageDraw.Draw(img)
    draw_antenna_long(draw)
    img.save(OUT_DIR_ANTENNA / "antenna_long.png")
    print(f"Created {OUT_DIR_ANTENNA / 'antenna_long.png'}")

    # horn_small.png (32x32)
    img = Image.new("L", (32, 32), 0)
    draw = ImageDraw.Draw(img)
    draw_horn_small(draw)
    img.save(OUT_DIR_HORN / "horn_small.png")
    print(f"Created {OUT_DIR_HORN / 'horn_small.png'}")

if __name__ == "__main__":
    main()
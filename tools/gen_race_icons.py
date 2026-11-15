#!/usr/bin/env python3
"""
Generate 24x24 race icons for Power Levelling.
Run with: python tools/gen_race_icons.py
"""
import os
from pathlib import Path
try:
    from PIL import Image, ImageDraw
except ImportError:
    print("Pillow not installed. Install with: pip install Pillow")
    exit(1)

OUT_DIR = Path(__file__).parent.parent / "src/main/resources/assets/powerlevelling/textures/gui/race"
OUT_DIR.mkdir(parents=True, exist_ok=True)

def draw_saiyan(draw):
    """Saiyan: a tail curl (brown curl)"""
    # Brown tail curl
    draw.arc([4, 16, 14, 22], start=270, end=450, fill="#8B4513", width=2)
    draw.arc([6, 18, 12, 20], start=270, end=450, fill="#5D2E0C", width=1)

def draw_half_saiyan(draw):
    """Half-Saiyan: half tail, half flame (brown tail + orange/yellow flame)"""
    # Half tail (left side)
    draw.arc([4, 16, 12, 20], start=270, end=360, fill="#8B4513", width=2)
    # Half flame (right side) - orange/yellow triangles
    draw.polygon([(16, 16), (22, 10), (18, 16)], fill="#FF8C00")  # orange
    draw.polygon([(18, 16), (22, 14), (16, 18)], fill="#FFD700")  # gold
    draw.polygon([(16, 18), (20, 16), (18, 20)], fill="#FFA500")  # orange

def draw_human(draw):
    """Human: a fist (simple fist shape)"""
    # Fist - dark skin tone
    draw.rounded_rectangle([8, 8, 16, 20], radius=3, fill="#D4A574")
    # Thumb
    draw.ellipse([6, 12, 10, 16], fill="#D4A574")
    # Knuckles
    draw.line([8, 8, 16, 8], fill="#C49464", width=2)

def draw_namekian(draw):
    """Namekian: an antenna (green antenna with bump)"""
    # Antenna stalk
    draw.line([12, 20, 12, 4], fill="#2E8B57", width=2)
    # Antenna bulb
    draw.ellipse([9, 2, 15, 8], fill="#2E8B57")
    draw.ellipse([10, 3, 14, 7], fill="#3CB371")

def draw_frost_demon(draw):
    """Frost Demon: a horn (white/ice blue horn)"""
    # Horn - triangle pointing up
    draw.polygon([(12, 4), (8, 18), (16, 18)], fill="#B0E0E6")
    # Horn highlight
    draw.polygon([(12, 5), (10, 16), (14, 16)], fill="#E0FFFF")
    # Horn ridges
    for y in [8, 12]:
        draw.line([9, y, 15, y], fill="#87CEEB", width=1)

def draw_majin(draw):
    """Majin: a tendril (pink curved tendril)"""
    # Curved tendril
    points = [(12, 4), (14, 8), (16, 12), (14, 16), (12, 20)]
    draw.line(points, fill="#FF69B4", width=3, joint="curve")
    # Second tendril
    points2 = [(12, 4), (10, 8), (8, 12), (10, 16), (12, 20)]
    draw.line(points2, fill="#FF69B4", width=3, joint="curve")

def draw_android(draw):
    """Android: a gear (grey gear with teeth)"""
    # Gear center
    draw.ellipse([8, 8, 16, 16], fill="#A0A0A0", outline="#808080", width=2)
    # Gear teeth - 8 teeth
    for i in range(8):
        angle = i * 45
        import math
        x1 = 12 + 8 * math.cos(math.radians(angle))
        y1 = 12 + 8 * math.sin(math.radians(angle))
        x2 = 12 + 12 * math.cos(math.radians(angle))
        y2 = 12 + 12 * math.sin(math.radians(angle))
        draw.line([x1, y1, x2, y2], fill="#808080", width=2)
    # Inner hole
    draw.ellipse([10, 10, 14, 14], fill="#C0C0C0")

def draw_custom_template(draw):
    """Custom Template: a question mark"""
    draw.arc([8, 4, 16, 12], start=270, end=360, fill="#FFFFFF", width=2)
    draw.line([12, 10, 12, 12], fill="#FFFFFF", width=2)
    draw.ellipse([11, 14, 13, 16], fill="#FFFFFF")

ICONS = {
    "saiyan": draw_saiyan,
    "half_saiyan": draw_half_saiyan,
    "human": draw_human,
    "namekian": draw_namekian,
    "frost_demon": draw_frost_demon,
    "majin": draw_majin,
    "android": draw_android,
    "custom_template": draw_custom_template,
}

def main():
    for name, draw_fn in ICONS.items():
        img = Image.new("RGBA", (24, 24), (0, 0, 0, 0))
        draw = ImageDraw.Draw(img)
        draw_fn(draw)
        out_path = OUT_DIR / f"{name}.png"
        img.save(out_path)
        print(f"Created {out_path}")

if __name__ == "__main__":
    main()
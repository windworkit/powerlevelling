import os

lines = []

# Increased scale (Radius 30 for a "way bigger" lobby)
cx, cy, cz = 26, -58, -37
radius = 30

# --- SETUP ---
lines.append('gamerule doDaylightCycle false')
lines.append('gamerule doWeatherCycle false')
lines.append('time set day')
lines.append('weather clear')
lines.append(f'setworldspawn {cx} {cy+1} {cz}')
lines.append('gamemode creative @a')

# --- 1. CLEAR & FOUNDATION (Bigger) ---
lines.append(f'fill {cx-50} {cy} {cz-50} {cx+50} {cy+15} {cz+50} air')
lines.append(f'fill {cx-50} {cy-1} {cz-50} {cx+50} {cy-1} {cz+50} stone_bricks')

# --- 2. THE MASSIVE ARENA FLOOR (DBZ THEMED: ORANGE/BLUE/YELLOW) ---
# Outer border (Gi Orange)
lines.append(f'fill {cx-radius} {cy} {cz-radius} {cx+radius} {cy} {cz+radius} orange_concrete')
# Main floor (Quartz/Smooth Stone)
lines.append(f'fill {cx-radius+2} {cy} {cz-radius+2} {cx+radius-2} {cy} {cz+radius-2} smooth_quartz')
# Arena Ring (Gi Blue)
lines.append(f'fill {cx-radius+5} {cy} {cz-radius+5} {cx+radius-5} {cy} {cz+radius-5} blue_concrete')
# Central Tournament Mat (Yellow)
lines.append(f'fill {cx-10} {cy} {cz-10} {cx+10} {cy} {cz+10} yellow_concrete')

# --- 3. TEMPLE PAVILION (World Tournament Style) ---
# Central dais
lines.append(f'fill {cx-3} {cy} {cz-3} {cx+3} {cy+1} {cz+3} white_terracotta')
# 4 large pillars
for px, pz in [(cx-25, cz-25), (cx+25, cz-25), (cx-25, cz+25), (cx+25, cz+25)]:
    lines.append(f'fill {px-1} {cy+1} {pz-1} {px+1} {cy+10} {pz+1} orange_terracotta')

# --- 4. INFO DISPLAY (Bigger) ---
lines.append(f'summon text_display {cx} {cy+6} {cz-20} {{text:\'{{"text":"⚡ DRAGON BALL SERVER LOBBY ⚡","color":"gold","bold":true,"font":"minecraft:uniform"}}\',billboard:"center",scale:[2f,2f,2f]}}')

# --- 5. TRAINING ARENA (More Dummies) ---
tx, tz = cx + 20, cz
lines.append('kill @e[type=powerlevelling:training_dummy]')
for i in range(5):
    lines.append(f'summon powerlevelling:training_dummy {tx} {cy+1} {tz-10 + i*5} {{CustomName:\'{{"text":"Dummy {i+1}"}}\',CustomNameVisible:1b}}')

# --- 6. KIT CHEST ---
lines.append(f'setblock {cx-3} {cy+1} {cz} chest[facing=east]{{Items:[{{Slot:0b,id:"powerlevelling:attunement_crystal",count:64b}},{{Slot:1b,id:"powerlevelling:reincarnation_orb",count:64b}},{{Slot:2b,id:"powerlevelling:training_dummy_spawn_egg",count:64b}}]}}')

output_path = r'C:\Users\kakal\Documents\PowerLevelling\run\saves\New World\datapacks\server_hub\data\server_hub\function\build.mcfunction'
os.makedirs(os.path.dirname(output_path), exist_ok=True)
with open(output_path, 'w', encoding='utf-8') as f:
    f.write('\n'.join(lines) + '\n')

print(f'Wrote {len(lines)} commands to massive_hub_v3.mcfunction')

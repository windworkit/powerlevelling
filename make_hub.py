import os

lines = []

# Spawn center coords: ~ 26, -58, -37
cx, cy, cz = 26, -58, -37

# Enable creative, day, and set spawn
lines.append('gamerule doDaylightCycle false')
lines.append('gamerule doWeatherCycle false')
lines.append('time set day')
lines.append('weather clear')
lines.append(f'setworldspawn {cx} {cy+2} {cz}')
lines.append('gamemode creative @a')

# 1. Clear area around spawn
lines.append(f'fill {cx-25} {cy} {cz-25} {cx+25} {cy+20} {cz+25} air')
# Base floor under the whole hub (smooth stone, one layer below the arena)
lines.append(f'fill {cx-25} {cy-1} {cz-25} {cx+25} {cy-1} {cz+25} smooth_stone')

# 2. Main Tournament Arena Platform (Radius 15)
lines.append(f'fill {cx-15} {cy} {cz-15} {cx+15} {cy} {cz+15} polished_blackstone')
lines.append(f'fill {cx-14} {cy} {cz-14} {cx+14} {cy} {cz+14} quartz_block')
lines.append(f'fill {cx-12} {cy} {cz-12} {cx+12} {cy} {cz+12} smooth_quartz')

# Yellow ring edge (Tenkaichi Budokai border)
lines.append(f'fill {cx-14} {cy+1} {cz-14} {cx+14} {cy+1} {cz-14} yellow_carpet')
lines.append(f'fill {cx-14} {cy+1} {cz+14} {cx+14} {cy+1} {cz+14} yellow_carpet')
lines.append(f'fill {cx-14} {cy+1} {cz-14} {cx-14} {cy+1} {cz+14} yellow_carpet')
lines.append(f'fill {cx+14} {cy+1} {cz-14} {cx+14} {cy+1} {cz+14} yellow_carpet')

# Center emblem & Beacon Column
lines.append(f'fill {cx-2} {cy} {cz-2} {cx+2} {cy} {cz+2} gold_block')
lines.append(f'setblock {cx} {cy} {cz} sea_lantern')
lines.append(f'setblock {cx} {cy-1} {cz} beacon')
lines.append(f'fill {cx-1} {cy-2} {cz-1} {cx+1} {cy-2} {cz+1} iron_block')

# 3. Corner Pillars & Torches
for px, pz in [(cx-14, cz-14), (cx+14, cz-14), (cx-14, cz+14), (cx+14, cz+14)]:
    lines.append(f'fill {px} {cy+1} {pz} {px} {cy+5} {pz} chiseled_quartz_block')
    lines.append(f'setblock {px} {cy+6} {pz} soul_lantern')

# 4. Info Pavilion / Keybind Holograms on the West side
ix, iz = cx - 18, cz
lines.append(f'fill {ix-2} {cy} {iz-5} {ix+2} {cy} {iz+5} polished_deepslate')
lines.append(f'fill {ix-2} {cy+1} {iz-5} {ix-2} {cy+4} {iz-5} deepslate_tile_wall')
lines.append(f'fill {ix-2} {cy+1} {iz+5} {ix-2} {cy+4} {iz+5} deepslate_tile_wall')
lines.append(f'fill {ix-2} {cy+5} {iz-5} {ix+2} {cy+5} {iz+5} polished_deepslate_slab')

# Spawn Info Displays using JSON text components (1.21 syntax)
lines.append(f'summon text_display {cx} {cy+4} {cz-6} {{text:\'{{"text":"⚡ POWER LEVELLING SERVER LOBBY ⚡","color":"gold","bold":true}}\',billboard:"center",background:-16777216}}')

lines.append(f'summon text_display {ix} {cy+3.5} {iz-3} {{text:\'{{"text":"[R] - Toggle Combat Stance","color":"yellow"}}\',billboard:"center"}}')
lines.append(f'summon text_display {ix} {cy+3.0} {iz-1} {{text:\'{{"text":"[C] - Charge Ki","color":"aqua"}}\',billboard:"center"}}')
lines.append(f'summon text_display {ix} {cy+2.5} {iz+1} {{text:\'{{"text":"[K] - Character and Stats","color":"green"}}\',billboard:"center"}}')
lines.append(f'summon text_display {ix} {cy+2.0} {iz+3} {{text:\'{{"text":"[G] - Transformations and Skills","color":"light_purple"}}\',billboard:"center"}}')

# 5. Training Arena Zone on East side with Training Dummies
tx, tz = cx + 10, cz
lines.append('kill @e[type=powerlevelling:training_dummy]')
lines.append(f'summon powerlevelling:training_dummy {tx} {cy+1} {tz-4} {{CustomName:\'{{"text":"Training Dummy [Alpha]"}}\',CustomNameVisible:1b}}')
lines.append(f'summon powerlevelling:training_dummy {tx} {cy+1} {tz+4} {{CustomName:\'{{"text":"Training Dummy [Beta]"}}\',CustomNameVisible:1b}}')

# 6. Kit Chest at Spawn
lines.append(f'setblock {cx-3} {cy+1} {cz} chest[facing=east]{{Items:[{{Slot:0b,id:"powerlevelling:attunement_crystal",count:64b}},{{Slot:1b,id:"powerlevelling:reincarnation_orb",count:64b}},{{Slot:2b,id:"powerlevelling:training_dummy_spawn_egg",count:64b}}]}}')

output_path = r'C:\Users\kakal\Documents\PowerLevelling\run\saves\New World\datapacks\server_hub\data\server_hub\function\build.mcfunction'
os.makedirs(os.path.dirname(output_path), exist_ok=True)
with open(output_path, 'w', encoding='utf-8') as f:
    f.write('\n'.join(lines) + '\n')

print(f'Wrote {len(lines)} commands to build.mcfunction')

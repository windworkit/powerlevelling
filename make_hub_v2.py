import os

lines = []

# Spawn center coords: ~ 26, -58, -37
cx, cy, cz = 26, -58, -37

# --- SETUP ---
lines.append('gamerule doDaylightCycle false')
lines.append('gamerule doWeatherCycle false')
lines.append('time set day')
lines.append('weather clear')
lines.append(f'setworldspawn {cx} {cy+1} {cz}')
lines.append('gamemode creative @a')

# --- 1. CLEAR & FOUNDATION ---
lines.append(f'fill {cx-25} {cy} {cz-25} {cx+25} {cy+10} {cz+25} air')
lines.append(f'fill {cx-25} {cy-1} {cz-25} {cx+25} {cy-1} {cz+25} stone_bricks')

# --- 2. THE ARENA FLOOR (Intricate Pattern) ---
# Outer border
lines.append(f'fill {cx-15} {cy} {cz-15} {cx+15} {cy} {cz+15} polished_blackstone_bricks')
# Main floor
lines.append(f'fill {cx-14} {cy} {cz-14} {cx+14} {cy} {cz+14} quartz_bricks')
# Inlay pattern (Cross)
lines.append(f'fill {cx-2} {cy} {cz-14} {cx+2} {cy} {cz+14} polished_blackstone_bricks')
lines.append(f'fill {cx-14} {cy} {cz-2} {cx+14} {cy} {cz+2} polished_blackstone_bricks')
# Center
lines.append(f'fill {cx-1} {cy} {cz-1} {cx+1} {cy} {cz+1} sea_lantern')
lines.append(f'setblock {cx} {cy-1} {cz} beacon')
lines.append(f'fill {cx-2} {cy-2} {cz-2} {cx+2} {cy-2} {cz+2} iron_block')

# --- 3. PILLARS & ROOFING (Structural) ---
# Corner pillars
for px, pz in [(cx-14, cz-14), (cx+14, cz-14), (cx-14, cz+14), (cx+14, cz+14)]:
    lines.append(f'fill {px} {cy+1} {pz} {px} {cy+6} {pz} quartz_pillar')
    lines.append(f'setblock {px} {cy+7} {pz} stone_brick_slab[type=top]')
    lines.append(f'setblock {px} {cy+8} {pz} soul_lantern[hanging=true]')

# --- 4. INFO PAVILION (Better Design) ---
ix, iz = cx - 20, cz
lines.append(f'fill {ix-3} {cy} {iz-6} {ix+3} {cy+3} {iz+6} stone_bricks')
lines.append(f'fill {ix-2} {cy+1} {iz-5} {ix+2} {cy+2} {iz+5} air')
lines.append(f'fill {ix-3} {cy+3} {iz-6} {ix+3} {cy+3} {iz+6} stone_brick_stairs[facing=west]')

# Displays
lines.append(f'summon text_display {cx} {cy+4} {cz-7} {{text:\'{{"text":"⚡ SERVER LOBBY ⚡","color":"gold","bold":true}}\',billboard:"center"}}')

# --- 5. TRAINING ARENA (Tidy) ---
tx, tz = cx + 10, cz
lines.append('kill @e[type=powerlevelling:training_dummy]')
lines.append(f'summon powerlevelling:training_dummy {tx} {cy+1} {tz-4} {{CustomName:\'{{"text":"Alpha"}}\',CustomNameVisible:1b}}')
lines.append(f'summon powerlevelling:training_dummy {tx} {cy+1} {tz+4} {{CustomName:\'{{"text":"Beta"}}\',CustomNameVisible:1b}}')

# --- 6. KIT CHEST ---
lines.append(f'setblock {cx-3} {cy+1} {cz} chest[facing=east]{{Items:[{{Slot:0b,id:"powerlevelling:attunement_crystal",count:64b}},{{Slot:1b,id:"powerlevelling:reincarnation_orb",count:64b}},{{Slot:2b,id:"powerlevelling:training_dummy_spawn_egg",count:64b}}]}}')

output_path = r'C:\Users\kakal\Documents\PowerLevelling\run\saves\New World\datapacks\server_hub\data\server_hub\function\build.mcfunction'
os.makedirs(os.path.dirname(output_path), exist_ok=True)
with open(output_path, 'w', encoding='utf-8') as f:
    f.write('\n'.join(lines) + '\n')

print(f'Wrote {len(lines)} improved commands to build.mcfunction')

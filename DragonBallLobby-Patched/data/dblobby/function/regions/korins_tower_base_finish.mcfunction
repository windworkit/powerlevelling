# KORIN'S TOWER — cloud plinth shaping and ceremonial base entrance.
# Chamfer the four corners in progressively smaller tiers to soften the square foundation.

# Lowest white foundation: 8x8 corner cuts.
fill ~-184 ~0 ~126 ~-177 ~2 ~133 minecraft:air
fill ~-184 ~0 ~177 ~-177 ~2 ~184 minecraft:air
fill ~-133 ~0 ~126 ~-126 ~2 ~133 minecraft:air
fill ~-133 ~0 ~177 ~-126 ~2 ~184 minecraft:air
# Quartz second tier: 6x6 corner cuts.
fill ~-180 ~3 ~130 ~-175 ~4 ~135 minecraft:air
fill ~-180 ~3 ~175 ~-175 ~4 ~180 minecraft:air
fill ~-135 ~3 ~130 ~-130 ~4 ~135 minecraft:air
fill ~-135 ~3 ~175 ~-130 ~4 ~180 minecraft:air
# Upper cloud tier: 4x4 corner cuts.
fill ~-176 ~5 ~134 ~-173 ~7 ~137 minecraft:air
fill ~-176 ~5 ~173 ~-173 ~7 ~176 minecraft:air
fill ~-137 ~5 ~134 ~-134 ~7 ~137 minecraft:air
fill ~-137 ~5 ~173 ~-134 ~7 ~176 minecraft:air
# Highest gray cloud tier: small final chamfers.
fill ~-171 ~8 ~139 ~-170 ~10 ~140 minecraft:air
fill ~-171 ~8 ~170 ~-170 ~10 ~171 minecraft:air
fill ~-140 ~8 ~139 ~-139 ~10 ~140 minecraft:air
fill ~-140 ~8 ~170 ~-139 ~10 ~171 minecraft:air

# Soft cloud puffs extend beyond the plinth edges at four diagonal corners.
fill ~-190 ~1 ~137 ~-184 ~3 ~143 minecraft:white_wool
fill ~-188 ~4 ~139 ~-184 ~5 ~143 minecraft:light_gray_wool
fill ~-128 ~1 ~137 ~-122 ~3 ~143 minecraft:white_wool
fill ~-128 ~4 ~139 ~-124 ~5 ~143 minecraft:light_gray_wool
fill ~-190 ~1 ~167 ~-184 ~3 ~173 minecraft:white_wool
fill ~-188 ~4 ~167 ~-184 ~5 ~171 minecraft:light_gray_wool
fill ~-128 ~1 ~167 ~-122 ~3 ~173 minecraft:white_wool
fill ~-128 ~4 ~167 ~-124 ~5 ~171 minecraft:light_gray_wool
# Smaller cloud lobes add an irregular silhouette rather than a flat square pad.
fill ~-186 ~2 ~146 ~-182 ~4 ~151 minecraft:white_wool
fill ~-130 ~2 ~146 ~-126 ~4 ~151 minecraft:white_wool
fill ~-186 ~2 ~159 ~-182 ~4 ~164 minecraft:white_wool
fill ~-130 ~2 ~159 ~-126 ~4 ~164 minecraft:white_wool

# Recessed quartz-and-gold trim defines the main tower threshold.
fill ~-164 ~12 ~143 ~-162 ~23 ~143 minecraft:quartz_pillar
fill ~-148 ~12 ~143 ~-146 ~23 ~143 minecraft:quartz_pillar
fill ~-164 ~15 ~142 ~-162 ~16 ~143 minecraft:gold_block
fill ~-148 ~15 ~142 ~-146 ~16 ~143 minecraft:gold_block
fill ~-164 ~22 ~142 ~-162 ~23 ~143 minecraft:gold_block
fill ~-148 ~22 ~142 ~-146 ~23 ~143 minecraft:gold_block
fill ~-163 ~13 ~142 ~-163 ~21 ~142 minecraft:light_blue_stained_glass
fill ~-147 ~13 ~142 ~-147 ~21 ~142 minecraft:light_blue_stained_glass
setblock ~-163 ~24 ~143 minecraft:sea_lantern
setblock ~-147 ~24 ~143 minecraft:sea_lantern
# Low cloudstone bollards and lanterns flank the south bridge landing.
fill ~-169 ~11 ~181 ~-167 ~14 ~183 minecraft:quartz_pillar
fill ~-143 ~11 ~181 ~-141 ~14 ~183 minecraft:quartz_pillar
fill ~-170 ~14 ~180 ~-166 ~14 ~184 minecraft:gold_block
fill ~-144 ~14 ~180 ~-140 ~14 ~184 minecraft:gold_block
setblock ~-168 ~15 ~182 minecraft:sea_lantern
setblock ~-142 ~15 ~182 minecraft:sea_lantern

# Cloud garden accents remain low and do not obstruct the approach bridge.

# KAMI'S LOOKOUT — accessible tower shaft and upper approach.
# Hollow a compact central shaft through the white tower; ladder is attached
# to the west inner wall. The shaft exits onto a short bridge to the palace stairs.
fill ~-2 ~1 ~98 ~2 ~27 ~102 minecraft:air
fill ~-3 ~1 ~97 ~-3 ~27 ~103 minecraft:quartz_pillar
fill ~3 ~1 ~97 ~3 ~27 ~103 minecraft:quartz_pillar
fill ~-3 ~1 ~97 ~3 ~27 ~97 minecraft:quartz_pillar
fill ~-3 ~1 ~103 ~3 ~27 ~103 minecraft:quartz_pillar
fill ~-2 ~1 ~99 ~-2 ~27 ~99 minecraft:ladder[facing=east]
# Clear the top of the shaft and form a small arrival landing.
fill ~-2 ~27 ~98 ~2 ~29 ~102 minecraft:air
fill ~-5 ~28 ~103 ~5 ~28 ~116 minecraft:smooth_quartz
fill ~-4 ~29 ~104 ~4 ~29 ~116 minecraft:air
# Side rails keep the narrow sky bridge readable while preserving the route.
fill ~-5 ~29 ~105 ~-5 ~30 ~115 minecraft:quartz_pillar
fill ~5 ~29 ~105 ~5 ~30 ~115 minecraft:quartz_pillar
fill ~-5 ~30 ~105 ~-5 ~30 ~115 minecraft:gold_block
fill ~5 ~30 ~105 ~5 ~30 ~115 minecraft:gold_block
# Lit threshold at the foot of the palace ceremonial stairs.
fill ~-4 ~29 ~114 ~4 ~29 ~116 minecraft:sea_lantern

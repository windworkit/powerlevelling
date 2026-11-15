# WORLD MARTIAL ARTS TOURNAMENT — pagoda arrival gate, vendor stalls and announcer stand.
# All details stay outside the five-block-wide westbound approach lane (Z -4..+4).

# Four deep red-and-white gate piers frame the west entrance without narrowing the road.
fill ~77 ~1 ~-13 ~79 ~1 ~-11 minecraft:cut_sandstone
fill ~77 ~1 ~11 ~79 ~1 ~13 minecraft:cut_sandstone
fill ~87 ~1 ~-13 ~89 ~1 ~-11 minecraft:cut_sandstone
fill ~87 ~1 ~11 ~89 ~1 ~13 minecraft:cut_sandstone
fill ~77 ~2 ~-13 ~79 ~12 ~-11 minecraft:red_nether_bricks
fill ~77 ~2 ~11 ~79 ~12 ~13 minecraft:red_nether_bricks
fill ~87 ~2 ~-13 ~89 ~12 ~-11 minecraft:red_nether_bricks
fill ~87 ~2 ~11 ~89 ~12 ~13 minecraft:red_nether_bricks
fill ~77 ~4 ~-13 ~79 ~5 ~-11 minecraft:gold_block
fill ~77 ~4 ~11 ~79 ~5 ~13 minecraft:gold_block
fill ~87 ~4 ~-13 ~89 ~5 ~-11 minecraft:gold_block
fill ~87 ~4 ~11 ~89 ~5 ~13 minecraft:gold_block
fill ~77 ~10 ~-13 ~79 ~11 ~-11 minecraft:white_concrete
fill ~77 ~10 ~11 ~79 ~11 ~13 minecraft:white_concrete
fill ~87 ~10 ~-13 ~89 ~11 ~-11 minecraft:white_concrete
fill ~87 ~10 ~11 ~89 ~11 ~13 minecraft:white_concrete
# Upper lintel and layered pagoda roof.
fill ~78 ~12 ~-12 ~88 ~13 ~12 minecraft:white_concrete
fill ~76 ~14 ~-15 ~90 ~14 ~15 minecraft:red_nether_bricks
fill ~77 ~15 ~-14 ~89 ~15 ~14 minecraft:dark_oak_planks
fill ~79 ~16 ~-12 ~87 ~16 ~12 minecraft:red_nether_bricks
fill ~80 ~17 ~-10 ~86 ~17 ~10 minecraft:dark_oak_planks
fill ~82 ~18 ~-7 ~84 ~18 ~7 minecraft:gold_block
fill ~82 ~19 ~-2 ~84 ~19 ~2 minecraft:red_concrete
fill ~83 ~20 ~0 ~83 ~22 ~0 minecraft:gold_block
setblock ~83 ~23 ~0 minecraft:sea_lantern
# Gold-and-white tournament crest above the central opening.
fill ~80 ~10 ~-1 ~86 ~11 ~1 minecraft:gold_block
fill ~81 ~11 ~-1 ~85 ~12 ~1 minecraft:red_concrete
fill ~82 ~12 ~-1 ~84 ~13 ~1 minecraft:white_concrete
setblock ~83 ~14 ~0 minecraft:sea_lantern
# Roofline lamps and side banners.
setblock ~77 ~14 ~-13 minecraft:sea_lantern
setblock ~89 ~14 ~-13 minecraft:sea_lantern
setblock ~77 ~14 ~13 minecraft:sea_lantern
setblock ~89 ~14 ~13 minecraft:sea_lantern
fill ~76 ~6 ~-14 ~76 ~11 ~-14 minecraft:red_concrete
fill ~90 ~6 ~14 ~90 ~11 ~14 minecraft:red_concrete
fill ~76 ~7 ~-14 ~76 ~10 ~-14 minecraft:white_concrete
fill ~90 ~7 ~14 ~90 ~10 ~14 minecraft:white_concrete

# Rebuild north registration stall as an open-front, red-awning vendor kiosk.
fill ~66 ~1 ~-18 ~72 ~7 ~-12 minecraft:air
fill ~66 ~0 ~-18 ~72 ~0 ~-12 minecraft:cut_sandstone
fill ~67 ~1 ~-17 ~67 ~4 ~-13 minecraft:quartz_pillar
fill ~71 ~1 ~-17 ~71 ~4 ~-13 minecraft:quartz_pillar
fill ~67 ~1 ~-17 ~71 ~1 ~-17 minecraft:polished_diorite
fill ~67 ~1 ~-13 ~71 ~1 ~-13 minecraft:polished_diorite
fill ~71 ~2 ~-16 ~71 ~4 ~-14 minecraft:white_concrete
fill ~68 ~2 ~-15 ~70 ~2 ~-14 minecraft:dark_oak_slab[type=top,waterlogged=false]
fill ~68 ~3 ~-15 ~70 ~3 ~-14 minecraft:barrel
fill ~66 ~5 ~-19 ~72 ~5 ~-11 minecraft:red_nether_bricks
fill ~67 ~6 ~-18 ~71 ~6 ~-12 minecraft:gold_block
fill ~68 ~7 ~-17 ~70 ~7 ~-13 minecraft:red_concrete
fill ~68 ~8 ~-16 ~70 ~8 ~-14 minecraft:sea_lantern
fill ~66 ~4 ~-18 ~66 ~5 ~-12 minecraft:red_concrete
setblock ~68 ~6 ~-18 minecraft:lantern
setblock ~70 ~6 ~-18 minecraft:lantern

# Rebuild south registration stall to match, with a yellow/gold service counter.
fill ~66 ~1 ~12 ~72 ~7 ~18 minecraft:air
fill ~66 ~0 ~12 ~72 ~0 ~18 minecraft:cut_sandstone
fill ~67 ~1 ~13 ~67 ~4 ~17 minecraft:quartz_pillar
fill ~71 ~1 ~13 ~71 ~4 ~17 minecraft:quartz_pillar
fill ~67 ~1 ~13 ~71 ~1 ~13 minecraft:polished_diorite
fill ~67 ~1 ~17 ~71 ~1 ~17 minecraft:polished_diorite
fill ~71 ~2 ~14 ~71 ~4 ~16 minecraft:white_concrete
fill ~68 ~2 ~15 ~70 ~2 ~16 minecraft:dark_oak_slab[type=top,waterlogged=false]
fill ~68 ~3 ~15 ~70 ~3 ~16 minecraft:barrel
fill ~66 ~5 ~11 ~72 ~5 ~19 minecraft:red_nether_bricks
fill ~67 ~6 ~12 ~71 ~6 ~18 minecraft:gold_block
fill ~68 ~7 ~13 ~70 ~7 ~17 minecraft:yellow_concrete
fill ~68 ~8 ~14 ~70 ~8 ~16 minecraft:sea_lantern
fill ~66 ~4 ~12 ~66 ~5 ~18 minecraft:yellow_concrete
setblock ~68 ~6 ~18 minecraft:lantern
setblock ~70 ~6 ~18 minecraft:lantern

# Announcer / judge platform on the east side of the ring, with a clear view of the mat.
fill ~170 ~1 ~-8 ~180 ~1 ~8 minecraft:cut_sandstone
fill ~171 ~2 ~-7 ~179 ~2 ~7 minecraft:dark_oak_planks
fill ~171 ~3 ~-7 ~171 ~8 ~7 minecraft:quartz_pillar
fill ~179 ~3 ~-7 ~179 ~8 ~7 minecraft:quartz_pillar
fill ~172 ~3 ~-6 ~178 ~7 ~6 minecraft:white_concrete
fill ~172 ~4 ~-5 ~172 ~6 ~5 minecraft:air
fill ~173 ~4 ~-4 ~177 ~6 ~4 minecraft:air
# Red roof with gold fascia and a raised announcer beacon.
fill ~170 ~9 ~-8 ~180 ~9 ~8 minecraft:red_nether_bricks
fill ~172 ~10 ~-6 ~178 ~10 ~6 minecraft:dark_oak_planks
fill ~174 ~11 ~-4 ~176 ~11 ~4 minecraft:gold_block
fill ~174 ~12 ~-1 ~176 ~13 ~1 minecraft:red_concrete
setblock ~175 ~14 ~0 minecraft:sea_lantern
# A short stair and carpet aisle connect the stand to the concourse.
fill ~168 ~1 ~-2 ~169 ~1 ~2 minecraft:stone_brick_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]
fill ~166 ~1 ~-1 ~168 ~1 ~1 minecraft:red_carpet
# Four short waiting benches sit along the ring's outer edge, clear of the fighting surface.
fill ~94 ~1 ~-16 ~100 ~1 ~-16 minecraft:dark_oak_slab[type=bottom,waterlogged=false]
fill ~94 ~1 ~16 ~100 ~1 ~16 minecraft:dark_oak_slab[type=bottom,waterlogged=false]
fill ~174 ~1 ~-18 ~180 ~1 ~-18 minecraft:dark_oak_slab[type=bottom,waterlogged=false]
fill ~174 ~1 ~18 ~180 ~1 ~18 minecraft:dark_oak_slab[type=bottom,waterlogged=false]
setblock ~94 ~2 ~-16 minecraft:lantern
setblock ~100 ~2 ~-16 minecraft:lantern
setblock ~94 ~2 ~16 minecraft:lantern
setblock ~100 ~2 ~16 minecraft:lantern

# CAPSULE CITY — architectural finish pass.
# The pass adds a readable Capsule Corp identity, façade depth, storefront glazing,
# and a more deliberate boulevard rhythm without narrowing the central route.
# Headquarters identity panel above the south-facing entrance.
fill ~-10 ~13 ~-149 ~10 ~18 ~-148 minecraft:blue_concrete
fill ~-8 ~14 ~-149 ~8 ~17 ~-148 minecraft:light_blue_concrete
fill ~-6 ~15 ~-149 ~6 ~16 ~-148 minecraft:white_concrete
fill ~-4 ~14 ~-149 ~4 ~17 ~-148 minecraft:light_blue_stained_glass
fill ~-3 ~15 ~-149 ~3 ~16 ~-148 minecraft:blue_concrete
# Capsule insignia: a clean blue-and-white capsule silhouette.
fill ~-2 ~15 ~-147 ~2 ~16 ~-147 minecraft:white_concrete
setblock ~-3 ~15 ~-147 minecraft:light_blue_concrete
setblock ~3 ~16 ~-147 minecraft:light_blue_concrete
# Repeated façade bands and windows on the two low HQ wings.
fill ~-46 ~4 ~-144 ~-28 ~4 ~-144 minecraft:blue_concrete
fill ~-46 ~8 ~-144 ~-28 ~8 ~-144 minecraft:blue_concrete
fill ~-44 ~5 ~-144 ~-30 ~7 ~-144 minecraft:light_blue_stained_glass
fill ~28 ~4 ~-144 ~46 ~4 ~-144 minecraft:blue_concrete
fill ~28 ~8 ~-144 ~46 ~8 ~-144 minecraft:blue_concrete
fill ~30 ~5 ~-144 ~44 ~7 ~-144 minecraft:light_blue_stained_glass
# Taller research blocks receive corner pilasters and narrow ribbon windows.
fill ~-63 ~2 ~-111 ~-63 ~16 ~-95 minecraft:quartz_pillar
fill ~-41 ~2 ~-111 ~-41 ~16 ~-95 minecraft:quartz_pillar
fill ~43 ~2 ~-111 ~43 ~21 ~-95 minecraft:quartz_pillar
fill ~61 ~2 ~-111 ~61 ~21 ~-95 minecraft:quartz_pillar
fill ~-60 ~6 ~-112 ~-44 ~7 ~-112 minecraft:light_blue_stained_glass
fill ~-60 ~12 ~-112 ~-44 ~13 ~-112 minecraft:light_blue_stained_glass
fill ~46 ~7 ~-111 ~60 ~8 ~-111 minecraft:light_blue_stained_glass
fill ~46 ~14 ~-111 ~60 ~15 ~-111 minecraft:light_blue_stained_glass
# Blue roof crowns and white fascia soften the box silhouettes.
fill ~-64 ~17 ~-115 ~-40 ~18 ~-91 minecraft:light_blue_concrete
fill ~-62 ~18 ~-113 ~-42 ~18 ~-93 minecraft:smooth_quartz
fill ~41 ~22 ~-115 ~63 ~23 ~-91 minecraft:blue_concrete
fill ~43 ~23 ~-113 ~61 ~23 ~-93 minecraft:smooth_quartz
# Residential and workshop bays receive lit windows and low entry canopies.
fill ~-75 ~6 ~-110 ~-75 ~9 ~-110 minecraft:light_blue_stained_glass
fill ~-59 ~6 ~-110 ~-59 ~9 ~-110 minecraft:light_blue_stained_glass
fill ~38 ~6 ~-74 ~52 ~8 ~-74 minecraft:light_blue_stained_glass
fill ~60 ~5 ~-118 ~60 ~7 ~-118 minecraft:light_blue_stained_glass
fill ~-52 ~10 ~-91 ~-36 ~10 ~-91 minecraft:blue_concrete
fill ~39 ~11 ~-91 ~53 ~11 ~-91 minecraft:blue_concrete
# Boulevard furniture: matching low planters and benches outside the road lane.
fill ~-33 ~0 ~-106 ~-27 ~0 ~-100 minecraft:smooth_quartz
fill ~-32 ~1 ~-105 ~-28 ~1 ~-101 minecraft:moss_block
setblock ~-30 ~2 ~-103 minecraft:flowering_azalea
fill ~27 ~0 ~-106 ~33 ~0 ~-100 minecraft:smooth_quartz
fill ~28 ~1 ~-105 ~32 ~1 ~-101 minecraft:moss_block
setblock ~30 ~2 ~-103 minecraft:azalea
fill ~-34 ~1 ~-94 ~-28 ~1 ~-94 minecraft:dark_oak_slab[type=bottom]
fill ~28 ~1 ~-94 ~34 ~1 ~-94 minecraft:dark_oak_slab[type=bottom]
# Blue-and-white threshold stripes align the avenue with the northern road.
fill ~-5 ~0 ~-76 ~5 ~0 ~-74 minecraft:light_blue_concrete
fill ~-3 ~0 ~-73 ~3 ~0 ~-72 minecraft:blue_concrete
# The north road rises two blocks into the elevated Capsule boulevard.
# Two north-facing stair rows replace the abrupt Y0-to-Y2 collision.
fill ~-5 ~1 ~-118 ~5 ~1 ~-117 minecraft:air
fill ~-5 ~2 ~-118 ~5 ~2 ~-116 minecraft:air
fill ~-4 ~0 ~-118 ~4 ~0 ~-118 minecraft:stone_brick_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]
fill ~-4 ~1 ~-117 ~4 ~1 ~-117 minecraft:stone_brick_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]
fill ~-4 ~2 ~-116 ~4 ~2 ~-76 minecraft:smooth_stone


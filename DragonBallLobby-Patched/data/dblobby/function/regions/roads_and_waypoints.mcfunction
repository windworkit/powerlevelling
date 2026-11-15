# Main roads remain five blocks wide at the walking surface.
fill ~-4 ~-1 ~-118 ~4 ~-1 ~-36 minecraft:gray_concrete
fill ~36 ~-1 ~-4 ~118 ~-1 ~4 minecraft:gray_concrete
fill ~-118 ~-1 ~-4 ~-36 ~-1 ~4 minecraft:gray_concrete
fill ~-4 ~-1 ~36 ~4 ~-1 ~118 minecraft:gray_concrete
fill ~-2 ~0 ~-65 ~2 ~0 ~-55 minecraft:blue_concrete
fill ~55 ~0 ~-2 ~65 ~0 ~2 minecraft:red_concrete
fill ~-65 ~0 ~-2 ~-55 ~0 ~2 minecraft:pink_concrete
fill ~-2 ~0 ~55 ~2 ~0 ~65 minecraft:yellow_concrete

# Remove the old centerline waypoint posts so the road is actually passable.
fill ~-1 ~1 ~-46 ~1 ~6 ~-44 minecraft:air
fill ~44 ~1 ~-1 ~46 ~6 ~1 minecraft:air
fill ~-46 ~1 ~-1 ~-44 ~6 ~1 minecraft:air
fill ~-1 ~1 ~44 ~1 ~6 ~46 minecraft:air

# NORTH: Capsule Corporation gateway. Posts sit outside the five-wide road.
fill ~-7 ~0 ~-49 ~-5 ~8 ~-47 minecraft:quartz_pillar
fill ~5 ~0 ~-49 ~7 ~8 ~-47 minecraft:quartz_pillar
fill ~-7 ~8 ~-49 ~7 ~10 ~-47 minecraft:blue_concrete
fill ~-4 ~8 ~-49 ~4 ~9 ~-47 minecraft:light_blue_concrete
fill ~-6 ~1 ~-48 ~-6 ~2 ~-48 minecraft:sea_lantern
fill ~6 ~1 ~-48 ~6 ~2 ~-48 minecraft:sea_lantern

# EAST: World Martial Arts Tournament gateway.
fill ~47 ~0 ~-7 ~49 ~8 ~-5 minecraft:deepslate_bricks
fill ~47 ~0 ~5 ~49 ~8 ~7 minecraft:deepslate_bricks
fill ~47 ~8 ~-7 ~49 ~10 ~7 minecraft:red_concrete
fill ~47 ~8 ~-4 ~49 ~9 ~4 minecraft:orange_concrete
fill ~48 ~1 ~-6 ~48 ~2 ~-6 minecraft:sea_lantern
fill ~48 ~1 ~6 ~48 ~2 ~6 minecraft:sea_lantern

# WEST: Kame House island gateway.
fill ~-49 ~0 ~-7 ~-47 ~8 ~-5 minecraft:cut_sandstone
fill ~-49 ~0 ~5 ~-47 ~8 ~7 minecraft:cut_sandstone
fill ~-49 ~8 ~-7 ~-47 ~10 ~7 minecraft:pink_concrete
fill ~-49 ~8 ~-4 ~-47 ~9 ~4 minecraft:magenta_concrete
fill ~-48 ~1 ~-6 ~-48 ~2 ~-6 minecraft:sea_lantern
fill ~-48 ~1 ~6 ~-48 ~2 ~6 minecraft:sea_lantern

# SOUTH: Kami's Lookout gateway.
fill ~-7 ~0 ~47 ~-5 ~8 ~49 minecraft:quartz_pillar
fill ~5 ~0 ~47 ~7 ~8 ~49 minecraft:quartz_pillar
fill ~-7 ~8 ~47 ~7 ~10 ~49 minecraft:yellow_concrete
fill ~-4 ~8 ~47 ~4 ~9 ~49 minecraft:gold_block
fill ~-6 ~1 ~48 ~-6 ~2 ~48 minecraft:sea_lantern
fill ~6 ~1 ~48 ~6 ~2 ~48 minecraft:sea_lantern

# Small illuminated route markers are set beside, never in, the walking lane.
setblock ~-9 ~1 ~-45 minecraft:sea_lantern
setblock ~45 ~1 ~-9 minecraft:sea_lantern
setblock ~-45 ~1 ~9 minecraft:sea_lantern
setblock ~9 ~1 ~45 minecraft:sea_lantern

# CENTRAL HUB ARCHITECTURE FINISH — frame, pavilion, stations and gardens.
# All ground-level public routes remain clear at Y0.

# Rotunda columns gain plinths, inset blue glazing and stepped gold capitals.
fill ~-18 ~1 ~-18 ~-13 ~2 ~-13 minecraft:chiseled_quartz_block
fill ~13 ~1 ~-18 ~18 ~2 ~-13 minecraft:chiseled_quartz_block
fill ~-18 ~1 ~13 ~-13 ~2 ~18 minecraft:chiseled_quartz_block
fill ~13 ~1 ~13 ~18 ~2 ~18 minecraft:chiseled_quartz_block
fill ~-14 ~8 ~-16 ~-14 ~14 ~-15 minecraft:light_blue_stained_glass
fill ~14 ~8 ~-16 ~14 ~14 ~-15 minecraft:light_blue_stained_glass
fill ~-14 ~8 ~15 ~-14 ~14 ~16 minecraft:light_blue_stained_glass
fill ~14 ~8 ~15 ~14 ~14 ~16 minecraft:light_blue_stained_glass
fill ~-18 ~15 ~-18 ~-13 ~15 ~-13 minecraft:quartz_bricks
fill ~13 ~15 ~-18 ~18 ~15 ~-13 minecraft:quartz_bricks
fill ~-18 ~15 ~13 ~-13 ~15 ~18 minecraft:quartz_bricks
fill ~13 ~15 ~13 ~18 ~15 ~18 minecraft:quartz_bricks

# The center aperture receives a luminous octagonal frame, not a roof over the orb.
fill ~-5 ~18 ~-6 ~5 ~18 ~-6 minecraft:light_blue_stained_glass
fill ~-5 ~18 ~6 ~5 ~18 ~6 minecraft:light_blue_stained_glass
fill ~-6 ~18 ~-5 ~-6 ~18 ~5 minecraft:light_blue_stained_glass
fill ~6 ~18 ~-5 ~6 ~18 ~5 minecraft:light_blue_stained_glass
fill ~-5 ~19 ~-7 ~5 ~19 ~-7 minecraft:gold_block
fill ~-5 ~19 ~7 ~5 ~19 ~7 minecraft:gold_block
fill ~-7 ~19 ~-5 ~-7 ~19 ~5 minecraft:gold_block
fill ~7 ~19 ~-5 ~7 ~19 ~5 minecraft:gold_block
# Roof cornice and spire caps add a final step to the square crown.
fill ~-18 ~20 ~-18 ~18 ~20 ~-17 minecraft:quartz_slab[type=top,waterlogged=false]
fill ~-18 ~20 ~17 ~18 ~20 ~18 minecraft:quartz_slab[type=top,waterlogged=false]
fill ~-18 ~20 ~-16 ~-17 ~20 ~16 minecraft:quartz_slab[type=top,waterlogged=false]
fill ~17 ~20 ~-16 ~18 ~20 ~16 minecraft:quartz_slab[type=top,waterlogged=false]
fill ~-16 ~25 ~-16 ~-15 ~25 ~-15 minecraft:gold_block
fill ~15 ~25 ~-16 ~16 ~25 ~-15 minecraft:gold_block
fill ~-16 ~25 ~15 ~-15 ~25 ~16 minecraft:gold_block
fill ~15 ~25 ~15 ~16 ~25 ~16 minecraft:gold_block

# The north guide pavilion receives a raised blue roof ridge and a clear front sign.
fill ~13 ~8 ~-58 ~29 ~8 ~-41 minecraft:light_blue_concrete
fill ~14 ~9 ~-57 ~28 ~9 ~-42 minecraft:dark_oak_planks
fill ~18 ~10 ~-55 ~24 ~10 ~-44 minecraft:blue_concrete
fill ~19 ~11 ~-54 ~23 ~11 ~-45 minecraft:light_blue_stained_glass
fill ~20 ~12 ~-53 ~22 ~12 ~-46 minecraft:blue_concrete
fill ~16 ~2 ~-42 ~26 ~5 ~-41 minecraft:white_concrete
fill ~18 ~3 ~-42 ~24 ~4 ~-41 minecraft:light_blue_stained_glass
fill ~20 ~6 ~-42 ~22 ~7 ~-41 minecraft:sea_lantern
# Map counter and waiting benches stay behind the public walking face.
fill ~16 ~1 ~-48 ~26 ~1 ~-47 minecraft:dark_oak_slab[type=bottom,waterlogged=false]
fill ~16 ~1 ~-54 ~19 ~1 ~-52 minecraft:dark_oak_slab[type=bottom,waterlogged=false]
fill ~23 ~1 ~-54 ~26 ~1 ~-52 minecraft:dark_oak_slab[type=bottom,waterlogged=false]
setblock ~16 ~2 ~-48 minecraft:lantern
setblock ~26 ~2 ~-48 minecraft:lantern

# Four NPC-ready stations gain matching caps, glowing cores and role-color plinths.
fill ~12 ~6 ~-36 ~18 ~6 ~-30 minecraft:quartz_slab[type=top,waterlogged=false]
fill ~13 ~7 ~-35 ~17 ~7 ~-31 minecraft:blue_concrete
fill ~14 ~8 ~-34 ~16 ~8 ~-32 minecraft:sea_lantern
fill ~30 ~6 ~12 ~36 ~6 ~18 minecraft:quartz_slab[type=top,waterlogged=false]
fill ~31 ~7 ~13 ~35 ~7 ~17 minecraft:red_concrete
fill ~32 ~8 ~14 ~34 ~8 ~16 minecraft:sea_lantern
fill ~-18 ~6 ~30 ~-12 ~6 ~36 minecraft:quartz_slab[type=top,waterlogged=false]
fill ~-17 ~7 ~31 ~-13 ~7 ~35 minecraft:yellow_concrete
fill ~-16 ~8 ~32 ~-14 ~8 ~34 minecraft:sea_lantern
fill ~-36 ~6 ~-18 ~-30 ~6 ~-12 minecraft:quartz_slab[type=top,waterlogged=false]
fill ~-35 ~7 ~-17 ~-31 ~7 ~-13 minecraft:magenta_concrete
fill ~-34 ~8 ~-16 ~-32 ~8 ~-14 minecraft:sea_lantern
# Small colored markers on the public-facing side make each station distinct.
setblock ~15 ~7 ~-36 minecraft:blue_concrete
setblock ~36 ~7 ~15 minecraft:red_concrete
setblock ~-15 ~7 ~36 minecraft:yellow_concrete
setblock ~-36 ~7 ~-15 minecraft:magenta_concrete

# Cherry gardens gain layered, uneven canopies and a perimeter seat/lantern rhythm.
fill ~-32 ~7 ~-30 ~-24 ~7 ~-26 minecraft:cherry_leaves[persistent=true]
fill ~-30 ~8 ~-32 ~-26 ~8 ~-24 minecraft:cherry_leaves[persistent=true]
fill ~25 ~7 ~-30 ~31 ~7 ~-26 minecraft:cherry_leaves[persistent=true]
fill ~26 ~8 ~-32 ~30 ~8 ~-24 minecraft:cherry_leaves[persistent=true]
fill ~-32 ~7 ~26 ~-24 ~7 ~30 minecraft:cherry_leaves[persistent=true]
fill ~-30 ~8 ~24 ~-26 ~8 ~32 minecraft:cherry_leaves[persistent=true]
fill ~25 ~7 ~26 ~31 ~7 ~30 minecraft:cherry_leaves[persistent=true]
fill ~26 ~8 ~24 ~30 ~8 ~32 minecraft:cherry_leaves[persistent=true]
setblock ~-35 ~1 ~-35 minecraft:lantern
setblock ~35 ~1 ~-35 minecraft:lantern
setblock ~-35 ~1 ~35 minecraft:lantern
setblock ~35 ~1 ~35 minecraft:lantern

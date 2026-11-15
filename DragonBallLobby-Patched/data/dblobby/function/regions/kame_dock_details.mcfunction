# Extend the existing wooden dock into a small, lit lookout pier.
# The original dock ends around Z +43; this continues it over the shoreline.
fill ~-140 ~-2 ~42 ~-132 ~-2 ~56 minecraft:oak_planks
# Four support piles beneath the outer pier corners.
fill ~-140 ~-5 ~44 ~-140 ~-2 ~44 minecraft:stripped_dark_oak_log
fill ~-132 ~-5 ~44 ~-132 ~-2 ~44 minecraft:stripped_dark_oak_log
fill ~-140 ~-5 ~54 ~-140 ~-2 ~54 minecraft:stripped_dark_oak_log
fill ~-132 ~-5 ~54 ~-132 ~-2 ~54 minecraft:stripped_dark_oak_log
# Low safety rails along the long edges.
fill ~-140 ~-1 ~45 ~-140 ~-1 ~53 minecraft:dark_oak_fence
fill ~-132 ~-1 ~45 ~-132 ~-1 ~53 minecraft:dark_oak_fence
# Lantern posts at the end of the pier.
fill ~-140 ~-1 ~55 ~-140 ~1 ~55 minecraft:dark_oak_fence
fill ~-132 ~-1 ~55 ~-132 ~1 ~55 minecraft:dark_oak_fence
setblock ~-140 ~2 ~55 minecraft:lantern
setblock ~-132 ~2 ~55 minecraft:lantern
# A wider landing pad and two mooring bollards.
fill ~-141 ~-2 ~54 ~-131 ~-2 ~57 minecraft:dark_oak_planks
setblock ~-142 ~-1 ~56 minecraft:oak_fence
setblock ~-130 ~-1 ~56 minecraft:oak_fence

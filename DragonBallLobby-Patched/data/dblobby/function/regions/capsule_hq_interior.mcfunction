# CAPSULE CORPORATION HQ — side-wing reception and research interior.
# The north-south boulevard remains an unobstructed 11-block-wide passage at X -5..+5.
# Public furniture occupies the west/east side bays only.

# Level circulation floor and two blue guiding lines, outside the main boulevard.
fill ~-18 ~2 ~-175 ~-7 ~2 ~-151 minecraft:smooth_quartz
fill ~7 ~2 ~-175 ~18 ~2 ~-151 minecraft:smooth_quartz
fill ~-17 ~2 ~-174 ~-8 ~2 ~-173 minecraft:light_blue_concrete
fill ~8 ~2 ~-174 ~17 ~2 ~-173 minecraft:light_blue_concrete

# West reception suite: low desk, staff station and a clear visitor counter.
fill ~-16 ~3 ~-171 ~-9 ~3 ~-168 minecraft:blue_concrete
fill ~-16 ~4 ~-171 ~-9 ~4 ~-168 minecraft:light_blue_concrete
fill ~-15 ~5 ~-170 ~-10 ~5 ~-169 minecraft:glass
fill ~-14 ~6 ~-170 ~-11 ~7 ~-169 minecraft:light_blue_stained_glass
setblock ~-12 ~8 ~-169 minecraft:sea_lantern
setblock ~-15 ~4 ~-167 minecraft:lectern
setblock ~-10 ~4 ~-167 minecraft:cartography_table
# Recessed Capsule emblem and visitor queue bench.
fill ~-18 ~5 ~-173 ~-18 ~9 ~-166 minecraft:blue_concrete
fill ~-18 ~6 ~-172 ~-18 ~8 ~-167 minecraft:light_blue_stained_glass
fill ~-17 ~7 ~-172 ~-17 ~7 ~-167 minecraft:white_concrete
fill ~-15 ~3 ~-160 ~-9 ~3 ~-160 minecraft:dark_oak_slab[type=bottom]
fill ~-15 ~4 ~-160 ~-9 ~4 ~-160 minecraft:cyan_concrete
setblock ~-15 ~3 ~-158 minecraft:lantern
setblock ~-9 ~3 ~-158 minecraft:lantern

# East research wing: three glass capsule containment pods and console desks.
fill ~9 ~3 ~-172 ~16 ~3 ~-168 minecraft:polished_diorite
fill ~10 ~4 ~-171 ~12 ~8 ~-169 minecraft:light_blue_stained_glass
fill ~13 ~4 ~-171 ~15 ~8 ~-169 minecraft:cyan_stained_glass
fill ~10 ~4 ~-165 ~12 ~8 ~-163 minecraft:light_blue_stained_glass
fill ~13 ~4 ~-165 ~15 ~8 ~-163 minecraft:cyan_stained_glass
setblock ~11 ~6 ~-170 minecraft:sea_lantern
setblock ~14 ~6 ~-170 minecraft:sea_lantern
setblock ~11 ~6 ~-164 minecraft:sea_lantern
setblock ~14 ~6 ~-164 minecraft:sea_lantern
fill ~9 ~3 ~-158 ~16 ~3 ~-155 minecraft:blue_concrete
fill ~10 ~4 ~-158 ~15 ~5 ~-155 minecraft:light_blue_stained_glass
setblock ~12 ~6 ~-156 minecraft:sea_lantern
setblock ~14 ~6 ~-156 minecraft:sea_lantern

# Structural columns define the interior bays without entering the boulevard.
fill ~-7 ~3 ~-174 ~-7 ~12 ~-172 minecraft:quartz_pillar
fill ~7 ~3 ~-174 ~7 ~12 ~-172 minecraft:quartz_pillar
fill ~-7 ~3 ~-154 ~-7 ~12 ~-152 minecraft:quartz_pillar
fill ~7 ~3 ~-154 ~7 ~12 ~-152 minecraft:quartz_pillar
fill ~-8 ~11 ~-174 ~-6 ~12 ~-172 minecraft:gold_block
fill ~6 ~11 ~-174 ~8 ~12 ~-172 minecraft:gold_block
fill ~-8 ~11 ~-154 ~-6 ~12 ~-152 minecraft:gold_block
fill ~6 ~11 ~-154 ~8 ~12 ~-152 minecraft:gold_block

# West and east visitor galleries: paired benches, capsule displays and wall screens.
fill ~-16 ~3 ~-155 ~-10 ~3 ~-152 minecraft:dark_oak_slab[type=bottom]
fill ~-15 ~4 ~-155 ~-11 ~4 ~-152 minecraft:cyan_concrete
fill ~10 ~3 ~-151 ~16 ~3 ~-151 minecraft:dark_oak_slab[type=bottom]
fill ~10 ~4 ~-151 ~16 ~5 ~-151 minecraft:blue_concrete
fill ~11 ~6 ~-151 ~15 ~8 ~-151 minecraft:light_blue_stained_glass
fill ~-17 ~5 ~-158 ~-17 ~9 ~-153 minecraft:light_blue_stained_glass
fill ~17 ~5 ~-158 ~17 ~9 ~-153 minecraft:cyan_stained_glass
fill ~-16 ~10 ~-158 ~-11 ~10 ~-153 minecraft:sea_lantern
fill ~11 ~10 ~-158 ~16 ~10 ~-153 minecraft:sea_lantern

# Overhead light bands sit beneath the dome and illuminate both wings.
fill ~-16 ~14 ~-172 ~-8 ~14 ~-172 minecraft:sea_lantern
fill ~8 ~14 ~-172 ~16 ~14 ~-172 minecraft:sea_lantern
fill ~-16 ~14 ~-154 ~-8 ~14 ~-154 minecraft:sea_lantern
fill ~8 ~14 ~-154 ~16 ~14 ~-154 minecraft:sea_lantern
setblock ~-17 ~13 ~-163 minecraft:sea_lantern
setblock ~17 ~13 ~-163 minecraft:sea_lantern
# A slim blue light column in the research bay acts as a visual centrepiece.
fill ~12 ~3 ~-162 ~14 ~3 ~-160 minecraft:quartz_block
fill ~12 ~4 ~-162 ~14 ~12 ~-160 minecraft:light_blue_stained_glass
fill ~13 ~4 ~-162 ~13 ~12 ~-160 minecraft:sea_lantern
fill ~11 ~12 ~-163 ~15 ~12 ~-159 minecraft:gold_block

# Re-clear the central transit corridor after placing all interior details.
fill ~-5 ~3 ~-185 ~5 ~13 ~-141 minecraft:air

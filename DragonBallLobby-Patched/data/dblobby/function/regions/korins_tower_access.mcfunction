# KORIN'S TOWER — exterior stair arrival, interior climbing shaft and rest landings. All vertical coordinates raised by 32 blocks.
# Carve a narrow vertical route through the otherwise solid terracotta core.
fill ~-158 ~14 ~152 ~-152 ~132 ~158 minecraft:air
# Reinforced inner shaft walls.
fill ~-159 ~14 ~151 ~-159 ~132 ~159 minecraft:cut_sandstone
fill ~-151 ~14 ~151 ~-151 ~132 ~159 minecraft:cut_sandstone
fill ~-159 ~14 ~151 ~-151 ~132 ~151 minecraft:cut_sandstone
fill ~-159 ~14 ~159 ~-151 ~132 ~159 minecraft:cut_sandstone
# Climb ladder on the west interior wall.
fill ~-158 ~15 ~153 ~-158 ~132 ~153 minecraft:ladder[facing=east]
# Ground-level approach corridor connects the entry arch to the shaft.
fill ~-158 ~13 ~145 ~-152 ~13 ~151 minecraft:smooth_sandstone
# Rest platforms at regular intervals; a 3x3 ladder opening remains clear.
fill ~-158 ~32 ~152 ~-152 ~32 ~158 minecraft:smooth_sandstone
fill ~-158 ~32 ~152 ~-156 ~32 ~154 minecraft:air
fill ~-158 ~57 ~152 ~-152 ~57 ~158 minecraft:smooth_sandstone
fill ~-158 ~57 ~152 ~-156 ~57 ~154 minecraft:air
fill ~-158 ~82 ~152 ~-152 ~82 ~158 minecraft:smooth_sandstone
fill ~-158 ~82 ~152 ~-156 ~82 ~154 minecraft:air
fill ~-158 ~107 ~152 ~-152 ~107 ~158 minecraft:smooth_sandstone
fill ~-158 ~107 ~152 ~-156 ~107 ~154 minecraft:air
# Lit rest alcoves on alternating sides.
setblock ~-154 ~33 ~154 minecraft:lantern
setblock ~-154 ~58 ~154 minecraft:lantern
setblock ~-154 ~83 ~154 minecraft:lantern
setblock ~-154 ~108 ~154 minecraft:lantern
# Open the summit pavilion floor into the shaft and provide a safe arrival landing.
fill ~-158 ~133 ~152 ~-152 ~135 ~158 minecraft:air
fill ~-161 ~133 ~149 ~-149 ~133 ~161 minecraft:smooth_sandstone
fill ~-160 ~134 ~150 ~-150 ~134 ~160 minecraft:air
# Broad exterior stair climbs from world level to the cloud plinth and tower entrance.
fill ~-165 ~0 ~206 ~-145 ~0 ~207 minecraft:stone_bricks
fill ~-165 ~0 ~204 ~-145 ~1 ~205 minecraft:stone_bricks
fill ~-165 ~0 ~202 ~-145 ~2 ~203 minecraft:stone_bricks
fill ~-165 ~0 ~200 ~-145 ~3 ~201 minecraft:stone_bricks
fill ~-165 ~0 ~198 ~-145 ~4 ~199 minecraft:stone_bricks
fill ~-165 ~0 ~196 ~-145 ~5 ~197 minecraft:stone_bricks
fill ~-165 ~0 ~194 ~-145 ~6 ~195 minecraft:stone_bricks
fill ~-165 ~0 ~192 ~-145 ~7 ~193 minecraft:stone_bricks
fill ~-165 ~0 ~190 ~-145 ~8 ~191 minecraft:stone_bricks
fill ~-165 ~0 ~188 ~-145 ~9 ~189 minecraft:stone_bricks
fill ~-165 ~0 ~186 ~-145 ~10 ~187 minecraft:stone_bricks
fill ~-166 ~0 ~209 ~-144 ~0 ~210 minecraft:smooth_sandstone
fill ~-165 ~0 ~208 ~-145 ~0 ~209 minecraft:stone_brick_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]
fill ~-166 ~0 ~208 ~-166 ~0 ~209 minecraft:stone_brick_wall
fill ~-144 ~0 ~208 ~-144 ~0 ~209 minecraft:stone_brick_wall
fill ~-165 ~1 ~206 ~-145 ~1 ~207 minecraft:stone_brick_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]
fill ~-166 ~1 ~206 ~-166 ~1 ~207 minecraft:stone_brick_wall
fill ~-144 ~1 ~206 ~-144 ~1 ~207 minecraft:stone_brick_wall
fill ~-165 ~2 ~204 ~-145 ~2 ~205 minecraft:stone_brick_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]
fill ~-166 ~2 ~204 ~-166 ~2 ~205 minecraft:stone_brick_wall
fill ~-144 ~2 ~204 ~-144 ~2 ~205 minecraft:stone_brick_wall
fill ~-165 ~3 ~202 ~-145 ~3 ~203 minecraft:stone_brick_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]
fill ~-166 ~3 ~202 ~-166 ~3 ~203 minecraft:stone_brick_wall
fill ~-144 ~3 ~202 ~-144 ~3 ~203 minecraft:stone_brick_wall
fill ~-165 ~4 ~200 ~-145 ~4 ~201 minecraft:stone_brick_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]
fill ~-166 ~4 ~200 ~-166 ~4 ~201 minecraft:stone_brick_wall
fill ~-144 ~4 ~200 ~-144 ~4 ~201 minecraft:stone_brick_wall
fill ~-165 ~5 ~198 ~-145 ~5 ~199 minecraft:stone_brick_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]
fill ~-166 ~5 ~198 ~-166 ~5 ~199 minecraft:stone_brick_wall
fill ~-144 ~5 ~198 ~-144 ~5 ~199 minecraft:stone_brick_wall
fill ~-165 ~6 ~196 ~-145 ~6 ~197 minecraft:stone_brick_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]
fill ~-166 ~6 ~196 ~-166 ~6 ~197 minecraft:stone_brick_wall
fill ~-144 ~6 ~196 ~-144 ~6 ~197 minecraft:stone_brick_wall
fill ~-165 ~7 ~194 ~-145 ~7 ~195 minecraft:stone_brick_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]
fill ~-166 ~7 ~194 ~-166 ~7 ~195 minecraft:stone_brick_wall
fill ~-144 ~7 ~194 ~-144 ~7 ~195 minecraft:stone_brick_wall
fill ~-165 ~8 ~192 ~-145 ~8 ~193 minecraft:stone_brick_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]
fill ~-166 ~8 ~192 ~-166 ~8 ~193 minecraft:stone_brick_wall
fill ~-144 ~8 ~192 ~-144 ~8 ~193 minecraft:stone_brick_wall
fill ~-165 ~9 ~190 ~-145 ~9 ~191 minecraft:stone_brick_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]
fill ~-166 ~9 ~190 ~-166 ~9 ~191 minecraft:stone_brick_wall
fill ~-144 ~9 ~190 ~-144 ~9 ~191 minecraft:stone_brick_wall
fill ~-165 ~10 ~188 ~-145 ~10 ~189 minecraft:stone_brick_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]
fill ~-166 ~10 ~188 ~-166 ~10 ~189 minecraft:stone_brick_wall
fill ~-144 ~10 ~188 ~-144 ~10 ~189 minecraft:stone_brick_wall
fill ~-165 ~11 ~186 ~-145 ~11 ~187 minecraft:stone_brick_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]
fill ~-166 ~11 ~186 ~-166 ~11 ~187 minecraft:stone_brick_wall
fill ~-144 ~11 ~186 ~-144 ~11 ~187 minecraft:stone_brick_wall
# The raised bridge is supported at the cliff edge and joins the tower's south entry.
fill ~-164 ~3 ~181 ~-146 ~11 ~185 minecraft:quartz_block
fill ~-165 ~-1 ~186 ~-145 ~-1 ~209 minecraft:stone_bricks
fill ~-164 ~12 ~151 ~-146 ~12 ~185 minecraft:smooth_sandstone
fill ~-163 ~12 ~180 ~-147 ~12 ~151 minecraft:gold_block
fill ~-162 ~12 ~180 ~-148 ~12 ~151 minecraft:smooth_sandstone
fill ~-164 ~3 ~181 ~-164 ~11 ~185 minecraft:quartz_pillar
fill ~-146 ~3 ~181 ~-146 ~11 ~185 minecraft:quartz_pillar
fill ~-162 ~13 ~150 ~-148 ~13 ~150 minecraft:stone_brick_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]
fill ~-162 ~13 ~145 ~-148 ~13 ~149 minecraft:smooth_sandstone
setblock ~-165 ~1 ~206 minecraft:lantern
setblock ~-145 ~1 ~206 minecraft:lantern
setblock ~-165 ~12 ~182 minecraft:sea_lantern
setblock ~-145 ~12 ~182 minecraft:sea_lantern

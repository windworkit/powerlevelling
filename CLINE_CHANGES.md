# Cline change log

Cline: add an entry for every task (date, task number, every file path you created or modified).

- **11/14/2026** (Task T1):
  - created: C:\Users\kakal\Documents\PowerLevelling\src\main\resources\assets\powerlevelling\sounds.json
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\resources\assets\powerlevelling\lang\en_us.json
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\combat\CombatInputHandler.java


- **11/14/2026** (Task T2):
  - created: C:\Users\kakal\Documents\PowerLevelling\src\main\resources\data\powerlevelling\powerlevelling\ki_attack\heavy_blast.json
  - created: C:\Users\kakal\Documents\PowerLevelling\src\main\resources\data\powerlevelling\powerlevelling\ki_attack\piercing_shot.json
  - created: C:\Users\kakal\Documents\PowerLevelling\src\main\resources\data\powerlevelling\powerlevelling\ki_attack\scatter_charge.json
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\resources\assets\powerlevelling\lang\en_us.json (added 3 new ki attack names)

- **11/14/2026** (Task T3):
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\command\ModCommands.java (restructured help, added command aliases: /pl stats, /pl me, /pl give exp, /pl help admin)
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\resources\assets\powerlevelling\lang\en_us.json (renamed help group)

- **11/14/2026** (Task T4):
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\item\RebirthOrbItem.java (added lore flavor text)
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\resources\assets\powerlevelling\lang\en_us.json (added rebirth_orb.lore key)

- **11/14/2026** (Task T5):
  - modified: C:\Users\kakal\Documents\PowerLevelling\README.md (rewritten for players/server owners)

- **11/14/2026** (Task T6):
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\config\ServerConfig.java (improved all comments with defaults and ranges)
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\config\ClientConfig.java (improved all comments with defaults and ranges)

- **11/14/2026** (Task T7):
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\registry\ModRegistries.java (added CREATIVE_MODE_TABS registry and registration)
  - created: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\registry\ModCreativeTabs.java (Power Levelling creative tab with 4 items, Rebirth Orb icon)
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\resources\assets\powerlevelling\lang\en_us.json (added itemGroup.powerlevelling)

- **11/14/2026** (Task T8):
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\PowerLevelling.java (registered IConfigScreenFactory for built-in ConfigurationScreen, client-only)

- **11/14/2026** (Task T9):
  - created: C:\Users\kakal\Documents\PowerLevelling\src\main\resources\data\powerlevelling\advancement\root.json (root advancement with Rebirth Orb icon)
  - created: C:\Users\kakal\Documents\PowerLevelling\src\main\resources\data\powerlevelling\advancement\fresh_start.json (obtain Rebirth Orb)
  - created: C:\Users\kakal\Documents\PowerLevelling\src\main\resources\data\powerlevelling\advancement\new_look.json (obtain Style Capsule)
  - created: C:\Users\kakal\Documents\PowerLevelling\src\main\resources\data\powerlevelling\advancement\attuned.json (obtain Attunement Crystal)
  - created: C:\Users\kakal\Documents\PowerLevelling\src\main\resources\data\powerlevelling\advancement\reborn.json (obtain Reincarnation Orb, challenge frame)
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\resources\assets\powerlevelling\lang\en_us.json (added 11 advancement title/description keys)

- **11/14/2026** (Task T11):
  - created: C:\Users\kakal\Documents\PowerLevelling\src\main\resources\data\powerlevelling\loot_table\chests\rare_capsules.json (loot table: 1 roll, 85% empty, 10% Style Capsule, 5% Attunement Crystal)
  - created: C:\Users\kakal\Documents\PowerLevelling\src\main\resources\data\powerlevelling\loot_modifiers\add_capsules.json (NeoForge add_table modifier with alternative condition for 3 chest types)
  - created: C:\Users\kakal\Documents\PowerLevelling\src\main\resources\data\neoforge\loot_modifiers\global_loot_modifiers.json (registers add_capsules with replace=false)

- **11/14/2026** (Task T12):
  - created: C:\Users\kakal\Documents\PowerLevelling\tools\create_spanish_translation.py (translation script)
  - created: C:\Users\kakal\Documents\PowerLevelling\src\main\resources\assets\powerlevelling\lang\es_es.json (Spanish translation with 79 keys translated, 328 fallback to English)

- **11/14/2026** (Task T13):
  - created: C:\Users\kakal\Documents\PowerLevelling\tools\gen_antenna_horn_textures.py (texture generator)
  - created: C:\Users\kakal\Documents\PowerLevelling\src\main\resources\assets\powerlevelling\powerlevelling\geo\antenna\antenna_short.geo.json
  - created: C:\Users\kakal\Documents\PowerLevelling\src\main\resources\assets\powerlevelling\powerlevelling\geo\antenna\antenna_long.geo.json
  - created: C:\Users\kakal\Documents\PowerLevelling\src\main\resources\assets\powerlevelling\powerlevelling\geo\horn\horn_small.geo.json
  - created: C:\Users\kakal\Documents\PowerLevelling\src\main\resources\assets\powerlevelling\textures\entity\antenna\antenna_short.png (32x32 grayscale)
  - created: C:\Users\kakal\Documents\PowerLevelling\src\main\resources\assets\powerlevelling\textures\entity\antenna\antenna_long.png (32x32 grayscale)
  - created: C:\Users\kakal\Documents\PowerLevelling\src\main\resources\assets\powerlevelling\textures\entity\horn\horn_small.png (32x32 grayscale)

- **11/14/2026** (Task T14):
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\resources\data\powerlevelling\powerlevelling\ki_attack\ki_blast.json (fire_sound -> powerlevelling:ki_blast_fire, impact_sound -> powerlevelling:ki_impact)
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\resources\data\powerlevelling\powerlevelling\ki_attack\charged_blast.json (same)
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\resources\data\powerlevelling\powerlevelling\ki_attack\rapid_blast.json (same)
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\resources\data\powerlevelling\powerlevelling\ki_attack\heavy_blast.json (same)
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\resources\data\powerlevelling\powerlevelling\ki_attack\piercing_shot.json (same)
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\resources\data\powerlevelling\powerlevelling\ki_attack\scatter_charge.json (same)

- **11/14/2026** (More Advancements + Recipe Book):
  - created 18 new advancement JSON files (level_10, level_25, level_50, mastery_5, mastery_25, mastery_max, first_character, first_ki_charge, first_ki_blast, zenkai_triggered, ki_kill, ki_kill_streak, defeat_boss, unlock_style_capsule, unlock_attunement_crystal, unlock_rebirth_orb, unlock_reincarnation_orb, unlock_style_capsule)
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\resources\data\powerlevelling\advancement\level_10.json (recipe reward)
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\resources\data\powerlevelling\advancement\level_25.json (recipe reward)
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\resources\data\powerlevelling\advancement\level_50.json (recipe reward)
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\resources\data\powerlevelling\advancement\mastery_5.json (recipe reward)
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\resources\assets\powerlevelling\lang\en_us.json (44 new advancement keys)

- **11/14/2026** (Ki Aura Particles):
  - created: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\client\fx\KiAuraParticle.java (new aura particle class with orbiting behavior)
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\registry\ModParticles.java (added KI_AURA particle type)
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\config\ClientConfig.java (added auraParticleDensity config)
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\client\ClientEvents.java (registered KiAuraParticle provider)
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\client\fx\KiFx.java (added aura spawning logic for combat stance and ki charging)
  - created: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\client\fx\KiAuraParticle.java (new aura particle class with orbiting behavior, hard cap of 200)

- **11/14/2026** (Fix: World load crash):
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\client\fx\KiAuraParticle.java (added own PROVIDER_SPRITES static field, provider no longer overwrites KiSparkParticle.SHARED)
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\client\fx\KiFx.java (spawnAuraForPlayer no longer references auraSprite, aura particles use their own sprite set)

- **11/14/2026** (Feature: Super Saiyan 1 Transformation):
  - created: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\combat\Transform.java (new transform handling class)
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\combat\CombatAction.java (added TRANSFORM action)
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\network\VisualStatePayload.java (added SUPER_SAIYAN flag)
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\data\CombatState.java (added form field)
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\network\ModNetwork.java (added SUPER_SAIYAN to visualFlags)
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\combat\CombatInputHandler.java (added TRANSFORM handling)
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\client\ModKeys.java (added TRANSFORM key binding - G)
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\client\ClientVisualState.java (added hasSuperSaiyan helper)
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\client\render\HairLayer.java (gold hair when SSJ)
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\client\render\EyeLayer.java (blue eyes when SSJ)
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\client\fx\KiFx.java (SSJ golden aura + sparks)
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\event\PlayerEvents.java (SSJ ki drain tick + cleanup on death/logout/dimension change)
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\resources\assets\powerlevelling\lang\en_us.json (added transform key translation)
- **11/14/2026** (Feature: Hold-to-Transform with Aura Animation):
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\network\VisualStatePayload.java (added TRANSFORMING flag)
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\data\CombatState.java (added transforming boolean + transformTicks fields)
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\network\ModNetwork.java (syncVisual + visualFlags include TRANSFORMING, send transformTicks)
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\client\ClientVisualState.java (TRANSFORMING flag handling, local tick counting, get() method)
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\client\input\InputHandler.java (TRANSFORM changed from pressOnly to trackKey, prediction for TRANSFORMING)
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\client\ClientCombat.java (rollback for rejected TRANSFORM)
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\combat\Transform.java (complete rewrite: startTransform/stopTransform/tickTransform with 40-tick hold, TRANSFORM_HOLD_TICKS constant)
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\combat\CombatInputHandler.java (calls startTransform on PRESS, stopTransform on RELEASE)
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\event\PlayerEvents.java (calls Transform.tickTransform each tick)
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\client\fx\KiFx.java (TRANSFORMING check for aura particles, increased particle count during transform)
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\client\render\AuraLayer.java (complete rewrite: renders aura geo model, scales/pulses based on charge/transform ticks, gold for SSJ, ki color otherwise)
- **11/14/2026** (Datapack: Replaced DragonBallLobby-Patched-1.0):
  - deleted: C:\Users\kakal\Documents\PowerLevelling\run\datapacks\DragonBallLobby-Patched-1.0 (old 23-file version)
  - deleted: C:\Users\kakal\Documents\PowerLevelling\run\saves\hi\datapacks\DragonBallLobby-Patched-1.0 (old 23-file version)
  - installed: C:\Users\kakal\Documents\PowerLevelling\run\datapacks\DragonBallLobby-Patched-1.0 (new 45-file version with expanded regions: capsule_city, kame_island, kamis_lookout_expansion, korins_tower, shenron_giant_form, hub, etc.)
  - installed: C:\Users\kakal\Documents\PowerLevelling\run\saves\hi\datapacks\DragonBallLobby-Patched-1.0 (same new version)
  - deleted: C:\Users\kakal\Documents\PowerLevelling\DragonBallLobby-Patched-1.0 (1).zip (original source zip)
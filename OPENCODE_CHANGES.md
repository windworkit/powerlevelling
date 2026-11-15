# OpenCode change log

OpenCode: add an entry for every task (date, task id, AI model, every file path created or modified) in the same turn
as the change. Newest entries go last. Full paths, one per line. All work recorded here so far was done by
**DeepSeek V4.1 Flash Free** (provider `opencode`, model id `deepseek-v4.1-flash-free`).

- **11/14/2026** (Task O1 · DeepSeek V4.1 Flash Free): Super Saiyan rework
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\config\ServerConfig.java
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\combat\Transform.java
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\progression\StatEffects.java
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\combat\KiCharge.java
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\combat\CombatInputHandler.java
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\data\CombatState.java
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\event\PlayerEvents.java
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\resources\data\powerlevelling\powerlevelling\race\half_saiyan.json

- **11/14/2026** (Task O2 · DeepSeek V4.1 Flash Free): Advancements obtainable
  - created: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\progression\Advancements.java
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\character\CharacterService.java
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\combat\KiCharge.java
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\progression\Progression.java
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\progression\Zenkai.java
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\event\ProgressionEvents.java
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\data\CombatState.java
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\event\PlayerEvents.java
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\ki\KiSkills.java
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\resources\data\powerlevelling\advancement\first_ki_blast.json
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\resources\data\powerlevelling\advancement\ki_kill.json

- **11/14/2026** (Task O3 · DeepSeek V4.1 Flash Free): Command permissions + key conflict
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\command\ModCommands.java
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\client\ModKeys.java

- **11/14/2026** (Task O4 · DeepSeek V4.1 Flash Free): Localization + audio polish
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\resources\assets\powerlevelling\lang\en_us.json
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\resources\assets\powerlevelling\lang\es_es.json
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\resources\assets\powerlevelling\sounds.json

- **11/14/2026** (Task O5 · DeepSeek V4.1 Flash Free): README accuracy
  - modified: C:\Users\kakal\Documents\PowerLevelling\README.md

- **11/14/2026** (Task O6 · DeepSeek V4.1 Flash Free): Workflow bookkeeping
  - created: C:\Users\kakal\Documents\PowerLevelling\OPENCODE_TASKS.md
  - created: C:\Users\kakal\Documents\PowerLevelling\OPENCODE_CHANGES.md

Verification for O1-O5: `gradlew build` BUILD SUCCESSFUL; all resources JSON parse; `es_es.json` 475/475 keys with
`en_us.json`; jar `build\libs\powerlevelling-0.1.0.jar` rebuilt (813,310 bytes). Backup taken before edits at
`%TEMP%\opencode\PowerLevelling-backup-20261114-185706`.

- **11/14/2026** (Task O15 · DeepSeek V4.1 Flash Free): Dash / dodge
  - created: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\combat\Dash.java
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\config\ServerConfig.java
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\data\CombatState.java
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\combat\CombatInputHandler.java
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\network\CombatInputPayload.java
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\client\input\InputHandler.java
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\registry\ModSounds.java
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\resources\assets\powerlevelling\sounds.json
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\resources\assets\powerlevelling\lang\en_us.json
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\resources\assets\powerlevelling\lang\es_es.json
  - modified: C:\Users\kakal\Documents\PowerLevelling\README.md
  - modified: C:\Users\kakal\Documents\PowerLevelling\OPENCODE_TASKS.md
  - modified: C:\Users\kakal\Documents\PowerLevelling\OPENCODE_CHANGES.md

Verification for O15: `gradlew build` BUILD SUCCESSFUL; all resources JSON parse; `es_es.json` 477/477 keys with
`en_us.json`; `sounds.json` `dash` event resolves to `minecraft:entity.player.attack.sweep`; jar
`build\libs\powerlevelling-0.1.0.jar` rebuilt (817,019 bytes).

- **11/14/2026** (Task O16 · DeepSeek V4.1 Flash Free): Ki visual rework (Dragon Ball style)
  - created: C:\Users\kakal\Documents\PowerLevelling\src\main\resources\assets\powerlevelling\textures\entity\ki_glow.png
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\client\render\KiOrbModel.java
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\client\render\KiOrbStyles.java
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\client\fx\BeamRenderer.java
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\client\fx\KiWorldFx.java
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\client\render\AuraLayer.java
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\client\fx\AuraFx.java
  - modified: C:\Users\kakal\Documents\PowerLevelling\OPENCODE_TASKS.md
  - modified: C:\Users\kakal\Documents\PowerLevelling\OPENCODE_CHANGES.md

Verification for O16: `gradlew build` BUILD SUCCESSFUL; all resources JSON parse; `es_es.json` 477/477 keys with
`en_us.json`; new texture `ki_glow.png` (64x64) created; jar `build\libs\powerlevelling-0.1.0.jar` rebuilt (823,478 bytes).

- **11/14/2026** (Task O17 · FreeLLMAPI Auto (opencode)): Dragon Ball aura particle system
  - created: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\client\fx\KiAuraWispParticle.java
  - created: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\client\fx\KiRockParticle.java
  - created: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\client\fx\KiDebrisParticle.java
  - created: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\client\fx\KiRockLargeParticle.java
  - created: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\client\fx\KiGroundRingParticle.java
  - created: C:\Users\kakal\Documents\PowerLevelling\src\main\resources\assets\powerlevelling\textures\particle\ki_aura_wisp.png
  - created: C:\Users\kakal\Documents\PowerLevelling\src\main\resources\assets\powerlevelling\textures\particle\ki_rock.png
  - created: C:\Users\kakal\Documents\PowerLevelling\src\main\resources\assets\powerlevelling\textures\particle\ki_debris.png
  - created: C:\Users\kakal\Documents\PowerLevelling\src\main\resources\assets\powerlevelling\textures\particle\ki_rock_large.png
  - created: C:\Users\kakal\Documents\PowerLevelling\src\main\resources\assets\powerlevelling\textures\particle\ki_ground_ring.png
  - created: C:\Users\kakal\Documents\PowerLevelling\src\main\resources\assets\powerlevelling\particles\ki_aura_wisp.json
  - created: C:\Users\kakal\Documents\PowerLevelling\src\main\resources\assets\powerlevelling\particles\ki_rock.json
  - created: C:\Users\kakal\Documents\PowerLevelling\src\main\resources\assets\powerlevelling\particles\ki_debris.json
  - created: C:\Users\kakal\Documents\PowerLevelling\src\main\resources\assets\powerlevelling\particles\ki_rock_large.json
  - created: C:\Users\kakal\Documents\PowerLevelling\src\main\resources\assets\powerlevelling\particles\ki_ground_ring.json
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\registry\ModParticles.java
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\client\ClientEvents.java
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\client\fx\AuraFx.java
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\client\fx\KiFx.java
  - modified: C:\Users\kakal\Documents\PowerLevelling\OPENCODE_TASKS.md
  - modified: C:\Users\kakal\Documents\PowerLevelling\OPENCODE_CHANGES.md
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\client\render\AuraLayer.java

Verification for O17: `gradlew build` BUILD SUCCESSFUL (compileJava ran fresh, no cache); all particle JSONs parse; 5 new
particle textures created (32x32 wisp, 16x16 rock, 8x8 debris, 24x24 large rock, 64x64 ground ring); jar
`build\libs\powerlevelling-0.1.0.jar` rebuilt (848,155 bytes).

- **11/14/2026** (Task O18 · FreeLLMAPI Auto (opencode)): SSJ flame shell + lightning crackle
  - created: C:\Users\kakal\Documents\PowerLevelling\src\main\resources\assets\powerlevelling\textures\entity\aura\aura_flame.png
  - created: C:\Users\kakal\Documents\PowerLevelling\src\main\resources\assets\powerlevelling\textures\entity\aura\aura_bolt.png
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\client\render\AuraLayer.java
  - modified: C:\Users\kakal\Documents\PowerLevelling\OPENCODE_TASKS.md
  - modified: C:\Users\kakal\Documents\PowerLevelling\OPENCODE_CHANGES.md

Verification for O18: `gradlew build` BUILD SUCCESSFUL; new aura textures: `aura_flame.png` (64x128 teardrop flame
silhouette with spiky fringe), `aura_bolt.png` (8x8 white); `AuraLayer` now has `renderFlameShell` (12-segment
flame cylinder, inner+outer faces, gold-tinted for SSJ) and `renderLightningCrackle` (branching jagged bolts,
deterministic seeds, flicker-gated, frequent when charging/transforming); jar rebuilt.

- **11/14/2026** (Task O19 · FreeLLMAPI Auto (opencode)): Aura LOD (Level of Detail)
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\config\ClientConfig.java
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\client\render\AuraLayer.java
  - modified: C:\Users\kakal\Documents\PowerLevelling\OPENCODE_TASKS.md
  - modified: C:\Users\kakal\Documents\PowerLevelling\OPENCODE_CHANGES.md

Verification for O19: `gradlew build` BUILD SUCCESSFUL; 12 new config entries for LOD (enabled, 3 distances, 3 wisp counts, 2 flame shell toggles, 2 lightning toggles); `AuraLayer` computes `lodTier` from camera distance via `Minecraft.getInstance().gameRenderer.getMainCamera()`; all render methods accept `lodTier` and scale complexity accordingly; jar rebuilt.

- **11/14/2026** (Task MT-001 · FreeLLMAPI Auto (opencode)): Guard / Block system (subagent)
  - created: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\combat\Guard.java
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\data\CombatState.java
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\config\ServerConfig.java
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\registry\ModSounds.java
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\combat\CombatInputHandler.java
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\event\PlayerEvents.java
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\event\ProgressionEvents.java
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\network\VisualStatePayload.java
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\network\ModNetwork.java
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\resources\assets\powerlevelling\sounds.json
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\resources\assets\powerlevelling\lang\en_us.json
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\resources\assets\powerlevelling\lang\es_es.json

- **11/14/2026** (Task MT-010 + MT-011 · FreeLLMAPI Auto (opencode)): Aura LOD + config/lang
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\config\ClientConfig.java
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\client\render\AuraLayer.java
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\resources\assets\powerlevelling\lang\en_us.json
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\resources\assets\powerlevelling\lang\es_es.json
  - modified: C:\Users\kakal\Documents\PowerLevelling\OPENCODE_TASKS.md
  - modified: C:\Users\kakal\Documents\PowerLevelling\OPENCODE_CHANGES.md
  - reverted: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\command\ModCommands.java (attempted MT-032 expansion referenced non-existent APIs `CharacterService.setForm` / `StatEffects.masteryProgress`; reverted to keep build green; MT-032 needs a proper design pass)

Verification for MT-001 + MT-010 + MT-011: `gradlew build` BUILD SUCCESSFUL; guard system integrated (state fields, config, sounds, subtitles, damage pipeline, tick drain, visual flags); aura LOD live with 12 config entries; lang parity maintained in en_us/es_es.

- **11/14/2026** (Task MT-002 · FreeLLMAPI Auto (opencode)): Lock-On Targeting
  - created: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\combat\LockOn.java
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\data\CombatState.java
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\config\ServerConfig.java
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\event\PlayerEvents.java
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\resources\assets\powerlevelling\lang\en_us.json
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\resources\assets\powerlevelling\lang\es_es.json
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\resources\assets\powerlevelling\sounds.json
  - modified: C:\Users\kakal\Documents\PowerLevelling\OPENCODE_TASKS.md
  - modified: C:\Users\kakal\Documents\PowerLevelling\OPENCODE_CHANGES.md

Verification for MT-002: `gradlew build` BUILD SUCCESSFUL; lock-on targeting integrated (server-authoritative acquisition, hold-to-lock with grace, per-tick validation, LOS raycast via ClipContext, sounds, particles, visual flags); 5 config entries in ServerConfig; lang parity en_us/es_es; JSON valid; jar rebuilt.

- **11/14/2026** (Fix · FreeLLMAPI Auto (opencode)): AuraLayer crash fix — stance mode crash
  - modified: C:\Users\kakal\Documents\PowerLevelling\src\main\java\com\powerlevelling\client\render\AuraLayer.java

Verification: Fixed `IllegalStateException: Missing elements in vertex: UV1` when entering stance. `RenderType.eyes` requires UV1 (overlay texture coords) on every vertex; `AuraLayer.quad()` was missing `.setOverlay(OverlayTexture.NO_OVERLAY)`. Added to all 4 vertices, matching `KiOrbModel.quad()`. Build passes.

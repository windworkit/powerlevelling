# OpenCode task list — Power Levelling

Work tracker for the OpenCode agent. Mirrors the Cline workflow in `CLINE_TASKS.md`, but this file and
`OPENCODE_CHANGES.md` are owned by OpenCode only. Cline never edits these; OpenCode never edits Cline's files.

**Model attribution:** every task row and every change-log entry names the AI model that did the work. All work
recorded so far was done by **DeepSeek V4.1 Flash Free** (provider `opencode`, model id
`deepseek-v4.1-flash-free`). Backlog rows say `—` because no model has worked on them yet.

## Ground rules (read first, every session)

1. **Log every file you touch** in `OPENCODE_CHANGES.md`: date, task id, and the full path of every created or
   modified file. Do this before replying, in the same turn as the change. A change without a log entry is not done.
2. **Never edit `CLINE_TASKS.md` or `CLINE_CHANGES.md`.** They belong to Cline's workflow. If a task overlaps,
   describe the overlap here instead.
3. **JSON is checked in, not compiled.** Save every `.json` as UTF-8 **without BOM** and run
   `python -m json.tool <file>` after editing (the build passes even when a lang file is broken).
   `en_us.json` contains `· — →`; keep UTF-8 or they turn into junk.
4. **Back up before bulk edits.** Copy `src/` (and any root docs) to
   `%TEMP%\opencode\PowerLevelling-backup-<timestamp>` before multi-file sessions.
5. **Build after every code change.** Use `.\gradlew.bat build --console=plain` directly; `tools\build.bat` ends
   with `timeout /t 10`, which fails under redirected shells. The jar lands in `build\libs\`.
6. **Keep `en_us.json` and `es_es.json` at key parity**, and keep `sounds.json` subtitles wired to existing keys.
7. **Don't delete or rewrite unrelated user work.** Read a file before changing it; prefer targeted edits.
8. **Record the model.** Name the AI model in the task row and in the change-log entry (see the attribution note
   above). A task row without a model, or a change entry without one, is incomplete.

## Tasks

| id | task | model | state | notes |
|----|------|-------|-------|-------|
| O1 | Super Saiyan rework | DeepSeek V4.1 Flash Free | done | Form multipliers in `StatEffects` (melee + ki + vanilla), race `hooks.forms` gate, config-driven numbers, press-time validation + messages, hold cancelled on stance-off/death/logout/dimension, login strips stale modifiers. Half-Saiyan added to forms. |
| O2 | Advancements obtainable | DeepSeek V4.1 Flash Free | done | New `Advancements` helper; wired root/first character/first charge/first blast/level 10-25-50/mastery 5-25-max/zenkai/ki kill/ki streak. Login backfills old saves. Fixed two invalid icons. |
| O3 | Command permissions + key conflict | DeepSeek V4.1 Flash Free | done | `/pl help`, `/pl info`, `/pl loadout set/clear` open to players; `bareHelp` lists them; LOADOUT moved G → V. |
| O4 | Localization + audio polish | DeepSeek V4.1 Flash Free | done | `es_es.json` 407 → 475 keys (parity with `en_us`); subtitles wired for all 11 existing subtitle keys. |
| O5 | README accuracy | DeepSeek V4.1 Flash Free | done | Level cap 150, melee/transform/loadout keys, command table, Coming Soon list. |
| O6 | Workflow bookkeeping | DeepSeek V4.1 Flash Free | done | Created `OPENCODE_TASKS.md` + `OPENCODE_CHANGES.md`; both updated in the same turn as every change. |
| O7 | Guard system | — | backlog | Guard key and payload enum exist; the server branch is still a validity check (`CombatInputHandler`). Dash was split off into O15. |
| O8 | Lock-on + loadout wheel | — | backlog | `LOCK_ON` key exists; `LOADOUT` action is a server no-op. |
| O9 | Flight | — | backlog | `CharacterGate` has a `TODO(flight)` note. |
| O10 | Barrier attack + custom attack creator | — | backlog | No barrier implementation; `KiAttacks.customResolver` hook reserved. |
| O11 | Mob scaling + per-target anti-farm EXP | — | backlog | Config flags exist; effects not wired. |
| O12 | Race playability decision | — | backlog | 7 races authored, only Saiyan/Half-Saiyan `playable: true`. Needs a product decision, not a code fix. |
| O13 | Docs drift | — | backlog | `docs/combat_framework_notes.md` lists finished melee work as not-done; `CLINE_TASKS.md` says T7 is next though T7-T14 are logged. Coordinate, don't edit Cline's file. |
| O14 | Config-screen labels | — | backlog | Optional: translation keys for the new `transform*` and `dash*` config entries. |
| O15 | Dash / dodge | DeepSeek V4.1 Flash Free | done | Stamina-priced burst in the movement direction (look direction when idle), config-driven cost/impulse/i-frames/cooldown, cancels melee, blocked while charging/transforming/skill-casting/hitstun, whoosh + cloud particles. `moveYaw` added to `CombatInputPayload`. |
| O16 | Ki visual rework (Dragon Ball style) | DeepSeek V4.1 Flash Free | done | Complete overhaul of ki visuals: camera-independent radial glow halos (3 orthogonal quads) for soft spherical glow, energy spikes/crackle, smoother comet tail for orbs/blasts. Extended to beams (glow halos at intervals, muzzle/tip enhancement), world FX (burst/dome halos + spikes), aura (glow halos + crackle on AuraLayer, SSJ/transforming/stance particles in AuraFx), and all in-hand orb shapes (disc, barrage, homing, bomb, beam). New `ki_glow.png` texture (64x64 radial gradient). All rendering uses additive `RenderType.eyes` for proper glow. |
| O17 | Dragon Ball aura particle system | FreeLLMAPI Auto (opencode) | done | Phase 2: 5 new particle classes (energy wisps, small rocks, debris burst, large rocks, expanding ground rings) with 5 new textures + particle JSONs; `AuraFx` fully rewritten so charging = ground-breakup debris + rising orbiting rocks + spiralling energy wisps + expanding ground rings, transforming = intensified version, SSJ = golden wisps + rare golden ring/rock, stance = subtle wisps. All caps respect `PARTICLE_DENSITY` and vanilla distance culling; counters reset on disconnect and world change. Fixed compile errors from phase 2 (missing imports, `roll`/`oRoll` field names, double→float conversions, out-of-scope `s.chargeTicks`). Also fully revamped the 3D aura render layer (`AuraLayer`) to replace the old static inflated-cube geo model with flowing vertical energy wisps, ground flare, expanding rings, and camera-independent glow halos - all tinted with player's ki color (gold for SSJ). |
| O18 | SSJ flame shell + lightning crackle | FreeLLMAPI Auto (opencode) | done | Added two signature Dragon Ball effects to `AuraLayer`: (1) `renderFlameShell` — a cylinder of ~12 vertical flame-textured quads using the new 64x128 `aura_flame.png` (teardrop silhouette with spiky fringe), forming the iconic SSJ golden flame aura; active strongly in SSJ, subtly when charging. (2) `renderLightningCrackle` — jagged multi-segment branching bolts using a tiny `aura_bolt.png` sprite; bolts fork and flicker with deterministic per-bolt seeds; frequent when charging/transforming, occasional in SSJ. Both use additive `RenderType.eyes` with fullbright lighting. New textures: `aura_flame.png` (procedural teardrop with triangular fringe) and `aura_bolt.png` (8x8 white). Build passes. |
| O19 | Aura LOD (Level of Detail) | FreeLLMAPI Auto (opencode) | done | Added camera-distance LOD to `AuraLayer`: 3 tiers (full/reduced/minimal) with config-driven distances and wisp counts. Tier 0 (≤16 blocks): full wisps, flame shell, lightning. Tier 1 (16–32): reduced wisps, no flame shell, no lightning. Tier 2 (>32): minimal wisps, glow halos only. Config entries in `ClientConfig`: `auraLodEnabled`, `auraLodDistance1/2/3`, `auraLodWisps1/2/3`, `auraLodFlameShell1/2`, `auraLodLightning1/2`. Uses `Minecraft.getInstance().gameRenderer.getMainCamera()` for camera position. Build passes. |
| MT-001 | Guard / Block System | FreeLLMAPI Auto (opencode) | done | Server-authoritative guard mechanic: `Guard.java` handles input validation (stance, charging, transforming, dashing, skill, hitstun), stamina drain, perfect guard window (0 damage + stamina refund), guard break (stun + particles). Config: `guard.enabled`, `guard.staminaPerTick`, `guard.damageReduction`, `guard.perfectWindowTicks`, `guard.cooldownTicks`, `guard.breakStunTicks`. Sounds: `guard_start`, `guard_perfect`, `guard_break`. Network: `GUARDING`/`PERFECT_GUARD` flags in `VisualStatePayload`. Integrated into `CombatInputHandler`, `PlayerEvents` (tick), `ProgressionEvents` (damage reduction). Build passes (MT-032 reverted). |
| MT-002 | Lock-On Targeting | FreeLLMAPI Auto (opencode) | done | Server-authoritative lock-on: `LockOn.java` — press LOCK_ON in stance to acquire nearest valid `LivingEntity` within config range + forward cone (dot-product); hold-to-lock with release grace (`lockon.releaseGraceTicks`); per-tick validation in `PlayerEvents` clears lock on grace expiry / target death / target gone / out of range + 2 block margin. Config: `lockon.enabled`, `lockon.range` (16), `lockon.coneAngle` (45°), `lockon.requireLineOfSight` (false), `lockon.releaseGraceTicks` (20). Optional LOS via `ServerLevel.clip` with `ClipContext` (entity param, mirrors KiBlastEntity). Sounds: `lockon_acquire`, `lockon_break`. States in `CombatState`: `lockTargetId`, `lockUntil`, `lockHeld`. Visual: `LOCKED` flag via `ModNetwork.syncVisual` + particle ring at target feet. Fixed compile errors (ServerLevel cast, ClipContext ambiguity, missing LockOn import in PlayerEvents). Build passes. |
| MT-010 | Aura LOD (Level of Detail) | FreeLLMAPI Auto (opencode) | done | See O19 — same task, different ID convention. |
| MT-011 | Aura Config/Lang | FreeLLMAPI Auto (opencode) | done | Added 12 config entries to `ClientConfig` for aura LOD (enabled, 3 distances, 3 wisp counts, 2 flame shell toggles, 2 lightning toggles). Added matching lang keys in `en_us.json` + `es_es.json`. Build passes. |

## Conventions

- Task ids are `O1`, `O2`, ...; never reuse an id.
- Change-log entries are newest-last and start with `- **<date>** (Task O<n> · <model>):`.
- Verification line at the end of each change entry: build result + JSON validation + jar size.

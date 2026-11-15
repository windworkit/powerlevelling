# Power Levelling — Master Task Breakdown
**Generated:** 2026-11-14  
**Purpose:** Single authoritative list of all remaining work, organized by domain, with enough detail to hand off to subagents.  
**Convention:** Each task gets a unique ID (`MT-XXX`), priority (P0–P3), estimated scope (S/M/L/XL), and suggested agent type.

---

## 📋 LEGEND
| Priority | Meaning |
|----------|---------|
| **P0** | Blocks release / crash / regression |
| **P1** | Core gameplay feature, high player visibility |
| **P2** | Important polish / secondary system |
| **P3** | Nice-to-have / tech debt / docs |

| Scope | Rough Effort |
|-------|--------------|
| **S** | < 2 hrs (single file, focused change) |
| **M** | 2–8 hrs (few files, one subsystem) |
| **L** | 8–24 hrs (multiple subsystems, design decisions) |
| **XL** | > 24 hrs (architectural, cross-cutting) |

| Agent | Best For |
|-------|----------|
| **explore** | Code archaeology, finding references, answering "how does X work?" |
| **general** | Implementation, refactors, research, multi-step tasks |

---

## 🎯 COMPLETED (Reference — Do Not Re-assign)
| ID | Task | Status |
|----|------|--------|
| O1 | Super Saiyan rework | ✅ Done |
| O2 | Advancements obtainable | ✅ Done |
| O3 | Command permissions + key conflict | ✅ Done |
| O4 | Localization + audio polish | ✅ Done |
| O5 | README accuracy | ✅ Done |
| O6 | Workflow bookkeeping | ✅ Done |
| O15 | Dash / dodge | ✅ Done |
| O16 | Ki visual rework (Dragon Ball style) | ✅ Done |
| O17 | Dragon Ball aura particle system | ✅ Done |
| O18 | SSJ flame shell + lightning crackle | ✅ Done |

---

## 🟦 COMBAT & MOVEMENT SYSTEMS

### MT-001 Guard / Block System
**Priority:** P1 | **Scope:** L | **Agent:** general  
**Description:** Implement the server-authoritative guard mechanic.  
**Details:**
- `CombatInputHandler` already has `GUARD` payload enum; server branch is a stub
- Needs: guard stamina drain, damage reduction % (config), guard break threshold, perfect guard window (parry), visual/audio feedback
- Must respect: cannot guard while charging/transforming/dashing/skill-casting/hitstun
- Network: sync guard state to clients for visual (shield/energy barrier)
- Config: `guard.staminaPerTick`, `guard.damageReduction`, `guard.perfectWindowTicks`, `guard.cooldown`

**Files to touch:** `CombatInputHandler`, `CombatState`, `ServerConfig`, `ModSounds`, `sounds.json`, new `Guard.java`

---

### MT-002 Lock-On Targeting
**Priority:** P1 | **Scope:** M | **Agent:** general  
**Description:** Implement lock-on (soft target) for combat.  
**Details:**
- `LOCK_ON` key exists; server no-op currently
- Behavior: press → acquire nearest hostile in forward cone (configurable angle/range); hold → maintain; release → clear
- Visual: reticle on target, subtle UI indicator, optional particle tether
- Server: validate target exists, is alive, in range; send `LockOnPayload` to client
- Config: `lockon.range`, `lockon.coneAngle`, `lockon.requireLineOfSight`

**Files:** `CombatInputHandler`, new `LockOn.java`, `LockOnPayload`, client render overlay

---

### MT-003 Flight System
**Priority:** P2 | **Scope:** L | **Agent:** general  
**Description:** Full flight mechanic (Ki Flight / Bukūjutsu).  
**Details:**
- `CharacterGate` has `TODO(flight)` — need design first
- Proposed: double-tap jump → toggle flight; consumes ki/stamina per tick; creative-style flight but survival-balanced
- Race gates: Saiyan/Half-Saiyan/Namekian/Frost Demon = innate; Human/Majin = learn via skill tree
- Config: `flight.kiPerTick`, `flight.staminaPerTick`, `flight.maxSpeed`, `flight.acceleration`
- Visual: subtle aura trail, sound loop, particle dust when near ground
- Must integrate with dash (dash while flying = burst), guard (can guard mid-air)

**Files:** `CharacterGate`, new `Flight.java`, `FlightPayload`, `ServerConfig`, client input + render

---

### MT-004 Barrier Attack + Custom Attack Creator
**Priority:** P2 | **Scope:** XL | **Agent:** general  
**Description:** Implement Barrier (spherical shield) attack type and the custom attack data-driven framework.  
**Details:**
- Barrier: stationary or mobile sphere; blocks projectiles/melee; drains ki; can reflect; breaks on damage threshold
- Custom attack creator: `KiAttacks.customResolver` hook reserved; needs JSON schema for defining attacks (shape, behavior, particles, damage formula, cooldown, cost)
- Schema: type (beam/blast/barrier/wave/rush), damage scaling, hitbox, VFX refs, sound refs, cooldown, ki/stamina cost, level req
- In-game editor command `/pl attack create` (admin) → writes to `data/powerlevelling/ki_attacks/`
- Validation: server-side schema check on load

**Files:** `KiAttacks`, new `BarrierAttack.java`, JSON schema + codec, command, datapack loader

---

### MT-005 Melee Combo System Expansion
**Priority:** P2 | **Scope:** M | **Agent:** general  
**Description:** Extend basic melee into light/heavy chains, launchers, vanish strikes.  
**Details:**
- Current: single melee attack
- Add: 4-hit light chain, heavy finisher, launcher (sends target up), vanish (teleport behind target, costs stamina)
- Combo state machine on `CombatState`; server-authoritative
- Config: per-hit damage multipliers, knockback, stamina costs
- Visual: afterimage particles on vanish, hit pause frames

**Files:** `CombatState`, `CombatInputHandler`, `MeleeAttack.java` (new), config, particles

---

## 🎨 VISUAL & AURA POLISH (Post-O18)

### MT-010 Aura LOD / Distance Culling
**Priority:** P1 | **Scope:** M | **Agent:** general  
**Description:** Add level-of-detail to aura rendering so it scales gracefully at distance.  
**Details:**
- Current: full geometry at all distances
- Implement: distance bands → reduce wisp count, disable flame shell, simplify rings, drop lightning
- Config: `aura.lod.enabled`, `aura.lod.distances[]`, `aura.lod.wispCounts[]`
- Use `EntityRenderDispatcher.distanceToSqr(camera)` in `AuraLayer`

**Files:** `AuraLayer`, `ClientConfig`

---

### MT-011 Aura Config Screen Integration
**Priority:** P2 | **Scope:** S | **Agent:** general  
**Description:** Expose aura visual options in the client config screen.  
**Details:**
- Sliders: wisp density, flame shell intensity, lightning frequency, ring count, color saturation
- Toggle: "Show aura in first person", "SSJ flame always on"
- Persist to `client.toml`

**Files:** `ClientConfig`, config screen registration (Forge/NeoForge config GUI)

---

### MT-012 Transformation VFX Sequences
**Priority:** P1 | **Scope:** L | **Agent:** general  
**Description:** Cinematic transformation sequences (SSJ, future forms).  
**Details:**
- Current: instant visual flip
- Design: 2–3 second sequence — screen shake, sound buildup, expanding shockwave, aura flare, hair/eye color lerp, particle burst
- Phases: charge → peak → settle
- Cancelable if interrupted (damage, move, logout)
- Reuse `AuraFx` + new `TransformSequenceFx` class
- Config: `transform.sequenceEnabled`, `transform.durationTicks`

**Files:** `Transform.java`, new `TransformSequenceFx.java`, `ClientEvents` (sound), `AuraFx`

---

### MT-013 Ki Blast / Beam Visual Variants per Race
**Priority:** P3 | **Scope:** M | **Agent:** explore → general  
**Description:** Distinct blast/beam shapes & colors per race (Namekian = antenna beams, Frost Demon = death beams, Majin = candy beams, etc.)  
**Details:**
- Current: unified visual with ki color tint
- Need: race-specific `KiOrbStyle` entries, model/texture variants
- Data-driven: `data/powerlevelling/ki_style/<race>.json`

**Files:** `KiOrbStyles`, new style JSONs, textures

---

### MT-014 First-Person Hand Orb / Charge Visuals
**Priority:** P2 | **Scope:** M | **Agent:** general  
**Description:** Polish the first-person view when charging/holding ki.  
**Details:**
- Current: basic orb in hand
- Add: energy swirling up arms, screen-space distortion shader (optional), hand pose changes, charge sound pitch ramp
- Must not obstruct view

**Files:** `KiOrbLayer` (first-person), `InputHandler`, shaders (if opted)

---

## ⚙️ PROGRESSION & BALANCE

### MT-020 Mob Scaling + Anti-Farm EXP
**Priority:** P1 | **Scope:** L | **Agent:** general  
**Description:** Implement config-gated mob level scaling and diminishing returns for repeated kills.  
**Details:**
- Config flags exist: `mobScaling.enabled`, `antiFarm.enabled`
- Scaling: mob level = player level ± range; health/damage/XP scale with level
- Anti-farm: track kills per mob type per player; XP multiplier decays after N kills, recovers over time
- Command: `/pl mobscaling reload`, `/pl antifarm reset <player>`

**Files:** `MobScaling.java` (new), `AntiFarm.java` (new), `ServerConfig`, `ProgressionEvents`, commands

---

### MT-021 Race Playability Gates
**Priority:** P2 | **Scope:** S | **Agent:** explore  
**Description:** Decide and implement which races are playable; gate content behind config/advancement.  
**Details:**
- 7 races authored; only Saiyan/Half-Saiyan `playable: true`
- Options: (a) all playable at start, (b) unlock via advancement/quest, (c) config toggle per race
- Need product decision → then implement gates in `CharacterService.createCharacter()`

**Files:** race JSONs, `CharacterService`, config

---

### MT-022 Zenkai System Polish
**Priority:** P2 | **Scope:** M | **Agent:** general  
**Description:** Refine Zenkai (near-death power boost) — visual, sound, balance.  
**Details:**
- Current: basic stat boost on low HP recovery
- Add: screen flash, heartbeat sound, temporary aura flare, combat log message
- Config: `zenkai.threshold`, `zenkai.multiplier`, `zenkai.cooldown`, `zenkai.visualEnabled`

**Files:** `Zenkai.java`, `ProgressionEvents`, sounds, client FX

---

### MT-023 Mastery System (Skill/Technique Levels)
**Priority:** P2 | **Scope:** L | **Agent:** general  
**Description:** Per-attack mastery: use → gain XP → unlock variants / reduce cost / increase damage.  
**Details:**
- Track per-player per-attack usage
- Mastery tiers: Novice → Adept → Expert → Master → Grandmaster
- Rewards: -10% ki cost, +15% damage, variant unlock (e.g., "Instant Kamehameha")
- UI: mastery tab in `/pl info` or custom screen

**Files:** new `Mastery.java`, `Progression`, network sync, command/UI

---

## 🖥️ UI / COMMANDS / QOL

### MT-030 Loadout Wheel (Radial Menu)
**Priority:** P1 | **Scope:** M | **Agent:** general  
**Description:** Implement the radial loadout wheel (bound to `LOADOUT` key, currently V).  
**Details:**
- 8 slots: assign skills/transforms/items
- Controller + mouse support
- Server validates slot contents; sync to client
- Config: `loadout.slots`, `loadout.key`

**Files:** `LoadoutWheel.java` (client), `LoadoutPayload`, `CombatInputHandler`, screen widget

---

### MT-031 HUD Overhaul (Ki/Stamina/Transform Bars)
**Priority:** P1 | **Scope:** M | **Agent:** general  
**Description:** Replace debug HUD with clean Dragon Ball style bars.  
**Details:**
- Ki bar (blue → gold in SSJ), Stamina bar (green), Transform charge bar (hidden until transforming)
- Race-specific icons (Saiyan tail, Namekian antenna, etc.)
- Config: `hud.style` (classic/minimal/off), `hud.position`, `hud.scale`
- Must use `GuiGraphics` render, not legacy

**Files:** `HudRenderer.java` (new), `ClientConfig`, `ClientEvents` (render overlay)

---

### MT-032 `/pl` Command Suite Expansion
**Priority:** P2 | **Scope:** M | **Agent:** general  
**Description:** Flesh out admin/player commands.  
**Details:**
- `/pl loadout set <slot> <skill>` ✅ (partial)
- Add: `/pl transform <form>`, `/pl ki set <amount>`, `/pl level set <level>`, `/pl race reroll`, `/pl mastery show`, `/pl config <key> <value>`
- Tab completions, permission nodes, help pages

**Files:** `ModCommands.java`, `CommandPermissions`, Brigadier builders

---

### MT-033 Keybinding Conflict Resolution UI
**Priority:** P3 | **Scope:** S | **Agent:** explore  
**Description:** In-game keybinding screen showing conflicts (mod keys vs vanilla).  
**Details:**
- NeoForge provides `KeyConflictContext`; just need to register our keys properly
- Show in controls menu: "Power Levelling" category

**Files:** `ModKeys.java`, registration

---

## 🔊 AUDIO & LOCALIZATION

### MT-040 Full Sound Design Pass
**Priority:** P1 | **Scope:** L | **Agent:** general  
**Description:** Replace placeholder sounds with custom Dragon Ball style audio.  
**Details:**
- Needed: charge loop (pitch ramp), transform startup/peak/settle, blast fire, beam hum, impact, dash whoosh, guard hit, perfect guard *ping*, flight loop, zenkai heartbeat
- Format: `.ogg`, mono for 3D, stereo for UI
- `sounds.json` entries + subtitles for all
- Volume categories: `player`, `ambient`, `master`

**Files:** `sounds.json`, `ModSounds`, `resources/assets/powerlevelling/sounds/`, `en_us.json` + `es_es.json` subtitle keys

---

### MT-041 Localization Parity Audit
**Priority:** P2 | **Scope:** S | **Agent:** explore  
**Description:** Ensure `es_es.json` key parity with `en_us.json` (currently 477/477).  
**Details:**
- Run script to diff keys after every string addition
- Add missing translations (can use placeholder `[ES]` tag for later)

**Files:** `en_us.json`, `es_es.json`, validation script

---

## 📦 DATAPACK / CONTENT

### MT-050 Datapack Versioning & Migration
**Priority:** P1 | **Scope:** M | **Agent:** general  
**Description:** Formalize datapack versioning, migration, and the "replace wholesale" workflow.  
**Details:**
- Current: manual delete + copy to 3 locations
- Implement: `datapack_version` in `pack.mcmeta`, migration scripts (JSON → JSON), `/pl datapack upgrade` command
- CI: validate datapack loads on server start

**Files:** `pack.mcmeta`, new `DatapackMigration.java`, command, CI workflow

---

### MT-051 Race Data Completeness
**Priority:** P2 | **Scope:** M | **Agent:** explore → general  
**Description:** Ensure all 7 races have complete data: forms, base stats, ki color, icons, sounds.  
**Details:**
- Checklist per race: `baseStats`, `forms[]`, `kiColor`, `hairModels[]`, `icon`, `transformSound`, `flightEnabled`
- Missing: Namekian (Great Namek), Frost Demon (forms 2–4), Majin (Pure/Kid), Human (Kaioken?), Android (infinite ki?)

**Files:** `data/powerlevelling/powerlevelling/race/*.json`, textures, sounds

---

### MT-052 Skill Tree / Technique Unlocks
**Priority:** P2 | **Scope:** XL | **Agent:** general  
**Description:** Data-driven skill tree per race: spend TP (training points) to unlock attacks, passives, utility.  
**Details:**
- TP from level up, zenkai, advancements
- Tree JSON: nodes (id, cost, reqs, rewards: attack/unlock/passive), edges
- UI: `/pl skills` screen (graph layout)
- Server validates unlocks; sync to client

**Files:** new `SkillTree.java`, `SkillNode.json` schema, UI screen, network

---

## 🧪 TESTING & QUALITY

### MT-060 Automated Test Suite
**Priority:** P2 | **Scope:** L | **Agent:** general  
**Description:** Add unit/integration tests for critical logic.  
**Details:**
- Test: `StatEffects` multipliers, `CombatState` transitions, `Progression` XP curves, `KiAttacks` damage formulas, `Mastery` XP, config bounds
- Framework: JUnit 5 + Fabric/NeoForge test harness (or headless server)
- CI: run on PR

**Files:** `src/test/`, `.github/workflows/test.yml`

---

### MT-061 Performance Profiling (Client + Server)
**Priority:** P1 | **Scope:** M | **Agent:** explore → general  
**Description:** Profile and optimize hot paths.  
**Details:**
- Client: `AuraLayer` render (vertex count), particle spawn rates, `AuraFx` tick
- Server: `CombatInputHandler` packet rate, `MobScaling` level calc, `AntiFarm` kill tracking
- Tools: Spark, JProfiler, `debug.profiler.enable`
- Targets: < 1ms/frame client aura, < 2ms/tick server combat

**Files:** profiling scripts, optimizations in hot classes

---

### MT-062 Multiplayer Stress Test
**Priority:** P1 | **Scope:** M | **Agent:** general  
**Description:** Test with 10+ players: transforming, charging, blasting, dashing simultaneously.  
**Details:**
- Check: packet spam, entity culling, particle caps, desyncs, memory leaks
- Script: headless server + fake clients (or local LAN)
- Metrics: TPS, bandwidth, heap, particle counts

**Files:** test script, any fixes needed

---

## 📚 DOCUMENTATION

### MT-070 Developer Docs Sync
**Priority:** P3 | **Scope:** S | **Agent:** explore  
**Description:** Sync `docs/combat_framework_notes.md` with reality (O13).  
**Details:**
- Mark completed items ✅, remove stale TODOs, add new architecture notes
- Keep as living design doc

**Files:** `docs/combat_framework_notes.md`

---

### MT-071 Player Wiki / Guide
**Priority:** P3 | **Scope:** L | **Agent:** general  
**Description:** Write user-facing guide: controls, forms, skills, commands, config.  
**Details:**
- Host on GitHub Wiki or `docs/user_guide.md`
- Screenshots/GIFs for each mechanic
- Translate to Spanish

**Files:** `docs/user_guide.md`, `docs/user_guide_es.md`, assets

---

### MT-072 Config Reference Doc
**Priority:** P2 | **Scope:** S | **Agent:** explore  
**Description:** Auto-generate `CONFIG.md` from `ServerConfig` / `ClientConfig` classes.  
**Details:**
- Script: reflect on `@ConfigEntry` fields → markdown table (key, type, default, min/max, description)
- Commit to repo

**Files:** `CONFIG.md`, generation script

---

## 🏗️ INFRASTRUCTURE / TECH DEBT

### MT-080 Dependency Updates
**Priority:** P3 | **Scope:** S | **Agent:** explore  
**Description:** Update Gradle, NeoForge, mappings, Java toolchain.  
**Details:**
- Check: `gradle.properties`, `build.gradle`, `gradle/wrapper/gradle-wrapper.properties`
- Test build after each bump

**Files:** build files

---

### MT-081 Render Type Consolidation
**Priority:** P2 | **Scope:** M | **Agent:** explore  
**Description:** Audit all `RenderType` usages; consolidate custom types; ensure no state leaks.  
**Details:**
- Current: `RenderType.eyes` (aura, particles, orbs), `entityCutoutNoCull` (core), custom?
- Verify: `RenderSystem` state reset, buffer cleanup, no stale bind

**Files:** `KiOrbModel`, `AuraLayer`, `BeamRenderer`, `KiWorldFx`, `KiBlastRenderer`

---

### MT-082 Network Payload Audit
**Priority:** P1 | **Scope:** M | **Agent:** explore  
**Description:** Verify all payloads: size limits, validation, thread safety, encryption.  
**Details:**
- List: `VisualStatePayload`, `CombatInputPayload`, `KiImpactPayload`, `LockOnPayload` (new), `FlightPayload` (new), `TransformPayload`, `LoadoutPayload`
- Check: `PacketByteBuf` read/write symmetry, `FriendlyByteBuf` limits, server/client thread confinement

**Files:** `network/*.java`

---

### MT-083 Config Screen Labels (O14)
**Priority:** P3 | **Scope:** S | **Agent:** general  
**Description:** Add translation keys for `transform*` and `dash*` config entries.  
**Details:**
- Keys: `config.powerlevelling.transform.*`, `config.powerlevelling.dash.*`
- Add to `en_us.json` + `es_es.json`

**Files:** `en_us.json`, `es_es.json`, `ServerConfig`/`ClientConfig` annotations

---

## 🎯 SUGGESTED SUBAGENT ASSIGNMENT PLAN

### Wave 1 (Parallel — Core Systems)
| Agent | Task |
|-------|------|
| general | MT-001 Guard System |
| general | MT-002 Lock-On |
| general | MT-003 Flight |
| explore | MT-021 Race Playability (decision doc) |

### Wave 2 (Parallel — Visual Polish)
| Agent | Task |
|-------|------|
| general | MT-010 Aura LOD |
| general | MT-012 Transform Sequences |
| general | MT-014 First-Person Charge Visuals |
| explore | MT-013 Race Blast Variants (inventory) |

### Wave 3 (Parallel — Progression)
| Agent | Task |
|-------|------|
| general | MT-020 Mob Scaling + Anti-Farm |
| general | MT-022 Zenkai Polish |
| explore | MT-051 Race Data Audit |

### Wave 4 (Parallel — UI/QOL)
| Agent | Task |
|-------|------|
| general | MT-030 Loadout Wheel |
| general | MT-031 HUD Overhaul |
| general | MT-032 Command Expansion |

### Wave 5 (Audio + Polish)
| Agent | Task |
|-------|------|
| general | MT-040 Sound Design Pass |
| explore | MT-041 Localization Audit |
| general | MT-061 Performance Profiling |

### Wave 6 (Testing + Docs)
| Agent | Task |
|-------|------|
| general | MT-060 Test Suite |
| general | MT-062 Multiplayer Stress |
| explore | MT-070 Docs Sync |
| general | MT-072 Config Reference Gen |

---

## 📌 HOW TO DISPATCH

```java
// Example: spawn Wave 1
subagent({ agent: "general", description: "MT-001 Guard System", prompt: `
Implement MT-001 Guard System per MASTER_TASK_BREAKDOWN.md:
- Server-authoritative guard in CombatInputHandler
- Stamina drain, damage reduction, perfect guard window
- Config entries in ServerConfig
- Visual/audio feedback (shield particle, guard sound)
- Network sync for client visual
Files: CombatInputHandler, CombatState, ServerConfig, ModSounds, new Guard.java
` });

subagent({ agent: "general", description: "MT-002 Lock-On", prompt: `...` });
// etc.
```

---

## ✅ TRACKING

When a subagent completes a task:
1. It reports result in `<subagent>` message
2. You (or I) add entry to `OPENCODE_TASKS.md` + `OPENCODE_CHANGES.md` with model attribution
3. Mark MT-XXX as **Done** in this file (edit the row)
4. Run `gradlew build` verification

---

*End of Master Task Breakdown — 47 tasks total (10 P0/P1, 22 P2, 15 P3)*
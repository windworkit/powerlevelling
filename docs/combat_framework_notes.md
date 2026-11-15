# Combat framework notes (M5, written for M6 / M7 / M8)

Short reference so later milestones extend melee the same way. Design source: design_doc.md 4.2-4.3, 8.4, Addendum A/C.

## Architecture
| Piece | Where | Role |
|---|---|---|
| `CombatMove` + `CombatMoves` | `combat/` | `powerlevelling:combat_move` datapack registry (synced to clients). `CombatMoves.resolve(access, input, chainFrom, onGround)` is the ONLY place that maps an input to a move. Server and client both call it. |
| `MeleeMachine` | `combat/` | Pure state machine (IDLE/STARTUP/ACTIVE/RECOVERY, chain index, one buffered input, 6-tick buffer, 12-tick chain grace). No world access, so server and client run the same code. |
| `MeleeEngine` | `combat/` (server) | Checks input, ticks `CombatState.melee` (from `PlayerEvents.onPlayerTick`), runs hit detection on ACTIVE ticks, damage/hitstun/knockback/XP/FX, tracks hitstun of every `LivingEntity` (`VICTIMS`) and handles interplay with skills. |
| `ClientMelee` | `client/` | Prediction for the local player (own `MeleeMachine`) and swing poses for every player. |
| Poses | `KiPoseTable` (`jab_r`, `cross_l`, `hook_r`, `kick`; keys can now pose legs) + `KiPoseClient` (priority: beam > orb > melee swing > ki fire hold). |
| Payloads | `MeleeInputPayload` (C2S), `MeleeStatePayload` (S2C verdict), `MeleeSwingPayload` (S2C to observers). |
| Dummy | `entity/TrainingDummyEntity` | Never dies, name tag shows last hit / combo / total. Sneak + right-click with an empty hand picks it up. |

## Rules in code
- Melee needs stance, no ki charge, `skillPhase == NONE` (WINDUP/CHARGE/ACTIVE/RECOVERY all block), and no hitstun.
- Skills and ki charge start only when `MeleeEngine.allowsSkill(st)` is true (melee IDLE or in its cancel window). Starting one calls `cancelForAction`.
- Hit detection: an oriented box in the attacker's yaw frame (`hitbox` reach/width/height/forward/y, +0.3 inflate). It needs line of sight, skips allies and blocked PvP, and hits each target at most once per move (`meleeHits`, cleared when `MeleeMachine.serial` changes).
- Damage: `move.damage × (1 + 0.01·Martial) × StatEffects.meleeMultiplier (Strength + race/nature) × comboScale`. The damage type is `powerlevelling:melee`, which is in `minecraft:no_knockback`, so the engine applies the knockback itself after `hurt`. `invulnerableTime` is set to 0 before each hit. Burst i-frames (`iframesUntil`) are still respected.
- Combo = hits taken by the victim while in hitstun (+3-tick grace). It is stored per victim, so team combos count.
- Hitstun: `MeleeEngine.applyHitstun(entity, ticks, now)` is public API. Movement and jump get -100% transient modifiers. Players also get `CombatState.hitstunUntil` (read by KiSkills/KiCharge/Burst), their melee, skill wind-up and charge are cancelled, and they receive a STUNNED payload. Stunned entities deal no non-ki direct damage (`ProgressionEvents`). Burst cast in hitstun costs 2× ki and calls `breakCombo`.
- Martial XP: 2 per hit, ×0.25 vs Dummy (fractions accumulate). Dummy hits also give 1 training EXP each, capped at 300/hour.

## Prediction contract (150 ms lag)
1. On press the client allocates `seq` (shared counter in `ClientCombat`). If it can predict, it runs `SELF.press`. On STARTED it plays the pose, the first-person swing and the swing sound immediately. Then it sends `MeleeInputPayload`.
2. The server runs the same machine on its own clock and replies STARTED / BUFFERED / REJECTED for that seq. A buffered input gets a second reply (STARTED or REJECTED) when it resolves.
3. Client on STARTED: same seq and same move means confirmed. Otherwise it adopts the server's move (`forceStart`). Stale seqs are ignored. On REJECTED for the current predicted seq, or on CANCELLED/STUNNED, it calls `SELF.cancel()` and removes the swing, and the pose blends out over 3 ticks.
4. Never predicted: hits, damage, hit sound/particles, hitstun. These are server-only, and the server excludes the attacker from the swing sound.
5. Any new state that the client predicts must be decided by `MeleeMachine` + `CombatMoves.resolve`. Put side-specific checks (stamina etc.) in the `Resolver` on both sides.

## Adding moves (M7)
- Drop JSON into `data/<ns>/powerlevelling/combat_move/`. Fields: `entry` (light/heavy), `requires` (ground/air/any), `startup`, `active`, `recovery`, `cancel_from` (-1 = none), `damage`, `hitstun`, `hitbox{reach,width,height,forward,y}`, `knockback{forward,up}`, `stamina_cost`, `pose`, `sounds{swing,hit}`, `follow_ups{light:…, heavy:…}`. The registry key is the move id.
- Heavy / Guard Break: a move with `entry: heavy`. Launcher: add `"heavy": "powerlevelling:launcher"` to `light_1`/`light_2` `follow_ups`. Finisher: the same on `light_3`/`light_4`. RMB already sends `MeleeInput.HEAVY`.
- Air chain: `air_light_1..3` with `requires: air`, and set `light_1` to `requires: ground`.
- New inputs (dash-cancel → Pursue): append to `MeleeInput` (wire = ordinal, append only) and add a C2S path that calls `MeleeEngine`. Pursue/launch movement belongs in a new per-move field (add a nested record; `CombatMove` has 14 of the 16 RecordCodecBuilder fields, so group new fields into sub-records).
- Knockdown / combo cap (12 hits / 60 ticks): extend `Victim` in MeleeEngine (it already counts `combo`). Apply knockdown in `applyHit` when the cap is reached.

## Extension points
- **M6 feel:** replace the `sendParticles` + `playSound` block at the end of `MeleeEngine.applyHit` with a `CombatEventPayload`. Hit-stop has to add the same ticks to the attacker's `MeleeMachine` (pause `tick`) and to the victim's `stunUntil`, and delay knockback.
- **M8 guard / perfect guard:** add a guard state to `CombatState` and check it in `MeleeEngine.applyHit` before `hurt` (guardMult, chip → stamina, Guard Crush). Perfect-guard timing needs the defender's input tick: `MeleeInputPayload.clientTick` is the model for that. Counter = a move with an `entry` that is gated by the counter window inside the server/client `Resolver`. The Dummy's guard/attack modes extend `TrainingDummyEntity`.
- **Ki hitstun (K9):** ki hits may call `MeleeEngine.applyHitstun`. This is what lets Burst-as-combo-breaker work against ki combos.
- **Not done yet:** pvpCompression (M9/M17), anti-farm per-target XP (M9), lock-on facing nudge (M11), and server config for buffer/grace/inflate (M16; these are constants in `MeleeMachine`/`MeleeEngine` for now).

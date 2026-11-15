# AI Work Log & Engineering Handoff — PowerLevelling Visual Effects

**Last updated:** 2026-11-15  
**Repository:** windworkit/powerlevelling  
**Working branch:** visual-overhaul/aura-foundation  
**Recorded branch head:** 689ef584a075c8c0c5dd2e7cdb47a25fe4b5f097  
**Baseline main commit:** 7a326c7e17e93393b8ff713883a34141d520f83c

This is the authoritative handoff for the aura/charging visual work. It records implemented changes, reverted experiments, test evidence, model attribution, scope restrictions, and the remaining plan. Treat “implemented” and “planned” as separate statuses.

---

## 1. AI model and tool attribution

### AI model responsible for the recorded implementation

**OpenAI GPT-6 (ChatGPT)** is the AI model responsible for the source review, rendering diagnosis, visual-system design decisions, Java changes, documentation, and implementation plan recorded here.

GPT-6 performed:
- Repository/source inspection and call-path tracing.
- Diagnosis of the player-model coordinate-space error and particle-origin issues.
- Design decisions for the procedural aura mesh, body-originating energy wisps, and ground-originating rocks/debris.
- Authoring and revising Java source changes and configuration defaults.
- Reviewing branch diffs and checking that the current branch no longer includes the temporary ki-orb rendering experiment.
- Writing this handoff and the visual-overhaul documentation.

### What was not another AI model

- **GitHub connector/API tools** were used to read files, create Git blobs/trees/commits, and update the feature-branch ref. These are repository tools, not AI models.
- **GitHub Actions** attempted automated builds. It is a CI service, not an AI model.
- **Gradle** is the project build system, not an AI model.
- **Blockbench** was not used to author a model in this work. The aura mesh described below is procedural Java geometry. No new .bbmodel file was produced.
- No separate Claude, Gemini, Codex, or other model was assigned a portion of this implementation in the recorded work.

Git commit metadata does not contain a model identifier. Therefore, GPT-6 attribution above is the work-session attribution, not a claim that GitHub itself records model provenance.

---

## 2. Non-negotiable scope

The user explicitly authorized work only on these visual systems:

1. **Charging:** charge animation, energy buildup, ramping intensity, and charging-only visual accents.
2. **Aura:** aura geometry, silhouette, colors, motion, particles, and effects.
3. **Transforming:** transformation buildup and transformation visual effects.
4. **Untransforming:** reversion/power-down visual effects.
5. **Turbo:** Turbo aura, energy intensity, and associated visual effects.

### Explicitly forbidden

Do not edit or redesign:
- Ki moves, attacks, abilities, or skill definitions.
- Ki orbs/projectiles, blasts, beams, discs, their renderers, or their mechanics.
- Combat, damage, targeting, lock-on, hit detection, or move animations.
- Unrelated HUD, lobby, character appearance, hair/tail, or general UI code.

Important terminology: “ki” in the approved scope means the character’s **ki energy/charging visuals**. It does not mean ki moves or ki projectiles.

A temporary ki-orb mesh experiment was mistakenly included earlier in this feature branch. It was removed during scope cleanup; see Section 5. Do not reintroduce it under the current authorization.

---

## 3. Current implementation — what is in the branch

The following is the state recorded at branch head 689ef584a075c8c0c5dd2e7cdb47a25fe4b5f097.

### 3.1 Third-person aura mesh

**Files**
- src/main/java/com/powerlevelling/client/render/AuraLayer.java
- src/main/java/com/powerlevelling/client/render/AuraMeshRenderer.java
- src/main/resources/assets/powerlevelling/textures/entity/aura/energy_ribbon.png

**Current design**
- AuraLayer is registered on both WIDE and SLIM player renderers through ClientEvents.
- AuraLayer reads synchronized player visual-state flags for stance, charging, Super Saiyan, transforming, and Turbo.
- The aura no longer requires an optional registered cosmetic aura part to exist.
- The aura uses the character’s aura color where available, with a fallback color; Super Saiyan uses gold.
- Aura geometry is emitted by AuraMeshRenderer as procedural, double-sided tapered flame ribbons with inner energy filaments.
- The ribbon texture is a dedicated 32×64 alpha texture, tinted per vertex.
- Crucial coordinate correction: AuraLayer now scales custom model-pixel vertices by 1/16 after applying the player body anchor. The custom vertices otherwise use pixel-like model coordinates and were being emitted at the wrong size/distance.

**Latest mesh profile**
- Charging and transforming use 12 main flame ribbons; normal stance uses 9; Super Saiyan uses 12.
- The current mesh profile increases flame height and output relative to the original implementation.
- The flame profile is intended to rise from around the waist/body, flare around the torso, and taper/curl upward.
- Turbo adds extra amplitude/scale.
- The broad horizontal pressure-ring mesh draw was removed in commit 689ef584. A private helper may remain in the source but is no longer called by the main aura render path.

**Important validation caveat**
The coordinate correction is a source-level fix, not proof that the aura now looks right in Minecraft. It has not yet been verified in-game at the current branch head.

### 3.2 First-person aura path

**File**
- src/main/java/com/powerlevelling/client/render/AuraFirstPersonRenderer.java

**Changes recorded**
- Uses the MultiBufferSource supplied by RenderHandEvent rather than the global world render buffer.
- Removes the prior second division of the already normalized charge ramp.
- Does not require a character snapshot to exist before rendering; fallback color is available.
- Uses a restrained peripheral view-space aura profile rather than a full-screen glow panel.

**Not yet verified**
- First-person visibility, hand overlap, crosshair obstruction, and correct buffer flushing still need an in-game test.
- Third-person front-facing camera mode should also be checked separately from first-person.

### 3.3 Body-originating energy wisps

**Files**
- src/main/java/com/powerlevelling/client/fx/AuraFx.java
- src/main/java/com/powerlevelling/client/fx/KiAuraWispParticle.java
- src/main/java/com/powerlevelling/config/ClientConfig.java

**Current behavior**
- AuraFx drives stance, charging, transformation, and Super Saiyan aura particles from synchronized visual state.
- Energy wisps are spawned around the character’s body center, with their vertical offset starting around the lower torso/body and rising upward.
- Wisp color uses the nearby character’s configured aura color/ki color where available.
- Wisp lifetime was shortened to 48–72 ticks and base size increased to 0.22–0.36 blocks; default maximum alpha was raised to 0.72.
- Default configuration counts were raised: stance 4, charging 8, Super Saiyan 8, transforming 12.
- Runtime lower bounds were added so legacy configuration files containing the previous zero defaults still produce visible wisps.

**Configuration caveat**
The runtime lower bounds currently override a configured zero for those particle counts. This is a deliberate temporary workaround for older config files whose defaults were zero, but it also means a user cannot fully disable those wisps by setting the count to zero. A proper config migration/enable-disable design is still needed.

### 3.4 Ground-originating rocks and debris

**File**
- src/main/java/com/powerlevelling/client/fx/AuraFx.java

**Current behavior**
- Small and large rocks are launched from points around the player’s ground footprint.
- The code searches nearby block collision shapes for an exposed surface height and spawns the rocks at that ground height, rather than starting them at player Y with a negative vertical offset.
- Initial debris fragments are placed at several points around the footprint and receive outward/upward velocity.
- Charging and transformation use separate rock counts/intensities; large stones are intended for higher charge/transformation intensity.
- The configuration defaults for charging rocks, large charging rocks, and the initial debris burst were increased.

### 3.5 Removal of camera-facing ring sprites from aura states

**File**
- src/main/java/com/powerlevelling/client/fx/AuraFx.java

The charging, transformation, and Super Saiyan aura paths no longer spawn the KI_GROUND_RING particle. The prior sprite-based ring could read as a large bright orb/hoop and was visually distracting when the camera pointed down. The broad horizontal mesh pressure ring was also removed from the active AuraMeshRenderer path.

The KI_GROUND_RING particle class/provider remains registered for the project, but the AuraFx aura-state paths no longer spawn it. Do not modify attack/impact rings or other combat/world effects as part of this aura task.

**Unresolved visual report**
The user’s most recent screenshot showed pale translucent arcs and large bright foot-level effects that made looking down difficult. The latest commit removes the known AuraFx ground-ring spawns and the active mesh pressure ring, but the exact source of every bright shape in that screenshot has not yet been proven. The screenshot predates the 689ef584 patch. Re-test the current head before assuming this issue remains or is fully resolved.

---

## 4. Bugs found and corrections made

1. **Aura mesh was not in player model space.** Custom vertices were authored in model pixels, but the layer did not apply the 1/16 model-to-block conversion. Fixed in AuraLayer by applying a 1/16 scale after the body anchor.
2. **Optional cosmetic-part dependency could suppress the entire aura.** Removed the requirement for a registered aura cosmetic part.
3. **Character snapshot dependency could suppress aura rendering.** Removed the created-character/snapshot hard gate and added a fallback tint.
4. **First-person renderer used the wrong buffer.** It now uses RenderHandEvent’s supplied buffer source.
5. **First-person charge ramp was divided by 100 twice.** The extra division was removed.
6. **Aura mesh was too close to the player/weak at default settings.** The earlier profile enlarged and increased output; the latest pass narrows the flame tongues while raising them and making the body-originating flow more legible.
7. **Wisp particle defaults were zero.** Defaults and temporary runtime lower bounds were added so wisps actually spawn on older configuration files.
8. **Wisp sprites were faint/long-lived.** Size, alpha, and refresh lifetime were adjusted.
9. **Rocks used player Y plus negative vertical offsets.** Replaced with ground collision-surface sampling and separate ground spawn points.
10. **Debris spawned at the player origin/feet.** Replaced with a burst from multiple nearby ground points.
11. **Aura state ground-ring sprites read as large glowing hoops/orbs.** Removed their spawn calls from the aura-state routines and removed the active mesh pressure-ring draw.

---

## 5. Temporary out-of-scope ki-orb experiment — reverted

The early feature branch briefly included:
- KiOrbMeshRenderer.
- KiOrbModel and KiOrbStyles changes.
- Small KiBlastRenderer and KiOrbHandRenderer changes.
- A ki_plasma_shell.png texture.

This was outside the user’s approved scope. Commit 6b75f7a5f4d88020d3168bd59abc9024f24d0425:
- Restored KiBlastRenderer.java, KiOrbHandRenderer.java, KiOrbModel.java, and KiOrbStyles.java from main.
- Removed KiOrbMeshRenderer.java.
- Removed the added ki_plasma_shell.png texture.

Those temporary changes are not part of the current implementation and must not be described as active features. The current branch diff from main was checked after cleanup and contains aura-related source/assets plus workflow/docs/wrapper metadata, not the temporary orb mesh files.

---

## 6. Commit history for this visual branch

Times below are GitHub commit timestamps (UTC), 2026-11-15. Short SHAs are shown for navigation; use the full SHA from the commit link when exact identification is required.

### Initial design and aura mesh
- cc201f82ec45 — define clean aura/ki visual direction.
- bb98b77c4476 — replace player aura quads with animated flame mesh.
- 6e2cf68473e6 — share flame-ribbon aura between first and third person.
- 3180e7d6575e — scale aura silhouette with configured aura size.
- 5aafa2896c9d — pass player scale through all aura ribbons.
- f1b255ff9a94 — add Gradle build-validation workflow.
- 66ca0221881b — clarify aura-layer ownership.
- 135737cb3eb1 — keep flame-ribbon texture continuous along mesh.
- 0f817a005c17 — sample consistent glow texture on both ribbon faces.

### Temporary ki-orb experiment, later reverted
- 0548944215da — temporary cubic-to-spherical ki-orb mesh experiment (out of scope; later reverted).
- 31e5a3b00cc1 — temporary orbital-band geometry refinement (later reverted).
- b3134b902f0e — temporary wrapped-filament winding fix (later reverted).
- ee357b2799fe — temporary ki-orb style-profile refactor (later reverted).
- fff6456d0845 — add plasma/ribbon texture work.
- 42f82d7e9202 — pressure-ring material adjustment.
- c65e6148583a — documentation of the temporary integrated sample.
- e034ed11275a — remove duplicate aura pressure render type.
- e9fef2c3e176 — mark Gradle wrapper executable for CI.
- 545f110c9219 — add temporary plasma-shell texture.
- 0308e9ccbda6 — document the temporary plasma material.
- 0e3e1aee903b — replace truncated plasma-shell texture.
- 9b738509e1cc — replace truncated aura ribbon texture.
- 46407ce61d61 — correct ribbon texture dimensions.

### Aura reliability, ground placement, and scope cleanup
- fff9feffbc3a — remove optional cosmetic-part dependency and increase aura visibility.
- e914b9ad7f97 — correct first-person render buffer/ramp and further remove aura render prerequisites.
- 32c8e70d9986 — apply model-space 1/16 scaling and ground-origin rock/debris placement.
- 3d7980c2e262 — tint body-originating aura wisps using character energy color and remove stance tint early-return.
- 6b75f7a5f4d8 — restore original ki-move renderers and remove the out-of-scope orb mesh experiment.
- 689ef584a075 — replace foot-level ring sprites with stronger body-originating aura behavior; raise particle defaults and remove aura-state ring spawns.

Current branch head recorded here: **689ef584a075c8c0c5dd2e7cdb47a25fe4b5f097**.

---

## 7. Testing and validation record

### User-provided test evidence
- The user previously reported a successful local Gradle build on an earlier feature-branch revision.
- The user then reported no meaningful in-game change from the aura work and observed that the visible effects did not match the intended result.
- A later screenshot in stance showed little/no readable aura.
- A later charging screenshot showed faint, broad translucent arcs and distracting foot-level shapes when looking down.

These reports are important acceptance feedback; do not dismiss them or call the visual overhaul finished.

### Current revision validation
- The current head 689ef584 has not been confirmed by a successful local build or a post-patch in-game screenshot.
- GitHub Actions runs for recent revisions have reported failure, but the available job information did not provide a successful compile result or useful compiler log. This must not be reported as a source compilation failure without actual compiler diagnostics.
- Do not infer that the build passes just because GitHub commit/tree operations succeeded.
- The next definitive check is a local clean build followed by a Minecraft client test.

### User test commands
From the PowerLevelling repository directory in PowerShell:

    git fetch origin
    git checkout visual-overhaul/aura-foundation
    git pull --ff-only origin visual-overhaul/aura-foundation
    git rev-parse --short HEAD
    .\gradlew.bat clean build
    .\gradlew.bat runClient

Expected short HEAD at the time of this handoff: **689ef58**.

---

## 8. Remaining implementation plan

Do these in order. Do not skip the validation gate.

### Phase 0 — Validate current head
1. Confirm local HEAD is 689ef58.
2. Run clean Gradle build.
3. Launch the dev client.
4. Capture a third-person screenshot in stance and while holding C to charge.
5. Also test a front-facing third-person camera and first-person, especially looking down.

### Phase 1 — Resolve the remaining visible-shape problem
1. Compare the new body wisps with the procedural mesh and identify which layer produces any remaining pale arcs/foot flashes.
2. Ensure no aura-state ground ring is being spawned by another aura emitter. Keep attack/impact/world rings out of this investigation unless a call site is proven to be an aura source.
3. Make the energy read as a cohesive, upward-flowing ki flame emanating from the character’s body, not as pale horizontal hoops or isolated white blobs.
4. Keep the player model readable inside the aura.
5. Keep the camera-down view clear; no large translucent screen-facing or foot-facing sprites should obscure the character.

### Phase 2 — Refine body-originating aura
1. Establish a recognizable low-output stance aura.
2. Build charging through a visible ramp: compact at start, progressively taller/brighter/faster, with a clear peak.
3. Use a small number of broad, asymmetric flame tongues plus narrow inner filaments. Avoid a uniform spinning ring.
4. Make the selected aura color obvious and keep the center/core from washing out the character.
5. Keep Turbo denser/more intense through motion and energy density, not just uniform scaling.

### Phase 3 — Ground debris and rock behavior
1. Verify stones visibly originate from actual nearby block surfaces on flat blocks, slabs, stairs, and uneven ground.
2. Make rocks lift with readable upward movement before orbiting outward; they must not pop into view at player-foot height.
3. Keep debris bursts short and ground-originating.
4. Tune count, size, lifetime, and opacity so looking down stays playable.
5. Keep aura-state ground accents separate from all ki attack/impact effects.

### Phase 4 — Transformation and reversion
1. Create a transformation-specific sequence: compression/buildup, release pulse, and sustained transformed aura.
2. Create a distinct untransform/power-down fade rather than an abrupt color/profile swap.
3. Current VisualStatePayload has CHARGING, STANCE, SUPER_SAIYAN, TRANSFORMING, and TURBO flags, but no dedicated UNTRANSFORMING flag. Transform.java sets the form back to base and syncs visual state; it does not expose a dedicated untransform visual window. A safe implementation may need a short-lived client visual event/flag. Keep this strictly visual and do not change transformation costs, timing, stats, or gameplay unless the user separately authorizes it.

### Phase 5 — First-person and performance
1. Verify RenderHandEvent buffer use and ensure no extra flush/buffer ordering issue.
2. Keep peripheral aura subtle enough not to obscure crosshair, hands, or the view.
3. Verify both WIDE and SLIM player skins.
4. Verify preview rendering, invisibility/spectator behavior, distance LOD, and multiple players.
5. Profile particle caps/performance; prefer better shapes/motion over excessive particle count.

### Phase 6 — Editable art assets
- The current aura flame is procedural Java geometry plus a texture; no Blockbench file exists from this work.
- Consider a Blockbench-authored aura model only if it provides a useful editable silhouette/animation beyond the procedural mesh.
- If any .bbmodel is created, commit the source alongside exported resources and document which AI model authored it. Do not claim an asset is Blockbench-authored unless the source file exists.

---

## 9. Acceptance criteria — do not mark complete until all pass

- A visible ki aura clearly emanates from the character’s body during stance and becomes substantially stronger during charging.
- The aura reads as upward-flowing energy flames from front, side, and three-quarter views.
- The aura color matches the selected character aura/ki color; Super Saiyan gold is distinct.
- Rocks visibly lift from the ground, not from the character’s feet/body.
- Debris originates from the surrounding ground and dissipates quickly.
- No huge pale ring/orb sprites obscure the screen when looking down.
- Transformation and reversion have distinct, readable visual transitions.
- Turbo has a distinct high-output visual profile.
- First-person and third-person views remain playable.
- Both player model types work and performance remains bounded.
- A successful clean build and in-game verification are recorded.

**Current status: IN PROGRESS — not visually accepted and not build-verified at the current head.**

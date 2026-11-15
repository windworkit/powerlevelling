> **Current-status notice (2026-11-15):** This art-direction document contains historical notes from an early branch experiment. For current implementation facts, exact AI-model attribution, out-of-scope rollback, commits, test evidence, and the remaining plan, read **docs/AI_WORK_LOG.md**. Ki-orb mesh notes below are explicitly historical and reverted.

# Visual Effects Overhaul — Aura & Ki Direction

This is a clean visual direction for the next effects pass. Existing effects are reference-only; the new presentation should not inherit their shapes or visual assumptions.

## Art direction

The target is a readable, anime-inspired Dragon Ball presentation rather than a cloud of generic particles.

- **Silhouette first:** an aura must read clearly around a player at a distance. Use tall, tapered flame tongues with a deliberate outer contour, not a uniform oval shell.
- **Layered energy:** separate the silhouette into a quiet outer envelope, a few broad flowing tongues, narrow inner streaks, and a restrained hot core. Each layer has its own motion and opacity.
- **Motion with intent:** tongues stretch upward, curl, collapse, and reform. Avoid every element rotating at the same speed or flickering randomly.
- **Controlled bloom:** emissive rendering should make the energy luminous without washing out the player or turning the entire effect into a flat white blob.
- **Grounding:** high-output states may disturb the ground with a short-lived pressure ring, dust, and debris. These are accents, not the main aura.
- **Readable state changes:** charging builds pressure; transformation has a distinct burst and sustained reveal; the transformed idle aura settles into a recognizable signature.

## First visual family: baseline ki aura

The baseline aura is the foundation and should be attractive before any transformation is added.

1. A narrow, low-opacity outer silhouette, taller than the player.
2. Three to five asymmetric flame tongues rising from around the legs and shoulders.
3. A small number of inner energy filaments that travel upward at different speeds.
4. A subtle waist/torso core, never a full-body opaque glow.
5. Optional ground pressure ring only while charging or releasing energy.

Color comes from the character's aura/ki color. Keep the value range controlled so saturated colors remain distinct.

## State profiles

| State | Shape and motion | Accent |
|---|---|---|
| Stance | Low, compact, restrained wisps | Minimal |
| Charging | Aura grows taller and wider over time; tongues become sharper and faster | Ground pressure and occasional debris |
| Transformation | Brief compression, radial burst, then expanding vertical flame silhouette | Strong light pulse and state-specific streaks |
| Super Saiyan | Tall, confident golden flame silhouette with a bright inner edge | Sparse electrical arcs |
| High-output / Turbo | Stronger amplitude and density, not simply a larger copy | More frequent pressure waves |

## Explicitly out of scope for the current implementation

Ki attacks and projectiles are not part of the current authorized visual pass. This includes ki orbs, blasts, beams, discs, impacts, their renderers, and their mechanics. The current task is limited to charging, body aura, transformation, untransformation, and Turbo visuals. Do not implement the older ki-attack visual ideas below unless the user separately authorizes that work.

## Technical approach

- Preserve the existing synchronized visual states and gameplay triggers where possible.
- Build rendering as small, independently tunable layers with explicit profiles for each state.
- Keep particle count bounded and distance/quality aware; visual quality should come from shape and timing, not brute-force particle volume.
- Use Blockbench-authored geometry/textures where it adds value, but do not force every effect into GeckoLib. Player-attached aura rendering and fast projectiles may be better served by dedicated render layers.
- Keep art source files (including `.bbmodel`) alongside exported assets so they remain editable.
- Do not delete or replace the existing renderer until the replacement covers third-person, first-person, preview, slim/wide player models, invisibility, and LOD behavior.

## Acceptance checks

- The aura has a distinctive silhouette from front, side, and three-quarter views.
- The player remains readable inside it.
- Charging has a visible ramp rather than an immediate on/off switch.
- The transformed idle effect is stable and recognizable, not noisy.
- First-person rendering does not obstruct the view.
- Different ki colors remain legible against bright and dark environments.
- Performance remains bounded with multiple players nearby.

## Implementation order

1. Validate the current body aura and particle placement in-game before making more speculative renderer changes.
2. Refine the stance and charging aura into a clearly readable, upward-flowing silhouette.
3. Verify ground-originating rocks/debris and remove any remaining foot-level screen-obscuring sprites.
4. Add dedicated transformation and untransformation transitions, using visual-only state handling.
5. Refine Turbo and Super Saiyan profiles after the baseline aura is visually accepted.
6. Ki attacks/projectiles remain out of scope unless the user separately authorizes them.


## Historical first implementation sample (partly superseded)

This section began as an implementation snapshot, not a guarantee of current branch contents. The current source of truth is docs/AI_WORK_LOG.md. The procedural aura mesh remains in the branch. The experimental ki-orb mesh described below was out of scope and has been reverted by commit 6b75f7a5f4d88020d3168bd59abc9024f24d0425.

### Aura mesh (current status in docs/AI_WORK_LOG.md)

- `AuraMeshRenderer` emits tapered, double-sided flame ribbons with an asymmetric silhouette, inner hot filaments, controlled curvature, and a subtle ground pressure ring.
- `energy_ribbon.png` is a dedicated 32×64 alpha texture for the flame material. Ki color is applied by vertex tint, so one authored texture supports different character ki colors and Super Saiyan gold.
- `AuraLayer` continues to resolve synchronized visual state, character tint, cosmetic anchor, helmet behavior, visibility, and LOD. Geometry is delegated to the new renderer.
- `AuraFirstPersonRenderer` uses the same ribbon language in a restrained peripheral view-space profile, avoiding a bright panel over the crosshair.

### Temporary ki-orb mesh experiment — reverted

- A temporary KiOrbMeshRenderer / plasma-shell experiment appeared in intermediate commits.
- It was outside the user-approved scope and was removed in commit 6b75f7a5f4d88020d3168bd59abc9024f24d0425.
- KiBlastRenderer.java, KiOrbHandRenderer.java, KiOrbModel.java, and KiOrbStyles.java were restored from main; KiOrbMeshRenderer.java and ki_plasma_shell.png were removed.
- This is historical context only. Do not treat the orb mesh as part of the current branch or recreate it under the current scope.

### Validation status

The source changes are committed on the feature branch, but the current head 689ef584a075c8c0c5dd2e7cdb47a25fe4b5f097 has **not** been verified by a successful clean build or post-patch Minecraft test. Recent GitHub Actions runs report failure without a useful compiler result; do not interpret that alone as proof of a Java compilation error or proof of a passing build. The user previously reported weak/no visible changes and provided screenshots showing pale arcs and foot-level effects. The latest aura particle/ring revision was pushed after that screenshot and still requires local testing. See docs/AI_WORK_LOG.md for the full commit timeline, model attribution, scope, current state, and test plan.

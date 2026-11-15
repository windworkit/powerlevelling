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

## Ki attacks

Ki attacks should share a material language with the aura but have their own silhouettes:

- **Orbs:** bright core, colored shell, controlled surface motion, and a soft falloff.
- **Blasts:** compact projectile with a clear direction and a short trailing wake.
- **Beams:** stable bright center, colored body, soft outer envelope, and impact flare.
- **Discs:** thin, sharply readable edge and a restrained motion trail.
- **Impacts:** brief radial flash, expanding ring, then dissipating fragments.

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

1. Replace the baseline aura presentation with the new layered flame silhouette.
2. Add charging ramp and transformation transition timing.
3. Add a distinct Super Saiyan profile and restrained lightning.
4. Rework ki orb, blast, beam, disc, and impact visuals to share the same material language.
5. Add additional transformation/aura profiles after the first family is tested in-game.

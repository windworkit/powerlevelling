package com.powerlevelling.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.powerlevelling.client.ClientOrbState;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;

/**
 * What the in-hand "ki forming" orb looks like, picked by the attack's present.shape (K2-K4): the default round orb, or
 * disc (flat spinning saw above the palm), barrage (small flickers in both hands), homing (mini orbs circling the hand),
 * bomb (pulsing dark-cored orb). Shared by the third-person layer ({@link KiOrbLayer}) and the first-person hand
 * ({@link KiOrbHandRenderer}); everything is drawn around the current pose origin.
 * <p>
 * The base orb is a continuous mesh with its own integrated surface bands and energy filament.
 */
public final class KiOrbStyles {
    private KiOrbStyles() {
    }

    /**
     * @param up      sign of "up" on the pose's Y axis: +1 in first-person view space, -1 in the player model space
     * @param lateral half the distance between the two hands for the barrage flickers (pose units)
     * @param elapsed ticks since the orb started (drives the bomb's pulse)
     */
    public static void render(ClientOrbState.Orb orb, PoseStack pose, MultiBufferSource buf, float radius, float ageTicks,
                              float partial, float alpha, float up, float lateral, float elapsed) {
        float age = ageTicks + partial;
        switch (orb.shape) {
            case "disc" -> {
                // Flat saw hovering above the raised palm, spinning up as the windup runs.
                pose.pushPose();
                pose.translate(0.0, up * radius * 1.15f, 0.0);
                KiOrbModel.renderDisc(pose, buf, radius * 1.6f, radius * 0.45f, orb.color, orb.coreColor, ageTicks, partial,
                        alpha, 0.6f + Math.min(1f, orb.progress(elapsed)) * 1.6f);
                
                // DRAGON BALL STYLE: Add glow halos to disc (flat but still gets spherical glow)
                float discRadius = radius * 1.6f;
                // Energy crackle around disc edge
                int discSpikes = 8 + (int)(radius * 4f);
                pose.popPose();
            }
            case "barrage" -> {
                // A small orb in each palm, flickering at different rates.
                for (int side = -1; side <= 1; side += 2) {
                    float flick = 0.65f + 0.45f * Mth.sin(age * (1.7f + 0.4f * side) + side * 1.3f)
                            * Mth.sin(age * 0.9f + side) ;
                    pose.pushPose();
                    pose.translate(side * lateral, 0.0, 0.0);
                    float orbRad = radius * Mth.clamp(flick, 0.35f, 1.15f);
                    KiOrbModel.render(pose, buf, orbRad, orb.color, orb.coreColor, ageTicks,
                            partial, alpha, false, null);
                    
                    // DRAGON BALL STYLE: Each barrage orb gets mini glow halos
                    pose.popPose();
                }
            }
            case "homing" -> {
                // Five mini orbs on tilted orbits around a small centre orb.
                float centerRad = radius * 0.5f;
                KiOrbModel.render(pose, buf, centerRad, orb.color, orb.coreColor, ageTicks, partial, alpha, false, null);
                
                // DRAGON BALL STYLE: Center orb gets glow halos
                int centerSpikes = 4 + (int)(radius * 2f);
                
                int n = 5;
                float ring = radius * 2.5f;
                for (int i = 0; i < n; i++) {
                    float ang = age * 0.28f + i * Mth.TWO_PI / n;
                    pose.pushPose();
                    pose.translate(Mth.cos(ang) * ring, up * Mth.sin(ang * 0.7f + i) * ring * 0.45f, Mth.sin(ang) * ring);
                    float orbRad = radius * 0.42f;
                    KiOrbModel.render(pose, buf, orbRad, orb.color, orb.coreColor, ageTicks + i * 3f, partial, alpha,
                            false, null);
                    
                    // DRAGON BALL STYLE: Each orbiting orb gets mini glow
                    pose.popPose();
                }
            }
            case "bomb" -> {
                // Heavy dark core, shell pulsing faster as the charge builds.
                float p = Mth.clamp(orb.progress(elapsed), 0f, 1f);
                float pulse = 1f + 0.14f * Mth.sin(age * (0.35f + 1.1f * p));
                int dark = KiOrbModel.mix(orb.color, 0x000000, 0.85f);
                float bombRad = radius * pulse;
                KiOrbModel.render(pose, buf, bombRad, orb.color, dark, ageTicks, partial, alpha, false, null);
                
                // DRAGON BALL STYLE: Bomb gets intense glow halos (building energy)
                float bombGlowAlpha = alpha * (0.5f + 0.5f * p); // More intense as charge builds
                // Heavy energy spikes for bomb
                int bombSpikes = 10 + (int)(p * 8f);
            }
            case "beam" -> {
                // Cupped, crackling orb: pulsing faster with charge, with short white sparks arcing off its surface.
                float p = Mth.clamp(orb.progress(elapsed), 0f, 1f);
                float pulse = 1f + (0.05f + 0.12f * p) * Mth.sin(age * (0.5f + 1.2f * p));
                float beamRad = radius * pulse;
                KiOrbModel.render(pose, buf, beamRad, orb.color, orb.coreColor, ageTicks, partial, alpha, false, null);
                
                // DRAGON BALL STYLE: Beam charge orb gets intense glow and crackle
                float beamGlowAlpha = alpha * (0.4f + 0.6f * p);
                
                int arcs = 4 + (int) (p * 5f);
                for (int i = 0; i < arcs; i++) {
                    float flick = Mth.sin(age * (1.3f + 0.37f * i) + i * 2.1f);
                    if (flick < 0.15f) continue;
                    pose.pushPose();
                    pose.mulPose(Axis.YP.rotationDegrees(i * 61f + age * 4f));
                    pose.mulPose(Axis.XP.rotationDegrees(i * 37f - age * 3f));
                    pose.translate(0.0, 0.0, beamRad * (1.25f + 0.25f * flick));
                    KiOrbModel.renderBox(pose, buf, beamRad * 0.045f, beamRad * 0.045f, beamRad * (0.25f + 0.35f * flick),
                            KiOrbModel.mix(orb.color, 0xFFFFFF, 0.7f), alpha * flick);
                    pose.popPose();
                }
                
                // Additional energy spikes for beam charge
                int beamSpikes = 6 + (int)(p * 6f);
            }
            default -> {
                float defaultRad = radius;
                KiOrbModel.render(pose, buf, defaultRad, orb.color, orb.coreColor, ageTicks, partial, alpha, false, null);
                // DRAGON BALL STYLE: Default orb also gets glow halos
                int defaultSpikes = 4 + (int)(radius * 3f);
            }
        }
    }
}

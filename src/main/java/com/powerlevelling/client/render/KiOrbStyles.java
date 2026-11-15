package com.powerlevelling.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.powerlevelling.client.ClientOrbState;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;

/**
 * Shape profiles for charged ki. The base orb is the same continuous mesh in hand and in flight;
 * these profiles only change placement, scale, and formation.
 */
public final class KiOrbStyles {
    private KiOrbStyles() {
    }

    /**
     * @param up sign of "up" on the pose's Y axis: +1 in first-person view space, -1 in player model space
     * @param lateral half the distance between hands for a two-orb barrage
     * @param elapsed ticks since charging began
     */
    public static void render(ClientOrbState.Orb orb, PoseStack pose, MultiBufferSource buffer, float radius,
                              float ageTicks, float partial, float alpha, float up, float lateral, float elapsed) {
        float age = ageTicks + partial;
        float progress = Mth.clamp(orb.progress(elapsed), 0.0f, 1.0f);

        switch (orb.shape) {
            case "disc" -> {
                pose.pushPose();
                pose.translate(0.0, up * radius * 1.15f, 0.0);
                KiOrbModel.renderDisc(pose, buffer, radius * 1.6f, radius * 0.45f,
                        orb.color, orb.coreColor, ageTicks, partial, alpha,
                        0.6f + progress * 1.6f);
                pose.popPose();
            }
            case "barrage" -> {
                for (int side = -1; side <= 1; side += 2) {
                    float pulse = 0.78f + 0.22f * Mth.sin(age * (1.25f + side * 0.18f) + side * 1.3f);
                    pose.pushPose();
                    pose.translate(side * lateral, 0.0, 0.0);
                    KiOrbModel.render(pose, buffer, radius * pulse, orb.color, orb.coreColor,
                            ageTicks, partial, alpha, false, null);
                    pose.popPose();
                }
            }
            case "homing" -> {
                // A central seed orb with five satellites following a slightly tilted orbital path.
                KiOrbModel.render(pose, buffer, radius * 0.52f, orb.color, orb.coreColor,
                        ageTicks, partial, alpha, false, null);
                int count = 5;
                float orbitRadius = radius * 2.35f;
                for (int i = 0; i < count; i++) {
                    float angle = age * 0.22f + i * Mth.TWO_PI / count;
                    float vertical = Mth.sin(angle * 0.72f + i * 0.9f) * orbitRadius * 0.42f;
                    pose.pushPose();
                    pose.translate(Mth.cos(angle) * orbitRadius, up * vertical,
                            Mth.sin(angle) * orbitRadius);
                    KiOrbModel.render(pose, buffer, radius * 0.34f, orb.color, orb.coreColor,
                            ageTicks + i * 2.5f, partial, alpha * 0.88f, false, null);
                    pose.popPose();
                }
            }
            case "bomb" -> {
                float pulse = 1.0f + 0.12f * Mth.sin(age * (0.35f + 1.1f * progress));
                int darkCore = KiOrbModel.mix(orb.color, 0x000000, 0.82f);
                KiOrbModel.render(pose, buffer, radius * pulse, orb.color, darkCore,
                        ageTicks, partial, alpha, false, null);
            }
            case "beam" -> {
                float pulse = 1.0f + (0.04f + 0.10f * progress) * Mth.sin(age * (0.5f + 1.0f * progress));
                KiOrbModel.render(pose, buffer, radius * pulse, orb.color, orb.coreColor,
                        ageTicks, partial, alpha, false, null);
            }
            default -> KiOrbModel.render(pose, buffer, radius, orb.color, orb.coreColor,
                    ageTicks, partial, alpha, false, null);
        }
    }
}

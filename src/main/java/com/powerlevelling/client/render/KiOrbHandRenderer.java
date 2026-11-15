package com.powerlevelling.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.powerlevelling.PowerLevelling;
import com.powerlevelling.client.ClientOrbState;
import com.powerlevelling.network.OrbStatePayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHandEvent;

/**
 * First-person "ki forms in your hand": draws the local player's forming orb in view space (camera at the origin, looking
 * down -Z, +X right, +Y up) in front of the main hand while the hand pass renders. RenderHandEvent is used (not a level
 * stage) because its pose stack already includes view bobbing and the hand pass has its own depth, so the orb can't be
 * clipped by terrain. The event fires per hand; we only draw on the MAIN_HAND call and never cancel it (the arm still draws).
 * The world-space radius is shrunk to a screen-friendly size: a full charge is big but leaves most of the view free.
 */
@EventBusSubscriber(modid = PowerLevelling.MODID, value = Dist.CLIENT)
public final class KiOrbHandRenderer {
    // View-space placement (blocks): sideways, down, forward. Roughly above-left of the vanilla fist.
    public static final float VIEW_X = 0.34f;
    public static final float VIEW_Y = -0.30f;
    public static final float VIEW_Z = -0.80f;

    private KiOrbHandRenderer() {
    }

    /** World radius (0.05..~1.0 blocks) to the radius drawn in view space (~0.03..~0.17). */
    public static float viewRadius(float worldRadius) {
        return 0.03f + 0.147f * Math.max(0f, worldRadius - 0.05f);
    }

    /** +1 right side of the screen, -1 left, 0 centred, for the orb's hand. */
    public static float side(LocalPlayer player, int hand) {
        float main = player.getMainArm() == HumanoidArm.RIGHT ? 1f : -1f;
        return switch (hand) {
            case OrbStatePayload.HAND_OFF -> -main;
            case OrbStatePayload.HAND_BOTH -> 0f;
            default -> main; // main, finger
        };
    }

    /** Overhead orb (two hands above the head): view-space spot, a bit up and further out so it reads as "over my head". */
    public static final float HIP_X = 0.40f;
    public static final float HIP_Y = -0.44f;
    public static final float HIP_Z = -0.55f;
    public static final float OVERHEAD_Y = 0.30f;
    public static final float OVERHEAD_Z = -0.95f;

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;
        ClientOrbState.Orb orb = ClientOrbState.get(player.getId());
        if (orb == null || orb.hand == OrbStatePayload.HAND_NONE) return; // body flare is particles only (KiOrbFx)

        float partial = event.getPartialTick();
        float elapsed = orb.elapsed(mc.level.getGameTime(), partial);
        float r = viewRadius(orb.radius(elapsed));
        float alpha = Mth.clamp(0.4f + 0.3f * elapsed, 0f, 1f);
        float age = player.tickCount + partial;
        float side = side(player, orb.hand);

        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        if (orb.anchor == OrbStatePayload.ANCHOR_HIP) {
            // Cupped low at the main-hand side, close to the camera.
            float main = player.getMainArm() == HumanoidArm.RIGHT ? 1f : -1f;
            pose.translate(HIP_X * main + 0.005f * Mth.cos(age * 0.2f), HIP_Y + 0.01f * Mth.sin(age * 0.22f), HIP_Z - r * 0.5f);
        } else if (orb.anchor == OrbStatePayload.ANCHOR_OVERHEAD) {
            pose.translate(0.006f * Mth.cos(age * 0.2f), OVERHEAD_Y + 0.012f * Mth.sin(age * 0.22f), OVERHEAD_Z - r * 0.5f);
        } else if (orb.hand == OrbStatePayload.HAND_FINGER) {
            // Small glowing cube at the fingertip: further out and a little higher than a palm orb.
            pose.translate(VIEW_X * side + 0.004f * Mth.cos(age * 0.3f), VIEW_Y + 0.12f, VIEW_Z - 0.28f);
        } else {
            pose.translate(VIEW_X * side + 0.006f * Mth.cos(age * 0.2f),
                    (side == 0f ? VIEW_Y + 0.04f : VIEW_Y) + 0.012f * Mth.sin(age * 0.22f),
                    VIEW_Z - r * 0.8f); // bigger orbs sit a little further out
        }
        KiOrbStyles.render(orb, pose, event.getMultiBufferSource(), r, player.tickCount, partial, alpha, 1f, 0.30f, elapsed);
        pose.popPose();
    }
}

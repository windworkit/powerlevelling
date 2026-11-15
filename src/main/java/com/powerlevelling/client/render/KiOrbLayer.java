package com.powerlevelling.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.powerlevelling.client.ClientOrbState;
import com.powerlevelling.network.OrbStatePayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Third-person "ki forms in your hand": draws the player's forming orb (ClientOrbState) at the chosen hand, for every player
 * (self in third person, others always). Added to the WIDE and SLIM player renderers in ClientEvents.
 * <p>
 * The pose stack here is the model space (block units, y down, x mirrored: a 180 degree turn about Z). We go to the arm's
 * joint with translateAndRotate, walk down the arm to the palm, and counter-rotate by the arm's rotation so the orb keeps the
 * body's orientation instead of swinging with the arm. Because the mod has no arm animation yet (design 8.9), an arm that
 * hangs is "virtually raised" to hold the orb in front of the shoulder; an arm an animation has already raised is used as is.
 */
public class KiOrbLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    /** Arm pitch (rad) the orb is held at when the arm itself hangs: ~80 degrees forward. */
    private static final float VIRTUAL_ARM_XROT = -1.4f;
    /** An arm pitched further forward than this (rad) counts as already raised. */
    private static final float ALREADY_RAISED_XROT = -1.2f;

    public KiOrbLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw,
                       float headPitch) {
        ClientOrbState.Orb orb = ClientOrbState.get(player.getId());
        if (orb == null || orb.hand == OrbStatePayload.HAND_NONE) return; // body flare is particles only (KiOrbFx)
        Minecraft mc = Minecraft.getInstance();
        // The local player's orb in first person is drawn by KiOrbHandRenderer: never twice.
        if (player == mc.player && mc.options.getCameraType().isFirstPerson()) return;

        long now = player.level().getGameTime();
        float elapsed = orb.elapsed(now, partialTick);
        float radius = orb.radius(elapsed);
        float alpha = Mth.clamp(0.4f + 0.3f * elapsed, 0f, 1f);
        PlayerModel<AbstractClientPlayer> model = getParentModel();

        poseStack.pushPose();
        if (orb.hand == OrbStatePayload.HAND_BOTH) {
            ModelPart body = model.body;
            body.translateAndRotate(poseStack);
            if (orb.anchor == OrbStatePayload.ANCHOR_HIP) {
                // Cupped at the hip on the main-hand side (model space: +Y down, right side = -X, front = -Z).
                boolean mainRight = player.getMainArm() == HumanoidArm.RIGHT;
                poseStack.translate(mainRight ? -0.42 : 0.42, 0.62, -(0.2 + 0.4 * radius));
            } else if (orb.anchor == OrbStatePayload.ANCHOR_OVERHEAD) {
                // Held above the head between both hands (model space: -Y is up, the head top is 8px above the body origin).
                poseStack.translate(0.0, -(10.0 / 16.0 + 0.8 * radius), -0.05);
            } else {
                // Between both hands, in front of the chest.
                poseStack.translate(0.0, 4.0 / 16.0, -(0.35 + 0.55 * radius));
            }
            counterRotate(poseStack, body.xRot, body.yRot, body.zRot);
        } else {
            boolean finger = orb.hand == OrbStatePayload.HAND_FINGER;
            boolean mainSide = orb.hand == OrbStatePayload.HAND_MAIN || finger;
            boolean rightArm = mainSide == (player.getMainArm() == HumanoidArm.RIGHT);
            ModelPart arm = rightArm ? model.rightArm : model.leftArm;
            arm.translateAndRotate(poseStack);
            float effX = arm.xRot;
            if (arm.xRot > ALREADY_RAISED_XROT) { // hanging / swinging arm: virtually hold it forward (adds to xRot)
                poseStack.mulPose(Axis.XP.rotation(VIRTUAL_ARM_XROT - arm.xRot));
                effX = VIRTUAL_ARM_XROT;
            }
            // Down the arm to the palm (+Y local; the arm cube is centred 1px off the joint), a bit beyond it for the
            // orb's size, and slightly above the palm (-Z local = the arm's front face).
            double cx = (rightArm ? -1.0 : 1.0) / 16.0;
            if (finger) {
                // The tip of the (virtually raised) arm: 12px down the arm, just past the end.
                poseStack.translate(cx, 12.0 / 16.0 + 0.6 * radius, -(0.5 / 16.0));
            } else {
                poseStack.translate(cx, 10.0 / 16.0 + 0.45 * radius, -(2.0 / 16.0 + 0.15 * radius));
            }
            counterRotate(poseStack, effX, arm.yRot, arm.zRot);
        }

        // Remember where the orb is in the world (camera-relative pose + camera position) for the suck-in particles.
        Vector3f p = new Vector3f();
        poseStack.last().pose().transformPosition(0f, 0f, 0f, p);
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        Vec3 world = new Vec3(cam.x + p.x(), cam.y + p.y(), cam.z + p.z());
        if (world.distanceToSqr(player.position()) < 16.0) { // sanity: e.g. the inventory preview has a different space
            orb.renderedPos = world;
            orb.renderedAt = now;
        }

        KiOrbStyles.render(orb, poseStack, buffer, radius, player.tickCount, partialTick, alpha, -1f, 0.33f, elapsed);
        poseStack.popPose();
    }

    /** Undoes ModelPart's rotation (Z, then Y, then X order, see ModelPart.translateAndRotate). */
    private static void counterRotate(PoseStack poseStack, float xRot, float yRot, float zRot) {
        poseStack.mulPose(new Quaternionf().rotationZYX(zRot, yRot, xRot).conjugate());
    }
}

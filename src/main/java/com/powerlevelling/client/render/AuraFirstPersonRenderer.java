package com.powerlevelling.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.powerlevelling.PowerLevelling;
import com.powerlevelling.character.CharacterSnapshot;
import com.powerlevelling.client.ClientCharacters;
import com.powerlevelling.client.ClientVisualState;
import com.powerlevelling.config.ClientConfig;
import com.powerlevelling.network.VisualStatePayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHandEvent;

/**
 * First-person aura renderer.
 * <p>
 * <b>Coordinate space matters here.</b> {@code RenderHandEvent} runs in view space where one unit is one
 * <i>block</i> (camera at the origin, looking down -Z). The third-person {@link AuraLayer} works in the
 * player model's pixel space (16 px = 1 block), so all the same-looking constants (11, 12, 23...) would be
 * 16x too large if reused verbatim - a quad that is "11 px" in the body layer is 11 <i>blocks</i> here, which
 * filled the whole screen with white. Everything below is therefore expressed in blocks.
 */
@EventBusSubscriber(modid = PowerLevelling.MODID, value = Dist.CLIENT)
public final class AuraFirstPersonRenderer {
    private AuraFirstPersonRenderer() {
    }

    /** Distance from the camera at which wisps orbit (blocks). */
    private static final float WISP_DISTANCE = 1.35f;
    /** Height of a wisp strand (blocks). */
    private static final float WISP_HEIGHT = 0.55f;
    /** Orbit radius of wisps around the view axis (blocks). */
    private static final float WISP_RADIUS = 0.85f;

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;
        if (event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) return;

        CharacterSnapshot snap = ClientCharacters.effective(player.getId());
        if (snap == null) return;

        boolean inStance = ClientVisualState.has(player.getId(), VisualStatePayload.STANCE);
        boolean charging = ClientVisualState.has(player.getId(), VisualStatePayload.CHARGING);
        boolean isSSJ = ClientVisualState.hasSuperSaiyan(player.getId());
        boolean transforming = ClientVisualState.has(player.getId(), VisualStatePayload.TRANSFORMING);
        boolean isTurbo = ClientVisualState.selfTurbo();
        if (!inStance && !charging && !isSSJ && !transforming && !isTurbo) return;

        ClientVisualState.State st = ClientVisualState.get(player.getId());
        float chargeTicks = st != null ? st.chargeTicks : 0f;
        boolean intense = charging || transforming;
        int ramp = (intense || isTurbo) ? Mth.clamp((int)chargeTicks / 40, 0, 100) : 0;

        int auraColor;
        if (isSSJ) {
            auraColor = 0xFFD700;
        } else {
            auraColor = snap.kiColor();
            if (auraColor == -1) auraColor = 0xFF0000;
        }

        float age = player.tickCount + event.getPartialTick();
        float configScale = ClientConfig.AURA_SCALE.get().floatValue();
        float turboMult = isTurbo ? ClientConfig.TURBO_AURA_SCALE_MULT.get().floatValue() : 1f;
        // Aura strength 0..1: quiet in stance, loud while charging, amplified by turbo.
        float strength = (intense ? 0.45f + 0.55f * ramp / 100f : isSSJ ? 0.7f : 0.3f) * turboMult;
        strength *= Mth.clamp(configScale, 0.1f, 2f);

        PoseStack pose = event.getPoseStack();
        MultiBufferSource buffer = mc.renderBuffers().bufferSource();
        pose.pushPose();
        drawViewSpaceAura(pose, buffer, auraColor, age, ramp, strength, isSSJ, isTurbo);
        pose.popPose();
    }

    private static void drawViewSpaceAura(PoseStack pose, MultiBufferSource buffer, int auraColor,
                                          float age, float ramp, float strength, boolean isSSJ, boolean isTurbo) {
        // Use the flame texture for first-person too (more DBZ-like)
        VertexConsumer vc = buffer.getBuffer(AuraLayer.FLAME_RENDER_TYPE);
        final int light = 0xF000F0;
        int r = (auraColor >> 16) & 0xFF;
        int g = (auraColor >> 8) & 0xFF;
        int b = auraColor & 0xFF;

        // More wisps for a fuller peripheral aura
        int wispCount = isSSJ ? 12 : 8;
        if (isTurbo) wispCount = Math.min(wispCount + 6, 20); // Add up to 6 more in turbo

        for (int i = 0; i < wispCount; i++) {
            float seed = i * 137.508f;
            float dir = (i % 2 == 0) ? 1f : -1f;
            float angle = seed + age * (0.06f + ramp * 0.08f) * dir;

            // Ring around the view axis: wisps drift past the edges of the screen, never dead centre.
            // Wider orbit for more peripheral coverage
            float radius = WISP_RADIUS * (1.0f + 0.4f * Mth.sin(seed * 2.7f + age * 0.3f));
            float x = Mth.cos(angle) * radius;
            float y = Mth.sin(angle) * radius * 0.7f; // slightly taller ellipse
            float z = -WISP_DISTANCE + 0.3f * Mth.sin(seed * 1.9f + age * 0.25f);

            float flicker = 0.5f + 0.5f * Mth.sin(age * (0.9f + 0.3f * Mth.sin(seed)) + seed * 3f);
            // Lower alpha to avoid blowout, but keep visible
            float alpha = strength * (0.12f + 0.10f * flicker);

            pose.pushPose();
            pose.translate(x, y, z);
            // Face the wisp back toward the camera (looks at +Z) and wobble slightly.
            pose.mulPose(Axis.YP.rotation(-angle + Mth.HALF_PI));
            pose.mulPose(Axis.ZP.rotation(0.15f * Mth.sin(age * 0.7f + seed)));
            // Slightly larger quads for better peripheral visibility
            AuraLayer.quad(vc, pose.last(), 0.12f, WISP_HEIGHT * 1.3f, r, g, b, AuraLayer.alpha(alpha), light);
            pose.mulPose(Axis.YP.rotation(Mth.HALF_PI));
            AuraLayer.quad(vc, pose.last(), 0.10f, WISP_HEIGHT * 1.2f, r, g, b, AuraLayer.alpha(alpha * 0.8f), light);
            pose.popPose();
        }

        // Soft ambient glow: gentle crossed quads far enough away to read as haze, not a wall.
        float haze = strength * 0.08f;
        if (haze > 0.001f) {
            pose.pushPose();
            pose.translate(0f, -0.1f, -2.2f);
            AuraLayer.quad(vc, pose.last(), 1.4f, 1.0f, r, g, b, AuraLayer.alpha(haze), light);
            pose.popPose();
        }
    }
}

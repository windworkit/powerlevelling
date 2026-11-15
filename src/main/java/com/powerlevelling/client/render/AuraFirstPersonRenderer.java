package com.powerlevelling.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
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

        boolean inStance = ClientVisualState.has(player.getId(), VisualStatePayload.STANCE);
        boolean charging = ClientVisualState.has(player.getId(), VisualStatePayload.CHARGING);
        boolean isSSJ = ClientVisualState.hasSuperSaiyan(player.getId());
        boolean transforming = ClientVisualState.has(player.getId(), VisualStatePayload.TRANSFORMING);
        boolean isTurbo = ClientVisualState.selfTurbo();
        if (!inStance && !charging && !isSSJ && !transforming && !isTurbo) return;

        ClientVisualState.State st = ClientVisualState.get(player.getId());
        float chargeTicks = st != null ? st.chargeTicks : 0f;
        boolean intense = charging || transforming;
        float ramp = (intense || isTurbo) ? Mth.clamp(chargeTicks / 40.0f, 0.0f, 1.0f) : 0.0f;

        int auraColor;
        if (isSSJ) {
            auraColor = 0xFFD700;
        } else {
            auraColor = snap != null
                    ? (snap.auraColor() >= 0 ? snap.auraColor() : snap.kiColor())
                    : -1;
            if (auraColor < 0) auraColor = 0xFF3A18;
        }

        float age = player.tickCount + event.getPartialTick();
        float configScale = ClientConfig.AURA_SCALE.get().floatValue();
        float turboMult = isTurbo ? ClientConfig.TURBO_AURA_SCALE_MULT.get().floatValue() : 1f;
        // Keep first-person effects at the edge of the view while giving charge,
        // transformation, SSJ and turbo distinct levels of presence.
        float strength = (transforming ? 0.82f + 0.18f * ramp
                : charging ? 0.68f + 0.26f * ramp
                : isSSJ ? 0.82f : isTurbo ? 0.78f : 0.34f) * turboMult;
        strength *= Mth.clamp(configScale, 0.1f, 2f);

        PoseStack pose = event.getPoseStack();
        // RenderHandEvent supplies the immediate hand-pass buffer; using the world
        // buffer here queues geometry into a batch that may already have been flushed.
        MultiBufferSource buffer = event.getMultiBufferSource();
        pose.pushPose();
        drawViewSpaceAura(pose, buffer, auraColor, age, ramp, strength, isSSJ, isTurbo);
        pose.popPose();
    }

    private static void drawViewSpaceAura(PoseStack pose, MultiBufferSource buffer, int auraColor,
                                          float age, float ramp, float strength, boolean isSSJ, boolean isTurbo) {
        AuraMeshRenderer.renderFirstPerson(pose, buffer, auraColor, age,
                Mth.clamp(ramp, 0.0f, 1.0f), strength, isSSJ, isTurbo);
    }
}

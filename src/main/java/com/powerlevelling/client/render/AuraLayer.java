package com.powerlevelling.client.render;

import java.util.Set;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.powerlevelling.character.CharacterRegistries;
import com.powerlevelling.character.CharacterSnapshot;
import com.powerlevelling.character.CosmeticPart;
import com.powerlevelling.client.ClientCharacters;
import com.powerlevelling.client.ClientVisualState;
import com.powerlevelling.config.ClientConfig;
import com.powerlevelling.network.VisualStatePayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;

/**
 * Dragon Ball style aura renderer: flowing energy wisp columns surging upwards from the feet,
 * a ground flare pulsing under the boots, expanding energy rings, and a teardrop flame-silhouette
 * shell for SSJ.
 * <p>
 * Standard Minecraft model space (+Y is DOWNWARDS):
 * - y = 0.0 : Neck / shoulders
 * - y = 6.0 : Mid-torso
 * - y = 12.0: Waist / hips
 * - y = 24.0: Feet / soles of boots on the ground
 * - y = -8.0: Top of head
 */
public class AuraLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    static final ResourceLocation AURA_PART_ID = ResourceLocation.fromNamespaceAndPath("powerlevelling", "aura");

    static final RenderType WISP_RENDER_TYPE = RenderType.entityTranslucentEmissive(ResourceLocation.fromNamespaceAndPath("powerlevelling", "textures/entity/aura/aura_wisp.png"));
    static final RenderType GROUND_RENDER_TYPE = RenderType.entityTranslucentEmissive(ResourceLocation.fromNamespaceAndPath("powerlevelling", "textures/entity/aura/aura_ground.png"));
    static final RenderType RING_RENDER_TYPE = RenderType.entityTranslucentEmissive(ResourceLocation.fromNamespaceAndPath("powerlevelling", "textures/entity/aura/aura_ring.png"));
    static final RenderType FLAME_RENDER_TYPE = RenderType.entityTranslucentEmissive(ResourceLocation.fromNamespaceAndPath("powerlevelling", "textures/entity/aura/aura_flame.png"));
    static final RenderType BOLT_RENDER_TYPE = RenderType.entityTranslucentEmissive(ResourceLocation.fromNamespaceAndPath("powerlevelling", "textures/entity/aura/aura_bolt.png"));
    private static final RenderType GLOW_RENDER_TYPE = RenderType.entityTranslucentEmissive(KiOrbModel.GLOW_TEXTURE);

    public AuraLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw,
                       float headPitch) {
        if (player.isInvisible() || player.isSpectator()) return;
        CharacterSnapshot snap = ClientCharacters.effective(player.getId());
        if (snap == null) return;
        boolean preview = snap == ClientCharacters.previewOverride;
        if (!snap.created() && !preview) return;

        boolean inStance = ClientVisualState.has(player.getId(), VisualStatePayload.STANCE);
        boolean charging = ClientVisualState.has(player.getId(), VisualStatePayload.CHARGING);
        boolean isSSJ = ClientVisualState.hasSuperSaiyan(player.getId());
        boolean transforming = ClientVisualState.has(player.getId(), VisualStatePayload.TRANSFORMING);
        boolean isTurbo = ClientVisualState.has(player.getId(), VisualStatePayload.TURBO);
        if (!inStance && !charging && !isSSJ && !transforming && !isTurbo) return;

        CosmeticPart part = CharacterRegistries.getPart(player.level().registryAccess(), AURA_PART_ID);
        if (part == null) return;
        if (!player.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
            CosmeticPart.HelmetMode mode = switch (ClientConfig.HAIR_WITH_HELMET.get()) {
                case ALWAYS_HIDE -> CosmeticPart.HelmetMode.HIDE;
                case ALWAYS_SHOW -> CosmeticPart.HelmetMode.SHOW;
                case RESPECT_PART -> part.helmet();
            };
            if (mode == CosmeticPart.HelmetMode.HIDE) return;
            if (mode == CosmeticPart.HelmetMode.PARTIAL && part.helmetVisibleBones().isEmpty()) return;
        }

        int auraColor;
        if (isSSJ) {
            auraColor = 0xFFD700;
        } else {
            auraColor = snap.kiColor();
            if (auraColor == -1) auraColor = 0xFF0000;
        }
        int color = part.tintable() ? 0xFF000000 | auraColor : 0xFFFFFFFF;

        ClientVisualState.State st = ClientVisualState.get(player.getId());
        float chargeTicks = st != null ? st.chargeTicks : 0f;
        boolean intense = charging || transforming || isTurbo;
        float ramp = intense ? Mth.clamp(chargeTicks / 40f, 0f, 1f) : 1f;

        float age = ageInTicks + partialTick;
        float configScale = ClientConfig.AURA_SCALE.get().floatValue();
        float opacity = ClientConfig.AURA_OPACITY.get().floatValue();
        float alphaScale = opacity * (isTurbo ? ClientConfig.TURBO_AURA_ALPHA_MULT.get().floatValue() : 1f);

        // Turbo amplifies scale on top of existing intensity
        float turboScaleMult = isTurbo ? ClientConfig.TURBO_AURA_SCALE_MULT.get().floatValue() : 1f;
        float baseScale = (intense ? 0.95f + ramp * 0.65f : isSSJ ? 1.25f : 1.0f);
        float scale = baseScale * turboScaleMult
                * (1f + 0.05f * Mth.sin(chargeTicks * 0.35f))
                * configScale;

        if (opacity <= 0.001f) return;

        int lodTier = 0;
        boolean lodEnabled = ClientConfig.AURA_LOD_ENABLED.get();
        var mc = Minecraft.getInstance();
        if (lodEnabled && mc.gameRenderer.getMainCamera() != null && mc.gameRenderer.getMainCamera().getEntity() != null) {
            double distSq = player.distanceToSqr(mc.gameRenderer.getMainCamera().getEntity());
            double d1 = ClientConfig.AURA_LOD_DISTANCE_1.get();
            double d2 = ClientConfig.AURA_LOD_DISTANCE_2.get();
            double d3 = ClientConfig.AURA_LOD_DISTANCE_3.get();
            if (distSq > d3 * d3) lodTier = 2;
            else if (distSq > d2 * d2) lodTier = 1;
            else if (distSq > d1 * d1) lodTier = 0;
        }

        PlayerModel<AbstractClientPlayer> model = getParentModel();
        ModelPart anchor = part.anchor() == CosmeticPart.Anchor.BODY ? model.body : model.head;

        poseStack.pushPose();
        try {
            anchor.translateAndRotate(poseStack);
            // Model space coordinates (+Y is DOWNWARDS):
            // Neck = y=0, Waist = y=12, Feet = y=24, Head = y=-8

            // The new mesh renderer owns the silhouette and internal energy strands.
            // Legacy helpers remain below temporarily for compatibility with the first-person layer.
            AuraMeshRenderer.render(poseStack, buffer, color, scale, age, ramp,
                    charging, transforming, isSSJ, isTurbo, lodTier, alphaScale);

        } finally {
            poseStack.popPose();
        }
    }

    /**
     * Vertical energy wisps: energy strands surging UPWARDS from feet (+24) to head (-12).
     */
    private static void renderWisps(PoseStack poseStack, MultiBufferSource buffer, int color, float scale,
                                    float age, float ramp, boolean intense, boolean isSSJ, boolean isTurbo, int lodTier, float alphaScale) {
        VertexConsumer vc = buffer.getBuffer(WISP_RENDER_TYPE);
        int light = 0xF000F0;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        int count;
        if (lodTier == 0) count = ClientConfig.AURA_LOD_WISPS_1.get();
        else if (lodTier == 1) count = ClientConfig.AURA_LOD_WISPS_2.get();
        else count = ClientConfig.AURA_LOD_WISPS_3.get();
        if (isSSJ) count = Math.max(count, 14);
        // Turbo adds more wisps on top
        if (isTurbo) count = Math.min(count + 8, 32); // Add up to 8 more wisps in turbo mode

        float baseRadius = (3.2f + ramp * 2.2f) * scale;
        float wispWidth = (2.2f + ramp * 1.2f) * scale;
        float wispHalfHeight = (7f + ramp * 3f) * scale;

        for (int i = 0; i < count; i++) {
            float seed = i * 137.508f;
            float dir = (i % 2 == 0) ? 1f : -1f;
            float speed = (intense ? 0.12f + ramp * 0.10f : 0.05f) * dir;
            float angle = seed + age * speed;

            // Rising motion from feet (+24) up to head (-12)
            float riseSpeed = intense ? 0.04f + ramp * 0.03f : 0.025f;
            float progress = Mth.frac(age * riseSpeed + (seed * 0.15f));
            float y = Mth.lerp(progress, 24.0f * scale, -10.0f * scale);

            // Radius breathes as it rises
            float radius = baseRadius * (0.85f + 0.3f * Mth.sin(seed * 2.7f + age * 0.4f * dir));
            double x = Mth.cos(angle) * radius;
            double z = Mth.sin(angle) * radius;

            float edgeFade = Mth.sin(progress * Mth.PI); // Fades in near feet, fades out at head
            float flicker = 0.5f + 0.5f * Mth.sin(age * (0.9f + 0.3f * Mth.sin(seed)) + seed * 3f);
            float alpha = (intense ? 0.22f + 0.26f * ramp : isSSJ ? 0.24f : 0.14f) * edgeFade * (0.35f + 0.65f * flicker) * alphaScale;

            poseStack.pushPose();
            poseStack.translate(x, y, z);
            poseStack.mulPose(Axis.YP.rotation(angle));
            float wobble = (intense ? 0.25f : 0.12f) * Mth.sin(age * 0.7f + seed);
            poseStack.mulPose(Axis.ZP.rotation(wobble));
            quad(vc, poseStack.last(), wispWidth * 0.5f, wispHalfHeight, r, g, b, alpha(alpha), light);
            poseStack.mulPose(Axis.YP.rotation(Mth.HALF_PI));
            quad(vc, poseStack.last(), wispWidth * 0.45f, wispHalfHeight * 0.9f, r, g, b, alpha(alpha * 0.8f), light);
            poseStack.popPose();
        }
    }

    /** Ground flare: glowing energy disk lying flat on the floor at the feet (+24). */
    private static void renderGroundFlare(PoseStack poseStack, MultiBufferSource buffer, int color, float scale,
                                          float age, float ramp, boolean intense, boolean isSSJ, boolean isTurbo, int lodTier, float alphaScale) {
        VertexConsumer vc = buffer.getBuffer(GROUND_RENDER_TYPE);
        int light = 0xF000F0;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        float pulse = 1f + 0.15f * Mth.sin(age * 1.4f);
        float radius = (4.5f + ramp * 4.5f) * scale * pulse;
        float alpha = (intense ? 0.16f + 0.22f * ramp : isSSJ ? 0.14f : 0.08f)
                * (0.5f + 0.5f * Mth.sin(age * 1.3f + 1f)) * alphaScale;

        poseStack.pushPose();
        // Translate DOWN to feet (+24.0)
        poseStack.translate(0.0, 24.0f * scale, 0.0);
        poseStack.mulPose(Axis.XP.rotationDegrees(90f));
        quad(vc, poseStack.last(), radius, radius, r, g, b, alpha(alpha), light);
        poseStack.popPose();
    }

    /** Expanding shockwave rings surging upward from the feet (+22) to chest (+4). */
    private static void renderRings(PoseStack poseStack, MultiBufferSource buffer, int color, float scale,
                                    float age, float ramp, boolean intense, boolean isSSJ, boolean isTurbo, int lodTier, float alphaScale) {
        VertexConsumer vc = buffer.getBuffer(RING_RENDER_TYPE);
        int light = 0xF000F0;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        int count = isSSJ ? 2 : (intense ? 2 : 0);
        for (int i = 0; i < count; i++) {
            float ringAge = age * 0.04f + i * 0.4f;
            float progress = Mth.frac(ringAge);
            float y = Mth.lerp(progress, 22.0f * scale, 4.0f * scale);
            float radius = (3.0f + progress * 5.0f) * scale;
            float alpha = (1.0f - progress) * (intense ? 0.18f + 0.20f * ramp : isSSJ ? 0.16f : 0.08f) * alphaScale;

            poseStack.pushPose();
            poseStack.translate(0.0, y, 0.0);
            poseStack.mulPose(Axis.XP.rotationDegrees(90f));
            poseStack.mulPose(Axis.ZP.rotation(ringAge * 3.0f));
            quad(vc, poseStack.last(), radius, radius, r, g, b, alpha(alpha), light);
            poseStack.popPose();
        }
    }

    /**
     * Teardrop flame-shell: Super Saiyan / Charging aura silhouette.
     * Envelops the entire character from boots (+24) to above head (-10).
     */
    private static void renderFlameShell(PoseStack poseStack, MultiBufferSource buffer, int color, float scale,
                                          float age, float ramp, boolean intense, boolean isSSJ, boolean isTurbo, int lodTier, float alphaScale) {
        if (!isSSJ && !intense) return;
        VertexConsumer vc = buffer.getBuffer(FLAME_RENDER_TYPE);
        int light = 0xF000F0;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        int segments = 16;
        float baseRadius = (4.2f + ramp * 2.5f) * scale;
        // Center shell at mid-body (y = 7.0)
        float centerY = 7.0f * scale;
        // Half height = 17.0: spans from y = 7 - 17 = -10 (above head) down to y = 7 + 17 = +24 (feet)
        float halfH = 17.0f * scale;

        float baseAlpha = isSSJ ? 0.24f : (0.07f + ramp * 0.12f);
        float globalSpin = age * (isSSJ ? 0.14f : 0.06f);

        for (int i = 0; i < segments; i++) {
            float angle = i * Mth.TWO_PI / segments + globalSpin;
            float x = Mth.cos(angle) * baseRadius;
            float z = Mth.sin(angle) * baseRadius;

            float segFlicker = 0.65f + 0.35f * Mth.sin(age * 1.2f + i * 1.7f);
            float bladeWidth = baseRadius * 0.20f;

            poseStack.pushPose();
            poseStack.translate(x, centerY, z);
            poseStack.mulPose(Axis.YP.rotation(angle));
            quad(vc, poseStack.last(), bladeWidth, halfH, r, g, b, alpha(baseAlpha * segFlicker * alphaScale), light);
            poseStack.mulPose(Axis.YP.rotation(Mth.PI));
            quad(vc, poseStack.last(), bladeWidth * 0.85f, halfH * 0.95f, r, g, b,
                 alpha(baseAlpha * segFlicker * 0.7f * alphaScale), light);
            poseStack.popPose();
        }
    }

    /** Branching lightning crackle: jagged bolts arcing around the character. */
    private static void renderLightningCrackle(PoseStack poseStack, MultiBufferSource buffer, int color, float scale,
                                                float age, float ramp, boolean intense, boolean isSSJ, boolean isTurbo, int lodTier, float alphaScale) {
        VertexConsumer vc = buffer.getBuffer(BOLT_RENDER_TYPE);
        int light = 0xF000F0;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        int boltCount = intense ? (4 + (int)(ramp * 4)) : (isSSJ ? 3 : 0);
        if (boltCount == 0) return;

        for (int i = 0; i < boltCount; i++) {
            float seed = i * 239.0f + age * 0.37f;
            float timeOffset = age * 2.3f + seed * 7.1f;
            float flickerGate = Mth.sin(timeOffset);
            if (flickerGate < 0.4f) continue;

            // Distribute origins between legs (+20) and head (-4)
            float originY = Mth.lerp(Mth.sin(seed) * 0.5f + 0.5f, 20.0f * scale, -4.0f * scale);
            float originR = (3.5f + ramp * 2.5f) * scale;
            float originAngle = seed * 5.7f;
            double ox = Mth.cos(originAngle) * originR;
            double oz = Mth.sin(originAngle) * originR;

            float dirPitch = -0.8f - 0.4f * Mth.sin(seed * 2.1f);
            float dirYaw = originAngle + 0.5f * Mth.sin(seed * 3.4f);
            float dirLen = (4f + ramp * 3.5f) * scale * (0.6f + 0.4f * Mth.sin(timeOffset * 0.5f));

            float segCount = 4 + (int)(ramp * 3);
            double px = ox, py = originY, pz = oz;

            poseStack.pushPose();
            for (int s = 0; s < segCount; s++) {
                float t = (s + 1) / (float) segCount;
                float jitter = (Mth.sin(timeOffset * 0.8f + s * 2.7f + seed * 5.0f) * 0.35f +
                               Mth.sin(timeOffset * 0.3f + s * 5.3f + seed * 8.0f) * 0.15f);
                float thisPitch = dirPitch + jitter * 0.5f;
                float thisYaw = dirYaw + jitter * 0.3f;

                float stepLen = dirLen / segCount;
                double nx = px + Mth.cos(thisPitch) * Mth.cos(thisYaw) * stepLen;
                double ny = py + Mth.sin(thisPitch) * stepLen;
                double nz = pz + Mth.cos(thisPitch) * Mth.sin(thisYaw) * stepLen;

                float segW = (1.0f + ramp * 0.5f) * scale * (1f - t * 0.5f);
                float segH = stepLen;
                float segAlpha = (intense ? 0.28f : 0.20f) * (1f - t * 0.6f) * (0.6f + 0.4f * flickerGate) * alphaScale;

                double mx = (px + nx) * 0.5;
                double my = (py + ny) * 0.5;
                double mz = (pz + nz) * 0.5;

                poseStack.pushPose();
                poseStack.translate(mx, my, mz);
                float segYaw = (float) Math.atan2(nz - pz, nx - px);
                float segPitch = (float) Math.atan2(ny - py, Math.hypot(nx - px, nz - pz));
                poseStack.mulPose(Axis.YP.rotation(segYaw + Mth.HALF_PI));
                poseStack.mulPose(Axis.ZP.rotation(-segPitch));
                quad(vc, poseStack.last(), segW * 0.5f, segH * 0.5f, r, g, b, alpha(segAlpha), light);
                poseStack.popPose();

                px = nx; py = ny; pz = nz;
            }
            poseStack.popPose();
        }
    }

    /** Soft spherical glow halo centered at mid-torso (y = +8). */
    private static void drawSoftGlow(PoseStack poseStack, MultiBufferSource buffer, float radius, int color, float age, float alphaMul, float alphaScale) {
        VertexConsumer vc = buffer.getBuffer(GLOW_RENDER_TYPE);
        final int light = LightTexture.FULL_BRIGHT;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        float pulse = 1f + 0.10f * Mth.sin(age * 0.85f);
        float base = 0.20f * alphaMul * alphaScale;
        float half = radius * pulse;

        poseStack.pushPose();
        poseStack.translate(0.0, 8.0f, 0.0);
        poseStack.mulPose(Axis.ZP.rotationDegrees(age * 1.5f));
        quad(vc, poseStack.last(), half, half, r, g, b, alpha(base * 0.8f), light);
        poseStack.popPose();

        poseStack.pushPose();
        poseStack.translate(0.0, 8.0f, 0.0);
        poseStack.mulPose(Axis.XP.rotationDegrees(90f));
        poseStack.mulPose(Axis.ZP.rotationDegrees(age * 1.2f));
        quad(vc, poseStack.last(), half, half, r, g, b, alpha(base * 0.7f), light);
        poseStack.popPose();
    }

    /** One quad centered on current pose origin, spanning (+-halfW, +-halfH) in local XY plane. */
    static void quad(VertexConsumer vc, PoseStack.Pose pose, float halfW, float halfH,
                             int r, int g, int b, int a, int light) {
        if (a <= 0) return;
        vc.addVertex(pose, -halfW, -halfH, 0.0f).setColor(r, g, b, a).setUv(0.0f, 1.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0.0f, 0.0f, 1.0f);
        vc.addVertex(pose, halfW, -halfH, 0.0f).setColor(r, g, b, a).setUv(1.0f, 1.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0.0f, 0.0f, 1.0f);
        vc.addVertex(pose, halfW, halfH, 0.0f).setColor(r, g, b, a).setUv(1.0f, 0.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0.0f, 0.0f, 1.0f);
        vc.addVertex(pose, -halfW, halfH, 0.0f).setColor(r, g, b, a).setUv(0.0f, 0.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0.0f, 0.0f, 1.0f);
    }

    static int alpha(float a) {
        return (int) (Mth.clamp(a, 0f, 1f) * 255f);
    }
}

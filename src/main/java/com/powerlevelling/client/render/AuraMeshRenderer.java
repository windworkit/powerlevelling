package com.powerlevelling.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.resources.ResourceLocation;

/**
 * A mesh-built ki aura. Unlike the previous effect stack, this draws continuous, tapered
 * flame ribbons with a stable silhouette and animated curvature. The ribbon mesh is emitted
 * directly in player-model space, so it follows the same body anchor as the player layer.
 */
public final class AuraMeshRenderer {
    private static final ResourceLocation RIBBON_TEXTURE = ResourceLocation.fromNamespaceAndPath("powerlevelling", "textures/entity/aura/energy_ribbon.png");
    // Use the same emissive translucent pipeline as the existing aura effects so the
    // ribbons retain alpha blending and render independently of the player's skin depth.
    private static final RenderType ENERGY = RenderType.entityTranslucentEmissive(RIBBON_TEXTURE);
    private static final RenderType PRESSURE = RenderType.entityTranslucentEmissive(
            ResourceLocation.fromNamespaceAndPath("powerlevelling", "textures/entity/aura/aura_ground.png"));
    private static final int LIGHT = LightTexture.FULL_BRIGHT;
    private static final float TWO_PI = (float) (Math.PI * 2.0);

    private AuraMeshRenderer() {
    }

    public static void render(PoseStack poseStack, MultiBufferSource buffers, int rgb,
                              float scale, float age, float charge, boolean charging,
                              boolean transforming, boolean superSaiyan, boolean turbo,
                              int lodTier, float opacity) {
        if (opacity <= 0.001f) return;

        // State intensity is deliberately distinct: stance is restrained, while charge,
        // transformation, SSJ and turbo each produce a clearly readable energy envelope.
        float stateStrength = superSaiyan ? 1.0f
                : transforming ? 0.92f + 0.18f * charge
                : charging ? 0.72f + 0.28f * charge
                : turbo ? 0.84f : 0.76f;
        if (turbo) stateStrength *= 1.12f;
        // AURA_OPACITY was historically tuned for tiny particle sprites. The ribbon mesh
        // needs a stronger response at the same config value to be visible in third person.
        float output = stateStrength * Mth.clamp(opacity * 2.6f, 0.0f, 1.5f);

        // These dimensions are in player-model pixels. The previous 1-3 px radius
        // hugged the skin and made the new aura almost impossible to distinguish.
        float height = superSaiyan ? 48.0f : 40.0f + 14.0f * charge;
        if (transforming) height += 6.0f * (0.5f + 0.5f * Mth.sin(age * 0.32f));
        if (turbo) height += 4.0f;
        if (!charging && !transforming && !superSaiyan && !turbo) height = 46.0f;

        // Place the ribbon centerlines outside the player model's roughly 4 px torso
        // half-width; this avoids the skin depth-buffer hiding most of the effect.
        float radius = (superSaiyan ? 11.5f : 9.6f + 2.0f * charge) * scale;
        float width = (superSaiyan ? 3.8f : 3.2f + 0.7f * charge) * scale;
        if (turbo) {
            radius *= 1.14f;
            width *= 1.12f;
        }

        VertexConsumer mesh = buffers.getBuffer(ENERGY);
        int mainCount = superSaiyan ? 9 : charging || transforming ? 8 : 8;
        int filamentCount = lodTier >= 2 ? 4 : lodTier == 1 ? 7 : 11;
        if (turbo) mainCount += 2;
        if (lodTier >= 2) {
            mainCount = Math.max(3, mainCount - 2);
            filamentCount = 3;
        }

        // Main tongues form an intentionally uneven crown rather than a spinning oval.
        for (int i = 0; i < mainCount; i++) {
            float seed = i * 2.3999632f + 0.37f;
            float angle = seed + 0.20f * Mth.sin(seed * 1.7f);
            float h = height * (0.72f + 0.25f * (0.5f + 0.5f * Mth.sin(seed * 2.1f)));
            float w = width * (0.78f + 0.34f * (0.5f + 0.5f * Mth.sin(seed * 3.7f)));
            float drift = 0.11f + 0.035f * Mth.sin(seed * 4.3f);
            float alpha = output * (superSaiyan ? 0.78f
                    : charging || transforming ? 0.72f : turbo ? 0.68f : 0.72f);
            drawFlame(mesh, poseStack.last(), rgb, angle, radius, h, w, drift,
                    age, seed, alpha * 1.15f, 1.0f, 0.70f, scale);

            // Hot inner filament. Kept narrow so the player remains readable through the aura.
            drawFlame(mesh, poseStack.last(), mix(rgb, 0xFFFFFF, superSaiyan ? 0.56f : 0.40f),
                    angle + 0.035f, radius * 0.985f, h * 0.91f, w * 0.25f,
                    drift * 0.62f, age * 1.13f, seed + 1.8f,
                    alpha * 0.82f, 0.34f, 0.82f, scale);
        }

        // Shorter, faster strands animate inside the silhouette; they are not a cloud of orbiting sprites.
        for (int i = 0; i < filamentCount; i++) {
            float seed = i * 2.3999632f + 0.91f;
            float angle = seed + 0.08f * Mth.sin(age * 0.09f + seed);
            float h = height * (0.42f + 0.25f * (0.5f + 0.5f * Mth.sin(seed * 2.7f)));
            float r = radius * (1.05f + 0.16f * Mth.sin(seed * 1.3f));
            float w = width * (0.20f + 0.05f * Mth.sin(seed * 2.0f));
            drawFlame(mesh, poseStack.last(), mix(rgb, 0xFFFFFF, 0.30f),
                    angle, r, h, w, 0.22f, age * 1.34f, seed + 3.2f,
                    output * 0.32f, 0.18f, 0.92f, scale);
        }

        // The pulse ring wraps the torso; ground debris is handled separately.
        if (charging || transforming || superSaiyan || turbo) {
            float pulse = 0.92f + 0.08f * Mth.sin(age * 0.18f);
            VertexConsumer pressure = buffers.getBuffer(PRESSURE);
            drawPressureRing(pressure, poseStack.last(), rgb, (6.2f + 1.8f * charge) * scale * pulse,
                    10.0f * scale, output * (transforming ? 0.30f : charging ? 0.26f : turbo ? 0.25f : 0.20f));
        }
    }


    /**
     * Peripheral first-person profile. It uses the same tapered ribbon language as third person,
     * but is scaled in view-space blocks and stays around the edge of the screen.
     */
    public static void renderFirstPerson(PoseStack poseStack, MultiBufferSource buffers, int rgb,
                                         float age, float charge, float strength,
                                         boolean superSaiyan, boolean turbo) {
        if (strength <= 0.001f) return;
        VertexConsumer mesh = buffers.getBuffer(ENERGY);
        int count = superSaiyan ? 8 : 6;
        if (turbo) count += 2;
        float rx = 0.78f + 0.10f * charge;
        float ry = 0.48f + 0.08f * charge;
        float zBase = -1.28f;
        for (int i = 0; i < count; i++) {
            float seed = i * 2.3999632f + 0.47f;
            float angle = seed + 0.12f * Mth.sin(age * 0.07f + seed);
            float cx = Mth.cos(angle) * rx;
            float cy = Mth.sin(angle) * ry;
            float h = 0.42f + 0.16f * (0.5f + 0.5f * Mth.sin(seed * 2.3f));
            float w = 0.075f + 0.025f * charge;
            drawViewFlame(mesh, poseStack.last(), rgb, cx, cy, zBase + 0.12f * Mth.sin(seed),
                    h, w, age, seed, strength * (superSaiyan ? 0.25f : 0.19f));
        }
        // Two quiet inner traces add motion without putting a glow panel over the crosshair.
        for (int i = 0; i < 3; i++) {
            float seed = i * 2.3999632f + 1.2f;
            float angle = seed - age * 0.035f;
            float cx = Mth.cos(angle) * rx * 0.72f;
            float cy = Mth.sin(angle) * ry * 0.72f;
            drawViewFlame(mesh, poseStack.last(), mix(rgb, 0xFFFFFF, 0.35f),
                    cx, cy, zBase - 0.08f, 0.34f, 0.027f, age * 1.25f,
                    seed + 4.0f, strength * 0.14f);
        }
    }

    private static void drawViewFlame(VertexConsumer vc, PoseStack.Pose pose, int rgb,
                                      float cx, float cy, float z, float height, float width,
                                      float age, float seed, float alpha) {
        final int segments = 12;
        float oldT = 0.0f;
        for (int i = 1; i <= segments; i++) {
            float t0 = oldT;
            float t1 = i / (float) segments;
            float y0 = cy - height * 0.5f + height * t0;
            float y1 = cy - height * 0.5f + height * t1;
            float wobble0 = Mth.sin(age * 0.10f + seed + t0 * 5.5f) * 0.045f;
            float wobble1 = Mth.sin(age * 0.10f + seed + t1 * 5.5f) * 0.045f;
            float w0 = width * Mth.sin(Mth.PI * t0) * (0.82f + 0.18f * Mth.sin(age * 0.16f + seed));
            float w1 = width * Mth.sin(Mth.PI * t1) * (0.82f + 0.18f * Mth.sin(age * 0.16f + seed));
            float x0 = cx + wobble0;
            float x1 = cx + wobble1;
            float fade = Mth.sin(Mth.PI * ((t0 + t1) * 0.5f));
            int a = Mth.clamp((int) (alpha * fade * 255.0f), 0, 255);
            int c = shade(rgb, 0.88f);
            int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
            vertex(vc, pose, x0 - w0, y0, z, 0f, t0, r, g, b, a, 0f, 0f, 1f);
            vertex(vc, pose, x0 + w0, y0, z, 1f, t0, r, g, b, a, 0f, 0f, 1f);
            vertex(vc, pose, x1 + w1, y1, z, 1f, t1, r, g, b, a, 0f, 0f, 1f);
            vertex(vc, pose, x1 - w1, y1, z, 0f, t1, r, g, b, a, 0f, 0f, 1f);
            // Back face, so the peripheral strands survive view-space rotations.
            vertex(vc, pose, x1 - w1, y1, z, 0f, t1, r, g, b, a, 0f, 0f, -1f);
            vertex(vc, pose, x1 + w1, y1, z, 1f, t1, r, g, b, a, 0f, 0f, -1f);
            vertex(vc, pose, x0 + w0, y0, z, 1f, t0, r, g, b, a, 0f, 0f, -1f);
            vertex(vc, pose, x0 - w0, y0, z, 0f, t0, r, g, b, a, 0f, 0f, -1f);
            oldT = t1;
        }
    }

    private static void drawFlame(VertexConsumer vc, PoseStack.Pose pose, int rgb,
                                  float baseAngle, float baseRadius, float height, float maxWidth,
                                  float drift, float age, float seed, float alpha,
                                  float radialBias, float brightness, float scale) {
        final int segments = 15;
        float previousT = 0.0f;
        FlamePoint previous = point(baseAngle, baseRadius, height, maxWidth, drift, age, seed, radialBias, 0.001f, scale);
        for (int i = 1; i <= segments; i++) {
            float t = i / (float) segments;
            FlamePoint next = point(baseAngle, baseRadius, height, maxWidth, drift, age, seed, radialBias, t, scale);
            float mid = (previousT + t) * 0.5f;
            float envelope = Mth.sin(Mth.PI * mid);
            float flicker = 0.83f + 0.17f * Mth.sin(age * 0.18f + seed * 3.7f + mid * 8.0f);
            int a = alpha * envelope * flicker > 0.001f
                    ? Mth.clamp((int) (alpha * envelope * flicker * 255.0f), 0, 255) : 0;
            int color = shade(rgb, brightness + 0.08f * (1.0f - mid));
            emitRibbon(vc, pose, previous, next, color, a, previousT, t);
            previous = next;
            previousT = t;
        }
    }

    private static FlamePoint point(float baseAngle, float baseRadius, float height, float maxWidth,
                                    float drift, float age, float seed, float radialBias, float t, float scale) {
        float envelope = Mth.sin(Mth.PI * t);
        float wave = Mth.sin(age * 0.105f + seed + t * 7.4f) * drift * envelope;
        float curl = Mth.sin(seed * 2.0f + t * 5.0f) * 0.075f * envelope;
        float angle = baseAngle + wave + curl;
        float radius = baseRadius * (0.78f + 0.20f * envelope + 0.06f * Mth.sin(age * 0.12f + seed + t * 4.0f));
        radius += radialBias * envelope * scale;
        float y = (22.5f - (height + 7.5f) * t) * scale;
        float taper = (float) Math.pow(Math.max(0.0f, envelope), 0.72);
        float flutter = 0.86f + 0.14f * Mth.sin(age * 0.16f + seed * 2.4f + t * 10.0f);
        float halfWidth = maxWidth * taper * flutter;
        return new FlamePoint(Mth.cos(angle) * radius, y, Mth.sin(angle) * radius,
                -Mth.sin(angle) * halfWidth, Mth.cos(angle) * halfWidth,
                Mth.cos(angle), 0.0f, Mth.sin(angle));
    }

    private static void emitRibbon(VertexConsumer vc, PoseStack.Pose pose, FlamePoint a, FlamePoint b,
                                   int rgb, int alpha, float v0, float v1) {
        float ar = (rgb >> 16) & 255;
        float ag = (rgb >> 8) & 255;
        float ab = rgb & 255;
        int r = (int) ar, g = (int) ag, bl = (int) ab;
        vertex(vc, pose, a.x - a.wx, a.y, a.z - a.wz, 0.0f, v0, r, g, bl, alpha, a.nx, a.ny, a.nz);
        vertex(vc, pose, a.x + a.wx, a.y, a.z + a.wz, 1.0f, v0, r, g, bl, alpha, a.nx, a.ny, a.nz);
        vertex(vc, pose, b.x + b.wx, b.y, b.z + b.wz, 1.0f, v1, r, g, bl, alpha, b.nx, b.ny, b.nz);
        vertex(vc, pose, b.x - b.wx, b.y, b.z - b.wz, 0.0f, v1, r, g, bl, alpha, b.nx, b.ny, b.nz);

        // Reverse winding too: the energy ribbon is visible from every camera angle.
        vertex(vc, pose, b.x - b.wx, b.y, b.z - b.wz, 0.0f, v1, r, g, bl, alpha, -b.nx, -b.ny, -b.nz);
        vertex(vc, pose, b.x + b.wx, b.y, b.z + b.wz, 1.0f, v1, r, g, bl, alpha, -b.nx, -b.ny, -b.nz);
        vertex(vc, pose, a.x + a.wx, a.y, a.z + a.wz, 1.0f, v0, r, g, bl, alpha, -a.nx, -a.ny, -a.nz);
        vertex(vc, pose, a.x - a.wx, a.y, a.z - a.wz, 0.0f, v0, r, g, bl, alpha, -a.nx, -a.ny, -a.nz);
    }

    private static void drawPressureRing(VertexConsumer vc, PoseStack.Pose pose, int rgb,
                                         float radius, float y, float alpha) {
        int a = Mth.clamp((int) (alpha * 255.0f), 0, 255);
        int color = mix(rgb, 0xFFFFFF, 0.30f);
        // The pressure ring uses the radial glow texture, separate from the shaped flame ribbon atlas.
        int r = (color >> 16) & 255, g = (color >> 8) & 255, b = color & 255;
        int segments = 36;
        float inner = radius * 0.72f;
        float outer = radius;
        for (int i = 0; i < segments; i++) {
            float a0 = TWO_PI * i / segments;
            float a1 = TWO_PI * (i + 1) / segments;
            float x0 = Mth.cos(a0), z0 = Mth.sin(a0);
            float x1 = Mth.cos(a1), z1 = Mth.sin(a1);
            vertex(vc, pose, x0 * inner, y, z0 * inner, 0.5f, 0.5f, r, g, b, a, 0f, -1f, 0f);
            vertex(vc, pose, x0 * outer, y, z0 * outer, 0.5f, 0.5f, r, g, b, a, 0f, -1f, 0f);
            vertex(vc, pose, x1 * outer, y, z1 * outer, 0.5f, 0.5f, r, g, b, a, 0f, -1f, 0f);
            vertex(vc, pose, x1 * inner, y, z1 * inner, 0.5f, 0.5f, r, g, b, a, 0f, -1f, 0f);
        }
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z,
                               float u, float v, int r, int g, int b, int a, float nx, float ny, float nz) {
        vc.addVertex(pose, x, y, z)
                .setColor(r, g, b, a)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LIGHT)
                .setNormal(pose, nx, ny, nz);
    }

    private static int shade(int rgb, float multiplier) {
        return ((int) (((rgb >> 16) & 255) * multiplier) << 16)
                | ((int) (((rgb >> 8) & 255) * multiplier) << 8)
                | ((int) ((rgb & 255) * multiplier));
    }

    private static int mix(int a, int b, float t) {
        int r = (int) Mth.lerp(t, (a >> 16) & 255, (b >> 16) & 255);
        int g = (int) Mth.lerp(t, (a >> 8) & 255, (b >> 8) & 255);
        int bl = (int) Mth.lerp(t, a & 255, b & 255);
        return (r << 16) | (g << 8) | bl;
    }

    private record FlamePoint(float x, float y, float z, float wx, float wz,
                              float nx, float ny, float nz) {
    }
}

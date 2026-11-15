package com.powerlevelling.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.powerlevelling.PowerLevelling;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * Shared spherical ki-orb model for hand charging and travelling blasts. The actual geometry is emitted by
 * KiOrbMeshRenderer: a translucent plasma shell, hot core, orbital bands, a wrapping filament, and an optional tail.
 * Disc attacks retain their own flat-disc geometry.
 */
public final class KiOrbModel {
    /** Subtle pixel texture used across the spherical plasma shell. */
    public static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(PowerLevelling.MODID, "textures/entity/ki_blast.png");

    /** Soft radial glow texture sampled by the inner core and energy ribbons (64x64). */
    public static final ResourceLocation GLOW_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(PowerLevelling.MODID, "textures/entity/ki_glow.png");

    /** Wrapped UV plasma surface for the new spherical orb mesh. */
    public static final ResourceLocation PLASMA_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(PowerLevelling.MODID, "textures/entity/ki_plasma_shell.png");

    /** Per-face brightness for the remaining faceted disc geometry. */
    private static final float[] FACE_SHADE = {0.85f, 0.70f, 1.00f, 0.55f, 0.90f, 0.75f};

    /**
     * Cube faces: outward normal n, then tangents u and v with u x v = n. Corners (-u-v, +u-v, +u+v, -u+v) are
     * therefore counter-clockwise seen from outside.
     */
    private static final float[][] FACES = {
            {1, 0, 0, /**/ 0, 1, 0, /**/ 0, 0, 1},   // +X
            {-1, 0, 0, /**/ 0, 0, 1, /**/ 0, 1, 0},  // -X
            {0, 1, 0, /**/ 0, 0, 1, /**/ 1, 0, 0},   // +Y
            {0, -1, 0, /**/ 1, 0, 0, /**/ 0, 0, 1},  // -Y
            {0, 0, 1, /**/ 1, 0, 0, /**/ 0, 1, 0},   // +Z
            {0, 0, -1, /**/ 0, 1, 0, /**/ 1, 0, 0},  // -Z
    };
    private static final int[][] CORNERS = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};

    private KiOrbModel() {
    }

    /**
     * Draws the shared hand-orb / travelling-blast model. Geometry is emitted by KiOrbMeshRenderer.
     */
    public static void render(PoseStack poseStack, MultiBufferSource buffer, float radius, int color, int coreColor,
                              float ageTicks, float partialTick, float alphaMul, boolean withTail, Vec3 tailDir) {
        KiOrbMeshRenderer.render(poseStack, buffer, radius, color, coreColor,
                ageTicks + partialTick, alphaMul, withTail, tailDir);
    }

    /**
     * The ki disc: a flat octagonal saw (two squares, one turned 45 degrees) with a brighter core slab and 8 alternating
     * teeth on the rim, spinning about Y, plus two fading ghost copies trailing behind the spin (afterimage). All additive.
     * {@code radius} = disc radius, {@code thickness} = full height, {@code spinMul} scales the spin speed.
     */
    public static void renderDisc(PoseStack poseStack, MultiBufferSource buffer, float radius, float thickness, int color,
                                  int coreColor, float ageTicks, float partialTick, float alphaMul, float spinMul) {
        if (radius <= 0f || alphaMul <= 0f) return;
        final float age = ageTicks + partialTick;
        final int light = LightTexture.FULL_BRIGHT;
        VertexConsumer vc = buffer.getBuffer(effectType());
        final float halfH = thickness * 0.5f;
        for (int g = 0; g < 3; g++) {
            float a = alphaMul * (g == 0 ? 1f : g == 1 ? 0.32f : 0.14f);
            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(age * 38f * spinMul - g * 26f));
            box(vc, poseStack.last(), radius * 0.78f, halfH, radius * 0.78f, color, alpha(a * 0.55f), light, true);
            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(45f));
            box(vc, poseStack.last(), radius * 0.78f, halfH * 0.9f, radius * 0.78f, color, alpha(a * 0.45f), light, true);
            poseStack.popPose();
            box(vc, poseStack.last(), radius * 0.46f, halfH * 0.6f, radius * 0.46f, coreColor, alpha(a * 0.9f), light, true);
            for (int t = 0; t < 8; t++) {
                float ang = t * Mth.PI / 4f;
                float rr = radius * (t % 2 == 0 ? 0.98f : 0.82f);
                poseStack.pushPose();
                poseStack.translate(Mth.cos(ang) * rr, 0.0, Mth.sin(ang) * rr);
                poseStack.mulPose(Axis.YP.rotation(-ang + 0.5f));
                box(vc, poseStack.last(), radius * 0.17f, halfH * 0.85f, radius * 0.07f, mix(color, 0xFFFFFF, 0.35f),
                        alpha(a * 0.85f), light, true);
                poseStack.popPose();
            }
            poseStack.popPose();
        }
    }

    // ---- helpers ---------------------------------------------------------------------------------------------------

    /** Rotates about Y, X, Z by age * the given degrees-per-tick (age already includes the partial tick). */
    private static void spin(PoseStack poseStack, float age, float degY, float degX, float degZ) {
        poseStack.mulPose(Axis.YP.rotationDegrees(age * degY));
        poseStack.mulPose(Axis.XP.rotationDegrees(age * degX));
        poseStack.mulPose(Axis.ZP.rotationDegrees(age * degZ));
    }

    private static int alpha(float a) {
        return Mth.clamp((int) (a * 255f + 0.5f), 0, 255);
    }

    /** Linear mix of two packed RGB colours (t = 0 -> a, t = 1 -> b). */
    public static int mix(int a, int b, float t) {
        int r = (int) Mth.lerp(t, (a >> 16) & 0xFF, (b >> 16) & 0xFF);
        int g = (int) Mth.lerp(t, (a >> 8) & 0xFF, (b >> 8) & 0xFF);
        int bl = (int) Mth.lerp(t, a & 0xFF, b & 0xFF);
        return (r << 16) | (g << 8) | bl;
    }

    /**
     * Emits one axis-aligned cube centred on the current pose origin: 6 faces x 4 vertices, outward normals,
     * counter-clockwise winding. {@code half} is half the edge length. {@code shaded} multiplies each face's colour by
     * FACE_SHADE (for the unlit additive layers); the solid core passes false and lets the entity shader light it.
     */
    private static void cube(VertexConsumer vc, PoseStack.Pose pose, float half, int rgb, int alpha, int light,
                             boolean shaded) {
        box(vc, pose, half, half, half, rgb, alpha, light, shaded);
    }

    /** Like {@link #cube} with a separate half extent per axis (a box). */
    private static void box(VertexConsumer vc, PoseStack.Pose pose, float hx, float hy, float hz, int rgb, int alpha,
                            int light, boolean shaded) {
        int baseR = (rgb >> 16) & 0xFF;
        int baseG = (rgb >> 8) & 0xFF;
        int baseB = rgb & 0xFF;
        for (int f = 0; f < 6; f++) {
            float[] face = FACES[f];
            float shade = shaded ? FACE_SHADE[f] : 1f;
            int r = (int) (baseR * shade);
            int g = (int) (baseG * shade);
            int b = (int) (baseB * shade);
            for (int[] c : CORNERS) {
                float x = hx * (face[0] + c[0] * face[3] + c[1] * face[6]);
                float y = hy * (face[1] + c[0] * face[4] + c[1] * face[7]);
                float z = hz * (face[2] + c[0] * face[5] + c[1] * face[8]);
                vc.addVertex(pose, x, y, z)
                        .setColor(r, g, b, alpha)
                        .setUv((c[0] + 1) * 0.5f, (c[1] + 1) * 0.5f)
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(light)
                        .setNormal(pose, face[0], face[1], face[2]);
            }
        }
    }

    // ---- world-space effect pieces (KiWorldFx): additive, full-bright, built from the same cube helper ------------------

    /** The render type all world-space pieces below use (call endBatch on it after drawing from a level-stage event). */
    public static RenderType effectType() {
        return RenderType.eyes(TEXTURE);
    }

    /**
     * Render type for the soft radial glow halos using the camera-independent glow texture. */
    public static RenderType glowType() {
        return RenderType.eyes(GLOW_TEXTURE);
    }

    /**
     * Renders camera-independent radial glow halos: 3 orthogonal quads (XY, XZ, YZ) using the soft glow texture.
     * Creates a Dragon Ball style soft spherical glow that works from any camera angle without billboarding.
     * @param radius   base radius of the glow halos (fraction of diameter)
     * @param color    glow colour (packed RGB)
     * @param age      total age in ticks (drives pulse and rotation)
     * @param alphaMul 0..1 multiplier on alpha (fade in/out)
     */
    public static void renderGlowHalos(PoseStack poseStack, MultiBufferSource buffer, float radius, int color,
                                       float age, float alphaMul) {
        if (radius <= 0f || alphaMul <= 0f) return;
        VertexConsumer vc = buffer.getBuffer(glowType());
        final int light = LightTexture.FULL_BRIGHT;
        final float pulse = 1f + 0.12f * Mth.sin(age * 0.85f);
        final float baseAlpha = 0.35f * alphaMul;
        final float half = radius * pulse;
        
        // Three orthogonal quads: XY, XZ, YZ planes
        // Each quad is a square billboard but since we use 3 orientations, it looks spherical from any angle
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        
        // XY plane (facing +Z / -Z)
        for (int side = -1; side <= 1; side += 2) {
            poseStack.pushPose();
            poseStack.mulPose(Axis.ZP.rotationDegrees(side * age * 1.5f));
            quad(vc, poseStack.last(), half, r, g, b, alpha(baseAlpha * (side == 1 ? 1f : 0.6f)), light);
            poseStack.popPose();
        }
        // XZ plane (facing +Y / -Y)
        for (int side = -1; side <= 1; side += 2) {
            poseStack.pushPose();
            poseStack.mulPose(Axis.XP.rotationDegrees(90f * side));
            poseStack.mulPose(Axis.ZP.rotationDegrees(age * 1.2f));
            quad(vc, poseStack.last(), half, r, g, b, alpha(baseAlpha * (side == 1 ? 0.8f : 0.5f)), light);
            poseStack.popPose();
        }
        // YZ plane (facing +X / -X)
        for (int side = -1; side <= 1; side += 2) {
            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(90f * side));
            poseStack.mulPose(Axis.ZP.rotationDegrees(age * 1.8f));
            quad(vc, poseStack.last(), half, r, g, b, alpha(baseAlpha * (side == 1 ? 0.7f : 0.4f)), light);
            poseStack.popPose();
        }
    }

    /**
     * Renders energy spikes/crackle emanating from the core: short radial beams that pulse and flicker.
     * Gives the Dragon Ball "unstable energy" look.
     * @param spikeCount number of spikes
     * @param radius     base radius where spikes start
     * @param length     spike length (fraction of diameter)
     * @param color      spike colour (packed RGB)
     * @param age        total age in ticks
     * @param alphaMul   0..1 alpha multiplier
     */
    public static void renderEnergySpikes(PoseStack poseStack, MultiBufferSource buffer, int spikeCount, float radius,
                                          float length, int color, float age, float alphaMul) {
        if (spikeCount <= 0 || radius <= 0f || length <= 0f || alphaMul <= 0f) return;
        VertexConsumer vc = buffer.getBuffer(effectType());
        final int light = LightTexture.FULL_BRIGHT;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        
        for (int i = 0; i < spikeCount; i++) {
            float seed = i * 137.508f; // golden angle for nice distribution
            float flicker = 0.5f + 0.5f * Mth.sin(age * (1.7f + i * 0.11f) + seed);
            float spikeLen = length * (0.4f + 0.6f * flicker);
            float spikeAlpha = alphaMul * (0.3f + 0.7f * flicker);
            if (spikeAlpha <= 0.05f) continue;
            
            poseStack.pushPose();
            // Random but stable orientation per spike
            poseStack.mulPose(Axis.YP.rotationDegrees(seed * 2.3f + age * 0.5f));
            poseStack.mulPose(Axis.XP.rotationDegrees(seed * 1.7f - age * 0.3f));
            // Draw as a thin elongated box along +Z
            box(vc, poseStack.last(), radius * 0.03f, radius * 0.03f, spikeLen * 0.5f,
                color, alpha(spikeAlpha), light, true);
            poseStack.popPose();
        }
    }

    /**
     * Renders a smoother comet tail: overlapping soft quads instead of discrete cubes.
     * Creates a Dragon Ball style energy trail.
     */
    public static void renderSmoothTail(PoseStack poseStack, MultiBufferSource buffer, float radius, int color,
                                        int coreColor, Vec3 tailDir, float age, float alphaMul) {
        if (tailDir == null || tailDir.lengthSqr() < 1.0E-6 || alphaMul <= 0f) return;
        Vec3 back = tailDir.normalize();
        VertexConsumer vc = buffer.getBuffer(effectType());
        final int light = LightTexture.FULL_BRIGHT;
        
        // Number of tail segments
        final int segments = 8;
        for (int i = 0; i < segments; i++) {
            float t = (float) i / segments;
            float dist = radius * (1.5f + 3.5f * t); // distance behind centre
            float segRadius = radius * (1f - t) * 0.6f;
            float segAlpha = alphaMul * (1f - t) * (1f - t) * 0.6f;
            if (segAlpha <= 0.02f) continue;
            
            // Add some wobble
            double wobble = radius * 0.15 * (i + 1);
            double jx = Mth.sin(age * 0.6f + i * 1.3f) * wobble;
            double jy = Mth.cos(age * 0.5f + i * 1.9f) * wobble;
            double jz = Mth.sin(age * 0.4f + i * 2.7f) * wobble;
            
            poseStack.pushPose();
            poseStack.translate(back.x * dist + jx, back.y * dist + jy, back.z * dist + jz);
            // Orient to face camera roughly (billboard-ish but aligned to tail)
            poseStack.mulPose(Axis.YP.rotationDegrees(age * 3f + i * 7f));
            poseStack.mulPose(Axis.XP.rotationDegrees(age * 2f + i * 5f));
            
            // Mix color toward white for tail
            int tailColor = mix(color, 0xFFFFFF, 0.3f + 0.2f * t);
            int r = (tailColor >> 16) & 0xFF;
            int g = (tailColor >> 8) & 0xFF;
            int b = tailColor & 0xFF;
            
            // Soft quad instead of cube
            quad(vc, poseStack.last(), segRadius, r, g, b, alpha(segAlpha), light);
            poseStack.popPose();
        }
    }

    /**
     * Emits a single axis-aligned quad centred on the current pose origin, facing +Z.
     * Uses the glow texture UVs for soft edges. Counter-clockwise winding.
     */
    private static void quad(VertexConsumer vc, PoseStack.Pose pose, float half, int r, int g, int b,
                             int alpha, int light) {
        // Four corners: (-half,-half), (+half,-half), (+half,+half), (-half,+half)
        vc.addVertex(pose, -half, -half, 0f)
                .setColor(r, g, b, alpha)
                .setUv(0f, 0f)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, 0f, 0f, 1f);
        vc.addVertex(pose, half, -half, 0f)
                .setColor(r, g, b, alpha)
                .setUv(1f, 0f)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, 0f, 0f, 1f);
        vc.addVertex(pose, half, half, 0f)
                .setColor(r, g, b, alpha)
                .setUv(1f, 1f)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, 0f, 0f, 1f);
        vc.addVertex(pose, -half, half, 0f)
                .setColor(r, g, b, alpha)
                .setUv(0f, 1f)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, 0f, 0f, 1f);
    }

    /**
     * A flat ring of {@code segments} small cubes of edge {@code cubeEdge} on a circle of {@code radius} in the XZ plane
     * (centred on the current pose origin), slowly rotating by {@code spinDeg}. All additive.
     */
    public static void renderRing(PoseStack poseStack, MultiBufferSource buffer, float radius, float cubeEdge, int color,
                                  float alpha, int segments, float spinDeg) {
        if (radius <= 0f || alpha <= 0f || segments < 3) return;
        VertexConsumer vc = buffer.getBuffer(effectType());
        int a = alpha(alpha);
        float half = cubeEdge * 0.5f;
        for (int i = 0; i < segments; i++) {
            float ang = Mth.TWO_PI * i / segments + spinDeg * Mth.DEG_TO_RAD;
            poseStack.pushPose();
            poseStack.translate(Mth.cos(ang) * radius, 0.0, Mth.sin(ang) * radius);
            poseStack.mulPose(Axis.YP.rotation(-ang)); // cube edges follow the ring
            cube(vc, poseStack.last(), half, color, a, LightTexture.FULL_BRIGHT, true);
            poseStack.popPose();
        }
    }

    /**
     * A dome flash: stacked cube rings that shrink and rise (elevations 0, 30, 60 degrees), faint. {@code radius} = the
     * dome's base radius; the origin is the dome's centre on the ground.
     */
    public static void renderDome(PoseStack poseStack, MultiBufferSource buffer, float radius, float cubeEdge, int color,
                                  float alpha) {
        if (radius <= 0f || alpha <= 0f) return;
        float[] elev = {0f, 30f * Mth.DEG_TO_RAD, 60f * Mth.DEG_TO_RAD};
        for (int i = 0; i < elev.length; i++) {
            float r = radius * Mth.cos(elev[i]);
            float y = radius * Mth.sin(elev[i]);
            int segs = Math.max(6, (int) (r * 5f));
            poseStack.pushPose();
            poseStack.translate(0.0, y, 0.0);
            renderRing(poseStack, buffer, r, cubeEdge * (1f - 0.2f * i), color, alpha * (1f - 0.25f * i), segs, i * 15f);
            poseStack.popPose();
        }
    }

    /**
     * A straight glowing line from {@code from} to {@code to} (both relative to the current pose origin): a thin white core
     * box inside a wider coloured box, both additive. {@code thickness} = edge of the coloured box.
     */
    public static void renderLine(PoseStack poseStack, MultiBufferSource buffer, Vec3 from, Vec3 to, float thickness,
                                  int color, int coreColor, float alpha) {
        Vec3 d = to.subtract(from);
        double len = d.length();
        if (len < 1.0E-3 || alpha <= 0f) return;
        VertexConsumer vc = buffer.getBuffer(effectType());
        poseStack.pushPose();
        poseStack.translate((from.x + to.x) * 0.5, (from.y + to.y) * 0.5, (from.z + to.z) * 0.5);
        // Rotate local +Z onto the line direction.
        poseStack.mulPose(new Quaternionf().rotationTo(0f, 0f, 1f, (float) (d.x / len), (float) (d.y / len), (float) (d.z / len)));
        float hl = (float) (len * 0.5);
        box(vc, poseStack.last(), thickness * 0.5f, thickness * 0.5f, hl, color, alpha(alpha * 0.55f), LightTexture.FULL_BRIGHT, true);
        box(vc, poseStack.last(), thickness * 0.2f, thickness * 0.2f, hl, coreColor, alpha(alpha), LightTexture.FULL_BRIGHT, true);
        poseStack.popPose();
    }

    /** One additive, full-bright box of half extents (hx, hy, hz) centred on the current pose origin (BeamRenderer, orb crackle). */
    public static void renderBox(PoseStack poseStack, MultiBufferSource buffer, float hx, float hy, float hz, int color,
                                 float alpha) {
        if (alpha <= 0f) return;
        box(buffer.getBuffer(effectType()), poseStack.last(), hx, hy, hz, color, alpha(alpha), LightTexture.FULL_BRIGHT, true);
    }
}

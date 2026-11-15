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
 * The reusable 3D ki orb: a real blocky model built from cubes emitted by hand (no billboards, no model files). Used by the
 * flying blast ({@link KiBlastRenderer}, with tail) and by the "ki forms in your hand" orb (third person layer and first
 * person hand, without tail), so a forming orb and the blast it becomes look identical.
 * <ul>
 *   <li>core: a solid, full-bright cube in the core colour (plus a faint additive overlay so it glows),</li>
 *   <li>two translucent shell cubes in the ki colour that tumble on different axes at different speeds,</li>
 *   <li>one very faint, big outer glow cube,</li>
 *   <li>camera-independent radial glow halos (3 orthogonal quads) for a Dragon Ball style soft spherical glow,</li>
 *   <li>energy spikes/crackle emanating from the core,</li>
 *   <li>optionally a tail of 4 shrinking, fading cubes trailing along {@code tailDir}.</li>
 * </ul>
 * All sizes are fractions of the diameter (2 * radius). Everything except the solid core uses RenderType.eyes: additive,
 * full-bright, no depth writes, so the layers never hide each other and need no sorting. Back faces of eyes() are culled,
 * so every cube is wound counter-clockwise seen from outside.
 */
public final class KiOrbModel {
    /** White 16x16 with a lighter 1px border and a faint pixel pattern, so cube faces read as blocky, not flat. */
    public static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(PowerLevelling.MODID, "textures/entity/ki_blast.png");

    /** Soft radial glow texture for camera-independent halos (64x64). */
    public static final ResourceLocation GLOW_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(PowerLevelling.MODID, "textures/entity/ki_glow.png");

    // ---- sizes, as fractions of the diameter (edge lengths) ----
    private static final float CORE_EDGE = 0.45f;
    private static final float SHELL_INNER_EDGE = 0.75f;
    private static final float SHELL_OUTER_EDGE = 1.0f;
    private static final float GLOW_EDGE = 1.5f;

    // ---- alphas (0..1) ----
    private static final float CORE_GLOW_ALPHA = 0.20f;
    private static final float SHELL_INNER_ALPHA = 0.55f;
    private static final float SHELL_OUTER_ALPHA = 0.30f;
    private static final float GLOW_ALPHA = 0.12f;

    /** Pulse: +-6% at 3 Hz. 3 cycles/s * 2pi / 20 ticks/s = 0.942 rad per tick. */
    private static final float PULSE_AMOUNT = 0.06f;
    private static final float PULSE_RAD_PER_TICK = 0.942f;

    // ---- tail: edge (fraction of diameter), alpha, distance behind the centre (fraction of diameter) ----
    private static final float[] TAIL_EDGE = {0.38f, 0.30f, 0.23f, 0.16f};
    private static final float[] TAIL_ALPHA = {0.50f, 0.36f, 0.24f, 0.12f};
    private static final float[] TAIL_DIST = {0.55f, 1.05f, 1.55f, 2.05f};

    /** Per-face brightness (+X, -X, +Y, -Y, +Z, -Z) for the unlit additive cubes, so they read as cubes when they spin. */
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
     * Draws the orb centred on the current pose origin (the caller translates to where the orb is).
     *
     * @param radius      orb radius in blocks of the current pose's units (shells = radius, outer glow = 1.5 * radius)
     * @param color       shell / glow / tail colour (packed RGB)
     * @param coreColor   core colour (packed RGB)
     * @param ageTicks    whole ticks the thing has existed (entity.tickCount): drives tumbling and pulsing
     * @param partialTick fraction of the current tick, added to ageTicks
     * @param alphaMul    0..1 multiplier on every alpha (fade in / out)
     * @param withTail    draw the 4-cube tail
     * @param tailDir     direction the tail trails towards (i.e. opposite to the travel direction), in the current pose's
     *                    axes; ignored when withTail is false or the vector is ~zero
     */
    public static void render(PoseStack poseStack, MultiBufferSource buffer, float radius, int color, int coreColor,
                              float ageTicks, float partialTick, float alphaMul, boolean withTail, Vec3 tailDir) {
        if (radius <= 0f || alphaMul <= 0f) return;
        final float diameter = radius * 2f;
        final float age = ageTicks + partialTick;
        final float pulse = 1f + PULSE_AMOUNT * Mth.sin(age * PULSE_RAD_PER_TICK);
        final int light = LightTexture.FULL_BRIGHT;

        // 1) Core, solid and full-bright (opaque, so it writes depth and hides whatever is behind it).
        poseStack.pushPose();
        spin(poseStack, age, 4f, 7f, 3f);
        float coreHalf = diameter * CORE_EDGE * 0.5f * pulse;
        cube(buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), poseStack.last(), coreHalf, coreColor,
                alpha(1f * alphaMul), light, false);
        poseStack.popPose();

        // 2) Everything else is additive (order independent). One RenderType, so the buffer is only switched once.
        VertexConsumer glow = buffer.getBuffer(RenderType.eyes(TEXTURE));

        // Faint additive overlay on the core: lifts the darker faces so the core looks self-lit.
        poseStack.pushPose();
        spin(poseStack, age, 4f, 7f, 3f);
        cube(glow, poseStack.last(), coreHalf * 1.04f, coreColor, alpha(CORE_GLOW_ALPHA * alphaMul), light, true);
        poseStack.popPose();

        // Inner shell, then outer shell (inner to outer), tumbling on different axes at different speeds.
        poseStack.pushPose();
        spin(poseStack, age, -9f, 5f, 2f);
        cube(glow, poseStack.last(), diameter * SHELL_INNER_EDGE * 0.5f * pulse, color, alpha(SHELL_INNER_ALPHA * alphaMul), light, true);
        poseStack.popPose();

        poseStack.pushPose();
        spin(poseStack, age, 3f, -4f, 6f);
        cube(glow, poseStack.last(), diameter * SHELL_OUTER_EDGE * 0.5f * pulse, color, alpha(SHELL_OUTER_ALPHA * alphaMul), light, true);
        poseStack.popPose();

        // Outer glow: one big, barely-there cube, turning slowly.
        poseStack.pushPose();
        spin(poseStack, age, 1.5f, -2f, 1f);
        cube(glow, poseStack.last(), diameter * GLOW_EDGE * 0.5f * pulse, color, alpha(GLOW_ALPHA * alphaMul), light, true);
        poseStack.popPose();

        // 3) DRAGON BALL STYLE: Camera-independent radial glow halos (soft spherical glow)
        // Using 3 orthogonal quads with the radial glow texture for a spherical look from any angle
        renderGlowHalos(poseStack, buffer, diameter * 0.85f, color, age, alphaMul * 0.9f);

        // 4) DRAGON BALL STYLE: Energy spikes/crackle emanating from the core
        // Number of spikes scales with radius for bigger blasts
        int spikeCount = 6 + (int) (radius * 6f);
        renderEnergySpikes(poseStack, buffer, spikeCount, diameter * 0.55f, diameter * 0.7f, color, age, alphaMul * 0.7f);

        // 5) Tail: smoother comet tail with soft quads instead of discrete cubes (Dragon Ball style energy trail)
        if (withTail && tailDir != null && tailDir.lengthSqr() > 1.0E-6) {
            renderSmoothTail(poseStack, buffer, radius, color, coreColor, tailDir, age, alphaMul);
        }
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

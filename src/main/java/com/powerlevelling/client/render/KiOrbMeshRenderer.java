package com.powerlevelling.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Continuous ki-orb mesh: a translucent spherical shell, a compact hot core, two animated
 * orbital bands, and a single wrapping filament. This replaces the old stack of tumbling cubes.
 */
public final class KiOrbMeshRenderer {
    private static final int LIGHT = LightTexture.FULL_BRIGHT;
    private static final float TWO_PI = (float) (Math.PI * 2.0);

    private KiOrbMeshRenderer() {
    }

    public static void render(PoseStack poseStack, MultiBufferSource buffer, float radius,
                              int color, int coreColor, float age, float alpha,
                              boolean withTail, Vec3 tailDir) {
        if (radius <= 0.0f || alpha <= 0.001f) return;

        VertexConsumer shell = buffer.getBuffer(RenderType.eyes(KiOrbModel.PLASMA_TEXTURE));
        VertexConsumer glow = buffer.getBuffer(RenderType.eyes(KiOrbModel.GLOW_TEXTURE));

        float pulse = 1.0f + 0.055f * Mth.sin(age * 0.20f) + 0.025f * Mth.sin(age * 0.47f + 1.1f);
        float r = radius * pulse;

        // Outer plasma skin and a smaller inner shell create depth without cube silhouettes.
        drawSphere(shell, poseStack.last(), r * 0.94f, color, alpha * 0.34f, 22, 12);
        drawSphere(glow, poseStack.last(), r * 0.58f, mix(coreColor, 0xFFFFFF, 0.22f),
                alpha * 0.64f, 18, 10);
        drawSphere(glow, poseStack.last(), r * 0.30f, mix(coreColor, 0xFFFFFF, 0.72f),
                alpha * 0.92f, 14, 8);

        // Two non-aligned bands orbit the orb on different planes.
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotation(age * 0.115f));
        poseStack.mulPose(Axis.XP.rotation(0.62f + 0.07f * Mth.sin(age * 0.08f)));
        drawBand(glow, poseStack.last(), r * 0.92f, r * 0.075f, mix(color, 0xFFFFFF, 0.62f),
                alpha * 0.76f, 48, 0.10f);
        poseStack.popPose();

        poseStack.pushPose();
        poseStack.mulPose(Axis.ZP.rotation(-age * 0.083f + 0.7f));
        poseStack.mulPose(Axis.YP.rotation(1.08f));
        drawBand(glow, poseStack.last(), r * 0.86f, r * 0.040f, mix(color, 0xFFFFFF, 0.78f),
                alpha * 0.60f, 48, 0.24f);
        poseStack.popPose();

        // A slow, thin helix travels over the shell. It reads as controlled ki flow, not random spikes.
        drawSpiral(glow, poseStack.last(), r * 0.965f, r * 0.035f,
                mix(color, 0xFFFFFF, 0.48f), alpha * 0.55f, age, 0.0f);

        // Tail remains a smooth ribbon trail and is shared by hand-formed orbs and travelling blasts.
        if (withTail && tailDir != null && tailDir.lengthSqr() > 1.0E-6) {
            KiOrbModel.renderSmoothTail(poseStack, buffer, radius, color,
                    mix(coreColor, 0xFFFFFF, 0.38f), tailDir, age, alpha);
        }
    }

    private static void drawSphere(VertexConsumer vc, PoseStack.Pose pose, float radius,
                                   int rgb, float alpha, int longitudeSegments, int latitudeSegments) {
        int a = Mth.clamp((int) (alpha * 255.0f), 0, 255);
        for (int lat = 0; lat < latitudeSegments; lat++) {
            float v0 = lat / (float) latitudeSegments;
            float v1 = (lat + 1) / (float) latitudeSegments;
            float phi0 = Mth.PI * v0;
            float phi1 = Mth.PI * v1;
            for (int lon = 0; lon < longitudeSegments; lon++) {
                float u0 = lon / (float) longitudeSegments;
                float u1 = (lon + 1) / (float) longitudeSegments;
                float theta0 = TWO_PI * u0;
                float theta1 = TWO_PI * u1;
                Point p00 = sphere(phi0, theta0, radius);
                Point p01 = sphere(phi0, theta1, radius);
                Point p11 = sphere(phi1, theta1, radius);
                Point p10 = sphere(phi1, theta0, radius);
                int shaded = mix(rgb, 0xFFFFFF, 0.07f + 0.12f * Mth.sin(Mth.PI * (v0 + v1) * 0.5f));
                int r = (shaded >> 16) & 255, g = (shaded >> 8) & 255, b = shaded & 255;
                vertex(vc, pose, p00, u0, v0, r, g, b, a);
                vertex(vc, pose, p01, u1, v0, r, g, b, a);
                vertex(vc, pose, p11, u1, v1, r, g, b, a);
                vertex(vc, pose, p10, u0, v1, r, g, b, a);
            }
        }
    }

    private static Point sphere(float phi, float theta, float radius) {
        float nx = Mth.sin(phi) * Mth.cos(theta);
        float ny = Mth.cos(phi);
        float nz = Mth.sin(phi) * Mth.sin(theta);
        return new Point(nx * radius, ny * radius, nz * radius, nx, ny, nz);
    }

    /** A broad, continuous ring ribbon in the local XY plane. */
    private static void drawBand(VertexConsumer vc, PoseStack.Pose pose, float radius, float halfWidth,
                                 int rgb, float alpha, int segments, float phase) {
        int a = Mth.clamp((int) (alpha * 255.0f), 0, 255);
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        for (int i = 0; i < segments; i++) {
            float u0 = i / (float) segments;
            float u1 = (i + 1) / (float) segments;
            float a0 = TWO_PI * u0 + phase;
            float a1 = TWO_PI * u1 + phase;
            float wobble0 = 1.0f + 0.035f * Mth.sin(a0 * 3.0f + phase * 7.0f);
            float wobble1 = 1.0f + 0.035f * Mth.sin(a1 * 3.0f + phase * 7.0f);
            float mid0 = radius * wobble0;
            float mid1 = radius * wobble1;
            Point inner0 = ringPoint(a0, mid0 - halfWidth);
            Point outer0 = ringPoint(a0, mid0 + halfWidth);
            Point inner1 = ringPoint(a1, mid1 - halfWidth);
            Point outer1 = ringPoint(a1, mid1 + halfWidth);

            // Front face
            vertex(vc, pose, inner0, u0, 0.0f, r, g, b, a);
            vertex(vc, pose, outer0, u0, 1.0f, r, g, b, a);
            vertex(vc, pose, outer1, u1, 1.0f, r, g, b, a);
            vertex(vc, pose, inner1, u1, 0.0f, r, g, b, a);
            // Reverse face for the opposite side of the tilted orbital plane.
            vertex(vc, pose, inner1, u1, 0.0f, r, g, b, a);
            vertex(vc, pose, outer1, u1, 1.0f, r, g, b, a);
            vertex(vc, pose, outer0, u0, 1.0f, r, g, b, a);
            vertex(vc, pose, inner0, u0, 0.0f, r, g, b, a);
        }
    }

    private static Point ringPoint(float angle, float radius) {
        float nx = Mth.cos(angle);
        float ny = Mth.sin(angle);
        return new Point(nx * radius, ny * radius, 0f, 0f, 0f, 1f);
    }

    private static void drawSpiral(VertexConsumer vc, PoseStack.Pose pose, float radius, float halfWidth,
                                   int rgb, float alpha, float age, float phase) {
        int a = Mth.clamp((int) (alpha * 255.0f), 0, 255);
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        final int segments = 80;
        Point previous = spiralPoint(0.0f, age * 0.045f + phase, radius);
        for (int i = 1; i <= segments; i++) {
            float t = i / (float) segments;
            float theta = age * 0.045f + phase + t * TWO_PI * 2.35f;
            Point next = spiralPoint(t, theta, radius);
            Point before = spiralPoint(Math.max(0.0f, t - 1.0f / segments), theta - TWO_PI * 2.35f / segments, radius);
            float tx = next.x - before.x, ty = next.y - before.y, tz = next.z - before.z;
            float cx = previous.ny * tz - previous.nz * ty;
            float cy = previous.nz * tx - previous.nx * tz;
            float cz = previous.nx * ty - previous.ny * tx;
            float length = (float) Math.sqrt(cx * cx + cy * cy + cz * cz);
            if (length < 1.0E-5f) length = 1.0f;
            cx = cx / length * halfWidth;
            cy = cy / length * halfWidth;
            cz = cz / length * halfWidth;
            Point left = new Point(previous.x - cx, previous.y - cy, previous.z - cz,
                    previous.nx, previous.ny, previous.nz);
            Point right = new Point(previous.x + cx, previous.y + cy, previous.z + cz,
                    previous.nx, previous.ny, previous.nz);
            Point nextLeft = new Point(next.x - cx, next.y - cy, next.z - cz, next.nx, next.ny, next.nz);
            Point nextRight = new Point(next.x + cx, next.y + cy, next.z + cz, next.nx, next.ny, next.nz);
            float fade = 0.35f + 0.65f * Mth.sin(Mth.PI * t);
            int bandAlpha = Mth.clamp((int) (a * fade), 0, 255);
            // Winding follows the outward radial normal.
            vertex(vc, pose, left, 0f, t, r, g, b, bandAlpha);
            vertex(vc, pose, nextLeft, 0f, t + 1f / segments, r, g, b, bandAlpha);
            vertex(vc, pose, nextRight, 1f, t + 1f / segments, r, g, b, bandAlpha);
            vertex(vc, pose, right, 1f, t, r, g, b, bandAlpha);
            // Back face for the opposite side of the wrapped filament.
            vertex(vc, pose, right, 1f, t, r, g, b, bandAlpha);
            vertex(vc, pose, nextRight, 1f, t + 1f / segments, r, g, b, bandAlpha);
            vertex(vc, pose, nextLeft, 0f, t + 1f / segments, r, g, b, bandAlpha);
            vertex(vc, pose, left, 0f, t, r, g, b, bandAlpha);
            previous = next;
        }
    }

    private static Point spiralPoint(float t, float theta, float radius) {
        float phi = Mth.PI * (0.12f + 0.76f * t);
        Point p = sphere(phi, theta, radius);
        return p;
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, Point p, float u, float v,
                               int r, int g, int b, int a) {
        vc.addVertex(pose, p.x, p.y, p.z)
                .setColor(r, g, b, a)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LIGHT)
                .setNormal(pose, p.nx, p.ny, p.nz);
    }

    private static int mix(int a, int b, float t) {
        int r = (int) Mth.lerp(t, (a >> 16) & 255, (b >> 16) & 255);
        int g = (int) Mth.lerp(t, (a >> 8) & 255, (b >> 8) & 255);
        int bl = (int) Mth.lerp(t, a & 255, b & 255);
        return (r << 16) | (g << 8) | bl;
    }

    private record Point(float x, float y, float z, float nx, float ny, float nz) {
    }
}

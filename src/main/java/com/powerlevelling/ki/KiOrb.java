package com.powerlevelling.ki;

import net.minecraft.util.Mth;

/**
 * The growth curve of the "forming ki orb" (shared by server and client; no client-only imports). The server only sends
 * the curve's parameters in OrbStatePayload; every client evaluates it locally from the elapsed ticks.
 * <pre>
 *   e &lt; growTicks :  radius = lerp(START_RADIUS -&gt; minRadius, easeOut(e / growTicks))
 *   e &gt;= growTicks:  radius = lerp(minRadius -&gt; maxRadius, (e - growTicks) / (durationTicks - growTicks))   (linear)
 * </pre>
 * Wind-up (tap attacks): growTicks == durationTicks == windup, minRadius == maxRadius == the attack's radius, so the orb
 * simply grows to full size, ease-out.
 * Charge (hold attacks): growTicks == charge.min_ticks, durationTicks == charge.max_ticks, minRadius/maxRadius == the
 * projectile radius range. From min_ticks on this is exactly the formula the server uses when it fires
 * (radius.lerp(chargeProgress)), so the orb becomes the blast without a size jump.
 */
public final class KiOrb {
    /** Radius an orb starts from (blocks). */
    public static final float START_RADIUS = 0.05f;

    private KiOrb() {
    }

    public static float easeOut(float t) {
        float u = 1f - Mth.clamp(t, 0f, 1f);
        return 1f - u * u * u; // cubic ease-out: fast at first, settles at the end
    }

    public static float radius(float elapsedTicks, int durationTicks, int growTicks, float minRadius, float maxRadius) {
        float e = Math.max(0f, elapsedTicks);
        int grow = Mth.clamp(growTicks, 0, Math.max(durationTicks, 0));
        if (e < grow) return Mth.lerp(easeOut(e / grow), START_RADIUS, Math.max(START_RADIUS, minRadius));
        if (durationTicks <= grow) return Math.max(START_RADIUS, durationTicks <= 0 ? minRadius : maxRadius);
        float t = Mth.clamp((e - grow) / (float) (durationTicks - grow), 0f, 1f);
        return Math.max(START_RADIUS, Mth.lerp(t, minRadius, maxRadius));
    }

    /** 0..1 fraction of the whole orb duration (drives sound pitch, particle rate, first-person scale). */
    public static float progress(float elapsedTicks, int durationTicks) {
        return durationTicks <= 0 ? 1f : Mth.clamp(elapsedTicks / durationTicks, 0f, 1f);
    }
}

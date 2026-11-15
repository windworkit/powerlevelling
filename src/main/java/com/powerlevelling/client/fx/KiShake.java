package com.powerlevelling.client.fx;

import com.powerlevelling.PowerLevelling;
import com.powerlevelling.config.ClientConfig;

import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * Client screen shake and FOV punch (K1). {@link #add} starts / refreshes a shake of a given intensity (0..~1) lasting some
 * ticks: the camera angles get a decaying noise (pitch / yaw / roll), scaled by the client config {@code screenShake}
 * (0 = off). {@link #fovPunch} widens the FOV by some degrees and eases back. Time runs on the wall clock (20 ticks per
 * second), so no tick hook is needed. Client only, no network.
 */
@EventBusSubscriber(modid = PowerLevelling.MODID, value = Dist.CLIENT)
public final class KiShake {
    private static final double NS_PER_TICK = 50_000_000.0;
    /** Max camera wobble in degrees at intensity 1. */
    private static final float MAX_DEG = 2.2f;

    private static float amp;
    private static long startNs;
    private static float durTicks = 1f;

    private static float fovAmount;
    private static long fovStartNs;
    private static final float FOV_TICKS = 8f;
    /** Steady FOV narrowing in degrees (beam charge); set every client tick by BeamRenderer. */
    private static float zoom;

    private KiShake() {
    }

    /** Starts a shake; a stronger running one is kept, a weaker one is ignored. */
    public static void add(float intensity, int ticks) {
        if (intensity <= 0.01f || ticks <= 0) return;
        if (intensity < current()) return;
        amp = Math.min(intensity, 1.5f);
        startNs = System.nanoTime();
        durTicks = ticks;
    }

    /** FOV opens by {@code degrees} and eases back over ~8 ticks. */
    public static void fovPunch(float degrees) {
        if (degrees <= 0f) return;
        fovAmount = degrees;
        fovStartNs = System.nanoTime();
    }

    /** Current shake intensity (decays quadratically to 0). */
    private static float current() {
        if (amp <= 0f) return 0f;
        float age = (float) ((System.nanoTime() - startNs) / NS_PER_TICK);
        if (age >= durTicks) {
            amp = 0f;
            return 0f;
        }
        float k = 1f - age / durTicks;
        return amp * k * k;
    }

    private static float currentFov() {
        if (fovAmount <= 0f) return 0f;
        float age = (float) ((System.nanoTime() - fovStartNs) / NS_PER_TICK);
        if (age >= FOV_TICKS) {
            fovAmount = 0f;
            return 0f;
        }
        float k = 1f - age / FOV_TICKS;
        return fovAmount * k * k;
    }

    /** Narrows the FOV by {@code degrees} (0 = off) until set again. Scaled by the screenShake option like the punch. */
    public static void setZoom(float degrees) {
        zoom = Math.max(0f, degrees);
    }

    public static void reset() {
        amp = 0f;
        fovAmount = 0f;
        zoom = 0f;
    }

    /** Two incommensurate sines: cheap pseudo noise in -1..1. */
    private static float noise(double t, double seed) {
        return (float) (0.6 * Math.sin(t * 47.0 + seed) + 0.4 * Math.sin(t * 113.0 + seed * 2.7));
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        float a = current() * ClientConfig.SCREEN_SHAKE.get().floatValue();
        if (a <= 0.001f) return;
        double t = System.nanoTime() / 1.0e9;
        event.setPitch(event.getPitch() + a * MAX_DEG * noise(t, 0.0));
        event.setYaw(event.getYaw() + a * MAX_DEG * noise(t, 1.7));
        event.setRoll(event.getRoll() + a * MAX_DEG * 0.8f * noise(t, 3.1));
    }

    @SubscribeEvent
    public static void onFov(ViewportEvent.ComputeFov event) {
        float f = (currentFov() - zoom) * ClientConfig.SCREEN_SHAKE.get().floatValue();
        if (Math.abs(f) <= 0.01f) return;
        event.setFOV(Mth.clamp(event.getFOV() + f, 1.0, 170.0));
    }
}

package com.powerlevelling.client;

/**
 * The local player's predicted "skill key held / orb forming" state: which slot and for how many ticks, so the HUD charge
 * bar reacts instantly. For hold attacks it follows the key; for tap attacks it is the wind-up (runs to the end even if the
 * key is released). The forming orb itself comes from the server (ClientOrbState). Visuals only.
 * Plain data, client thread only, no client-only imports.
 */
public final class ClientSkillState {
    /** Slot 0-3, or -1. */
    public static int heldSlot = -1;
    public static int heldTicks;
    /** Ticks for a full bar (charge max_ticks or windup_ticks). */
    public static int heldMax = 1;
    /** True for a tap wind-up (releasing the key does not end it). */
    public static boolean heldWindup;

    private ClientSkillState() {
    }

    public static void start(int slot, boolean windup, int maxTicks) {
        heldSlot = slot;
        heldTicks = 0;
        heldMax = Math.max(1, maxTicks);
        heldWindup = windup;
    }

    /** Key released: ends a hold charge, but not a wind-up. */
    public static void release(int slot) {
        if (heldSlot == slot && !heldWindup) stop(slot);
    }

    /** Rejected press / forced end for this slot. */
    public static void stop(int slot) {
        if (heldSlot == slot) reset();
    }

    /** 0..1 fill of the bar for this slot (0 if it is not the active one). */
    public static float fraction(int slot) {
        if (heldSlot != slot) return 0f;
        return Math.min(1f, heldTicks / (float) heldMax);
    }

    public static void reset() {
        heldSlot = -1;
        heldTicks = 0;
        heldMax = 1;
        heldWindup = false;
    }
}

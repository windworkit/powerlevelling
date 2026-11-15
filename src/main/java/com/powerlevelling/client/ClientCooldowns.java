package com.powerlevelling.client;

import com.powerlevelling.data.KiProgression;
import com.powerlevelling.network.CooldownSyncPayload;

/**
 * Client-side skill slot cooldowns (index 0-3 = slots 1-4). The server sends a cooldown once; the client counts it down
 * (KiFx calls {@link #tick()} once per unpaused client tick). Plain data, client thread only, no client-only imports.
 */
public final class ClientCooldowns {
    private static final int[] REMAINING = new int[KiProgression.LOADOUT_SLOTS];
    private static final int[] TOTAL = new int[KiProgression.LOADOUT_SLOTS];

    private ClientCooldowns() {
    }

    public static void apply(CooldownSyncPayload p) {
        if (p.slot() < 0 || p.slot() >= REMAINING.length) return;
        REMAINING[p.slot()] = Math.max(0, p.remaining());
        TOTAL[p.slot()] = Math.max(Math.max(0, p.total()), REMAINING[p.slot()]);
    }

    /** Local prediction: starts a cooldown right away (the server's sync overwrites it a moment later). */
    public static void start(int slot, int ticks) {
        if (slot < 0 || slot >= REMAINING.length || ticks <= 0) return;
        REMAINING[slot] = ticks;
        TOTAL[slot] = ticks;
    }

    public static void tick() {
        for (int i = 0; i < REMAINING.length; i++) {
            if (REMAINING[i] > 0) REMAINING[i]--;
        }
    }

    public static int remaining(int slot) {
        return REMAINING[slot];
    }

    /** Remaining fraction 0..1 (1 = just started). */
    public static float fraction(int slot) {
        return TOTAL[slot] <= 0 ? 0f : Math.min(1f, REMAINING[slot] / (float) TOTAL[slot]);
    }

    public static void reset() {
        java.util.Arrays.fill(REMAINING, 0);
        java.util.Arrays.fill(TOTAL, 0);
    }
}

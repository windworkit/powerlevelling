package com.powerlevelling.client;

import java.util.HashMap;
import java.util.Map;

import com.powerlevelling.combat.CombatAction;
import com.powerlevelling.combat.Phase;
import com.powerlevelling.network.VisualStatePayload;

/**
 * Client-side cache of other entities' (and our own, predicted) visual flags: charging, stance, ki break.
 * Plain data, client thread only. Deliberately imports no client-only Minecraft classes (safe to reference from
 * common code such as the payload registration).
 */
public final class ClientVisualState {
    /** Per-entity state. Entries with no flags are removed. */
    public static final class State {
        public int flags;
        /** Ticks the charge aura has been running; counted up locally (the payload only seeds it). */
        public float chargeTicks;
        /** Ticks until the next charge sound (client-side, per entity). */
        public int soundCooldown;

        public boolean has(int flag) {
            return (flags & flag) != 0;
        }
    }

    private static final Map<Integer, State> STATES = new HashMap<>();

    /** Entity id of the local player; kept up to date by InputHandler every tick (-1 = unknown). */
    public static int selfId = -1;

    private ClientVisualState() {
    }

    // ---- server updates ------------------------------------------------------------------------------------------

    public static void apply(VisualStatePayload p) {
        int flags = p.flags();
        State old = STATES.get(p.entityId());
        int oldFlags = old == null ? 0 : old.flags;

        // Our own stance/charge are predicted: while a prediction is still waiting for its ack, don't let an
        // older server state flip it back (the ack will roll it back if the server refused).
        if (p.entityId() == selfId) {
            if (ClientCombat.hasPending(CombatAction.STANCE_TOGGLE, Phase.PRESS)) {
                flags = (flags & ~VisualStatePayload.STANCE) | (oldFlags & VisualStatePayload.STANCE);
            }
            if (ClientCombat.hasPending(CombatAction.CHARGE_KI, Phase.PRESS)) {
                flags = (flags & ~VisualStatePayload.CHARGING) | (oldFlags & VisualStatePayload.CHARGING);
            }
            if (ClientCombat.hasPending(CombatAction.TURBO, Phase.PRESS)) {
                flags = (flags & ~VisualStatePayload.TURBO) | (oldFlags & VisualStatePayload.TURBO);
            }
        }

        if (flags == 0) {
            STATES.remove(p.entityId());
            return;
        }
        State s = old != null ? old : new State();
        boolean wasCharging = (oldFlags & VisualStatePayload.CHARGING) != 0;
        boolean charging = (flags & VisualStatePayload.CHARGING) != 0;
        if (charging && !wasCharging) s.chargeTicks = Math.max(0f, p.chargeTicksOrStrength());
        if (!charging) s.chargeTicks = 0f;

        // Transform hold: negative chargeTicksOrStrength means we're transforming; count up from there
        boolean wasTransforming = (oldFlags & VisualStatePayload.TRANSFORMING) != 0;
        boolean transforming = (flags & VisualStatePayload.TRANSFORMING) != 0;
        if (transforming && !wasTransforming) s.chargeTicks = Math.max(0f, -p.chargeTicksOrStrength());
        if (!transforming) s.chargeTicks = 0f;

        s.flags = flags;
        STATES.put(p.entityId(), s);
    }

    // ---- queries -------------------------------------------------------------------------------------------------

    public static boolean has(int entityId, int flag) {
        State s = STATES.get(entityId);
        return s != null && s.has(flag);
    }

    public static boolean selfStance() {
        return selfId >= 0 && has(selfId, VisualStatePayload.STANCE);
    }

    public static boolean selfCharging() {
        return selfId >= 0 && has(selfId, VisualStatePayload.CHARGING);
    }

    public static boolean hasSuperSaiyan(int entityId) {
        return has(entityId, VisualStatePayload.SUPER_SAIYAN);
    }

    public static boolean selfTurbo() {
        return selfId >= 0 && has(selfId, VisualStatePayload.TURBO);
    }

    /** Returns the State object for an entity (or null if no flags are set). */
    public static State get(int entityId) {
        return STATES.get(entityId);
    }

    /** Live view for the FX code (do not modify the map). */
    public static Iterable<Map.Entry<Integer, State>> entries() {
        return STATES.entrySet();
    }

    // ---- local prediction ----------------------------------------------------------------------------------------

    public static void setSelfFlag(int flag, boolean on) {
        if (selfId < 0) return;
        State s = STATES.get(selfId);
        if (s == null) {
            if (!on) return;
            s = new State();
            STATES.put(selfId, s);
        }
        boolean had = s.has(flag);
        if (on) s.flags |= flag;
        else s.flags &= ~flag;
        if (flag == VisualStatePayload.CHARGING && on && !had) s.chargeTicks = 0f;
        if (flag == VisualStatePayload.CHARGING && !on) s.chargeTicks = 0f;
        if (s.flags == 0) STATES.remove(selfId);
    }

    public static void flipSelfStance() {
        setSelfFlag(VisualStatePayload.STANCE, !selfStance());
    }

    // ---- lifecycle -----------------------------------------------------------------------------------------------

    public static void remove(int entityId) {
        STATES.remove(entityId);
    }

    public static void reset() {
        STATES.clear();
        selfId = -1;
    }
}

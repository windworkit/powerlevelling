package com.powerlevelling.client;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.powerlevelling.combat.CombatAction;
import com.powerlevelling.combat.Phase;
import com.powerlevelling.network.ActionAckPayload;
import com.powerlevelling.network.VisualStatePayload;

/**
 * Client side of the input pipeline: sequence numbers and the map of inputs still waiting for an ack.
 * A rejected ack rolls the local prediction back (M2: only stance and charge predict anything; M5 adds move rollback).
 * Plain data, client thread only, no client-only imports.
 */
public final class ClientCombat {
    private record Pending(CombatAction action, Phase phase, long sentTick) {
    }

    /** Drop unacked entries after this many client ticks (a lost ack must not block prediction forever). */
    private static final long PENDING_TIMEOUT_TICKS = 200;

    private static final Map<Integer, Pending> PENDING = new HashMap<>();
    private static int nextSeq = 0;

    private ClientCombat() {
    }

    /** Allocates a sequence number and remembers the input until it is acked. */
    public static int register(CombatAction action, Phase phase, long nowTick) {
        int seq = ++nextSeq;
        PENDING.put(seq, new Pending(action, phase, nowTick));
        return seq;
    }

    /** A sequence number for an input acked elsewhere (melee: MeleeStatePayload). Same counter, so seqs stay unique. */
    public static int allocSeq() {
        return ++nextSeq;
    }

    public static boolean hasPending(CombatAction action, Phase phase) {
        for (Pending p : PENDING.values()) {
            if (p.action == action && p.phase == phase) return true;
        }
        return false;
    }

    public static void onAck(ActionAckPayload ack) {
        Pending p = PENDING.remove(ack.seq());
        if (p == null || ack.accepted()) return;
        rollback(p);
    }

    private static void rollback(Pending p) {
        switch (p.action) {
            case CHARGE_KI -> {
                // Server refused the charge: stop the local aura/shimmer.
                if (p.phase == Phase.PRESS) ClientVisualState.setSelfFlag(VisualStatePayload.CHARGING, false);
            }
            case STANCE_TOGGLE -> ClientVisualState.flipSelfStance(); // undo the predicted flip
            case TRANSFORM -> {
                // Server refused the transform: stop the local aura.
                if (p.phase == Phase.PRESS) ClientVisualState.setSelfFlag(VisualStatePayload.TRANSFORMING, false);
            }
            case TURBO -> {
                // Server refused the toggle: flip back to previous state.
                if (p.phase == Phase.PRESS) ClientVisualState.setSelfFlag(VisualStatePayload.TURBO, !ClientVisualState.selfTurbo());
            }
            case SKILL_1, SKILL_2, SKILL_3, SKILL_4 -> {
                // Server refused the skill press: drop the predicted charging orb. (A predicted muzzle flash can't be undone.)
                if (p.phase == Phase.PRESS) ClientSkillState.stop(p.action.ordinal() - CombatAction.SKILL_1.ordinal());
            }
            default -> {
                // Moves, guard: nothing predicted yet (M5+ blends the animation back to idle here).
            }
        }
    }

    /** Called every client tick: forgets acks that never came. */
    public static void tick(long nowTick) {
        Iterator<Pending> it = PENDING.values().iterator();
        while (it.hasNext()) {
            if (nowTick - it.next().sentTick > PENDING_TIMEOUT_TICKS) it.remove();
        }
    }

    public static void clearPending() {
        PENDING.clear();
    }

    public static void reset() {
        PENDING.clear();
        nextSeq = 0;
    }

    /** True if the local player is currently charging a skill (hold or wind-up). */
    public static boolean selfSkillCharging() {
        return ClientSkillState.heldSlot != -1;
    }
}

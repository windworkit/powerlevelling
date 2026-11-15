package com.powerlevelling.combat;

import net.minecraft.resources.ResourceLocation;

/**
 * The melee state machine (design 4.2), as plain data + pure logic with no world access, so the server (authoritative,
 * in CombatState) and the client (prediction, ClientMelee) run the SAME code on their own tick clocks.
 * <p>
 * IDLE -> STARTUP -> ACTIVE -> RECOVERY -> IDLE. A press while {@link #interruptible()} (IDLE, or RECOVERY at/after the move's
 * cancel_from tick) starts a move at once; otherwise it is buffered (one at a time) and fires at the first interruptible tick,
 * or expires after {@link #BUFFER_TICKS}. After RECOVERY the chain stays open for {@link #CHAIN_GRACE_TICKS}: an input in that
 * grace still uses the last move's follow_ups. Which move an input starts is decided by the {@link Resolver}
 * (CombatMoves.resolve + side-specific checks such as stamina).
 */
public final class MeleeMachine {
    public static final int BUFFER_TICKS = 6;
    public static final int CHAIN_GRACE_TICKS = 12;

    public enum MeleePhase { IDLE, STARTUP, ACTIVE, RECOVERY }

    public enum Outcome { STARTED, BUFFERED, REJECTED }

    /** Picks the move for an input; {@code chainFrom} = move whose follow-ups apply (null = neutral). Null = can't start. */
    public interface Resolver {
        CombatMoves.Resolved resolve(MeleeInput input, ResourceLocation chainFrom);
    }

    /** Tick callbacks for the buffered input. */
    public interface Listener {
        /** The buffered input with this seq started a move (read moveId / move / chain). */
        void started(int seq);

        /** The buffered input with this seq expired or could not start. */
        void expired(int seq);
    }

    public MeleePhase phase = MeleePhase.IDLE;
    /** Current move (or, while IDLE in chain grace, the last one). */
    public ResourceLocation moveId;
    public CombatMove move;
    /** Ticks spent in the current phase. */
    public int ticks;
    /** 1-based position of the current move in its chain; 0 = no chain. */
    public int chain;
    /** Input sequence number that started the current move (-1 = none / server-forced). */
    public int seq = -1;
    /** Increments every time a move starts (the server clears its per-move hit set when this changes). */
    public int serial;
    /** IDLE ticks left in which an input still chains off {@link #moveId}. */
    public int chainGrace;

    public MeleeInput buffered;
    public int bufferedSeq = -1;
    public int bufferAge;

    public boolean busy() {
        return phase != MeleePhase.IDLE;
    }

    public boolean active() {
        return phase == MeleePhase.ACTIVE;
    }

    public boolean inCancelWindow() {
        return phase == MeleePhase.RECOVERY && move != null && move.cancelFrom() >= 0 && ticks >= move.cancelFrom();
    }

    /** IDLE or in the cancel window: a new move, a skill or ki charge may start now (starting one cancels this move). */
    public boolean interruptible() {
        return phase == MeleePhase.IDLE || inCancelWindow();
    }

    /** Ticks since the current move started. */
    public int moveTicks() {
        if (move == null || phase == MeleePhase.IDLE) return 0;
        return switch (phase) {
            case STARTUP -> ticks;
            case ACTIVE -> move.startup() + ticks;
            case RECOVERY -> move.startup() + move.active() + ticks;
            default -> 0;
        };
    }

    private ResourceLocation chainSource() {
        if (inCancelWindow()) return moveId;
        if (phase == MeleePhase.IDLE && chainGrace > 0) return moveId;
        return null;
    }

    /** An input edge (press). */
    public Outcome press(MeleeInput input, int seq, Resolver resolver) {
        if (interruptible()) return start(input, seq, resolver) ? Outcome.STARTED : Outcome.REJECTED;
        if (buffered != null) return Outcome.REJECTED; // one buffered input at a time; mashing doesn't queue
        buffered = input;
        bufferedSeq = seq;
        bufferAge = 0;
        return Outcome.BUFFERED;
    }

    private boolean start(MeleeInput input, int seq, Resolver resolver) {
        ResourceLocation from = chainSource();
        CombatMoves.Resolved res = resolver.resolve(input, from);
        if (res == null) return false;
        boolean continues = from != null && move != null && res.id().equals(move.followUps().get(input));
        forceStart(res.id(), res.move(), seq, continues ? chain + 1 : 1);
        return true;
    }

    /** Starts a move unconditionally (also used by the client to adopt the server's verdict). */
    public void forceStart(ResourceLocation id, CombatMove m, int seq, int chainIndex) {
        moveId = id;
        move = m;
        phase = MeleePhase.STARTUP;
        ticks = 0;
        chain = Math.max(1, chainIndex);
        this.seq = seq;
        serial++;
        chainGrace = 0;
    }

    /** One tick. Order: advance phase, then resolve the buffer (so a buffered press fires on the first cancel tick). */
    public void tick(Resolver resolver, Listener listener) {
        if (phase == MeleePhase.IDLE) {
            if (chainGrace > 0 && --chainGrace == 0) {
                chain = 0;
                moveId = null;
                move = null;
            }
        } else if (move == null) {
            cancel();
        } else {
            ticks++;
            if (phase == MeleePhase.STARTUP && ticks >= move.startup()) {
                phase = MeleePhase.ACTIVE;
                ticks = 0;
            } else if (phase == MeleePhase.ACTIVE && ticks >= move.active()) {
                phase = MeleePhase.RECOVERY;
                ticks = 0;
            } else if (phase == MeleePhase.RECOVERY && ticks >= move.recovery()) {
                phase = MeleePhase.IDLE;
                ticks = 0;
                chainGrace = CHAIN_GRACE_TICKS;
            }
        }
        if (buffered == null) return;
        if (interruptible()) {
            MeleeInput in = buffered;
            int s = bufferedSeq;
            clearBuffer();
            if (start(in, s, resolver)) listener.started(s);
            else listener.expired(s);
        } else if (++bufferAge > BUFFER_TICKS) {
            int s = bufferedSeq;
            clearBuffer();
            listener.expired(s);
        }
    }

    /** Back to IDLE, chain dropped. Returns the seq of a buffered input that is thereby dropped, or -1. */
    public int cancel() {
        int dropped = buffered != null ? bufferedSeq : -1;
        clearBuffer();
        phase = MeleePhase.IDLE;
        ticks = 0;
        chain = 0;
        chainGrace = 0;
        moveId = null;
        move = null;
        seq = -1;
        return dropped;
    }

    private void clearBuffer() {
        buffered = null;
        bufferedSeq = -1;
        bufferAge = 0;
    }
}

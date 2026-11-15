package com.powerlevelling.data;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.powerlevelling.network.OrbStatePayload;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

/** Transient (never saved) per-player combat bookkeeping: stance, ki charge, skill phase, melee state machine, hitstun. */
public class CombatState {
    // Far in the past so a fresh player counts as "out of combat" and "not spending".
    public long lastCombatTick = Long.MIN_VALUE / 2;
    public long lastStaminaSpendTick = Long.MIN_VALUE / 2;

    // ---- Stance / ki charge (M2) ----
    /** Combat Stance is on (server-authoritative; mirrored to clients through VisualStatePayload). */
    public boolean stance;
    public boolean charging;
    /** Ticks the current charge has been running (0 when not charging). */
    public int chargeTicks;
    /** Ticks charged while out of combat since the last training EXP award. */
    public int trainingTicks;
    /** Game time before which a new charge cannot start (set when a hit interrupts a charge). */
    public long chargeLockUntil = Long.MIN_VALUE / 2;
    /** True while the charge slowdown attribute modifier is on the player (so cleanup knows it may need removing). */
    public boolean chargeSlowApplied;

    // ---- Transformation (Forms) ----
    /** Active form id (e.g., "powerlevelling:super_saiyan"), or null/empty for base form. */
    public ResourceLocation form;
    /** True while the player is holding the transform key (aura gathers, then transforms when held long enough). */
    public boolean transforming;
    /** Ticks the current transform hold has been running (0 when not transforming). */
    public int transformTicks;

    // ---- Turbo Mode ----
    /** True while the player is holding the turbo key (amplified speed and aura). */
    public boolean turbo;
    /** True while the turbo speed modifier is on the player (so cleanup knows it may need removing). */
    public boolean turboSpeedApplied;

    // ---- Dash / dodge ----
    /** Game time before which another dash cannot start (set on each dash). */
    public long dashCooldownUntil = Long.MIN_VALUE / 2;

    // ---- Guard / Block ----
    /** True while the player is actively guarding. */
    public boolean isGuarding;
    /** Game time when the current guard started (for perfect window tracking). */
    public long guardStartTick = -1;
    /** True while the perfect guard (parry) window is active. */
    public boolean perfectWindowActive;
    /** Game time before which another guard cannot start (cooldown after end/break). */
    public long guardCooldownUntil = Long.MIN_VALUE / 2;

    // ---- Ki attacks (M3) ----
    /** Loadout slot (0-3) currently being held to charge a skill, or -1. */
    public int skillSlot = -1;
    /** The attack being charged (kept so a loadout change mid-charge can't swap it). */
    public ResourceLocation skillAttack;
    /** Ticks the skill charge has been running. */
    public int skillTicks;
    /** True while the skill-charge slowdown attribute modifier is on the player. */
    public boolean skillSlowApplied;
    /**
     * Where the running skill is (Addendum C.6): WINDUP (tap, the orb forms, fires by itself), CHARGE (hold, release fires),
     * ACTIVE (after firing, for attacks that keep going: beam, barrage ...), RECOVERY (after firing: skills and melee are
     * blocked until skillPhaseEnd), NONE. skillSlot / skillAttack are set in WINDUP, CHARGE and ACTIVE, not in RECOVERY.
     */
    public SkillPhase skillPhase = SkillPhase.NONE;
    /** Game time RECOVERY ends. */
    public long skillPhaseEnd;
    /** Per-cast scratch data of an ACTIVE attack (null otherwise). */
    public ActiveCast cast;
    /** Game time the skill charge / wind-up started (the forming orb is timed from this). */
    public long skillStartTick;
    /** The forming-orb state last broadcast to self + trackers (null = no orb). Resent to new observers on StartTracking. */
    public OrbStatePayload orb;
    /** Game time each ki attack's cooldown ends (per attack id, so the same attack in two slots shares it). */
    public final Map<ResourceLocation, Long> kiCooldowns = new HashMap<>();
    /** Ki spent on attacks that hasn't been turned into Ki Control mastery XP yet (1 XP per 10 ki). */
    public float kiSpentAccum;

    public enum SkillPhase { NONE, WINDUP, CHARGE, ACTIVE, RECOVERY }

    /** State an attack keeps while it is ACTIVE (barrage count / next shot tick, beam id, disc UUID, homing list ...). */
    public static final class ActiveCast {
        public int ticks;
        public int shots;
        public long nextShotTick;
        public int beamId = -1;
        public UUID discId;
        public final List<UUID> homing = new ArrayList<>();
    }

    // ---- Melee (M5, see docs/combat_framework_notes.md) ----
    /** Server-authoritative melee state machine (ticked by MeleeEngine from PlayerEvents.onPlayerTick). */
    public final com.powerlevelling.combat.MeleeMachine melee = new com.powerlevelling.combat.MeleeMachine();
    /** Entity ids already hit by the current move (each move hits each target at most once). */
    public final Set<Integer> meleeHits = new HashSet<>();
    /** MeleeMachine.serial the hit set belongs to. */
    public int meleeSerial = -1;
    /** Highest melee input seq seen (older / duplicate packets are refused). */
    public int lastMeleeSeq = Integer.MIN_VALUE;
    /** Fractional Martial XP (Dummy hits pay 0.5). */
    public float martialAccum;
    /** Training EXP from Dummy hits: window start + count (cap per hour, Addendum A.1). */
    public long dummyExpWindowStart = Long.MIN_VALUE / 2;
    public int dummyExpInWindow;
    /** Consecutive kills credited to ki attacks; reset when the player dies (ki_kill_streak advancement). */
    public int kiKillStreak;

    /**
     * Real hitstun (M5): game time the stun ends. Set by MeleeEngine.applyHitstun (melee hits; ki attacks may call it later).
     * Replaces the K1 "hit within 10 ticks" stand-in, so Burst's combo breaker (C.8) now keys off real hitstun.
     */
    public long hitstunUntil = Long.MIN_VALUE / 2;
    /** Game time the player was last damaged (set by PlayerEvents.onDamage; informational, no longer drives hitstun). */
    public long lastHurtTick = Long.MIN_VALUE / 2;
    /** Damage is cancelled until this game time (burst i-frames; ProgressionEvents.onIncomingDamage). */
    public long iframesUntil = Long.MIN_VALUE / 2;

    /** In hitstun: no movement / melee / ki charge / skills except usable_in_hitstun ones (Burst). */
    public boolean inHitstun(long now) {
        return now < hitstunUntil;
    }

    public boolean invulnerable(long now) {
        return now < iframesUntil;
    }

    public boolean skillWindingUp() {
        return skillSlot >= 0 && skillPhase == SkillPhase.WINDUP;
    }

    /** True while the slot key's charge is running (hold-to-charge). */
    public boolean skillHolding() {
        return skillSlot >= 0 && skillPhase == SkillPhase.CHARGE;
    }

    /**
     * True for any skill phase except NONE: wind-up, charge, active AND recovery. Blocks starting other skills, melee moves
     * and ki charging (RECOVERY "blocks skills + melee").
     */
    public boolean skillCharging() {
        return skillPhase != SkillPhase.NONE;
    }

    public long cooldownUntil(ResourceLocation id) {
        return kiCooldowns.getOrDefault(id, 0L);
    }

    // ---- Character system (C4): rate limits and the join prompt (transient) ----
    public long lastCharacterSubmitTick = Long.MIN_VALUE / 2;
    public long lastTailToggleTick = Long.MIN_VALUE / 2;
    public long lastGateHintTick = Long.MIN_VALUE / 2;
    /** Set at login: once game time reaches joinPromptAt the server sends the creation prompt / free re-creation notice. */
    public boolean joinPromptPending;
    public long joinPromptAt;

    /** Last visual flags broadcast; -1 = never sent. */
    public int sentVisualFlags = -1;

    // Input flood protection: at most INPUTS_PER_WINDOW inputs per 20 ticks.
    private static final int INPUTS_PER_WINDOW = 80;
    private long inputWindowStart = Long.MIN_VALUE / 2;
    private int inputCount;

    public boolean allowInput(long now) {
        if (now - inputWindowStart >= 20) {
            inputWindowStart = now;
            inputCount = 0;
        }
        return ++inputCount <= INPUTS_PER_WINDOW;
    }

    // ---- Anti-farm: kill timestamps per entity type, rolling 10-minute window (Addendum A.1) ----
    public static final long KILL_WINDOW_TICKS = 10 * 60 * 20L;
    private final Map<EntityType<?>, Deque<Long>> recentKills = new HashMap<>();

    /** Records a kill and returns how many kills of this type were already inside the window (before this one). */
    public int recordKill(EntityType<?> type, long now) {
        Deque<Long> q = recentKills.computeIfAbsent(type, t -> new ArrayDeque<>());
        while (!q.isEmpty() && now - q.peekFirst() > KILL_WINDOW_TICKS) q.pollFirst();
        int prior = q.size();
        q.addLast(now);
        return prior;
    }

    // ---- Zenkai window (Addendum A.2) ----
    /** Game time the window closes; negative = closed. */
    public long zenkaiWindowEnd = -1;
    /** Everything that damaged the player while the window was open. */
    public final Set<UUID> zenkaiAttackers = new HashSet<>();

    public boolean zenkaiWindowOpen(long now) {
        return zenkaiWindowEnd >= 0 && now <= zenkaiWindowEnd;
    }

    // ---- Lock-On Targeting (MT-002) ----
    /** Entity ID of the currently locked-on target, or 0 if none. */
    public int lockTargetId = 0;
    /** Game time until which the lock remains active (grace period after key release). */
    public long lockUntil = Long.MIN_VALUE / 2;
    /** Whether the lock is actively being held (used to distinguish from expired grace). */
    public boolean lockHeld;

    // Snapshot of what the client was last told; NaN means "never sent".
    public long lastSendTick = Long.MIN_VALUE / 2;
    private float sentKi = Float.NaN;
    private float sentMaxKi = Float.NaN;
    private float sentStamina = Float.NaN;
    private float sentMaxStamina = Float.NaN;
    private float sentEndurance = Float.NaN;
    private boolean sentKiBreak;

    /** True if any synced value differs from the last snapshot (NaN comparisons are always "different"). */
    public boolean isDirty(CombatResources r, float maxKi, float maxStamina) {
        return r.ki != sentKi || maxKi != sentMaxKi || r.stamina != sentStamina
                || maxStamina != sentMaxStamina || r.endurance != sentEndurance || r.kiBreak != sentKiBreak;
    }

    public void markSent(CombatResources r, float maxKi, float maxStamina, long gameTime) {
        sentKi = r.ki;
        sentMaxKi = maxKi;
        sentStamina = r.stamina;
        sentMaxStamina = maxStamina;
        sentEndurance = r.endurance;
        sentKiBreak = r.kiBreak;
        lastSendTick = gameTime;
    }
}

package com.powerlevelling.ki;

import com.powerlevelling.ki.KiAttackParts.Charge;
import com.powerlevelling.ki.KiAttackParts.Common;
import com.powerlevelling.ki.KiAttackParts.FloatRange;
import com.powerlevelling.ki.KiAttackParts.InputMode;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * A ki attack definition loaded from data/&lt;ns&gt;/powerlevelling/ki_attack/&lt;id&gt;.json. The JSON "type" picks the
 * implementation (see {@link KiAttackTypes}). Addendum C: the dispatcher {@link KiSkills} owns gates, ki spending, mastery,
 * cooldowns, slowdown and the orb lifecycle; the attack itself only implements what happens at the moment of firing
 * ({@link #fire}), optionally per wind-up tick ({@link #tickWindup}), while it stays active ({@link #tickActive}) and on a
 * re-press ({@link #onRepress}).
 */
public interface KiAttack {
    /** The "type" value this attack was loaded from. */
    ResourceLocation typeId();

    Common common();

    // ---- input ---------------------------------------------------------------------------------------------------------

    /** What this type does when the JSON has no "input.mode". */
    default InputMode defaultInputMode() {
        return InputMode.TAP;
    }

    /** How the slot key drives this attack (JSON input.mode, else the type's default). */
    default InputMode inputMode() {
        return common().input().mode().orElse(defaultInputMode());
    }

    /** True for hold-to-charge attacks (the slot key is held, release fires). */
    default boolean charged() {
        return inputMode() == InputMode.HOLD_CHARGE;
    }

    /**
     * Ticks the ki orb forms in the hand after a tap before the attack fires by itself (release does not matter).
     * Only used by tap attacks; hold-to-charge attacks return 0 here because their charge is the wind-up.
     */
    default int windupTicks() {
        if (charged()) return 0;
        int w = common().windupTicks();
        return w >= 0 ? w : defaultWindupTicks();
    }

    /** What {@link #windupTicks()} is when the JSON has no "windup_ticks". Types override (blast 5, needle 8, burst 4). */
    default int defaultWindupTicks() {
        return 0;
    }

    /** Ticks skills and melee stay blocked after this attack fired (input.recovery_ticks). */
    default int recoveryTicks() {
        return common().input().recoveryTicks();
    }

    // ---- cost / numbers ------------------------------------------------------------------------------------------------

    /** Ki charged on press, before race/nature/tier multipliers: cost.base + cost.max_ki_fraction * maxKi. */
    default float baseCost(float maxKi) {
        return common().cost().base() + common().cost().maxKiFraction() * maxKi;
    }

    /** Guard damage multiplier (guard.mult). Stored for the guard milestone; nothing applies it yet. */
    default float guardMult() {
        return common().guard().mult();
    }

    /** Radius of the impact explosion; 0 = no explosion. */
    default float explosionRadius() {
        return 0f;
    }

    /** 0..1 charge fraction for a hold of {@code ticksHeld} ticks (0 for non-charged attacks). */
    default float chargeProgress(int ticksHeld) {
        if (!charged()) return 0f;
        Charge ch = common().charge();
        if (ch.maxTicks() <= ch.minTicks()) return 1f;
        int eff = Mth.clamp(ticksHeld, ch.minTicks(), ch.maxTicks());
        return (eff - ch.minTicks()) / (float) (ch.maxTicks() - ch.minTicks());
    }

    /**
     * Radius range of the in-hand orb: the wind-up shows lerp(0), a charge grows from min to max. Default = the projectile's
     * radius, so a forming orb becomes the blast without a size jump. Needle / burst override (finger glow, body flare).
     */
    default FloatRange orbRadiusRange() {
        return common().projectile().radius();
    }

    // ---- hitstun (burst combo breaker, C.8) ------------------------------------------------------------------------------

    /** Usable while being hit (hitstun). */
    default boolean usableInHitstun() {
        return false;
    }

    /** Ki cost multiplier when cast in hitstun (only used if {@link #usableInHitstun()}). */
    default float hitstunCostMult() {
        return 1f;
    }

    // ---- execution -----------------------------------------------------------------------------------------------------

    /** The moment the attack goes off. Ki is already paid, cooldown / mastery / recovery are the dispatcher's job. */
    void fire(CastContext ctx);

    /** Called every tick of the wind-up (1..windupTicks) before the attack fires at the last one. Default: nothing. */
    default void tickWindup(CastContext ctx, int windupTick) {
    }

    /** True if after {@link #fire} the attack stays active (beam, barrage, ...) and {@link #tickActive} is called. */
    default boolean hasActivePhase() {
        return false;
    }

    /** Called every tick while ACTIVE. Return false when the attack is over (the dispatcher then starts recovery). */
    default boolean tickActive(CastContext ctx) {
        return false;
    }

    /** True if the cooldown starts when the ACTIVE phase ends instead of at fire (barrage: the stream is the attack). */
    default boolean cooldownAfterActive() {
        return false;
    }

    /** The slot key was pressed again while ACTIVE (disc recall ...). */
    default void onRepress(CastContext ctx) {
    }
}

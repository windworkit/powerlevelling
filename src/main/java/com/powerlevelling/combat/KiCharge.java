package com.powerlevelling.combat;

import com.powerlevelling.PowerLevelling;
import com.powerlevelling.character.CharacterGate;
import com.powerlevelling.character.CharacterMods;
import com.powerlevelling.character.CharacterService;
import com.powerlevelling.config.ServerConfig;
import com.powerlevelling.data.CombatResources;
import com.powerlevelling.data.CombatState;
import com.powerlevelling.data.KiProgression;
import com.powerlevelling.data.KiProgression.Track;
import com.powerlevelling.ki.KiSkills;
import com.powerlevelling.network.ModNetwork;
import com.powerlevelling.progression.Advancements;
import com.powerlevelling.progression.Progression;
import com.powerlevelling.progression.Progression.ExpSource;
import com.powerlevelling.progression.StatEffects;
import com.powerlevelling.registry.ModAttachments;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Server-side ki charging (design 3.1, Addendum A.1): start/stop/interrupt, per-tick gain, slowdown, training EXP. */
public final class KiCharge {
    public static final ResourceLocation CHARGE_SLOW_ID =
            ResourceLocation.fromNamespaceAndPath(PowerLevelling.MODID, "charge_slow");

    /** Getting hit blocks starting a new charge for this long. */
    public static final int HIT_LOCK_TICKS = 10;
    /** Out of combat = no damage dealt or taken for this long (same as the regen rule). */
    private static final int OUT_OF_COMBAT_TICKS = 100;
    private static final int TRAINING_EXP_INTERVAL = 100;
    private static final int MASTERY_XP_INTERVAL = 20;

    private KiCharge() {
    }

    private static CombatState state(ServerPlayer p) {
        return p.getData(ModAttachments.COMBAT_STATE);
    }

    /** Handles a CHARGE_KI press. Returns false when the charge is not allowed right now. */
    public static boolean tryStart(ServerPlayer player) {
        if (!CharacterGate.check(player)) return false; // Addendum B.7 (CombatInputHandler gates too; this is the second lock)
        CombatState st = state(player);
        if (st.charging) return true; // already charging: idempotent
        if (st.skillCharging()) return false; // can't ki-charge while holding a skill charge
        if (!player.isAlive() || player.isSpectator()) return false; // creative is fine
        if (player.level().getGameTime() < st.chargeLockUntil) return false;
        if (st.inHitstun(player.level().getGameTime())) return false; // M5: no charging while stunned
        if (!MeleeEngine.allowsSkill(st)) return false;                 // M5: melee must be idle / in its cancel window
        MeleeEngine.cancelForAction(player, st);

        st.charging = true;
        st.chargeTicks = 0;
        st.trainingTicks = 0;
        applySlow(player, st);
        Advancements.firstKiCharge(player);
        ModNetwork.syncVisual(player);
        return true;
    }

    /** Ends a charge (release, or any forced stop) and removes the slowdown. Safe to call when not charging. */
    public static void stop(ServerPlayer player) {
        CombatState st = state(player);
        if (!st.charging && !st.chargeSlowApplied) return;
        st.charging = false;
        st.chargeTicks = 0;
        st.trainingTicks = 0;
        removeSlow(player, st);
        ModNetwork.syncVisual(player);
    }

    /** A hit: ends the charge and blocks re-pressing for 10 ticks. No effect when not charging. */
    public static void interrupt(ServerPlayer player) {
        CombatState st = state(player);
        if (!st.charging) return;
        stop(player);
        st.chargeLockUntil = player.level().getGameTime() + HIT_LOCK_TICKS;
    }

    /**
     * Full reset for logout / death / respawn / dimension change: charge off, stance off, slowdown removed.
     * Does not send anything; callers that want observers updated call ModNetwork.syncVisual afterwards.
     */
    public static void resetAll(ServerPlayer player) {
        CombatState st = state(player);
        st.charging = false;
        st.chargeTicks = 0;
        st.trainingTicks = 0;
        st.stance = false;
        st.turbo = false;
        Transform.cancelHold(player); // drop a half-finished transformation hold (the form itself is reverted by callers)
        KiSkills.reset(player); // M3: drop any held skill charge and its slowdown
        MeleeEngine.reset(player); // M5: melee state, hitstun and its movement lock
        removeSlow(player, st);
        // Also strip it unconditionally: covers a leftover saved from a crash or an unclean logout.
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(CHARGE_SLOW_ID);
        if (speed != null) speed.removeModifier(ResourceLocation.fromNamespaceAndPath(PowerLevelling.MODID, "turbo_speed"));
    }

    /** Called once per server tick per player, before passive regen. */
    public static void tick(ServerPlayer player, CombatState st, CombatResources r, KiProgression prog, long now) {
        if (!st.charging) return;
        if (!player.isAlive() || player.isSpectator()) {
            stop(player);
            return;
        }

        // Ki gain: rate * maxKi, x0.5 in Ki Break, x(1 + 1% per Ki Control level), x race/nature ki_charge_rate.
        CharacterMods mods = CharacterService.get(player).mods;
        float maxKi = StatEffects.maxKi(prog, mods);
        float mult = (1f + 0.01f * prog.level(Track.KI_CONTROL)) * (float) StatEffects.kiChargeRateMult(mods);
        if (r.kiBreak) mult *= 0.5f;
        float gain = (float) (ServerConfig.KI_CHARGE_RATE.get() * maxKi * mult);
        r.ki = Mth.clamp(r.ki + gain, 0f, maxKi);

        st.chargeTicks++;

        // Ki Control mastery: 1 XP per 20 ticks of charging.
        if (st.chargeTicks % MASTERY_XP_INTERVAL == 0) {
            Progression.addMasteryXp(player, Track.KI_CONTROL, 1);
        }

        // Training EXP: +1 EXP per 100 ticks charged while out of combat. Getting into a fight restarts the count.
        if (now - st.lastCombatTick >= OUT_OF_COMBAT_TICKS) {
            if (++st.trainingTicks >= TRAINING_EXP_INTERVAL) {
                st.trainingTicks = 0;
                Progression.addExp(player, 1, ExpSource.TRAINING);
            }
        } else {
            st.trainingTicks = 0;
        }
    }

    // ---- slowdown ------------------------------------------------------------------------------------------------

    private static void applySlow(ServerPlayer player, CombatState st) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        double mult = Mth.clamp(ServerConfig.CHARGE_MOVE_MULT.get(), 0.0, 1.0);
        // ADD_MULTIPLIED_TOTAL with (mult - 1): final speed x mult (-0.7 -> x0.3).
        speed.addOrReplacePermanentModifier(
                new AttributeModifier(CHARGE_SLOW_ID, mult - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        st.chargeSlowApplied = true;
    }

    private static void removeSlow(ServerPlayer player, CombatState st) {
        if (!st.chargeSlowApplied) return;
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(CHARGE_SLOW_ID);
        st.chargeSlowApplied = false;
    }
}

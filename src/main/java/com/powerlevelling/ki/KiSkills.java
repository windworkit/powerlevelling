package com.powerlevelling.ki;

import com.powerlevelling.PowerLevelling;
import com.powerlevelling.character.CharacterGate;
import com.powerlevelling.combat.CombatAction;
import com.powerlevelling.combat.Phase;
import com.powerlevelling.config.ServerConfig;
import com.powerlevelling.data.CombatResources;
import com.powerlevelling.data.CombatState;
import com.powerlevelling.data.CombatState.ActiveCast;
import com.powerlevelling.data.CombatState.SkillPhase;
import com.powerlevelling.data.KiProgression;
import com.powerlevelling.data.KiProgression.Track;
import com.powerlevelling.ki.KiAttackParts.Charge;
import com.powerlevelling.network.ModNetwork;
import com.powerlevelling.progression.Advancements;
import com.powerlevelling.progression.Progression;
import com.powerlevelling.progression.StatEffects;
import com.powerlevelling.registry.ModAttachments;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * The ki attack dispatcher (M3, reworked in Addendum C / K0). It owns everything that is the same for every attack:
 * SKILL_n PRESS/RELEASE, the gates (character, stance, ki break, cooldown, ki), ki spending (race/nature cost, per-attack
 * mastery tier, hitstun surcharge), the phase machine (WINDUP / CHARGE / ACTIVE / RECOVERY in CombatState), Ki Control
 * mastery XP, per-attack uses and tiers, cooldowns, the charge slowdown and the forming-orb lifecycle (KiOrbs).
 * What an attack actually does when it goes off is {@link KiAttack#fire(CastContext)}.
 * <p>
 * Everything is validated here; the client only predicts visuals. Called from CombatInputHandler (inputs) and PlayerEvents
 * (per tick), and from KiCharge.resetAll (cleanup).
 */
public final class KiSkills {
    public static final ResourceLocation SKILL_SLOW_ID =
            ResourceLocation.fromNamespaceAndPath(PowerLevelling.MODID, "skill_charge_slow");

    /** 1 Ki Control mastery XP per this much ki spent on attacks (design 3.2). */
    private static final float KI_PER_MASTERY_XP = 10f;

    private KiSkills() {
    }

    // ---- input ---------------------------------------------------------------------------------------------------

    /** Handles SKILL_n PRESS/RELEASE. The return value is the ack verdict (false = client rolls its prediction back). */
    public static boolean handle(ServerPlayer player, CombatState st, CombatAction action, Phase phase,
                                 float yaw, float pitch) {
        int slot = action.ordinal() - CombatAction.SKILL_1.ordinal();
        Vec3 look = lookVector(Mth.clamp(pitch, -90f, 90f), yaw);
        return phase == Phase.PRESS ? press(player, st, slot, look) : release(player, st, slot, look);
    }

    /** Same maths as Entity.getViewVector, from raw yaw/pitch degrees. */
    private static Vec3 lookVector(float pitch, float yaw) {
        float p = pitch * Mth.DEG_TO_RAD;
        float y = -yaw * Mth.DEG_TO_RAD;
        float cy = Mth.cos(y);
        float sy = Mth.sin(y);
        float cp = Mth.cos(p);
        float sp = Mth.sin(p);
        return new Vec3(sy * cp, -sp, cy * cp);
    }

    private static boolean press(ServerPlayer player, CombatState st, int slot, Vec3 look) {
        if (!CharacterGate.check(player)) return false;        // Addendum B.7: needs a created character
        if (!st.stance || st.charging) return false;           // skills need stance and can't mix with ki charging
        if (st.skillPhase == SkillPhase.RECOVERY) return false; // recovery blocks skills
        if (st.skillCharging()) {
            if (st.skillSlot != slot) return false;            // other slots are blocked while one runs
            if (st.skillPhase == SkillPhase.ACTIVE) repress(player, st, look);
            return true;                                       // key repeat on the held slot is fine
        }

        KiProgression prog = player.getData(ModAttachments.KI_PROGRESSION);
        CombatResources r = player.getData(ModAttachments.RESOURCES);
        ResourceLocation id = prog.loadout(slot);
        KiAttack attack = KiAttacks.resolve(player, id);
        if (attack == null) return false;
        if (r.kiBreak) return false;
        // M5: in hitstun only usable_in_hitstun attacks (Burst combo breaker, C.8); melee must be idle or in its cancel window.
        if (st.inHitstun(player.level().getGameTime()) && !attack.usableInHitstun()) return false;
        if (!com.powerlevelling.combat.MeleeEngine.allowsSkill(st)) return false;
        // K7: level gate (JSON unlock_level, e.g. the beam unlocks at level 20).
        int unlock = attack.common().unlockLevel();
        if (unlock > 0 && prog.level < unlock && !player.isCreative()) {
            player.displayClientMessage(Component.translatable("message.powerlevelling.attack_locked",
                    Component.translatable(attack.common().name()), unlock).withStyle(ChatFormatting.RED), true);
            return false;
        }

        long now = player.level().getGameTime();
        if (now < st.cooldownUntil(id)) return false;

        float cost = castCost(player, st, prog, attack, id, now);
        if (!canAfford(player, r, cost)) {
            player.displayClientMessage(Component.translatable("message.powerlevelling.no_ki").withStyle(ChatFormatting.RED), true);
            return false;
        }

        boolean comboBreak = attack.usableInHitstun() && st.inHitstun(now);
        com.powerlevelling.combat.MeleeEngine.cancelForAction(player, st); // a skill cancels melee (cancel window only)
        spendKi(player, st, r, cost);
        if (comboBreak) com.powerlevelling.combat.MeleeEngine.breakCombo(player); // Burst ends the stun + combo
        if (attack.charged()) {
            // Hold: the orb grows while the key is held, release fires. Keeps the attack's move_speed_mult slowdown.
            beginSkillState(player, st, slot, id, attack, SkillPhase.CHARGE, now);
            applySlow(player, st, attack.common().charge().moveSpeedMult());
        } else if (attack.windupTicks() > 0) {
            // Tap: the orb forms for windup_ticks, then the attack fires by itself (see tick). No slowdown: it is short.
            beginSkillState(player, st, slot, id, attack, SkillPhase.WINDUP, now);
            // Stream (barrage): the key stays held, so the slowdown runs for the whole stream (removed in endCharge).
            if (attack.inputMode() == KiAttackParts.InputMode.HOLD_STREAM) {
                applySlow(player, st, attack.common().charge().moveSpeedMult());
            }
        } else {
            cast(player, st, attack, id, slot, 0, look);
        }
        return true;
    }

    /**
     * Ki charged on press: attack cost (base + max_ki_fraction * max ki) x race/nature ki_cost x per-attack mastery tier,
     * and x hitstun_cost_mult when the attack is usable in hitstun and the player was just hit (combo breaker).
     */
    private static float castCost(ServerPlayer player, CombatState st, KiProgression prog, KiAttack attack,
                                  ResourceLocation id, long now) {
        float base = attack.baseCost(StatEffects.maxKi(player));
        float mult = (float) StatEffects.kiCostMult(player) * KiProgression.tierCostMult(prog.attackTier(id));
        if (attack.usableInHitstun() && st.inHitstun(now)) mult *= attack.hitstunCostMult();
        return base * mult;
    }

    /** Starts a skill state (hold-charge or tap wind-up) and the forming orb that goes with it. */
    private static void beginSkillState(ServerPlayer player, CombatState st, int slot, ResourceLocation id, KiAttack attack,
                                        SkillPhase phase, long now) {
        st.skillSlot = slot;
        st.skillAttack = id;
        st.skillTicks = 0;
        st.skillPhase = phase;
        st.skillStartTick = now;
        st.cast = null;
        KiOrbs.start(player, st, attack, id, phase == SkillPhase.WINDUP, now);
        // One-shot charge sound for tap wind-ups (needle_charge) and hold charges that name one (beam). Looping ones: later.
        if ((phase == SkillPhase.WINDUP || attack.charged()) && player.level() instanceof ServerLevel level) {
            attack.common().present().sounds().chargeLoop().ifPresent(sound ->
                    level.playSound(null, player.getX(), player.getEyeY(), player.getZ(),
                            KiAttackParts.Present.soundOf(sound), SoundSource.PLAYERS, 0.7f, 1.0f));
        }
    }

    private static boolean release(ServerPlayer player, CombatState st, int slot, Vec3 look) {
        if (st.skillSlot != slot) return true;               // releasing a key we never started: harmless
        KiAttack held = st.skillAttack == null ? null : KiAttacks.resolve(player, st.skillAttack);
        if (held != null && held.inputMode() == KiAttackParts.InputMode.HOLD_STREAM) {
            // Barrage: the key IS the attack. Letting go during the warm-up cancels it, during the stream it stops it.
            if (st.skillPhase == SkillPhase.WINDUP) cancel(player);
            else if (st.skillPhase == SkillPhase.ACTIVE) endActive(player, st, held, player.level().getGameTime());
            return true;
        }
        if (st.skillPhase != SkillPhase.CHARGE) return true; // a wind-up fires by itself; letting go neither fires nor cancels it
        fireHeld(player, st, look);
        return true;
    }

    /** The slot key was pressed again while the attack is ACTIVE (disc recall ...). */
    private static void repress(ServerPlayer player, CombatState st, Vec3 look) {
        KiAttack attack = KiAttacks.resolve(player, st.skillAttack);
        if (attack == null) return;
        long now = player.level().getGameTime();
        attack.onRepress(context(player, st, attack, st.skillAttack, st.cast == null ? 0 : st.cast.ticks, look, now));
    }

    // ---- per tick ------------------------------------------------------------------------------------------------

    /** Advances the running skill: wind-up ticks, charge cost per tick, ACTIVE ticks, recovery timer; cancels when invalid. */
    public static void tick(ServerPlayer player, CombatState st, CombatResources r) {
        if (st.skillPhase == SkillPhase.NONE) return;
        long now = player.level().getGameTime();
        if (st.skillPhase == SkillPhase.RECOVERY) {
            if (now >= st.skillPhaseEnd) st.skillPhase = SkillPhase.NONE;
            return;
        }
        if (!player.isAlive() || player.isSpectator() || !st.stance || r.kiBreak || st.charging) {
            cancel(player);
            return;
        }
        KiAttack attack = KiAttacks.resolve(player, st.skillAttack);
        if (attack == null) {
            cancel(player);
            return;
        }

        switch (st.skillPhase) {
            case WINDUP -> {
                // Tap attack forming in the hand: fire automatically once the orb is full. Aim = where the player looks NOW.
                int t = ++st.skillTicks;
                Vec3 look = player.getLookAngle();
                attack.tickWindup(context(player, st, attack, st.skillAttack, t, look, now), t);
                if (t >= attack.windupTicks()) fireHeld(player, st, look);
            }
            case CHARGE -> {
                Charge ch = attack.common().charge();
                if (st.skillTicks < ch.maxTicks()) {
                    KiProgression prog = player.getData(ModAttachments.KI_PROGRESSION);
                    float perTick = attack.common().cost().perChargeTick() * (float) StatEffects.kiCostMult(player)
                            * KiProgression.tierCostMult(prog.attackTier(st.skillAttack));
                    if (perTick > 0f && !isFree(player)) {
                        if (r.ki < perTick) { // out of ki mid-charge: fire what we have
                            fireHeld(player, st, player.getLookAngle());
                            return;
                        }
                        spendKi(player, st, r, perTick);
                    }
                    st.skillTicks++;
                }
            }
            case ACTIVE -> {
                if (st.cast == null) st.cast = new ActiveCast();
                st.cast.ticks++;
                boolean goOn = attack.tickActive(context(player, st, attack, st.skillAttack, st.cast.ticks,
                        player.getLookAngle(), now));
                if (!goOn) endActive(player, st, attack, now);
            }
            default -> {
            }
        }
    }

    // ---- firing --------------------------------------------------------------------------------------------------

    /** Fires the running skill state: a hold-charge on release (or when ki runs out), or a tap wind-up when it completes. */
    private static void fireHeld(ServerPlayer player, CombatState st, Vec3 look) {
        KiAttack attack = KiAttacks.resolve(player, st.skillAttack);
        ResourceLocation id = st.skillAttack;
        int slot = st.skillSlot;
        int ticks = st.skillTicks;
        // Attacks with an ACTIVE phase (barrage, homing launch) keep their orb and slowdown until the phase ends.
        if (attack == null || !attack.hasActivePhase()) endCharge(player, st);
        if (attack != null) cast(player, st, attack, id, slot, ticks, look);
        flushMastery(player, st);
    }

    /** Builds the cast context (damage multiplier = Ki Power x race/nature x mastery tier). */
    private static CastContext context(ServerPlayer player, CombatState st, KiAttack attack, ResourceLocation id,
                                       int ticks, Vec3 look, long now) {
        KiProgression prog = player.getData(ModAttachments.KI_PROGRESSION);
        int tier = prog.attackTier(id);
        // Addendum A/B: the Ki Power stat and the race/nature ki_damage channel multiply ki damage.
        float dm = StatEffects.kiDamageMultiplier(player) * KiProgression.tierDamageMult(tier);
        return new CastContext(player, st, attack, id, ticks, look, tier, dm, now);
    }

    /**
     * The moment of firing: the attack's own fire(), then cooldown, per-attack use count (and tier-up notice), and the
     * phase that follows (ACTIVE for attacks that keep going, RECOVERY if input.recovery_ticks > 0). Cost was already paid.
     */
    private static void cast(ServerPlayer player, CombatState st, KiAttack attack, ResourceLocation id, int slot,
                             int ticksHeld, Vec3 look) {
        if (!(player.level() instanceof ServerLevel)) return;
        long now = player.level().getGameTime();
        KiProgression prog = player.getData(ModAttachments.KI_PROGRESSION);

        attack.fire(context(player, st, attack, id, ticksHeld, look, now));

        if (!attack.cooldownAfterActive()) startCooldown(player, st, prog, id, attack.common().cooldown(), now);
        noteUse(player, prog, attack, id);
        Advancements.firstKiBlast(player);

        if (attack.hasActivePhase()) {
            st.skillSlot = slot;
            st.skillAttack = id;
            st.skillTicks = 0;
            st.skillPhase = SkillPhase.ACTIVE;
            st.cast = new ActiveCast();
        } else {
            startRecovery(st, attack, now);
        }
        flushMastery(player, st);
    }

    /** Ends an ACTIVE attack (it finished, ran out of ki, or the stream key was released): orb, slow, cooldown, recovery. */
    private static void endActive(ServerPlayer player, CombatState st, KiAttack attack, long now) {
        ResourceLocation id = st.skillAttack;
        endCharge(player, st);
        if (attack.cooldownAfterActive() && id != null) {
            startCooldown(player, st, player.getData(ModAttachments.KI_PROGRESSION), id, attack.common().cooldown(), now);
        }
        startRecovery(st, attack, now);
        flushMastery(player, st);
    }

    /**
     * Charges {@code base} ki (x race/nature cost x mastery tier) for one shot of an ACTIVE attack (barrage). False = could
     * not afford it (nothing is taken); the attack should end.
     */
    public static boolean spendPerShot(CastContext ctx, float base) {
        if (base <= 0f) return true;
        CombatResources r = ctx.player.getData(ModAttachments.RESOURCES);
        float cost = base * (float) StatEffects.kiCostMult(ctx.player) * KiProgression.tierCostMult(ctx.tier);
        if (!canAfford(ctx.player, r, cost)) return false;
        spendKi(ctx.player, ctx.state, r, cost);
        return true;
    }

    private static void startRecovery(CombatState st, KiAttack attack, long now) {
        st.cast = null;
        int rec = attack.recoveryTicks();
        if (rec > 0) {
            st.skillPhase = SkillPhase.RECOVERY;
            st.skillPhaseEnd = now + rec;
        } else {
            st.skillPhase = SkillPhase.NONE;
        }
    }

    /** Counts one use of the attack; a new tier is announced and synced to the owner (client display comes later). */
    private static void noteUse(ServerPlayer player, KiProgression prog, KiAttack attack, ResourceLocation id) {
        if (!prog.recordAttackUse(id)) return;
        ModNetwork.syncProgression(player);
        player.displayClientMessage(Component.translatable("message.powerlevelling.attack_tier",
                Component.translatable(attack.common().name()), prog.attackTier(id)).withStyle(ChatFormatting.AQUA), true);
    }

    // ---- cooldowns -----------------------------------------------------------------------------------------------

    private static void startCooldown(ServerPlayer player, CombatState st, KiProgression prog, ResourceLocation id,
                                      int cooldownTicks, long now) {
        if (cooldownTicks <= 0) return;
        st.kiCooldowns.put(id, now + cooldownTicks);
        // The cooldown belongs to the attack, so every slot holding it shows it.
        for (int slot = 0; slot < KiProgression.LOADOUT_SLOTS; slot++) {
            if (id.equals(prog.loadout(slot))) ModNetwork.sendCooldown(player, slot, cooldownTicks, cooldownTicks);
        }
    }

    /** Re-sends the cooldown of all 4 slots (after a loadout change). */
    public static void syncCooldowns(ServerPlayer player) {
        CombatState st = player.getData(ModAttachments.COMBAT_STATE);
        KiProgression prog = player.getData(ModAttachments.KI_PROGRESSION);
        long now = player.level().getGameTime();
        for (int slot = 0; slot < KiProgression.LOADOUT_SLOTS; slot++) {
            ResourceLocation id = prog.loadout(slot);
            int remaining = 0;
            int total = 0;
            if (id != null) {
                remaining = (int) Math.max(0L, st.cooldownUntil(id) - now);
                KiAttack attack = KiAttacks.resolve(player, id);
                total = attack == null ? remaining : Math.max(remaining, attack.common().cooldown());
            }
            ModNetwork.sendCooldown(player, slot, remaining, total);
        }
    }

    // ---- ki ------------------------------------------------------------------------------------------------------

    private static boolean isFree(ServerPlayer player) {
        return player.isCreative() && ServerConfig.CREATIVE_FREE_KI.get();
    }

    private static boolean canAfford(ServerPlayer player, CombatResources r, float amount) {
        return isFree(player) || r.ki >= amount;
    }

    /** Takes ki (never below 0) and banks it towards Ki Control mastery XP. */
    private static void spendKi(ServerPlayer player, CombatState st, CombatResources r, float amount) {
        if (amount <= 0f || isFree(player)) return;
        float spent = Math.min(r.ki, amount);
        r.ki -= spent;
        st.kiSpentAccum += spent;
        // M9: when r.ki reaches 0 this is where Ki Break gets triggered.
    }

    /** Turns banked ki into Ki Control XP (1 per 10 ki). One call = at most one progression sync. */
    private static void flushMastery(ServerPlayer player, CombatState st) {
        int xp = (int) (st.kiSpentAccum / KI_PER_MASTERY_XP);
        if (xp <= 0) return;
        st.kiSpentAccum -= xp * KI_PER_MASTERY_XP;
        Progression.addMasteryXp(player, Track.KI_CONTROL, xp);
    }

    // ---- skill state / slowdown ----------------------------------------------------------------------------------

    /** Aborts a running skill without firing (stance off, ki break, death ...). Ki already spent is not refunded. */
    public static void cancel(ServerPlayer player) {
        CombatState st = player.getData(ModAttachments.COMBAT_STATE);
        if (st.skillPhase == SkillPhase.RECOVERY) return; // recovery can't be cancelled (that would be a free reset)
        if (!st.skillCharging() && !st.skillSlowApplied) return;
        if (st.skillPhase == SkillPhase.ACTIVE && st.skillAttack != null) { // no free cooldown reset by cancelling a stream
            KiAttack a = KiAttacks.resolve(player, st.skillAttack);
            if (a != null && a.cooldownAfterActive()) {
                startCooldown(player, st, player.getData(ModAttachments.KI_PROGRESSION), st.skillAttack,
                        a.common().cooldown(), player.level().getGameTime());
            }
        }
        endCharge(player, st);
        flushMastery(player, st);
    }

    /** Back to phase NONE: clears the slot, the orb and the slowdown. */
    private static void endCharge(ServerPlayer player, CombatState st) {
        st.skillSlot = -1;
        st.skillAttack = null;
        st.skillTicks = 0;
        st.skillPhase = SkillPhase.NONE;
        st.cast = null;
        st.skillStartTick = 0;
        KiOrbs.stop(player, st);
        removeSlow(player, st);
    }

    /** Full reset for logout / death / respawn / dimension change (called from KiCharge.resetAll). */
    public static void reset(ServerPlayer player) {
        CombatState st = player.getData(ModAttachments.COMBAT_STATE);
        st.skillSlot = -1;
        st.skillAttack = null;
        st.skillTicks = 0;
        st.skillPhase = SkillPhase.NONE;
        st.skillPhaseEnd = 0L;
        st.cast = null;
        st.skillStartTick = 0;
        st.kiSpentAccum = 0f;
        KiOrbs.stop(player, st);
        removeSlow(player, st);
        // Also strip it unconditionally: covers a modifier saved by a crash or an unclean logout.
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(SKILL_SLOW_ID);
    }

    /** K7: sets the running skill's slowdown to {@code mult} (0 = rooted). Removed with the skill state (endCharge / reset). */
    public static void setSkillSlow(ServerPlayer player, float mult) {
        applySlow(player, player.getData(ModAttachments.COMBAT_STATE), mult);
    }

    private static void applySlow(ServerPlayer player, CombatState st, float mult) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        // ADD_MULTIPLIED_TOTAL with (mult - 1): final speed x mult.
        speed.addOrReplacePermanentModifier(new AttributeModifier(SKILL_SLOW_ID,
                Mth.clamp(mult, 0f, 1f) - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        st.skillSlowApplied = true;
    }

    private static void removeSlow(ServerPlayer player, CombatState st) {
        if (!st.skillSlowApplied) return;
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(SKILL_SLOW_ID);
        st.skillSlowApplied = false;
    }
}

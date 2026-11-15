package com.powerlevelling.event;

import com.powerlevelling.PowerLevelling;
import com.powerlevelling.combat.Guard;
import com.powerlevelling.config.ServerConfig;
import com.powerlevelling.data.CombatResources;
import com.powerlevelling.data.CombatState;
import com.powerlevelling.progression.Advancements;
import com.powerlevelling.progression.MobClass;
import com.powerlevelling.progression.Progression;
import com.powerlevelling.progression.Progression.ExpSource;
import com.powerlevelling.progression.StatEffects;
import com.powerlevelling.progression.Zenkai;
import com.powerlevelling.registry.ModAttachments;
import com.powerlevelling.registry.ModDamageTypes;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/** Kill EXP + Zenkai trigger, and the stat-based damage math (strength out, defense in). */
@EventBusSubscriber(modid = PowerLevelling.MODID)
public final class ProgressionEvents {
    private static final int ANTI_FARM_KILLS = 30;      // kills of one type per window before the penalty
    private static final double ANTI_FARM_MULT = 0.25;

    private ProgressionEvents() {
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) return;
        // Source entity (not direct) so arrows/thrown things credit their owner.
        if (!(event.getSource().getEntity() instanceof ServerPlayer killer)) return;
        if (victim == killer || victim instanceof ArmorStand) return;

        MobClass cls = MobClass.of(victim);
        double exp;
        ExpSource source;
        if (victim instanceof ServerPlayer victimPlayer) {
            // PvP: 20 + 10 * victim level
            exp = 20 + 10.0 * victimPlayer.getData(ModAttachments.KI_PROGRESSION).level;
            source = ExpSource.PVP_KILL;
        } else {
            exp = victim.getMaxHealth() * (1.0 + victim.getArmorValue() / 10.0) * cls.expMultiplier;
            // Anti-farm: more than 30 of the same type in 10 minutes pays x0.25.
            CombatState cs = killer.getData(ModAttachments.COMBAT_STATE);
            int prior = cs.recordKill(victim.getType(), killer.level().getGameTime());
            if (prior >= ANTI_FARM_KILLS) exp *= ANTI_FARM_MULT;
            source = ExpSource.KILL;
        }
        Progression.addExp(killer, (int) Math.max(1, Math.round(exp)), source);

        // Ki kills: first-kill advancement plus the streak counter (reset on the killer's death).
        if (event.getSource().is(ModDamageTypes.KI)) {
            CombatState cs = killer.getData(ModAttachments.COMBAT_STATE);
            cs.kiKillStreak++;
            Advancements.kiKill(killer);
            Advancements.kiKillStreak(killer, cs.kiKillStreak);
        }

        // After the kill EXP so the ZENKAI! title wins over a level-up title.
        Zenkai.tryTrigger(killer, victim, cls);
    }

    /** Strength boosts the attacker's vanilla melee hits; Defense trims whatever a player takes. */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        DamageSource source = event.getSource();
        float amount = event.getAmount();

        // Burst i-frames (K1): damage is cancelled while CombatState.iframesUntil is in the future. Void / /kill still work.
        if (event.getEntity() instanceof ServerPlayer invuln
                && invuln.getData(ModAttachments.COMBAT_STATE).invulnerable(invuln.level().getGameTime())
                && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            event.setCanceled(true);
            return;
        }

        // M5: a stunned entity can't hurt anyone up close (ki attacks exempt so Burst still works as a combo breaker).
        if (source.getEntity() instanceof LivingEntity stunned && source.getDirectEntity() == stunned
                && !source.is(com.powerlevelling.registry.ModDamageTypes.KI)
                && com.powerlevelling.combat.MeleeEngine.isStunned(stunned, stunned.level().getGameTime())) {
            event.setCanceled(true);
            return;
        }

        if (source.getEntity() instanceof ServerPlayer attacker
                && source.getDirectEntity() == attacker
                && source.is(DamageTypes.PLAYER_ATTACK)) {
            amount *= StatEffects.meleeMultiplier(attacker);
        }
        if (event.getEntity() instanceof ServerPlayer victim) {
            amount *= StatEffects.damageTakenMultiplier(victim);
            // Charging ki leaves you exposed (the charge itself is interrupted in PlayerEvents on the actual hit).
            if (victim.getData(ModAttachments.COMBAT_STATE).charging) {
                amount *= ServerConfig.CHARGE_DAMAGE_TAKEN_MULT.get().floatValue();
            }
            // Guard damage reduction
            CombatState vState = victim.getData(ModAttachments.COMBAT_STATE);
            long now = victim.level().getGameTime();
            float guardMult = Guard.getDamageMultiplier(vState, now);
            if (guardMult < 1.0f) {
                amount *= guardMult;
                // Perfect guard: stamina refund + parry effect (already handled in Guard.getDamageMultiplier)
                if (Guard.isPerfectGuard(vState, now)) {
                    // Refund some stamina on perfect guard
                    CombatResources r = victim.getData(ModAttachments.RESOURCES);
                    float maxStamina = StatEffects.maxStamina(victim);
                    r.stamina = Math.min(maxStamina, r.stamina + ServerConfig.GUARD_STAMINA_PER_TICK.get() * 2);
                }
            }
        }
        event.setAmount(amount);
    }
}

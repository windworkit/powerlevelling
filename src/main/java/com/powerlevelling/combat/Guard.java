package com.powerlevelling.combat;

import com.powerlevelling.config.ServerConfig;
import com.powerlevelling.data.CombatResources;
import com.powerlevelling.data.CombatState;
import com.powerlevelling.registry.ModAttachments;
import com.powerlevelling.registry.ModSounds;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;

/**
 * Server-authoritative guard/block mechanic.
 * <p>
 * Pressing the GUARD key starts guarding (if valid); releasing ends it.
 * Guard drains stamina per tick, reduces incoming damage, and has a perfect guard (parry) window.
 * If stamina reaches 0, guard breaks with a brief stun.
 */
public final class Guard {
    private Guard() {
    }

    /**
     * Handles the GUARD input edge (press = start, release = end).
     *
     * @return true if the input was accepted
     */
    public static boolean handle(ServerPlayer player, CombatState st, boolean press) {
        long now = player.level().getGameTime();

        if (press) {
            return tryStartGuard(player, st, now);
        } else {
            endGuard(player, st, now);
            return true; // Release is always accepted
        }
    }

    /** Tries to start guarding. */
    private static boolean tryStartGuard(ServerPlayer player, CombatState st, long now) {
        // Cannot guard while: charging, transforming, dashing, skill-casting, in hitstun
        if (!st.stance) {
            player.displayClientMessage(Component.translatable("message.powerlevelling.guard.no_stance")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (st.charging) {
            player.displayClientMessage(Component.translatable("message.powerlevelling.guard.charging")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (st.transforming) {
            player.displayClientMessage(Component.translatable("message.powerlevelling.guard.transforming")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        // Check if dashing (cooldown active means recently dashed)
        if (now < st.dashCooldownUntil) {
            player.displayClientMessage(Component.translatable("message.powerlevelling.guard.dashing")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (st.skillCharging()) {
            player.displayClientMessage(Component.translatable("message.powerlevelling.guard.skill_active")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (st.inHitstun(now)) {
            player.displayClientMessage(Component.translatable("message.powerlevelling.guard.hitstun")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }

        // Stamina check: need at least 1 stamina to start
        CombatResources r = player.getData(ModAttachments.RESOURCES);
        if (r.stamina <= 0f) {
            player.displayClientMessage(Component.translatable("message.powerlevelling.no_stamina")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }

        // Guard cooldown check
        if (now < st.guardCooldownUntil) {
            return false; // Silently refuse during cooldown
        }

        // Start guarding
        st.isGuarding = true;
        st.guardStartTick = now;
        st.perfectWindowActive = true;

        // Play guard start sound
        player.level().playSound(null, player.blockPosition(), ModSounds.GUARD_START.get(),
                SoundSource.PLAYERS, 0.6f, 1.0f);

        // Visual feedback - small shield particle
        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1.0, player.getZ(),
                    8, 0.3, 0.3, 0.3, 0.02);
        }

        return true;
    }

    /** Ends guarding. */
    public static void endGuard(ServerPlayer player, CombatState st, long now) {
        if (!st.isGuarding) return;

        // Check if we were in perfect window
        boolean wasPerfect = st.perfectWindowActive && (now - st.guardStartTick) <= ServerConfig.GUARD_PERFECT_WINDOW_TICKS.get();

        st.isGuarding = false;
        st.perfectWindowActive = false;
        st.guardStartTick = -1;
        st.guardCooldownUntil = now + ServerConfig.GUARD_COOLDOWN_TICKS.get();

        // If perfect guard window was active, play parry sound
        if (wasPerfect) {
            player.level().playSound(null, player.blockPosition(), ModSounds.GUARD_PERFECT.get(),
                    SoundSource.PLAYERS, 0.8f, 1.2f);
            // Visual parry effect
            if (player.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 1.0, player.getZ(),
                        12, 0.3, 0.3, 0.3, 0.05);
                level.sendParticles(ParticleTypes.FLASH, player.getX(), player.getY() + 1.0, player.getZ(),
                        1, 0.0, 0.0, 0.0, 0.0);
            }
        }
    }

    /**
     * Called every server tick while the player is guarding.
     * Drains stamina, updates perfect window, handles guard break.
     */
    public static void tickGuard(ServerPlayer player, CombatState st, CombatResources r, long now) {
        if (!st.isGuarding) return;

        // Drain stamina
        int staminaPerTick = ServerConfig.GUARD_STAMINA_PER_TICK.get();
        r.stamina = Math.max(0f, r.stamina - staminaPerTick);
        st.lastStaminaSpendTick = now;

        // Check perfect window expiration
        int perfectWindow = ServerConfig.GUARD_PERFECT_WINDOW_TICKS.get();
        if (st.perfectWindowActive && (now - st.guardStartTick) > perfectWindow) {
            st.perfectWindowActive = false;
        }

        // Guard break: stamina depleted
        if (r.stamina <= 0f) {
            breakGuard(player, st, now);
        }
    }

    /** Handles guard break - stuns the player briefly. */
    private static void breakGuard(ServerPlayer player, CombatState st, long now) {
        st.isGuarding = false;
        st.perfectWindowActive = false;
        st.guardStartTick = -1;
        st.guardCooldownUntil = now + ServerConfig.GUARD_COOLDOWN_TICKS.get();
        st.hitstunUntil = Math.max(st.hitstunUntil, now + ServerConfig.GUARD_BREAK_STUN_TICKS.get());

        player.displayClientMessage(Component.translatable("message.powerlevelling.guard.broken")
                .withStyle(ChatFormatting.RED), true);

        player.level().playSound(null, player.blockPosition(), ModSounds.GUARD_BREAK.get(),
                SoundSource.PLAYERS, 0.8f, 0.8f);

        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY() + 1.0, player.getZ(),
                    15, 0.4, 0.4, 0.4, 0.05);
            level.sendParticles(ParticleTypes.EXPLOSION, player.getX(), player.getY() + 1.0, player.getZ(),
                    3, 0.2, 0.2, 0.2, 0.0);
        }
    }

    /**
     * Calculates the damage multiplier when guarding.
     * Returns 0.0 for perfect guard (parry), damageReduction for normal guard.
     *
     * @param st combat state
     * @param now current game time
     * @return damage multiplier (0.0 = no damage, 1.0 = full damage)
     */
    public static float getDamageMultiplier(CombatState st, long now) {
        if (!st.isGuarding) return 1.0f;

        // Perfect guard: zero damage
        if (st.perfectWindowActive && (now - st.guardStartTick) <= ServerConfig.GUARD_PERFECT_WINDOW_TICKS.get()) {
            return 0.0f;
        }

        // Normal guard: apply damage reduction
        return (float) (1.0 - ServerConfig.GUARD_DAMAGE_REDUCTION.get());
    }

    /** Checks if the player is currently in the perfect guard window. */
    public static boolean isPerfectGuard(CombatState st, long now) {
        return st.isGuarding && st.perfectWindowActive
                && (now - st.guardStartTick) <= ServerConfig.GUARD_PERFECT_WINDOW_TICKS.get();
    }
}
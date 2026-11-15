package com.powerlevelling.ki;

import com.powerlevelling.data.CombatState;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Everything an attack needs when it fires, built by {@link KiSkills} (server thread only). The dispatcher has already
 * folded the Ki Power stat, race/nature ki_damage and the per-attack mastery tier into {@link #damageMult}, so attacks just
 * write {@code ctx.damage(baseDamage)}.
 */
public final class CastContext {
    public final ServerPlayer player;
    public final CombatState state;
    public final KiAttack attack;
    public final ResourceLocation id;
    /** Ticks the slot key was held (hold-to-charge), 0 for taps. */
    public final int ticksHeld;
    /** Aim, normalised. */
    public final Vec3 look;
    /** Per-attack mastery tier 0..4. */
    public final int tier;
    /** kiDamageMultiplier(player) * tier damage bonus. */
    public final float damageMult;
    public final long now;

    public CastContext(ServerPlayer player, CombatState state, KiAttack attack, ResourceLocation id, int ticksHeld,
                       Vec3 look, int tier, float damageMult, long now) {
        this.player = player;
        this.state = state;
        this.attack = attack;
        this.id = id;
        this.ticksHeld = ticksHeld;
        Vec3 l = look == null || look.lengthSqr() < 1.0E-6 ? player.getLookAngle() : look.normalize();
        this.look = l;
        this.tier = tier;
        this.damageMult = damageMult;
        this.now = now;
    }

    /** Same context, other aim (e.g. wind-up ticks aim where the player looks now). */
    public CastContext withLook(Vec3 newLook) {
        return new CastContext(player, state, attack, id, ticksHeld, newLook, tier, damageMult, now);
    }

    public ServerLevel level() {
        return (ServerLevel) player.level();
    }

    public float damage(float base) {
        return base * damageMult;
    }

    public float chargeProgress() {
        return attack.chargeProgress(ticksHeld);
    }
}

package com.powerlevelling.ki;

import com.powerlevelling.registry.ModDamageTypes;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * Shared server-side hit helpers for the non-projectile ki attacks (needle, burst, later disc / beam): who may be hit, the
 * ki damage call and a knockback that can push up. Server thread only.
 */
public final class KiHits {
    private KiHits() {
    }

    /** Alive, not the owner, not a spectator, not a pet of the owner. */
    public static boolean canTarget(LivingEntity owner, LivingEntity e) {
        if (e == owner || !e.isAlive() || e.isSpectator()) return false;
        return !(e instanceof TamableAnimal pet && pet.isOwnedBy(owner));
    }

    /** ki damage from {@code direct} (credited to {@code owner}); clears the invulnerability window first so every hit lands. */
    public static boolean hurt(ServerLevel sl, Entity direct, Entity owner, Entity target, float amount) {
        DamageSource src = sl.damageSources().source(ModDamageTypes.KI, direct, owner);
        target.invulnerableTime = 0;
        return target.hurt(src, amount);
    }

    /**
     * Pushes {@code target} along the horizontal direction (dx, dz) with {@code horizontal} strength and at least {@code up}
     * vertical speed, scaled by the target's knockback resistance. Zero horizontal vector = straight up only.
     */
    public static void push(LivingEntity target, double dx, double dz, float horizontal, float up) {
        double res = Math.max(0.0, 1.0 - target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
        if (res <= 0.0) return;
        Vec3 h = new Vec3(dx, 0, dz);
        h = h.lengthSqr() < 1.0E-6 ? Vec3.ZERO : h.normalize();
        Vec3 v = target.getDeltaMovement();
        target.setDeltaMovement(v.x * 0.5 + h.x * horizontal * res, Math.max(v.y, up * res), v.z * 0.5 + h.z * horizontal * res);
        target.hasImpulse = true;
        target.hurtMarked = true; // make sure players receive the new velocity
    }
}

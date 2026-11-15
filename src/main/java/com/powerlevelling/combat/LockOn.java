package com.powerlevelling.combat;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.powerlevelling.config.ServerConfig;
import com.powerlevelling.data.CombatState;
import com.powerlevelling.network.ModNetwork;
import com.powerlevelling.network.VisualStatePayload;
import com.powerlevelling.registry.ModAttachments;
import com.powerlevelling.registry.ModSounds;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Lock-on targeting (MT-002): press LOCK_ON to acquire the nearest valid target within config range
 * and inside a forward cone of the player's view direction. Hold-to-lock: lock persists while key
 * is held, with a short grace period so tapping works.
 *
 * <p>Server-authoritative: client sends the key press; server validates and picks the target;
 * never trust client target picks. Lock clears when: key released (after grace), target dies,
 * target leaves range+margin, or player logs out.
 */
public final class LockOn {
    private LockOn() {
    }

    /**
     * Handles the LOCK_ON input edge (press = acquire, release = start grace countdown).
     *
     * @return true if the input was accepted
     */
    public static boolean handle(ServerPlayer player, CombatState st, boolean press) {
        long now = player.level().getGameTime();

        if (!ServerConfig.LOCKON_ENABLED.get()) {
            return false;
        }

        if (press) {
            return tryAcquireLock(player, st, now);
        } else {
            // Release: start grace countdown (lock held until grace expires)
            st.lockUntil = Math.max(st.lockUntil, now + ServerConfig.LOCKON_RELEASE_GRACE_TICKS.get());
            return true;
        }
    }

    /** Tries to acquire a lock-on target. */
    private static boolean tryAcquireLock(ServerPlayer player, CombatState st, long now) {
        if (!player.isAlive() || player.isSpectator()) {
            return false;
        }
        if (!st.stance) {
            return false;
        }
        if (st.charging || st.transforming || st.inHitstun(now)) {
            return false;
        }
        if (st.skillCharging()) {
            return false;
        }

        double range = ServerConfig.LOCKON_RANGE.get();
        double coneAngle = ServerConfig.LOCKON_CONE_ANGLE.get();
        boolean requireLos = ServerConfig.LOCKON_REQUIRE_LINE_OF_SIGHT.get();

        Optional<LivingEntity> targetOpt = findNearestValidTarget(player, range, coneAngle, requireLos);
        if (targetOpt.isEmpty()) {
            return false;
        }

        LivingEntity target = targetOpt.get();
        st.lockTargetId = target.getId();
        st.lockUntil = now + ServerConfig.LOCKON_RELEASE_GRACE_TICKS.get(); // Initial grace
        st.lockHeld = true;

        // Play lock-on sound
        player.level().playSound(null, player.blockPosition(), ModSounds.LOCKON_ACQUIRE.get(),
                SoundSource.PLAYERS, 0.7f, 1.0f);

        // Visual feedback: ring at target's feet
        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, target.getX(), target.getY() + 0.1, target.getZ(),
                    12, 0.3, 0.2, 0.3, 0.02);
        }

        // Sync visual state to client (LOCKED flag)
        ModNetwork.syncVisual(player);

        return true;
    }

    /**
     * Finds the nearest valid target within range and cone angle.
     * Valid target: living entity, not the player, not dead, attackable, within range, inside cone,
     * optionally line of sight.
     */
    private static Optional<LivingEntity> findNearestValidTarget(ServerPlayer player, double range,
            double coneAngle, boolean requireLos) {
        Vec3 playerPos = player.getPosition(1.0f);
        Vec3 lookDir = player.getLookAngle();

        // Bounding box for search
        AABB searchBox = player.getBoundingBox().inflate(range);
        List<LivingEntity> candidates = player.level().getEntitiesOfClass(LivingEntity.class, searchBox,
                e -> isValidTarget(player, e, playerPos, lookDir, range, coneAngle, requireLos));

        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        // Sort by distance and return nearest
        return candidates.stream()
                .min(Comparator.comparingDouble(e -> e.distanceToSqr(player)));
    }

    /** Checks if an entity is a valid lock-on target. */
    private static boolean isValidTarget(ServerPlayer player, LivingEntity entity, Vec3 playerPos,
            Vec3 lookDir, double range, double coneAngle, boolean requireLos) {
        // Not the player themselves
        if (entity == player) {
            return false;
        }
        // Must be alive
        if (!entity.isAlive()) {
            return false;
        }
        // Must be attackable (not in creative/spectator)
        if (!entity.isAttackable()) {
            return false;
        }
        // Within range
        double dist = entity.distanceTo(player);
        if (dist > range) {
            return false;
        }
        // Inside cone angle
        Vec3 toEntity = entity.getPosition(1.0f).subtract(playerPos).normalize();
        double dot = lookDir.dot(toEntity);
        double cosAngle = Math.cos(Math.toRadians(coneAngle / 2.0));
        if (dot < cosAngle) {
            return false;
        }
        // Line of sight (optional)
        ServerLevel sl = (ServerLevel) player.level();
        if (requireLos && !hasLineOfSight(sl, player.getPosition(1.0f), entity)) {
            return false;
        }
        return true;
    }

    /** Raycast line‑of‑sight check similar to KiBlastEntity. */
    private static boolean hasLineOfSight(ServerLevel sl, Vec3 from, Entity to) {
        Vec3 end = to.getBoundingBox().getCenter();
        return sl.clip(new ClipContext(from, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, to))
                .getType() == HitResult.Type.MISS;
    }

    /**
     * Called every server tick to validate and maintain the lock.
     * Clears lock if: grace expired, target dead/gone, target out of range+margin.
     */
    public static void tickLock(ServerPlayer player, CombatState st, long now) {
        if (st.lockTargetId <= 0) {
            return;
        }

        // Check grace expiration
        if (now >= st.lockUntil) {
            clearLock(player, st, LockClearReason.GRACE_EXPIRED);
            return;
        }

        // Find target entity
        Entity target = player.level().getEntity(st.lockTargetId);
        if (target == null || !(target instanceof LivingEntity livingTarget)) {
            clearLock(player, st, LockClearReason.TARGET_GONE);
            return;
        }

        if (!livingTarget.isAlive()) {
            clearLock(player, st, LockClearReason.TARGET_DEAD);
            return;
        }

        // Check range with small margin (so lock doesn't flicker at edge)
        double range = ServerConfig.LOCKON_RANGE.get();
        double margin = 2.0; // blocks
        if (livingTarget.distanceTo(player) > range + margin) {
            clearLock(player, st, LockClearReason.TARGET_OUT_OF_RANGE);
            return;
        }

        // Lock is still valid - extend grace slightly to maintain while holding
        st.lockUntil = Math.max(st.lockUntil, now + ServerConfig.LOCKON_RELEASE_GRACE_TICKS.get());
    }

    /** Clears the lock-on target. */
    private static void clearLock(ServerPlayer player, CombatState st, LockClearReason reason) {
        if (st.lockTargetId <= 0) {
            return;
        }

        int oldTargetId = st.lockTargetId;
        st.lockTargetId = 0;
        st.lockUntil = Long.MIN_VALUE / 2;
        st.lockHeld = false;

        // Play lock break sound
        player.level().playSound(null, player.blockPosition(), ModSounds.LOCKON_BREAK.get(),
                SoundSource.PLAYERS, 0.5f, 0.8f);

        // Sync visual state (remove LOCKED flag)
        ModNetwork.syncVisual(player);
    }

    /** Reason for lock clear (for debugging/logging). */
    private enum LockClearReason {
        GRACE_EXPIRED,
        TARGET_GONE,
        TARGET_DEAD,
        TARGET_OUT_OF_RANGE
    }

    /** Checks if the player currently has an active lock-on target. */
    public static boolean isLocked(CombatState st, long now) {
        return st.lockTargetId > 0 && now < st.lockUntil && st.lockHeld;
    }

    /** Gets the locked-on target entity ID (0 if none). */
    public static int getLockTargetId(CombatState st) {
        return st.lockTargetId;
    }
}

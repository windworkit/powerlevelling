package com.powerlevelling.ki;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.powerlevelling.PowerLevelling;
import com.powerlevelling.data.CombatState.ActiveCast;
import com.powerlevelling.ki.KiAttackParts.Common;
import com.powerlevelling.ki.KiAttackParts.FloatRange;
import com.powerlevelling.network.OrbStatePayload;

import net.minecraft.resources.ResourceLocation;
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
 * powerlevelling:homing - tap: {@code count} mini orbs circle the hand while it gathers (windup), then they are launched
 * {@code launch_interval} ticks apart in a fan ({@code fan_deg} between neighbours). The target is the living entity
 * nearest the crosshair inside a {@code cone_deg} cone with a clear line (picked when the first seeker leaves; seekers fall
 * back to their own retargeting every 5 ticks, see KiBlastEntity.setHoming). Each seeker flies straight for
 * {@code steer_delay} ticks (so the fan is visible), then turns at most {@code turn_deg} per tick for {@code homing_ticks}.
 */
public record HomingAttack(Common common, Homing homing) implements KiAttack {
    public static final ResourceLocation TYPE = ResourceLocation.fromNamespaceAndPath(PowerLevelling.MODID, "homing");

    public record Homing(int count, int launchInterval, float fanDeg, float turnDeg, int homingTicks, int steerDelay,
                         float coneDeg, float seekRange, float capWeight) {
        public static final Homing DEFAULT = new Homing(5, 3, 14f, 6f, 40, 4, 20f, 32f, 0.6f);
        public static final Codec<Homing> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(1, 12).optionalFieldOf("count", 5).forGetter(Homing::count),
                Codec.intRange(1, 20).optionalFieldOf("launch_interval", 3).forGetter(Homing::launchInterval),
                Codec.floatRange(0f, 45f).optionalFieldOf("fan_deg", 14f).forGetter(Homing::fanDeg),
                Codec.floatRange(0.5f, 45f).optionalFieldOf("turn_deg", 6f).forGetter(Homing::turnDeg),
                Codec.intRange(1, 400).optionalFieldOf("homing_ticks", 40).forGetter(Homing::homingTicks),
                Codec.intRange(0, 40).optionalFieldOf("steer_delay", 4).forGetter(Homing::steerDelay),
                Codec.floatRange(1f, 90f).optionalFieldOf("cone_deg", 20f).forGetter(Homing::coneDeg),
                Codec.floatRange(4f, 128f).optionalFieldOf("seek_range", 32f).forGetter(Homing::seekRange),
                Codec.floatRange(0.05f, 4f).optionalFieldOf("cap_weight", 0.6f).forGetter(Homing::capWeight)
        ).apply(i, Homing::new));
    }

    public static final MapCodec<HomingAttack> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Common.REQUIRING_PROJECTILE.forGetter(HomingAttack::common),
            Homing.CODEC.optionalFieldOf("homing", Homing.DEFAULT).forGetter(HomingAttack::homing)
    ).apply(i, HomingAttack::new));

    @Override
    public ResourceLocation typeId() {
        return TYPE;
    }

    /** The gather: mini orbs circle the hand while they form. */
    @Override
    public int defaultWindupTicks() {
        return 12;
    }

    @Override
    public FloatRange orbRadiusRange() {
        return FloatRange.of(0.16f);
    }

    @Override
    public boolean hasActivePhase() {
        return true;
    }

    /** The launches happen in tickActive (the dispatcher creates the ActiveCast after fire). */
    @Override
    public void fire(CastContext ctx) {
    }

    @Override
    public boolean tickActive(CastContext ctx) {
        ActiveCast c = ctx.state.cast;
        if (c == null || c.shots >= homing.count()) return false;
        if (ctx.now < c.nextShotTick) return true;

        // c.homing[0] = the locked target; looked up again if it died.
        LivingEntity target = null;
        if (!c.homing.isEmpty() && ctx.level().getEntity(c.homing.get(0)) instanceof LivingEntity le && le.isAlive()) target = le;
        if (target == null) {
            c.homing.clear();
            target = pickTarget(ctx);
            if (target != null) c.homing.add(target.getUUID());
        }
        launch(ctx, c.shots, target);
        c.shots++;
        c.nextShotTick = ctx.now + homing.launchInterval();
        return c.shots < homing.count();
    }

    private void launch(CastContext ctx, int index, LivingEntity target) {
        ServerPlayer player = ctx.player;
        ServerLevel level = ctx.level();
        float k = index - (homing.count() - 1) / 2f;
        double yaw = Math.toRadians(k * homing.fanDeg());
        Vec3 dir = ctx.look.yRot((float) -yaw);
        dir = dir.add(0, (index % 2 == 0 ? 0.05 : -0.02) + 0.01 * Math.abs(k), 0).normalize();

        ProjectileSpawner.Spec spec = ProjectileSpawner.specFor(ctx, Float.NaN, 0f, 0f, 0f);
        Vec3 origin = KiOrbs.orbWorldPos(player, ctx.look, OrbStatePayload.HAND_MAIN, OrbStatePayload.ANCHOR_PALM, spec.radius());
        // A little scatter around the palm so the seekers don't leave from one point.
        origin = origin.add((level.random.nextDouble() - 0.5) * 0.18, (level.random.nextDouble() - 0.5) * 0.18,
                (level.random.nextDouble() - 0.5) * 0.18);
        ProjectileSpawner.spawnAt(ctx, origin, dir, spec,
                b -> b.setHoming(target, homing.turnDeg(), homing.homingTicks(), homing.steerDelay(), homing.seekRange(), 70f,
                        homing.capWeight()));
        level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), common.present().fireSoundEvent(),
                SoundSource.PLAYERS, 0.5f, 1.2f + index * 0.08f);
    }

    /** Living entity closest to the crosshair inside cone_deg, in range, with a clear line from the eye. Null if none. */
    private LivingEntity pickTarget(CastContext ctx) {
        ServerPlayer player = ctx.player;
        ServerLevel level = ctx.level();
        Vec3 eye = player.getEyePosition();
        Vec3 look = ctx.look;
        double cosCone = Math.cos(Math.toRadians(homing.coneDeg()));
        AABB area = player.getBoundingBox().expandTowards(look.scale(homing.seekRange())).inflate(homing.seekRange() * 0.5);
        LivingEntity best = null;
        double bestScore = -10.0;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, area, c -> KiHits.canTarget(player, c))) {
            Vec3 d = e.getBoundingBox().getCenter().subtract(eye);
            double dist = d.length();
            if (dist < 0.5 || dist > homing.seekRange()) continue;
            double cos = look.dot(d.scale(1.0 / dist));
            if (cos < cosCone) continue;
            if (level.clip(new ClipContext(eye, e.getBoundingBox().getCenter(), ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, player)).getType() != HitResult.Type.MISS) continue;
            double score = cos - dist * 0.003;
            if (score > bestScore) {
                bestScore = score;
                best = e;
            }
        }
        return best;
    }
}

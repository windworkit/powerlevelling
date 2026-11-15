package com.powerlevelling.ki;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.powerlevelling.PowerLevelling;
import com.powerlevelling.entity.KiBlastEntity;
import com.powerlevelling.ki.KiAttackParts.Common;
import com.powerlevelling.ki.KiAttackParts.FloatRange;
import com.powerlevelling.network.KiImpactPayload;
import com.powerlevelling.network.ModNetwork;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * powerlevelling:burst - the radial shockwave (C.1). After a 4-tick wind-up (the whole body flares, no hand orb) everything
 * within {@code radius} takes damage and is pushed outward (common "knockback") and up ({@code knockback_up}); enemy ki
 * blasts inside the radius are destroyed (own blasts are not); the caster gets {@code iframes} ticks of invulnerability.
 * Usable as a combo breaker while being hit at {@code hitstun_cost_mult} x ki (see CombatState.inHitstun).
 */
public record BurstAttack(Common common, Burst burst) implements KiAttack {
    public static final ResourceLocation TYPE = ResourceLocation.fromNamespaceAndPath(PowerLevelling.MODID, "burst");

    /** The wind-up flare is drawn as a body-sized glow of about this radius. */
    private static final float FLARE_RADIUS = 0.9f;

    public record Burst(float radius, int expandTicks, int iframes, boolean destroyProjectiles, float knockbackUp,
                        boolean usableInHitstun, float hitstunCostMult) {
        public static final Burst DEFAULT = new Burst(5f, 6, 6, true, 0.35f, true, 2f);
        public static final Codec<Burst> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.floatRange(0.5f, 32f).optionalFieldOf("radius", 5f).forGetter(Burst::radius),
                Codec.intRange(1, 60).optionalFieldOf("expand_ticks", 6).forGetter(Burst::expandTicks),
                Codec.intRange(0, 60).optionalFieldOf("iframes", 6).forGetter(Burst::iframes),
                Codec.BOOL.optionalFieldOf("destroy_projectiles", true).forGetter(Burst::destroyProjectiles),
                Codec.floatRange(0f, 5f).optionalFieldOf("knockback_up", 0.35f).forGetter(Burst::knockbackUp),
                Codec.BOOL.optionalFieldOf("usable_in_hitstun", true).forGetter(Burst::usableInHitstun),
                Codec.floatRange(1f, 10f).optionalFieldOf("hitstun_cost_mult", 2f).forGetter(Burst::hitstunCostMult)
        ).apply(i, Burst::new));
    }

    public static final MapCodec<BurstAttack> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Common.MAP_CODEC.forGetter(BurstAttack::common),
            Burst.CODEC.optionalFieldOf("burst", Burst.DEFAULT).forGetter(BurstAttack::burst)
    ).apply(i, BurstAttack::new));

    @Override
    public ResourceLocation typeId() {
        return TYPE;
    }

    @Override
    public int defaultWindupTicks() {
        return 4;
    }

    @Override
    public FloatRange orbRadiusRange() {
        return FloatRange.of(FLARE_RADIUS);
    }

    @Override
    public boolean usableInHitstun() {
        return burst.usableInHitstun();
    }

    @Override
    public float hitstunCostMult() {
        return burst.hitstunCostMult();
    }

    @Override
    public void fire(CastContext ctx) {
        ServerPlayer player = ctx.player;
        ServerLevel level = ctx.level();
        Vec3 center = player.position().add(0, player.getBbHeight() * 0.5, 0);
        float r = burst.radius();
        AABB box = new AABB(center, center).inflate(r);

        // i-frames. invulnerableTime alone is not enough (vanilla only ignores weaker hits), so CombatState.iframesUntil is
        // checked in ProgressionEvents.onIncomingDamage, which cancels the damage.
        if (burst.iframes() > 0) {
            ctx.state.iframesUntil = ctx.now + burst.iframes();
            player.invulnerableTime = Math.max(player.invulnerableTime, 10 + burst.iframes());
        }

        if (burst.destroyProjectiles()) {
            double r2 = (double) r * r;
            for (KiBlastEntity blast : level.getEntitiesOfClass(KiBlastEntity.class, box,
                    other -> !player.getUUID().equals(other.getShooterUuid()) && other.position().distanceToSqr(center) <= r2)) {
                blast.discard();
            }
        }

        float damage = ctx.damage(common.damage().base());
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, cand -> KiHits.canTarget(player, cand))) {
            if (Math.sqrt(e.getBoundingBox().distanceToSqr(center)) > r) continue;
            Vec3 away = e.getBoundingBox().getCenter().subtract(center);
            if (KiHits.hurt(level, player, player, e, damage)) {
                KiHits.push(e, away.x, away.z, common.knockback(), burst.knockbackUp());
            }
        }

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                common.present().fireSoundEvent(), SoundSource.PLAYERS, 1.2f, 1.0f);
        // Clients draw the ring + dome and shake the camera from this (size = radius).
        ModNetwork.sendImpact(player, player.position().add(0, 0.1, 0), ctx.id, r, false,
                KiOrbs.playerKiColor(player, this), KiImpactPayload.STYLE_RING);
    }
}

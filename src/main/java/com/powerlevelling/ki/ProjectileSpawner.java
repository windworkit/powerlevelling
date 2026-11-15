package com.powerlevelling.ki;

import com.powerlevelling.config.ServerConfig;
import com.powerlevelling.entity.KiBlastEntity;
import com.powerlevelling.ki.KiAttackParts.Common;
import com.powerlevelling.registry.ModEntities;

import java.util.function.Consumer;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Spawns {@link KiBlastEntity}s for the projectile archetypes (blast, sphere; later barrage, homing, scatter). All the
 * "where does the blast appear, what colour, which sound, cap bookkeeping" work lives here so the attack types only decide
 * the numbers. Server thread only.
 */
public final class ProjectileSpawner {
    private ProjectileSpawner() {
    }

    /**
     * The numbers of one blast. {@code radialKb} > 0 switches the explosion to radial knockback (outward + {@code radialKbUp}
     * up, scaled by distance falloff) instead of the plain 0.6 x knockback push.
     */
    public record Spec(float progress, float radius, float speed, float damage, float knockback, float range, int lifetime,
                       float explosionRadius, boolean breakBlocks, float radialKb, float radialKbUp) {
    }

    /**
     * Standard numbers from the attack's common block at the cast's charge level: radius = projectile.radius.lerp(progress),
     * damage = base * charge_mult.lerp(progress) * ctx.damageMult. {@code speed} NaN = projectile.speed.
     */
    public static Spec specFor(CastContext ctx, float speed, float explosionRadius, float radialKb, float radialKbUp) {
        Common c = ctx.attack.common();
        float progress = ctx.chargeProgress();
        float chargeMult = c.damage().chargeMult().lerp(progress);
        float radius = c.projectile().radius().lerp(progress);
        float damage = ctx.damage(c.damage().base() * chargeMult);
        boolean breakBlocks = c.blockDamage() && ServerConfig.KI_BLOCK_DAMAGE.get();
        float sp = Float.isNaN(speed) ? c.projectile().speed() : speed;
        return new Spec(progress, radius, sp, damage, c.knockback(), c.projectile().range(), c.projectile().lifetime(),
                explosionRadius, breakBlocks, radialKb, radialKbUp);
    }

    /** Spawns one blast at the orb position (where the forming orb was) flying along {@code dir}. */
    public static KiBlastEntity spawn(CastContext ctx, Vec3 dir, Spec s) {
        return spawn(ctx, dir, s, null);
    }

    /** {@link #spawn(CastContext, Vec3, Spec)} with a hook that runs on the blast before it is registered / added. */
    public static KiBlastEntity spawn(CastContext ctx, Vec3 dir, Spec s, Consumer<KiBlastEntity> pre) {
        var orb = ctx.attack.common().present().orb();
        // The blast appears where the forming orb was (same radius too: KiOrb.radius == radius.lerp(progress) once charged).
        Vec3 orbPos = KiOrbs.orbWorldPos(ctx.player, dir, orb.handId(), orb.anchorId(), s.radius());
        return spawnAt(ctx, orbPos, dir, s, pre);
    }

    /** Uniformly random direction inside a cone of {@code degrees} half-angle around {@code dir}. */
    public static Vec3 jitter(Vec3 dir, float degrees, RandomSource rand) {
        Vec3 d = dir.normalize();
        if (degrees <= 0f) return d;
        Vec3 u = d.cross(new Vec3(0, 1, 0));
        if (u.lengthSqr() < 1.0E-6) u = new Vec3(1, 0, 0);
        u = u.normalize();
        Vec3 v = d.cross(u);
        double a = Math.toRadians(degrees) * Math.sqrt(rand.nextDouble());
        double phi = rand.nextDouble() * Math.PI * 2.0;
        return d.scale(Math.cos(a)).add(u.scale(Math.cos(phi) * Math.sin(a))).add(v.scale(Math.sin(phi) * Math.sin(a))).normalize();
    }

    /** Spawns one blast centred on {@code orbPos}. {@code pre} (nullable) configures the blast before it is registered. */
    public static KiBlastEntity spawnAt(CastContext ctx, Vec3 orbPos, Vec3 dir, Spec s, Consumer<KiBlastEntity> pre) {
        ServerPlayer player = ctx.player;
        ServerLevel level = ctx.level();
        KiAttack attack = ctx.attack;

        KiBlastEntity blast = new KiBlastEntity(ModEntities.KI_BLAST.get(), level);
        blast.setup(ctx.id, player, s.progress(), s.radius(), s.damage(), s.knockback(), s.range(), s.lifetime(),
                s.explosionRadius(), s.breakBlocks());
        blast.setRadialKnockback(s.radialKb(), s.radialKbUp());
        blast.setImpactStyle(attack.common().present().impactStyle());
        int kiColor = KiOrbs.playerKiColor(player, attack);
        if (kiColor >= 0) blast.setKiColor(kiColor, KiOrbs.coreOf(kiColor));
        blast.setPos(orbPos.x, orbPos.y - blast.getBbHeight() / 2.0, orbPos.z); // entity pos = bottom of its box, model is centred
        blast.setDeltaMovement(dir.scale(s.speed()));
        blast.setYRot(player.getYRot());
        blast.setXRot(player.getXRot());
        if (pre != null) pre.accept(blast);

        KiBlastManager.register(level, player.getUUID(), blast);
        level.addFreshEntity(blast);
        return blast;
    }

    /** Fire sound (server broadcast, so everyone nearby hears it, including the shooter). Lower pitch for bigger charges. */
    public static void playFireSound(CastContext ctx, float progress) {
        ServerLevel level = ctx.level();
        ServerPlayer player = ctx.player;
        float pitch = 1.5f - 0.5f * progress + (level.random.nextFloat() - 0.5f) * 0.1f;
        level.playSound(null, player.getX(), player.getEyeY(), player.getZ(),
                ctx.attack.common().present().fireSoundEvent(), SoundSource.PLAYERS, 0.8f, pitch);
    }
}

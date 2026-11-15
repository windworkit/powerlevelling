package com.powerlevelling.ki;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.powerlevelling.PowerLevelling;
import com.powerlevelling.ki.KiAttackParts.Common;
import com.powerlevelling.ki.KiAttackParts.Ramp;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * powerlevelling:bomb - hold to charge power, release to lob a ki grenade: it flies a gravity arc (speed = bomb.speed over
 * the charge), bounces once, blinks faster and faster and explodes when the fuse ({@code fuse} ticks) runs out, or at once
 * if it hits something alive. The explosion (radius {@code explosion_radius}, damage falling to {@code edge_factor} at the
 * edge, radial knock-up) reuses the sphere explosion fx; real terrain damage needs "block_damage": true in the JSON AND the
 * server config kiBlockDamage.
 */
public record BombAttack(Common common, Bomb bomb) implements KiAttack {
    public static final ResourceLocation TYPE = ResourceLocation.fromNamespaceAndPath(PowerLevelling.MODID, "bomb");

    public record Bomb(float gravity, Ramp speed, int fuse, int bounces, float explosionRadius, float edgeFactor,
                       float radialKnockback, float knockUp) {
        public static final Bomb DEFAULT = new Bomb(0.05f, new Ramp(0.6f, 1.6f), 40, 1, 4.5f, 0.5f, 0.7f, 0.8f);
        public static final Codec<Bomb> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.floatRange(0f, 0.5f).optionalFieldOf("gravity", 0.05f).forGetter(Bomb::gravity),
                Ramp.CODEC.optionalFieldOf("speed", new Ramp(0.6f, 1.6f)).forGetter(Bomb::speed),
                Codec.intRange(5, 400).optionalFieldOf("fuse", 40).forGetter(Bomb::fuse),
                Codec.intRange(0, 4).optionalFieldOf("bounces", 1).forGetter(Bomb::bounces),
                Codec.floatRange(0.5f, 32f).optionalFieldOf("explosion_radius", 4.5f).forGetter(Bomb::explosionRadius),
                Codec.floatRange(0f, 1f).optionalFieldOf("edge_factor", 0.5f).forGetter(Bomb::edgeFactor),
                Codec.floatRange(0f, 10f).optionalFieldOf("radial_knockback", 0.7f).forGetter(Bomb::radialKnockback),
                Codec.floatRange(0f, 5f).optionalFieldOf("knock_up", 0.8f).forGetter(Bomb::knockUp)
        ).apply(i, Bomb::new));
    }

    public static final MapCodec<BombAttack> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Common.REQUIRING_PROJECTILE.forGetter(BombAttack::common),
            Bomb.CODEC.optionalFieldOf("bomb", Bomb.DEFAULT).forGetter(BombAttack::bomb)
    ).apply(i, BombAttack::new));

    @Override
    public ResourceLocation typeId() {
        return TYPE;
    }

    @Override
    public KiAttackParts.InputMode defaultInputMode() {
        return KiAttackParts.InputMode.HOLD_CHARGE;
    }

    @Override
    public float explosionRadius() {
        return bomb.explosionRadius();
    }

    @Override
    public void fire(CastContext ctx) {
        float p = ctx.chargeProgress();
        ProjectileSpawner.Spec spec = ProjectileSpawner.specFor(ctx, bomb.speed().at(p), bomb.explosionRadius(),
                bomb.radialKnockback(), bomb.knockUp());
        Vec3 dir = ctx.look.add(0, 0.22, 0).normalize(); // underhand lob: a bit above the crosshair
        ProjectileSpawner.spawn(ctx, dir, spec, b -> b.setBomb(bomb.gravity(), bomb.fuse(), bomb.bounces(), bomb.edgeFactor()));
        ProjectileSpawner.playFireSound(ctx, p);
    }
}

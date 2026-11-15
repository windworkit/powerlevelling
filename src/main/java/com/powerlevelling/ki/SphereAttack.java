package com.powerlevelling.ki;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.powerlevelling.PowerLevelling;
import com.powerlevelling.ki.KiAttackParts.Common;
import com.powerlevelling.ki.KiAttackParts.Explosion;
import com.powerlevelling.ki.KiAttackParts.Ramp;

import net.minecraft.resources.ResourceLocation;

/**
 * powerlevelling:sphere - hold to charge an orb (overhead, between both hands), release to throw it; it detonates with a
 * radial explosion. The v1 type id powerlevelling:charged_blast is registered as an alias of this type (same codec), and
 * its top-level "explosion" block is still read.
 * <p>
 * "sphere" block (all optional): speed [from, to] over the charge (a big sphere is slower), explosion_radius [from, to]
 * over the charge, radial_knockback (outward push at the centre, falls off to the edge) and radial_knockback_up.
 * Without these the sphere behaves exactly like the old charged_blast.
 */
public record SphereAttack(Common common, Optional<Explosion> explosion, Sphere sphere) implements KiAttack {
    public static final ResourceLocation TYPE = ResourceLocation.fromNamespaceAndPath(PowerLevelling.MODID, "sphere");
    /** v1 name, still decodes (and is what charged_blast.json says). */
    public static final ResourceLocation ALIAS_CHARGED_BLAST =
            ResourceLocation.fromNamespaceAndPath(PowerLevelling.MODID, "charged_blast");

    public record Sphere(Optional<Ramp> speed, Optional<Ramp> explosionRadius, float radialKnockback, float radialKnockbackUp) {
        public static final Sphere DEFAULT = new Sphere(Optional.empty(), Optional.empty(), 0f, 0f);
        public static final Codec<Sphere> CODEC = RecordCodecBuilder.create(i -> i.group(
                Ramp.CODEC.optionalFieldOf("speed").forGetter(Sphere::speed),
                Ramp.CODEC.optionalFieldOf("explosion_radius").forGetter(Sphere::explosionRadius),
                Codec.floatRange(0f, 10f).optionalFieldOf("radial_knockback", 0f).forGetter(Sphere::radialKnockback),
                Codec.floatRange(0f, 5f).optionalFieldOf("radial_knockback_up", 0f).forGetter(Sphere::radialKnockbackUp)
        ).apply(i, Sphere::new));
    }

    public static final MapCodec<SphereAttack> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Common.REQUIRING_PROJECTILE.forGetter(SphereAttack::common),
            Explosion.CODEC.optionalFieldOf("explosion").forGetter(SphereAttack::explosion),
            Sphere.CODEC.optionalFieldOf("sphere", Sphere.DEFAULT).forGetter(SphereAttack::sphere)
    ).apply(i, SphereAttack::new));

    @Override
    public ResourceLocation typeId() {
        return TYPE;
    }

    @Override
    public KiAttackParts.InputMode defaultInputMode() {
        return KiAttackParts.InputMode.HOLD_CHARGE;
    }

    /** The biggest explosion this attack can make. */
    @Override
    public float explosionRadius() {
        return sphere.explosionRadius().map(Ramp::max).orElseGet(() -> explosion.map(Explosion::radius).orElse(0f));
    }

    @Override
    public void fire(CastContext ctx) {
        float p = ctx.chargeProgress();
        float speed = sphere.speed().map(r -> r.at(p)).orElse(Float.NaN); // NaN = projectile.speed
        float explosionR = sphere.explosionRadius().map(r -> r.at(p)).orElseGet(() -> explosion.map(Explosion::radius).orElse(0f));
        ProjectileSpawner.Spec spec = ProjectileSpawner.specFor(ctx, speed, explosionR,
                sphere.radialKnockback(), sphere.radialKnockbackUp());
        ProjectileSpawner.spawn(ctx, ctx.look, spec);
        ProjectileSpawner.playFireSound(ctx, spec.progress());
    }
}

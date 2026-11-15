package com.powerlevelling.ki;

import com.mojang.serialization.MapCodec;
import com.powerlevelling.PowerLevelling;
import com.powerlevelling.ki.KiAttackParts.Common;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** powerlevelling:blast - tap to fire one projectile (after a short wind-up). */
public record BlastAttack(Common common) implements KiAttack {
    public static final ResourceLocation TYPE = ResourceLocation.fromNamespaceAndPath(PowerLevelling.MODID, "blast");
    public static final MapCodec<BlastAttack> CODEC = Common.REQUIRING_PROJECTILE.xmap(BlastAttack::new, BlastAttack::common);

    @Override
    public ResourceLocation typeId() {
        return TYPE;
    }

    /** A short wind-up so the orb is seen forming in the hand before it flies. */
    @Override
    public int defaultWindupTicks() {
        return 5;
    }

    @Override
    public void fire(CastContext ctx) {
        ProjectileSpawner.Spec spec = ProjectileSpawner.specFor(ctx, Float.NaN, explosionRadius(), 0f, 0f);
        ProjectileSpawner.spawn(ctx, ctx.look, spec);
        ProjectileSpawner.playFireSound(ctx, spec.progress());
    }
}

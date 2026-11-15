package com.powerlevelling.ki;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.powerlevelling.PowerLevelling;
import com.powerlevelling.entity.KiDiscEntity;
import com.powerlevelling.ki.KiAttackParts.Common;
import com.powerlevelling.ki.KiAttackParts.FloatRange;
import com.powerlevelling.progression.StatEffects;
import com.powerlevelling.registry.ModEntities;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * powerlevelling:disc - tap: a flat spinning saw of cubes forms above the raised palm (windup), is thrown, flies out to
 * projectile.range (or until it hits a solid block), then comes back to the thrower. It pierces and hits every target once
 * per leg (so once going out and once coming back). Catching it ({@code catch_radius}) refunds {@code ki_refund} x the
 * attack's ki cost and cuts the remaining cooldown to {@code catch_cooldown_mult}. One disc per player at a time.
 * Out speed / range / lifetime come from the common "projectile" block.
 */
public record DiscAttack(Common common, Disc disc) implements KiAttack {
    public static final ResourceLocation TYPE = ResourceLocation.fromNamespaceAndPath(PowerLevelling.MODID, "disc");

    public record Disc(float width, float thickness, float returnSpeed, float catchRadius, float kiRefund,
                       float catchCooldownMult) {
        public static final Disc DEFAULT = new Disc(1.4f, 0.25f, 1.5f, 1.2f, 0.25f, 0.5f);
        public static final Codec<Disc> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.floatRange(0.3f, 4f).optionalFieldOf("width", 1.4f).forGetter(Disc::width),
                Codec.floatRange(0.05f, 1f).optionalFieldOf("thickness", 0.25f).forGetter(Disc::thickness),
                Codec.floatRange(0.1f, 6f).optionalFieldOf("return_speed", 1.5f).forGetter(Disc::returnSpeed),
                Codec.floatRange(0.3f, 4f).optionalFieldOf("catch_radius", 1.2f).forGetter(Disc::catchRadius),
                Codec.floatRange(0f, 1f).optionalFieldOf("ki_refund", 0.25f).forGetter(Disc::kiRefund),
                Codec.floatRange(0f, 1f).optionalFieldOf("catch_cooldown_mult", 0.5f).forGetter(Disc::catchCooldownMult)
        ).apply(i, Disc::new));
    }

    public static final MapCodec<DiscAttack> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Common.REQUIRING_PROJECTILE.forGetter(DiscAttack::common),
            Disc.CODEC.optionalFieldOf("disc", Disc.DEFAULT).forGetter(DiscAttack::disc)
    ).apply(i, DiscAttack::new));

    @Override
    public ResourceLocation typeId() {
        return TYPE;
    }

    /** The spin-up above the palm. */
    @Override
    public int defaultWindupTicks() {
        return 10;
    }

    /** In-hand size (the disc is drawn about 1.6x this wide). */
    @Override
    public FloatRange orbRadiusRange() {
        return FloatRange.of(0.3f);
    }

    @Override
    public void fire(CastContext ctx) {
        ServerPlayer player = ctx.player;
        ServerLevel level = ctx.level();
        var proj = common.projectile();

        // One live disc per player.
        for (KiDiscEntity old : level.getEntitiesOfClass(KiDiscEntity.class, player.getBoundingBox().inflate(160.0),
                d -> player.getUUID().equals(d.getShooterUuid()))) {
            old.discard();
        }

        Vec3 dir = ctx.look;
        var orb = common.present().orb();
        Vec3 origin = KiOrbs.orbWorldPos(player, dir, orb.handId(), orb.anchorId(), 0.3f);
        float refund = disc.kiRefund() * baseCost(StatEffects.maxKi(player));

        KiDiscEntity d = new KiDiscEntity(ModEntities.KI_DISC.get(), level);
        d.setup(ctx.id, player, ctx.damage(common.damage().base()), common.knockback(), proj.speed(), proj.range(),
                proj.lifetime(), disc.width() * 0.5f, disc.thickness(), disc.returnSpeed(), disc.catchRadius(), refund,
                disc.catchCooldownMult());
        int kiColor = KiOrbs.playerKiColor(player, this);
        if (kiColor >= 0) d.setKiColor(kiColor, KiOrbs.coreOf(kiColor));
        d.setPos(origin.x, origin.y - d.getBbHeight() / 2.0, origin.z);
        d.setDeltaMovement(dir.scale(proj.speed()));
        level.addFreshEntity(d);

        level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), common.present().fireSoundEvent(),
                SoundSource.PLAYERS, 0.8f, 1.0f + (level.random.nextFloat() - 0.5f) * 0.1f);
    }
}

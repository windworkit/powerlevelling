package com.powerlevelling.ki;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.powerlevelling.PowerLevelling;
import com.powerlevelling.ki.KiAttackParts.Common;
import com.powerlevelling.ki.KiAttackParts.FloatRange;

import net.minecraft.resources.ResourceLocation;

/**
 * powerlevelling:beam - hold to charge (two-hand orb cupped at the hip), release to fire a sustained beam (K7, Addendum C.1).
 * The beam is not an entity: {@link BeamManager} raycasts from the hands every tick, hits everything along the segment every
 * damage.hit_interval ticks per target, and keeps draining cost.per_active_tick ki until it ends. It lasts
 * lerp(duration, charge) ticks; pressing the slot key again ends it early, running out of ki ends it too. The cooldown starts
 * when it ends. Beam numbers live in the "beam" block (all optional):
 * <ul>
 * <li>{@code max_length} blocks (48), {@code radius} [min, max] over the charge, {@code duration} [min, max] ticks over the charge</li>
 * <li>{@code turn} max degrees per tick the beam follows the camera (3: it lags behind fast turns, heavy feel)</li>
 * <li>{@code root_caster} (true: no walking while firing, else {@code fire_move_mult}), {@code recoil} push-back per tick</li>
 * <li>{@code pierce} (false: the first entity stops the beam), {@code block_radius} (tip explosion that breaks blocks when
 * block_damage and server kiBlockDamage are on), {@code clash_power} (K8: 0 = cannot clash)</li>
 * <li>{@code orb_radius} [min, max] of the charging orb</li>
 * </ul>
 */
public record BeamAttack(Common common, Beam beam) implements KiAttack {
    public static final ResourceLocation TYPE = ResourceLocation.fromNamespaceAndPath(PowerLevelling.MODID, "beam");

    public record Beam(float maxLength, FloatRange radius, FloatRange duration, float turn, boolean rootCaster,
                       float fireMoveMult, float recoil, boolean pierce, float blockRadius, int clashPower,
                       FloatRange orbRadius) {
        public static final Beam DEFAULT = new Beam(48f, new FloatRange(0.45f, 0.9f), new FloatRange(24f, 44f), 3f, true,
                0.1f, 0.03f, true, 1.6f, 0, new FloatRange(0.2f, 0.85f));
        public static final Codec<Beam> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.floatRange(2f, 256f).optionalFieldOf("max_length", 48f).forGetter(Beam::maxLength),
                FloatRange.CODEC.optionalFieldOf("radius", DEFAULT.radius()).forGetter(Beam::radius),
                FloatRange.CODEC.optionalFieldOf("duration", DEFAULT.duration()).forGetter(Beam::duration),
                Codec.floatRange(0.1f, 90f).optionalFieldOf("turn", 3f).forGetter(Beam::turn),
                Codec.BOOL.optionalFieldOf("root_caster", true).forGetter(Beam::rootCaster),
                Codec.floatRange(0f, 1f).optionalFieldOf("fire_move_mult", 0.1f).forGetter(Beam::fireMoveMult),
                Codec.floatRange(0f, 1f).optionalFieldOf("recoil", 0.03f).forGetter(Beam::recoil),
                Codec.BOOL.optionalFieldOf("pierce", true).forGetter(Beam::pierce),
                Codec.floatRange(0f, 8f).optionalFieldOf("block_radius", 1.6f).forGetter(Beam::blockRadius),
                Codec.intRange(0, 100).optionalFieldOf("clash_power", 0).forGetter(Beam::clashPower),
                FloatRange.CODEC.optionalFieldOf("orb_radius", DEFAULT.orbRadius()).forGetter(Beam::orbRadius)
        ).apply(i, Beam::new));
    }

    public static final MapCodec<BeamAttack> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Common.MAP_CODEC.forGetter(BeamAttack::common),
            Beam.CODEC.optionalFieldOf("beam", Beam.DEFAULT).forGetter(BeamAttack::beam)
    ).apply(i, BeamAttack::new));

    @Override
    public ResourceLocation typeId() {
        return TYPE;
    }

    @Override
    public KiAttackParts.InputMode defaultInputMode() {
        return KiAttackParts.InputMode.HOLD_CHARGE;
    }

    @Override
    public FloatRange orbRadiusRange() {
        return beam.orbRadius();
    }

    @Override
    public boolean hasActivePhase() {
        return true;
    }

    /** The beam is the attack: the cooldown starts when it ends. */
    @Override
    public boolean cooldownAfterActive() {
        return true;
    }

    @Override
    public void fire(CastContext ctx) {
        BeamManager.start(ctx, this);
    }

    @Override
    public boolean tickActive(CastContext ctx) {
        return BeamManager.tick(ctx, this);
    }

    /** Pressing the slot key again while the beam runs ends it (the dispatcher then starts the cooldown / recovery). */
    @Override
    public void onRepress(CastContext ctx) {
        BeamManager.requestStop(ctx.player);
    }
}

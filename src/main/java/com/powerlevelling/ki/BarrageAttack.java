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
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * powerlevelling:barrage - hold the slot key: after a short warm-up (the orbs flicker in both hands) a stream of small
 * blasts leaves alternating hands. The fire rate ramps from {@code start_interval} down to {@code interval} ticks over the
 * first {@code ramp_shots} shots, every shot has a little random spread ({@code spread} degrees) and costs
 * cost.per_shot ki; the player is slowed (input.move_speed_mult) for as long as the key is held. The stream ends on release,
 * at {@code max_shots} or when ki runs out. Shots are cheap in the blast cap ({@code cap_weight}) and lose
 * {@code diminish_step} damage per earlier hit on the same target within 20 ticks (down to {@code diminish_min}), so
 * the barrage is for pressure, not for melting one target. The cooldown starts when the stream ends.
 */
public record BarrageAttack(Common common, Barrage barrage) implements KiAttack {
    public static final ResourceLocation TYPE = ResourceLocation.fromNamespaceAndPath(PowerLevelling.MODID, "barrage");

    public record Barrage(int interval, int startInterval, int rampShots, int maxShots, float spread, boolean alternateHands,
                          float capWeight, float diminishStep, float diminishMin) {
        public static final Barrage DEFAULT = new Barrage(3, 6, 8, 30, 4f, true, 0.5f, 0.12f, 0.5f);
        public static final Codec<Barrage> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(1, 40).optionalFieldOf("interval", 3).forGetter(Barrage::interval),
                Codec.intRange(1, 80).optionalFieldOf("start_interval", 6).forGetter(Barrage::startInterval),
                Codec.intRange(1, 100).optionalFieldOf("ramp_shots", 8).forGetter(Barrage::rampShots),
                Codec.intRange(1, 200).optionalFieldOf("max_shots", 30).forGetter(Barrage::maxShots),
                Codec.floatRange(0f, 45f).optionalFieldOf("spread", 4f).forGetter(Barrage::spread),
                Codec.BOOL.optionalFieldOf("alternate_hands", true).forGetter(Barrage::alternateHands),
                Codec.floatRange(0.05f, 4f).optionalFieldOf("cap_weight", 0.5f).forGetter(Barrage::capWeight),
                Codec.floatRange(0f, 0.5f).optionalFieldOf("diminish_step", 0.12f).forGetter(Barrage::diminishStep),
                Codec.floatRange(0.1f, 1f).optionalFieldOf("diminish_min", 0.5f).forGetter(Barrage::diminishMin)
        ).apply(i, Barrage::new));
    }

    public static final MapCodec<BarrageAttack> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Common.REQUIRING_PROJECTILE.forGetter(BarrageAttack::common),
            Barrage.CODEC.optionalFieldOf("barrage", Barrage.DEFAULT).forGetter(BarrageAttack::barrage)
    ).apply(i, BarrageAttack::new));

    @Override
    public ResourceLocation typeId() {
        return TYPE;
    }

    @Override
    public KiAttackParts.InputMode defaultInputMode() {
        return KiAttackParts.InputMode.HOLD_STREAM;
    }

    /** Warm-up before the first shot. */
    @Override
    public int defaultWindupTicks() {
        return 6;
    }

    @Override
    public FloatRange orbRadiusRange() {
        return FloatRange.of(0.16f);
    }

    @Override
    public boolean hasActivePhase() {
        return true;
    }

    @Override
    public boolean cooldownAfterActive() {
        return true;
    }

    /** The first shot comes from tickActive (the dispatcher creates the ActiveCast after fire). */
    @Override
    public void fire(CastContext ctx) {
    }

    @Override
    public boolean tickActive(CastContext ctx) {
        ActiveCast c = ctx.state.cast;
        if (c == null || c.shots >= barrage.maxShots()) return false;
        if (ctx.now < c.nextShotTick) return true;
        if (!KiSkills.spendPerShot(ctx, common.cost().perShot())) return false; // out of ki: the stream stops

        shoot(ctx, c.shots);
        c.shots++;
        int interval = c.shots >= barrage.rampShots() ? barrage.interval()
                : Math.max(1, Math.round(Mth.lerp(c.shots / (float) barrage.rampShots(), barrage.startInterval(), barrage.interval())));
        c.nextShotTick = ctx.now + interval;
        return c.shots < barrage.maxShots();
    }

    private void shoot(CastContext ctx, int index) {
        ServerPlayer player = ctx.player;
        ServerLevel level = ctx.level();
        Vec3 dir = ProjectileSpawner.jitter(ctx.look, barrage.spread(), level.random);
        int hand = barrage.alternateHands() && index % 2 == 1 ? OrbStatePayload.HAND_OFF : OrbStatePayload.HAND_MAIN;
        ProjectileSpawner.Spec spec = ProjectileSpawner.specFor(ctx, Float.NaN, 0f, 0f, 0f);
        Vec3 origin = KiOrbs.orbWorldPos(player, ctx.look, hand, OrbStatePayload.ANCHOR_PALM, spec.radius());
        ProjectileSpawner.spawnAt(ctx, origin, dir, spec,
                b -> b.setBarrage(barrage.capWeight(), barrage.diminishStep(), barrage.diminishMin()));
        level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), common.present().fireSoundEvent(),
                SoundSource.PLAYERS, 0.35f, 1.5f + level.random.nextFloat() * 0.4f);
    }
}

package com.powerlevelling.ki;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.powerlevelling.PowerLevelling;
import com.powerlevelling.ki.KiAttackParts.Common;
import com.powerlevelling.ki.KiAttackParts.FloatRange;
import com.powerlevelling.network.KiTracePayload;
import com.powerlevelling.network.ModNetwork;
import com.powerlevelling.network.OrbStatePayload;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * powerlevelling:needle - the fingertip lance (C.1): a hitscan after an 8-tick wind-up. For the last {@code aim_ticks} ticks
 * of the wind-up everyone nearby sees a faint aim line (KiTracePayload phase AIM, re-sent every tick so it follows the
 * caster's aim); then one ray goes {@code range} blocks, stops at the first solid block and hits up to {@code pierce}
 * entities with damage x pierce_mult[i]. No knockback unless the common "knockback" is set. Guard (x0.6, never deflected)
 * is stored in the "guard" block and not applied yet.
 */
public record NeedleAttack(Common common, Needle needle) implements KiAttack {
    public static final ResourceLocation TYPE = ResourceLocation.fromNamespaceAndPath(PowerLevelling.MODID, "needle");

    /** The finger orb is a small glowing cube of about this radius (blocks). */
    private static final float FINGER_ORB_RADIUS = 0.09f;

    public record Needle(float range, int pierce, List<Float> pierceMult, int aimTicks, float width) {
        public static final Needle DEFAULT = new Needle(40f, 3, List.of(1.0f, 0.75f, 0.5f), 4, 0.1f);
        public static final Codec<Needle> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.floatRange(1f, 256f).optionalFieldOf("range", 40f).forGetter(Needle::range),
                Codec.intRange(1, 16).optionalFieldOf("pierce", 3).forGetter(Needle::pierce),
                Codec.FLOAT.listOf().optionalFieldOf("pierce_mult", List.of(1.0f, 0.75f, 0.5f)).forGetter(Needle::pierceMult),
                Codec.intRange(0, 40).optionalFieldOf("aim_ticks", 4).forGetter(Needle::aimTicks),
                Codec.floatRange(0f, 2f).optionalFieldOf("width", 0.1f).forGetter(Needle::width)
        ).apply(i, Needle::new));

        float multFor(int hitIndex) {
            if (pierceMult.isEmpty()) return 1f;
            return pierceMult.get(Math.min(hitIndex, pierceMult.size() - 1));
        }
    }

    public static final MapCodec<NeedleAttack> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Common.MAP_CODEC.forGetter(NeedleAttack::common),
            Needle.CODEC.optionalFieldOf("needle", Needle.DEFAULT).forGetter(NeedleAttack::needle)
    ).apply(i, NeedleAttack::new));

    @Override
    public ResourceLocation typeId() {
        return TYPE;
    }

    @Override
    public int defaultWindupTicks() {
        return 8;
    }

    @Override
    public FloatRange orbRadiusRange() {
        return FloatRange.of(FINGER_ORB_RADIUS);
    }

    // ---- aim line during the wind-up ------------------------------------------------------------------------------------

    @Override
    public void tickWindup(CastContext ctx, int windupTick) {
        int windup = windupTicks();
        if (needle.aimTicks() <= 0 || windupTick >= windup || windupTick < windup - needle.aimTicks()) return;
        Ray ray = cast(ctx, ctx.look, false);
        send(ctx, ray, 0, KiTracePayload.PHASE_AIM);
    }

    // ---- the shot ------------------------------------------------------------------------------------------------------

    @Override
    public void fire(CastContext ctx) {
        ServerPlayer player = ctx.player;
        ServerLevel level = ctx.level();
        Ray ray = cast(ctx, ctx.look, true);

        int hits = 0;
        int kiColor = KiOrbs.playerKiColor(player, this);
        int style = common.present().impactStyle();
        for (Hit h : ray.hits) {
            float dmg = ctx.damage(common.damage().base() * needle.multFor(hits));
            if (!KiHits.hurt(level, player, player, h.entity, dmg)) continue;
            if (common.knockback() > 0f && h.entity instanceof LivingEntity living) {
                KiHits.push(living, ctx.look.x, ctx.look.z, common.knockback(), 0f);
            }
            hits++;
            ModNetwork.sendImpact(player, h.pos, ctx.id, 0.8f, false, kiColor, style);
        }
        send(ctx, ray, hits, KiTracePayload.PHASE_FIRE);

        level.playSound(null, player.getX(), player.getEyeY(), player.getZ(),
                common.present().fireSoundEvent(), SoundSource.PLAYERS, 0.9f, 1.0f + (level.random.nextFloat() - 0.5f) * 0.1f);
    }

    // ---- ray -----------------------------------------------------------------------------------------------------------

    private record Hit(Entity entity, Vec3 pos, double dist) {
    }

    /** from = where the line is drawn (the fingertip), to = end (block or max range); hits sorted near to far, at most pierce. */
    private record Ray(Vec3 from, Vec3 to, List<Hit> hits) {
    }

    private Ray cast(CastContext ctx, Vec3 dir, boolean withEntities) {
        ServerPlayer player = ctx.player;
        ServerLevel level = ctx.level();
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(dir.scale(needle.range()));
        BlockHitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (block.getType() != HitResult.Type.MISS) end = block.getLocation();

        List<Hit> hits = new ArrayList<>();
        if (withEntities) {
            AABB area = new AABB(eye, end).inflate(1.0);
            final Vec3 rayEnd = end;
            List<Entity> candidates = level.getEntities(player, area,
                    e -> e instanceof LivingEntity le && KiHits.canTarget(player, le));
            for (Entity e : candidates) {
                AABB box = e.getBoundingBox().inflate(e.getPickRadius() + needle.width());
                Optional<Vec3> p = box.contains(eye) ? Optional.of(eye) : box.clip(eye, rayEnd);
                p.ifPresent(pos -> hits.add(new Hit(e, pos, eye.distanceTo(pos))));
            }
            hits.sort(Comparator.comparingDouble(Hit::dist));
            while (hits.size() > needle.pierce()) hits.remove(hits.size() - 1);
        }

        Vec3 from = KiOrbs.orbWorldPos(player, dir, OrbStatePayload.HAND_FINGER, OrbStatePayload.ANCHOR_FINGERTIP, 0.05f);
        return new Ray(from, end, hits);
    }

    private void send(CastContext ctx, Ray ray, int hitCount, int phase) {
        ServerPlayer player = ctx.player;
        ModNetwork.sendTrace(player, new KiTracePayload(player.getId(), ctx.id, ray.from, ray.to, hitCount, phase,
                KiOrbs.resolveOrbColor(player, this), KiOrbs.resolveOrbCoreColor(player, this)));
    }
}

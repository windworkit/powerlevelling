package com.powerlevelling.ki;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.powerlevelling.PowerLevelling;
import com.powerlevelling.config.ServerConfig;
import com.powerlevelling.data.CombatState;
import com.powerlevelling.data.CombatState.SkillPhase;
import com.powerlevelling.ki.BeamAttack.Beam;
import com.powerlevelling.ki.KiAttackParts.Common;
import com.powerlevelling.network.BeamPayload;
import com.powerlevelling.network.ModNetwork;
import com.powerlevelling.network.OrbStatePayload;
import com.powerlevelling.registry.ModAttachments;
import com.powerlevelling.registry.ModDamageTypes;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Server side of sustained beams (K7, design 8.5). Beams are not entities: one {@link Active} per player, created by
 * {@link BeamAttack#fire}, advanced once per player tick by {@link BeamAttack#tickActive} (so it shares the dispatcher's ki
 * and phase handling) and swept every server tick for beams whose owner stopped being in the ACTIVE phase of that attack
 * (death, stance off, ki break, logout ...).
 * <p>
 * Per tick: pay per_active_tick ki, turn the beam towards the camera by at most beam.turn degrees (it lags behind fast camera
 * moves), one {@code level.clip} from the hands to max_length, one entity scan along the segment, damage every
 * damage.hit_interval ticks per target (own cooldown per target), knockback along the beam, block damage at the tip (only if
 * the JSON block_damage and the server kiBlockDamage are on), recoil on the caster, and a {@link BeamPayload} UPDATE to
 * self + trackers. Server thread only.
 */
@EventBusSubscriber(modid = PowerLevelling.MODID)
public final class BeamManager {
    private static final Map<UUID, Active> BEAMS = new HashMap<>();
    /** Hit interval when damage.hit_interval is 0. */
    private static final int DEFAULT_HIT_INTERVAL = 4;
    private static final int LOOP_SOUND_TICKS = 16;
    private static final int IMPACT_SOUND_TICKS = 8;
    private static final int BLOCK_BREAK_TICKS = 6;

    /** Explosion that only breaks blocks (no entity damage; the beam hurts entities itself). Same trick as KiBlastEntity. */
    private static final ExplosionDamageCalculator BLOCKS_ONLY = new ExplosionDamageCalculator() {
        public boolean shouldDamageEntity(Explosion explosion, Entity entity) {
            return false;
        }
    };

    private static final class Active {
        final ResourceLocation id;
        final float charge;
        final float radius;
        final int color;
        final int core;
        int left;
        int age;
        Vec3 dir;
        float length;
        boolean hit;
        boolean stopRequested;
        long nextImpactSound;
        long nextBlockBreak;
        final Map<Integer, Long> hitUntil = new HashMap<>();

        Active(ResourceLocation id, float charge, float radius, int duration, Vec3 dir, int color, int core) {
            this.id = id;
            this.charge = charge;
            this.radius = radius;
            this.left = duration;
            this.dir = dir;
            this.color = color;
            this.core = core;
        }
    }

    private BeamManager() {
    }

    // ---- lifecycle -------------------------------------------------------------------------------------------------------

    /** Fires: replaces the charging orb by the beam, roots / slows the caster, kicks back a little, tells the clients. */
    static void start(CastContext ctx, BeamAttack attack) {
        ServerPlayer player = ctx.player;
        ServerLevel level = ctx.level();
        Beam beam = attack.beam();
        Active old = BEAMS.remove(player.getUUID()); // one beam per player
        if (old != null) sendStop(player, old);

        float charge = ctx.chargeProgress();
        int duration = Math.max(1, Math.round(beam.duration().lerp(charge)));
        int color = KiOrbs.resolveOrbColor(player, attack);
        int core = KiOrbs.resolveOrbCoreColor(player, attack);
        Active b = new Active(ctx.id, charge, beam.radius().lerp(charge), duration, ctx.look, color, core);
        BEAMS.put(player.getUUID(), b);

        KiOrbs.stop(player, ctx.state); // the charge orb is spent; the clients draw the muzzle flare from the payload
        KiSkills.setSkillSlow(player, beam.rootCaster() ? 0f : beam.fireMoveMult());
        recoil(player, b.dir, beam.recoil() * 6f);

        level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), attack.common().present().fireSoundEvent(),
                SoundSource.PLAYERS, 1.6f, 1.0f);
        ModNetwork.sendBeam(player, payload(BeamPayload.PHASE_START, player, b, 0f, false));
    }

    /** The slot key was pressed again: end at the next tick. */
    static void requestStop(ServerPlayer player) {
        Active b = BEAMS.get(player.getUUID());
        if (b != null) b.stopRequested = true;
    }

    private static void sendStop(ServerPlayer player, Active b) {
        ModNetwork.sendBeam(player, payload(BeamPayload.PHASE_STOP, player, b, b.length, b.hit));
    }

    private static void end(ServerPlayer player, Active b) {
        BEAMS.remove(player.getUUID());
        sendStop(player, b);
    }

    private static BeamPayload payload(int phase, ServerPlayer player, Active b, float length, boolean hit) {
        return new BeamPayload(phase, player.getId(), b.id, length, b.radius, b.color, b.core, b.charge,
                (float) b.dir.x, (float) b.dir.y, (float) b.dir.z, hit);
    }

    /** Safety net: ends beams whose owner is gone / dead / no longer in the ACTIVE phase of this attack. */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (BEAMS.isEmpty()) return;
        Iterator<Map.Entry<UUID, Active>> it = BEAMS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Active> e = it.next();
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(e.getKey());
            if (player == null) {
                it.remove();
                continue;
            }
            CombatState st = player.getData(ModAttachments.COMBAT_STATE);
            if (!player.isAlive() || st.skillPhase != SkillPhase.ACTIVE || !e.getValue().id.equals(st.skillAttack)) {
                it.remove();
                sendStop(player, e.getValue());
            }
        }
    }

    // ---- per tick ----------------------------------------------------------------------------------------------------------

    /** One tick of the player's beam. False = the beam is over (the dispatcher then ends the ACTIVE phase). */
    static boolean tick(CastContext ctx, BeamAttack attack) {
        ServerPlayer player = ctx.player;
        Active b = BEAMS.get(player.getUUID());
        if (b == null || !b.id.equals(ctx.id)) return false;
        Common common = attack.common();
        Beam beam = attack.beam();
        if (b.stopRequested || b.left <= 0 || !player.isAlive()) {
            end(player, b);
            return false;
        }
        if (!KiSkills.spendPerShot(ctx, common.cost().perActiveTick())) { // out of ki
            end(player, b);
            return false;
        }
        b.left--;
        b.age++;
        ServerLevel level = ctx.level();
        long now = ctx.now;

        // Aim: follow the camera, but only so fast.
        b.dir = turnToward(b.dir, player.getLookAngle(), beam.turn());
        Vec3 dir = b.dir;
        Vec3 origin = KiOrbs.orbWorldPos(player, dir, OrbStatePayload.HAND_BOTH, OrbStatePayload.ANCHOR_CHEST, 0.3f);
        Vec3 end = origin.add(dir.scale(beam.maxLength()));
        BlockHitResult block = level.clip(new ClipContext(origin, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        boolean blockHit = block.getType() != HitResult.Type.MISS;
        Vec3 tip = blockHit ? block.getLocation() : end;

        // Entities along the segment (box test with the beam radius), nearest first.
        List<EntityHit> hits = new ArrayList<>();
        final Vec3 segEnd = tip;
        List<Entity> candidates = level.getEntities(player, new AABB(origin, segEnd).inflate(b.radius + 1.0),
                e -> e instanceof LivingEntity le && KiHits.canTarget(player, le));
        for (Entity e : candidates) {
            AABB box = e.getBoundingBox().inflate(b.radius * 0.9);
            Optional<Vec3> p = box.contains(origin) ? Optional.of(origin) : box.clip(origin, segEnd);
            p.ifPresent(pos -> hits.add(new EntityHit(e, pos, origin.distanceTo(pos))));
        }
        hits.sort(Comparator.comparingDouble(EntityHit::dist));
        boolean entityHit = !hits.isEmpty();
        if (!beam.pierce() && entityHit) { // the first entity stops the beam
            EntityHit first = hits.get(0);
            hits.subList(1, hits.size()).clear();
            tip = first.pos;
            blockHit = false;
        }
        tip = clashTip(player, b, origin, tip);

        // Damage ticks: every hit_interval per target.
        int interval = common.damage().hitInterval() > 0 ? common.damage().hitInterval() : DEFAULT_HIT_INTERVAL;
        float damage = ctx.damage(common.damage().base() * common.damage().chargeMult().lerp(b.charge));
        for (EntityHit h : hits) {
            Long until = b.hitUntil.get(h.entity.getId());
            if (until != null && now < until) continue;
            b.hitUntil.put(h.entity.getId(), now + interval);
            if (KiHits.hurt(level, player, player, h.entity, damage) && common.knockback() > 0f
                    && h.entity instanceof LivingEntity living) {
                KiHits.push(living, dir.x, dir.z, common.knockback(), 0.05f);
            }
        }
        if (b.age % 40 == 0) b.hitUntil.values().removeIf(t -> t < now);

        // Block damage at the tip only (JSON block_damage AND server kiBlockDamage).
        if (blockHit && common.blockDamage() && ServerConfig.KI_BLOCK_DAMAGE.get() && now >= b.nextBlockBreak) {
            b.nextBlockBreak = now + BLOCK_BREAK_TICKS;
            Vec3 in = tip.add(dir.scale(0.25));
            DamageSource src = level.damageSources().source(ModDamageTypes.KI, player, player);
            level.explode(player, src, BLOCKS_ONLY, in.x, in.y, in.z, beam.blockRadius() * (0.6f + 0.4f * b.charge), false,
                    Level.ExplosionInteraction.TNT);
        }

        // Sounds: a loop-ish layer every few ticks, impact thuds while the tip touches something.
        if (b.age % LOOP_SOUND_TICKS == 1) {
            common.present().sounds().loop().ifPresent(id -> level.playSound(null, player.getX(), player.getEyeY(),
                    player.getZ(), KiAttackParts.Present.soundOf(id), SoundSource.PLAYERS, 0.9f, 1.0f));
        }
        boolean touching = blockHit || entityHit;
        if (touching && now >= b.nextImpactSound) {
            b.nextImpactSound = now + IMPACT_SOUND_TICKS;
            level.playSound(null, tip.x, tip.y, tip.z, common.present().impactSoundEvent(), SoundSource.PLAYERS, 0.8f,
                    0.8f + level.random.nextFloat() * 0.3f);
        }

        recoil(player, dir, beam.recoil());

        b.length = (float) origin.distanceTo(tip);
        b.hit = touching;
        ModNetwork.sendBeam(player, payload(BeamPayload.PHASE_UPDATE, player, b, b.length, b.hit));
        return true;
    }

    private record EntityHit(Entity entity, Vec3 pos, double dist) {
    }

    /**
     * TODO(K8 beam clash): this is where two beams' tips meet. Look for another beam in BEAMS whose segment comes within
     * ~(radius + other radius) of this one (ClashManager.tryPair, only if both beam.clash_power > 0), and return the meeting
     * point so both beams end there. Until then the tip is returned unchanged.
     */
    private static Vec3 clashTip(ServerPlayer player, Active b, Vec3 origin, Vec3 tip) {
        return tip;
    }

    /** Pushes the caster back along the beam (horizontal only). */
    private static void recoil(ServerPlayer player, Vec3 dir, float amount) {
        if (amount <= 0f) return;
        Vec3 v = player.getDeltaMovement();
        player.setDeltaMovement(v.x - dir.x * amount, v.y, v.z - dir.z * amount);
        player.hurtMarked = true; // send the new velocity to the player
    }

    /** Rotates {@code cur} towards {@code target} by at most {@code maxDeg} degrees (normalised lerp). */
    private static Vec3 turnToward(Vec3 cur, Vec3 target, float maxDeg) {
        double dot = Mth.clamp(cur.dot(target), -1.0, 1.0);
        double angle = Math.acos(dot);
        double max = Math.toRadians(maxDeg);
        if (angle <= max) return target;
        double t = max / angle;
        Vec3 v = cur.scale(1.0 - t).add(target.scale(t));
        if (v.lengthSqr() < 1.0E-6) { // exactly opposite: step sideways
            Vec3 side = cur.cross(new Vec3(0, 1, 0));
            v = side.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : side;
        }
        return v.normalize();
    }
}

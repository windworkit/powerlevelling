package com.powerlevelling.entity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.powerlevelling.config.ClientConfig;
import com.powerlevelling.ki.KiAttack;
import com.powerlevelling.ki.KiAttackParts;
import com.powerlevelling.ki.KiAttackRegistry;
import com.powerlevelling.ki.KiAttacks;
import com.powerlevelling.ki.KiHits;
import com.powerlevelling.network.KiImpactPayload;
import com.powerlevelling.network.ModNetwork;
import com.powerlevelling.registry.ModDamageTypes;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * One ki blast (design 8.5). No gravity, custom tick, server-authoritative hits. Only the attack id, charge fraction and
 * radius are synced (for the renderer); damage, knockback, range etc. live on the server only.
 * Never saved to disk (EntityType noSave) and no server-side particles: all visuals are client-side.
 */
public class KiBlastEntity extends Projectile {
    private static final EntityDataAccessor<String> DATA_ATTACK =
            SynchedEntityData.defineId(KiBlastEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Float> DATA_CHARGE =
            SynchedEntityData.defineId(KiBlastEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_RADIUS =
            SynchedEntityData.defineId(KiBlastEntity.class, EntityDataSerializers.FLOAT);

    /** Shooter's ki colour (RGB) or -1 = use the attack's colours; core colour alongside. */
    private static final EntityDataAccessor<Integer> DATA_KI_COLOR =
            SynchedEntityData.defineId(KiBlastEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_KI_CORE =
            SynchedEntityData.defineId(KiBlastEntity.class, EntityDataSerializers.INT);

    /** Synced MotionMode ordinal, the homing target's entity id (-1 none), the STYLE_* look, and the bomb's fuse (0 = none). */
    private static final EntityDataAccessor<Integer> DATA_MOTION =
            SynchedEntityData.defineId(KiBlastEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_TARGET =
            SynchedEntityData.defineId(KiBlastEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_STYLE =
            SynchedEntityData.defineId(KiBlastEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_FUSE =
            SynchedEntityData.defineId(KiBlastEntity.class, EntityDataSerializers.INT);

    /** How the blast moves: straight (default), HOMING (steers to a target), GRAVITY (lobbed bomb), RESTING (bomb at rest). */
    public enum MotionMode {
        STRAIGHT, HOMING, GRAVITY, RESTING;

        static MotionMode of(int i) {
            MotionMode[] v = values();
            return v[Mth.clamp(i, 0, v.length - 1)];
        }
    }

    public static final int STYLE_NORMAL = 0;
    public static final int STYLE_BARRAGE = 1;
    public static final int STYLE_HOMING = 2;
    public static final int STYLE_BOMB = 3;

    private static final ResourceLocation BOMB_FUSE_SOUND = ResourceLocation.parse("powerlevelling:bomb_fuse");
    private static final ResourceLocation BOMB_BOUNCE_SOUND = ResourceLocation.parse("powerlevelling:bomb_bounce");
    /** Barrage diminishing returns: hits on the same target closer together than this many ticks stack. */
    private static final int DIMINISH_WINDOW = 20;
    /** (shooter ^ target) -> {hits in the current window, game time of the last hit}. Server thread only. */
    private static final Map<UUID, long[]> HIT_STACKS = new HashMap<>();

    /** AoE damage at the very edge of an explosion, as a fraction of the centre damage (centre = 100%). */
    private static final float AOE_EDGE_FACTOR = 0.3f;

    /**
     * For explosions that really break blocks: stops vanilla from also damaging entities (we do our own scaled damage).
     * NOTE: deliberately no @Override - if this method doesn't exist in the ExplosionDamageCalculator of this MC version,
     * nothing breaks, entities just take vanilla explosion damage on top (only when block damage is enabled).
     */
    private static final ExplosionDamageCalculator BLOCKS_ONLY = new ExplosionDamageCalculator() {
        public boolean shouldDamageEntity(Explosion explosion, Entity entity) {
            return false;
        }
    };

    // ---- server-only data (set by setup(), not synced, not saved) ----
    private UUID shooter;
    private float damage;
    private float knockback;
    private float range = 48f;
    private int lifetime = 100;
    private float explosionRadius;
    private boolean breakBlocks;
    private float travelled;
    /** > 0: explosions push radially (outward + radialKnockbackUp, scaled by falloff) instead of the plain 0.6 x knockback. */
    private float radialKnockback;
    private float radialKnockbackUp;
    /** KiImpactPayload.STYLE_* sent with the impact. */
    private int impactStyle = KiImpactPayload.STYLE_DEFAULT;
    /** Weight in the per-player blast cap (barrage 0.5, homing 0.6, default 1). */
    private float capWeight = 1f;
    /** Barrage: each further hit on the same target within the window loses this fraction, down to diminishMin x. */
    private float diminishStep;
    private float diminishMin = 1f;
    // homing
    private float homingTurnDeg;
    private int homingTicks;
    private int homingDelay;
    private float seekRange;
    private float seekConeCos = 0.5f;
    private UUID targetUuid;
    // bomb
    private float gravity;
    private int bounces;
    private int maxBounces;
    private float edgeFactor = AOE_EDGE_FACTOR;
    private int lastFusePhase;
    /** Client only: last blink cycle the ground-ring preview was drawn for (KiFx). */
    public int clientFxCycle = -1;

    // ---- client-side cache of the attack's colours, re-resolved when the synced attack id changes ----
    private String visualFor = "\u0000";
    private int color = KiAttackParts.Present.DEFAULT.color();
    private int coreColor = KiAttackParts.Present.DEFAULT.coreColor();

    public KiBlastEntity(EntityType<? extends KiBlastEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_ATTACK, "");
        builder.define(DATA_CHARGE, 0f);
        builder.define(DATA_RADIUS, 0.3f);
        builder.define(DATA_KI_COLOR, -1);
        builder.define(DATA_KI_CORE, -1);
        builder.define(DATA_MOTION, 0);
        builder.define(DATA_TARGET, -1);
        builder.define(DATA_STYLE, 0);
        builder.define(DATA_FUSE, 0);
    }

    /** Server side: fill in everything the blast needs before it is added to the level. */
    public void setup(ResourceLocation attackId, Entity owner, float chargeProgress, float radius, float damage,
                      float knockback, float range, int lifetime, float explosionRadius, boolean breakBlocks) {
        this.setOwner(owner);
        this.shooter = owner == null ? null : owner.getUUID();
        this.entityData.set(DATA_ATTACK, attackId.toString());
        this.entityData.set(DATA_CHARGE, chargeProgress);
        this.entityData.set(DATA_RADIUS, radius);
        this.damage = damage;
        this.knockback = knockback;
        this.range = range;
        this.lifetime = lifetime;
        this.explosionRadius = explosionRadius;
        this.breakBlocks = breakBlocks;
    }

    /** Server side, at spawn: radial explosion knockback (sphere). 0 = off. */
    public void setRadialKnockback(float outward, float up) {
        this.radialKnockback = outward;
        this.radialKnockbackUp = up;
    }

    /** Server side, at spawn: KiImpactPayload.STYLE_* of this blast's impact. */
    public void setImpactStyle(int style) {
        this.impactStyle = style;
    }

    /** Server side, at spawn: tint this blast with the shooter's ki colour. */
    public void setKiColor(int color, int core) {
        this.entityData.set(DATA_KI_COLOR, color);
        this.entityData.set(DATA_KI_CORE, core);
    }

    /** The player-colour tint to use, or -1. On a client with forceDefaultAttackColors the tint is ignored. */
    private boolean usePlayerTint() {
        return this.entityData.get(DATA_KI_COLOR) >= 0
                && !(level().isClientSide && ClientConfig.FORCE_DEFAULT_ATTACK_COLORS.get());
    }

    /** Server side, at spawn: barrage shot (small, low cap weight, diminishing hits per target). */
    public void setBarrage(float capWeight, float diminishStep, float diminishMin) {
        this.capWeight = capWeight;
        this.diminishStep = diminishStep;
        this.diminishMin = diminishMin;
        this.entityData.set(DATA_STYLE, STYLE_BARRAGE);
    }

    /**
     * Server side, at spawn: a seeker. Flies straight for {@code delay} ticks, then turns at most {@code turnDeg} per tick
     * towards its target for {@code ticks} ticks; re-checks line of sight / picks a new target (within a cone of
     * {@code coneDeg} around its heading) every 5 ticks.
     */
    public void setHoming(LivingEntity target, float turnDeg, int ticks, int delay, float seekRange, float coneDeg, float capWeight) {
        this.entityData.set(DATA_MOTION, MotionMode.HOMING.ordinal());
        this.entityData.set(DATA_STYLE, STYLE_HOMING);
        this.homingTurnDeg = turnDeg;
        this.homingTicks = ticks;
        this.homingDelay = delay;
        this.seekRange = seekRange;
        this.seekConeCos = (float) Math.cos(Math.toRadians(coneDeg));
        this.capWeight = capWeight;
        setTarget(target);
    }

    /** Server side, at spawn: a lobbed bomb. Explodes when the fuse runs out (or on hitting an entity); bounces off blocks. */
    public void setBomb(float gravity, int fuse, int maxBounces, float edgeFactor) {
        this.entityData.set(DATA_MOTION, MotionMode.GRAVITY.ordinal());
        this.entityData.set(DATA_STYLE, STYLE_BOMB);
        this.entityData.set(DATA_FUSE, fuse);
        this.gravity = gravity;
        this.maxBounces = maxBounces;
        this.edgeFactor = edgeFactor;
        this.lifetime = Math.max(lifetime, fuse + 20);
    }

    private void setTarget(Entity t) {
        this.targetUuid = t == null ? null : t.getUUID();
        this.entityData.set(DATA_TARGET, t == null ? -1 : t.getId());
    }

    public float getCapWeight() {
        return capWeight;
    }

    public MotionMode getMotion() {
        return MotionMode.of(this.entityData.get(DATA_MOTION));
    }

    public int getStyle() {
        return this.entityData.get(DATA_STYLE);
    }

    public int getFuseTotal() {
        return this.entityData.get(DATA_FUSE);
    }

    public int getTargetId() {
        return this.entityData.get(DATA_TARGET);
    }

    /** Bomb blink: cumulative blink cycles after {@code t} ticks of a {@code fuse}-tick fuse (1.4 Hz speeding up to 8 Hz). */
    public static float fusePhase(float t, int fuse) {
        float total = Math.max(1, fuse);
        float x = Math.min(t, total);
        float f0 = 0.07f;
        float f1 = 0.40f;
        return f0 * x + (f1 - f0) * x * x / (2f * total);
    }

    /** Forget the barrage hit stacks (server stopped). */
    public static void clearHitStacks() {
        HIT_STACKS.clear();
    }

    public UUID getShooterUuid() {
        return shooter;
    }

    public String getAttackIdString() {
        return this.entityData.get(DATA_ATTACK);
    }

    /** 0..1 charge fraction (renderer / effects). */
    public float getChargeProgress() {
        return this.entityData.get(DATA_CHARGE);
    }

    /** Hit/visual radius in blocks. */
    public float getBlastRadius() {
        return this.entityData.get(DATA_RADIUS);
    }

    public int getColor() {
        if (usePlayerTint()) return this.entityData.get(DATA_KI_COLOR);
        resolveVisual();
        return color;
    }

    public int getCoreColor() {
        if (usePlayerTint()) return this.entityData.get(DATA_KI_CORE);
        resolveVisual();
        return coreColor;
    }

    private void resolveVisual() {
        String id = getAttackIdString();
        if (id.equals(visualFor)) return;
        visualFor = id;
        ResourceLocation rl = id.isEmpty() ? null : ResourceLocation.tryParse(id);
        KiAttack attack = rl == null ? null : KiAttacks.resolve(level().registryAccess(), rl);
        KiAttackParts.Present v = attack == null ? KiAttackParts.Present.DEFAULT : attack.common().present();
        color = v.color();
        coreColor = v.coreColor();
    }

    @Override
    public boolean isPickable() {
        return false; // can't be punched, and other blasts fly through it
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return entity instanceof LivingEntity && entity != getOwner() && super.canHitEntity(entity);
    }

    // ---- tick ----------------------------------------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick(); // Projectile bookkeeping (leftOwner) + Entity.baseTick (updates xOld/yOld/zOld)
        Vec3 vel = this.getDeltaMovement();

        if (this.level().isClientSide) {
            // Visual only: keep gliding between the server's position updates. Hits are decided on the server.
            this.setPos(this.position().add(vel));
            return;
        }

        if (this.tickCount > lifetime || travelled >= range || !this.level().isLoaded(this.blockPosition())) {
            this.discard();
            return;
        }

        ServerLevel slv = (ServerLevel) this.level();
        MotionMode mode = getMotion();
        int fuse = getFuseTotal();
        if (fuse > 0) {
            if (this.tickCount >= fuse) { // bomb: boom
                detonate(slv, this.position().add(0, getBbHeight() / 2.0, 0), null);
                this.discard();
                return;
            }
            int phase = (int) fusePhase(this.tickCount, fuse);
            if (phase != lastFusePhase) { // one beep per blink, climbing in pitch
                lastFusePhase = phase;
                slv.playSound(null, getX(), getY(), getZ(), SoundEvent.createVariableRangeEvent(BOMB_FUSE_SOUND),
                        SoundSource.PLAYERS, 0.6f, 1.0f + (float) this.tickCount / fuse);
            }
        }
        if (mode == MotionMode.RESTING) return; // a bomb at rest just waits for its fuse
        if (mode == MotionMode.HOMING && this.tickCount >= homingDelay) {
            vel = steer(slv, vel);
        } else if (mode == MotionMode.GRAVITY) {
            vel = vel.add(0, -gravity, 0);
            this.setDeltaMovement(vel);
        }

        Vec3 from = this.position();
        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);

        // The vanilla test only inflates targets by 0.3, so big charged blasts get an extra radius-aware sweep.
        float radius = getBlastRadius();
        if (radius > 0.35f) {
            Vec3 end = hit.getType() == HitResult.Type.MISS ? from.add(vel) : hit.getLocation();
            EntityHitResult wide = wideEntityHit(from, end, radius - 0.3);
            if (wide != null && (hit.getType() != HitResult.Type.ENTITY
                    || from.distanceToSqr(wide.getLocation()) < from.distanceToSqr(hit.getLocation()))) {
                hit = wide;
            }
        }

        if (hit.getType() == HitResult.Type.ENTITY && hit instanceof EntityHitResult ehr) {
            impactEntity(ehr);
        } else if (hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult bhr) {
            impactBlock(bhr);
        } else {
            this.setPos(from.add(vel));
            travelled += (float) vel.length();
        }
    }

    /** Nearest living entity whose box, inflated by {@code extra}, the segment from..to passes through. */
    private EntityHitResult wideEntityHit(Vec3 from, Vec3 to, double extra) {
        AABB area = this.getBoundingBox().expandTowards(to.subtract(from)).inflate(extra + 1.0);
        Entity best = null;
        Vec3 bestPos = null;
        double bestDist = Double.MAX_VALUE;
        List<Entity> candidates = this.level().getEntities(this, area, this::canHitEntity);
        for (Entity e : candidates) {
            AABB box = e.getBoundingBox().inflate(extra);
            Optional<Vec3> p = box.contains(from) ? Optional.of(from) : box.clip(from, to);
            if (p.isPresent()) {
                double d = from.distanceToSqr(p.get());
                if (d < bestDist) {
                    bestDist = d;
                    best = e;
                    bestPos = p.get();
                }
            }
        }
        return best == null ? null : new EntityHitResult(best, bestPos);
    }

    // ---- homing ----------------------------------------------------------------------------------------------------

    private boolean hasLineOfSight(ServerLevel sl, Vec3 from, Entity to) {
        Vec3 end = to.getBoundingBox().getCenter();
        return sl.clip(new ClipContext(from, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this))
                .getType() == HitResult.Type.MISS;
    }

    /** Turns {@code vel} towards the target by at most homingTurnDeg; keeps the speed. Retargets every 5 ticks. */
    private Vec3 steer(ServerLevel sl, Vec3 vel) {
        double speed = vel.length();
        if (speed < 1.0E-4 || this.tickCount > homingDelay + homingTicks) return vel;
        Vec3 center = this.position().add(0, getBbHeight() / 2.0, 0);
        Vec3 cur = vel.scale(1.0 / speed);

        int id = getTargetId();
        Entity target = id < 0 ? null : sl.getEntity(id);
        boolean valid = target instanceof LivingEntity le && le.isAlive() && hasLineOfSight(sl, center, le);
        if (!valid && (this.tickCount % 5 == 0 || target == null)) {
            target = findTarget(sl, center, cur);
            setTarget(target);
            valid = target != null;
        } else if (!valid) {
            target = null; // lost it this tick; the next retarget tick looks again
        }
        if (target == null || !valid) return vel;

        Vec3 want = target.getBoundingBox().getCenter().subtract(center);
        if (want.lengthSqr() < 1.0E-6) return vel;
        want = want.normalize();
        double dot = Mth.clamp(cur.dot(want), -1.0, 1.0);
        double maxAngle = Math.toRadians(homingTurnDeg);
        Vec3 nd;
        if (Math.acos(dot) <= maxAngle) {
            nd = want;
        } else {
            Vec3 perp = want.subtract(cur.scale(dot));
            if (perp.lengthSqr() < 1.0E-8) {
                perp = cur.cross(new Vec3(0, 1, 0));
                if (perp.lengthSqr() < 1.0E-8) perp = new Vec3(1, 0, 0);
            }
            perp = perp.normalize();
            nd = cur.scale(Math.cos(maxAngle)).add(perp.scale(Math.sin(maxAngle)));
        }
        Vec3 nv = nd.normalize().scale(speed);
        this.setDeltaMovement(nv);
        return nv;
    }

    /** Best living target in a cone around the heading with a clear line: smallest angle, nearer wins ties. */
    private LivingEntity findTarget(ServerLevel sl, Vec3 center, Vec3 heading) {
        Entity owner = getOwner();
        LivingEntity best = null;
        double bestScore = -10.0;
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(seekRange),
                c -> owner instanceof LivingEntity lo ? KiHits.canTarget(lo, c) : c.isAlive())) {
            Vec3 d = e.getBoundingBox().getCenter().subtract(center);
            double dist = d.length();
            if (dist < 0.5 || dist > seekRange) continue;
            double cos = heading.dot(d.scale(1.0 / dist));
            if (cos < seekConeCos || !hasLineOfSight(sl, center, e)) continue;
            double score = cos - dist * 0.005;
            if (score > bestScore) {
                bestScore = score;
                best = e;
            }
        }
        return best;
    }

    // ---- barrage diminishing hits ----------------------------------------------------------------------------------

    /** Damage multiplier for this hit: 1, then -diminishStep per earlier hit on the same target within the window. */
    private float stackMult(ServerLevel sl, Entity target) {
        if (diminishStep <= 0f || shooter == null) return 1f;
        long now = sl.getGameTime();
        UUID t = target.getUUID();
        UUID key = new UUID(shooter.getMostSignificantBits() ^ t.getMostSignificantBits(),
                shooter.getLeastSignificantBits() ^ t.getLeastSignificantBits());
        long[] s = HIT_STACKS.get(key);
        int count = s != null && now - s[1] <= DIMINISH_WINDOW ? (int) s[0] : 0;
        HIT_STACKS.put(key, new long[] {count + 1, now});
        if (HIT_STACKS.size() > 512) HIT_STACKS.values().removeIf(v -> now - v[1] > DIMINISH_WINDOW);
        return Math.max(diminishMin, 1f - diminishStep * count);
    }

    /** Bomb hits a block: bounce once (damped), after that stop on floors (RESTING) or slide down walls. */
    private void bombBounce(ServerLevel sl, BlockHitResult hit) {
        Vec3 n = Vec3.atLowerCornerOf(hit.getDirection().getNormal());
        Vec3 at = hit.getLocation().add(n.scale(0.05));
        this.setPos(at.x, at.y, at.z);
        Vec3 v = this.getDeltaMovement();
        double vn = v.dot(n);
        if (bounces < maxBounces) {
            bounces++;
            Vec3 refl = v.subtract(n.scale(2.0 * vn));
            double rn = refl.dot(n);
            this.setDeltaMovement(n.scale(rn * 0.55).add(refl.subtract(n.scale(rn)).scale(0.7)));
            sl.playSound(null, at.x, at.y, at.z, SoundEvent.createVariableRangeEvent(BOMB_BOUNCE_SOUND),
                    SoundSource.PLAYERS, 0.7f, 0.9f + sl.random.nextFloat() * 0.2f);
        } else if (n.y > 0.5) {
            this.setDeltaMovement(Vec3.ZERO);
            this.entityData.set(DATA_MOTION, MotionMode.RESTING.ordinal());
        } else {
            this.setDeltaMovement(v.subtract(n.scale(vn))); // wall / ceiling: lose the push into it and keep falling
        }
    }

    // ---- impacts (server) ----------------------------------------------------------------------------------------

    private void impactEntity(EntityHitResult hit) {
        if (!(this.level() instanceof ServerLevel sl)) return;
        Entity target = hit.getEntity();
        Vec3 at = hit.getLocation();
        this.setPos(at);
        Vec3 dir = direction();
        float dmg = damage * stackMult(sl, target);

        if (radialKnockback > 0f && target instanceof LivingEntity living) {
            // Sphere: the direct target is pushed like everything else in the blast, from the impact point outwards.
            boolean hurt = hurt(sl, target, dmg, 0f, dir.x, dir.z);
            if (hurt) {
                Vec3 away = living.getBoundingBox().getCenter().subtract(at);
                KiHits.push(living, away.lengthSqr() < 1.0E-4 ? dir.x : away.x, away.lengthSqr() < 1.0E-4 ? dir.z : away.z,
                        radialKnockback, radialKnockbackUp);
            }
        } else {
            hurt(sl, target, dmg, knockback * (1f + getChargeProgress()), dir.x, dir.z);
        }
        detonate(sl, at, target);
        this.discard();
    }

    private void impactBlock(BlockHitResult hit) {
        if (!(this.level() instanceof ServerLevel sl)) return;
        if (getMotion() == MotionMode.GRAVITY) { // bombs bounce, they don't explode on landing
            bombBounce(sl, hit);
            return;
        }
        // Step slightly back off the surface so the explosion centre isn't inside the block.
        Vec3 at = hit.getLocation().add(Vec3.atLowerCornerOf(hit.getDirection().getNormal()).scale(0.1));
        this.setPos(at);
        detonate(sl, at, null);
        this.discard();
    }

    /** Impact FX for clients, then the explosion (if this attack has one). */
    private void detonate(ServerLevel sl, Vec3 at, Entity directTarget) {
        ResourceLocation id = ResourceLocation.tryParse(getAttackIdString());
        if (id == null) id = KiAttackRegistry.DEFAULT_SLOT_1;
        boolean explodes = explosionRadius > 0f;
        ModNetwork.sendImpact(this, at, id, explodes ? explosionRadius : getBlastRadius() * 2f, explodes, this.entityData.get(DATA_KI_COLOR),
                impactStyle);
        if (!explodes) return;

        Entity owner = getOwner();
        if (breakBlocks) {
            // Real block damage (attack json AND server config kiBlockDamage both on). Entities are handled below.
            DamageSource src = sl.damageSources().source(ModDamageTypes.KI, this, owner);
            sl.explode(owner, src, BLOCKS_ONLY, at.x, at.y, at.z, explosionRadius, false, Level.ExplosionInteraction.TNT);
        }

        // Radial damage: 100% at the centre falling to 30% at the edge. The entity we hit directly and the shooter are skipped.
        AABB area = new AABB(at, at).inflate(explosionRadius);
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, area, this::canExplodeOn)) {
            if (e == directTarget) continue;
            double dist = Math.sqrt(e.getBoundingBox().distanceToSqr(at));
            if (dist > explosionRadius) continue;
            float falloff = 1f - (1f - edgeFactor) * (float) (dist / explosionRadius);
            Vec3 away = e.getBoundingBox().getCenter().subtract(at);
            if (radialKnockback > 0f) {
                if (hurt(sl, e, damage * falloff, 0f, away.x, away.z)) {
                    KiHits.push(e, away.x, away.z, radialKnockback * falloff, radialKnockbackUp * falloff);
                }
            } else {
                hurt(sl, e, damage * falloff, knockback * falloff * 0.6f, away.x, away.z);
            }
        }
    }

    private boolean canExplodeOn(LivingEntity e) {
        Entity owner = getOwner();
        if (e == owner || !e.isAlive() || e.isSpectator()) return false;
        return !(e instanceof TamableAnimal pet && owner instanceof LivingEntity lo && pet.isOwnedBy(lo));
    }

    /** Damages one entity and pushes it along (pushX, pushZ). Direct hit: the blast's direction; radial: away from the centre. */
    private boolean hurt(ServerLevel sl, Entity target, float amount, float kb, double pushX, double pushZ) {
        DamageSource src = sl.damageSources().source(ModDamageTypes.KI, this, getOwner());
        target.invulnerableTime = 0; // rapid blasts must each register (vanilla's 10-tick window would eat them)
        boolean hurt = target.hurt(src, amount);
        if (hurt && kb > 0f && target instanceof LivingEntity living) {
            // Vanilla's knockback(x, z) takes the direction TOWARDS the attacker and pushes the opposite way.
            living.knockback(kb, -pushX, -pushZ);
            living.hurtMarked = true; // make sure players receive the new velocity
        }
        return hurt;
    }

    private Vec3 direction() {
        Vec3 v = this.getDeltaMovement();
        return v.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : v.normalize();
    }
}

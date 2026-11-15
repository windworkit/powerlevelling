package com.powerlevelling.entity;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.powerlevelling.config.ClientConfig;
import com.powerlevelling.data.CombatResources;
import com.powerlevelling.data.CombatState;
import com.powerlevelling.ki.KiAttack;
import com.powerlevelling.ki.KiAttackParts;
import com.powerlevelling.ki.KiAttacks;
import com.powerlevelling.ki.KiHits;
import com.powerlevelling.ki.KiSkills;
import com.powerlevelling.network.KiImpactPayload;
import com.powerlevelling.network.ModNetwork;
import com.powerlevelling.progression.StatEffects;
import com.powerlevelling.registry.ModAttachments;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The thrown ki disc (Addendum C, K2). Two legs: OUT (flies along the throw direction, stops and turns back on a solid block
 * or at max range) and RETURN (eases up to {@code returnSpeed} and homes in on the thrower, ignoring blocks). Pierces: every
 * living target is hit once per leg (the hit set is cleared when the return starts). Catch = within {@code catchRadius} of the
 * thrower on the way back: refunds ki and shortens the cooldown. Server authoritative, never saved; the client only draws it
 * (KiDiscRenderer) and glides it between position updates.
 */
public class KiDiscEntity extends Projectile {
    private static final EntityDataAccessor<String> DATA_ATTACK =
            SynchedEntityData.defineId(KiDiscEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Float> DATA_RADIUS =
            SynchedEntityData.defineId(KiDiscEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_THICKNESS =
            SynchedEntityData.defineId(KiDiscEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_RETURNING =
            SynchedEntityData.defineId(KiDiscEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_KI_COLOR =
            SynchedEntityData.defineId(KiDiscEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_KI_CORE =
            SynchedEntityData.defineId(KiDiscEntity.class, EntityDataSerializers.INT);

    private static final ResourceLocation CATCH_SOUND = ResourceLocation.parse("powerlevelling:disc_catch");

    // ---- server-only ----
    private UUID shooter;
    private float damage;
    private float knockback;
    private float speed = 1.3f;
    private float range = 24f;
    private int lifetime = 120;
    private float returnSpeed = 1.5f;
    private float catchRadius = 1.2f;
    private float kiRefund;
    private float catchCooldownMult = 0.5f;
    private float travelled;
    private int returnTicks;
    private final Set<UUID> hitThisLeg = new HashSet<>();

    // ---- client-side colour cache ----
    private String visualFor = "\u0000";
    private int color = KiAttackParts.Present.DEFAULT.color();
    private int coreColor = KiAttackParts.Present.DEFAULT.coreColor();

    public KiDiscEntity(EntityType<? extends KiDiscEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_ATTACK, "");
        builder.define(DATA_RADIUS, 0.7f);
        builder.define(DATA_THICKNESS, 0.25f);
        builder.define(DATA_RETURNING, false);
        builder.define(DATA_KI_COLOR, -1);
        builder.define(DATA_KI_CORE, -1);
    }

    /** Server side, before adding to the level. {@code radius} is the disc's radius (half its width). */
    public void setup(ResourceLocation attackId, Entity owner, float damage, float knockback, float speed, float range,
                      int lifetime, float radius, float thickness, float returnSpeed, float catchRadius, float kiRefund,
                      float catchCooldownMult) {
        this.setOwner(owner);
        this.shooter = owner == null ? null : owner.getUUID();
        this.entityData.set(DATA_ATTACK, attackId.toString());
        this.entityData.set(DATA_RADIUS, radius);
        this.entityData.set(DATA_THICKNESS, thickness);
        this.damage = damage;
        this.knockback = knockback;
        this.speed = speed;
        this.range = range;
        this.lifetime = lifetime;
        this.returnSpeed = returnSpeed;
        this.catchRadius = catchRadius;
        this.kiRefund = kiRefund;
        this.catchCooldownMult = catchCooldownMult;
    }

    public void setKiColor(int color, int core) {
        this.entityData.set(DATA_KI_COLOR, color);
        this.entityData.set(DATA_KI_CORE, core);
    }

    public UUID getShooterUuid() {
        return shooter;
    }

    public String getAttackIdString() {
        return this.entityData.get(DATA_ATTACK);
    }

    public float getDiscRadius() {
        return this.entityData.get(DATA_RADIUS);
    }

    public float getThickness() {
        return this.entityData.get(DATA_THICKNESS);
    }

    public boolean isReturning() {
        return this.entityData.get(DATA_RETURNING);
    }

    private boolean usePlayerTint() {
        return this.entityData.get(DATA_KI_COLOR) >= 0
                && !(level().isClientSide && ClientConfig.FORCE_DEFAULT_ATTACK_COLORS.get());
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
        return false;
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return false; // all hit logic is our own sweep
    }

    // ---- tick ----------------------------------------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            this.setPos(this.position().add(this.getDeltaMovement())); // glide between the server's position updates
            return;
        }
        if (!(this.level() instanceof ServerLevel sl)) return;
        if (!(getOwner() instanceof ServerPlayer owner) || !owner.isAlive() || owner.level() != sl
                || this.tickCount > lifetime || !sl.isLoaded(this.blockPosition())) {
            this.discard();
            return;
        }
        if (isReturning()) tickReturn(sl, owner);
        else tickOut(sl, owner);
    }

    private Vec3 center() {
        return this.position().add(0, getBbHeight() / 2.0, 0);
    }

    private void tickOut(ServerLevel sl, ServerPlayer owner) {
        Vec3 vel = this.getDeltaMovement();
        Vec3 c0 = center();
        Vec3 c1 = c0.add(vel);
        BlockHitResult block = sl.clip(new ClipContext(c0, c1, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        boolean blocked = block.getType() != HitResult.Type.MISS;
        Vec3 end = blocked ? block.getLocation() : c1;

        sweepHits(sl, owner, c0, end, vel);
        this.setPos(end.x, end.y - getBbHeight() / 2.0, end.z);
        travelled += (float) c0.distanceTo(end);

        if (blocked) {
            ModNetwork.sendImpact(this, end, attackId(), 1.0f, false, this.entityData.get(DATA_KI_COLOR), KiImpactPayload.STYLE_SLICE);
            startReturn();
        } else if (travelled >= range) {
            startReturn();
        }
    }

    private void startReturn() {
        this.entityData.set(DATA_RETURNING, true);
        this.hitThisLeg.clear(); // the way back can hit everything again
        this.returnTicks = 0;
        this.setDeltaMovement(Vec3.ZERO);
    }

    private void tickReturn(ServerLevel sl, ServerPlayer owner) {
        Vec3 target = owner.position().add(0, owner.getBbHeight() * 0.6, 0);
        Vec3 c = center();
        Vec3 d = target.subtract(c);
        double dist = d.length();
        if (dist > 160.0) {
            this.discard();
            return;
        }
        if (dist <= catchRadius) {
            catchDisc(sl, owner);
            return;
        }
        returnTicks++;
        double sp = Math.min(returnSpeed, 0.45 + 0.2 * returnTicks); // eases up so the turn-around reads
        Vec3 vel = d.scale(1.0 / dist).scale(Math.min(sp, dist));
        sweepHits(sl, owner, c, c.add(vel), vel);
        this.setDeltaMovement(vel);
        this.setPos(this.position().add(vel));
    }

    /** Hits every not-yet-hit living target whose (flattened) box the segment a..b passes through. */
    private void sweepHits(ServerLevel sl, ServerPlayer owner, Vec3 a, Vec3 b, Vec3 dir) {
        float r = getDiscRadius();
        float halfThick = getThickness() * 0.5f + 0.15f;
        AABB area = new AABB(a, b).inflate(r + 0.6);
        List<LivingEntity> list = sl.getEntitiesOfClass(LivingEntity.class, area,
                e -> KiHits.canTarget(owner, e) && !hitThisLeg.contains(e.getUUID()));
        for (LivingEntity e : list) {
            AABB box = e.getBoundingBox().inflate(r, halfThick, r);
            if (!box.contains(a) && box.clip(a, b).isEmpty()) continue;
            hitThisLeg.add(e.getUUID());
            if (KiHits.hurt(sl, this, owner, e, damage)) {
                KiHits.push(e, dir.x, dir.z, knockback, 0.08f);
            }
            ModNetwork.sendImpact(this, e.getBoundingBox().getCenter(), attackId(), 1.0f, false,
                    this.entityData.get(DATA_KI_COLOR), KiImpactPayload.STYLE_SLICE);
        }
    }

    private void catchDisc(ServerLevel sl, ServerPlayer owner) {
        if (kiRefund > 0f) {
            CombatResources r = owner.getData(ModAttachments.RESOURCES);
            r.ki = Math.min(StatEffects.maxKi(owner), r.ki + kiRefund);
        }
        ResourceLocation id = attackId();
        CombatState st = owner.getData(ModAttachments.COMBAT_STATE);
        Long until = st.kiCooldowns.get(id);
        long now = sl.getGameTime();
        if (until != null && until > now) {
            st.kiCooldowns.put(id, now + (long) ((until - now) * catchCooldownMult));
            KiSkills.syncCooldowns(owner);
        }
        sl.playSound(null, owner.getX(), owner.getEyeY(), owner.getZ(), SoundEvent.createVariableRangeEvent(CATCH_SOUND),
                SoundSource.PLAYERS, 0.8f, 1.0f + sl.random.nextFloat() * 0.2f);
        ModNetwork.sendImpact(this, owner.position().add(0, owner.getBbHeight() * 0.6, 0), id, 0.5f, false,
                this.entityData.get(DATA_KI_COLOR), KiImpactPayload.STYLE_SPARK_SMALL);
        this.discard();
    }

    private ResourceLocation attackId() {
        ResourceLocation id = ResourceLocation.tryParse(getAttackIdString());
        return id == null ? ResourceLocation.parse("powerlevelling:crescent_saw") : id;
    }
}

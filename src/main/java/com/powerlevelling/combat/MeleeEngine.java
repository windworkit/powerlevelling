package com.powerlevelling.combat;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.powerlevelling.PowerLevelling;
import com.powerlevelling.character.CharacterGate;
import com.powerlevelling.data.CombatResources;
import com.powerlevelling.data.CombatState;
import com.powerlevelling.data.KiProgression.Track;
import com.powerlevelling.entity.TrainingDummyEntity;
import com.powerlevelling.ki.KiAttackParts;
import com.powerlevelling.ki.KiSkills;
import com.powerlevelling.network.MeleeInputPayload;
import com.powerlevelling.network.MeleeStatePayload;
import com.powerlevelling.network.MeleeSwingPayload;
import com.powerlevelling.progression.Progression;
import com.powerlevelling.progression.Progression.ExpSource;
import com.powerlevelling.progression.StatEffects;
import com.powerlevelling.registry.ModAttachments;
import com.powerlevelling.registry.ModDamageTypes;
import com.powerlevelling.registry.ModSounds;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Server side of melee (M5): input validation, ticking the per-player {@link MeleeMachine} (in CombatState), oriented-box hit
 * detection during ACTIVE ticks, damage (powerlevelling:melee, design 3.3 + Addendum A), hitstun, knockback, Martial XP and
 * minimal hit FX. Hitstun of ANY living entity (players and mobs) is tracked here in {@link #VICTIMS}; for players it is
 * mirrored to CombatState.hitstunUntil so KiSkills / KiCharge / Burst read it cheaply.
 * See docs/combat_framework_notes.md.
 */
@EventBusSubscriber(modid = PowerLevelling.MODID)
public final class MeleeEngine {
    /** Design 4.2: the box is inflated by 0.3 to hide latency. */
    public static final float HITBOX_LATENCY_INFLATE = 0.3f;
    /** Addendum A.3: Martial keeps +1% melee damage per level. */
    public static final double MARTIAL_PER_LEVEL = 0.01;
    /** Design 3.2: 2 Martial XP per hit on a living target, x0.25 vs the Training Dummy. */
    public static final float MARTIAL_XP_PER_HIT = 2f;
    public static final float DUMMY_XP_MULT = 0.25f;
    /** Addendum A.1: training EXP from Dummy hits, 1 per hit, capped 300 per hour. */
    public static final int DUMMY_EXP_CAP = 300;
    public static final long HOUR_TICKS = 72000L;
    /** A hit landing within this many ticks after hitstun ended still continues the combo. */
    public static final int COMBO_GRACE_TICKS = 3;

    public static final ResourceLocation STUN_MOVE_ID = ResourceLocation.fromNamespaceAndPath(PowerLevelling.MODID, "hitstun_move");
    public static final ResourceLocation STUN_JUMP_ID = ResourceLocation.fromNamespaceAndPath(PowerLevelling.MODID, "hitstun_jump");

    /** Hitstun / combo bookkeeping of one victim. */
    private static final class Victim {
        long stunUntil;
        int combo;
        boolean locked;
    }

    private static final Map<LivingEntity, Victim> VICTIMS = new HashMap<>();

    private MeleeEngine() {
    }

    private static CombatState state(ServerPlayer p) {
        return p.getData(ModAttachments.COMBAT_STATE);
    }

    // ---- input ---------------------------------------------------------------------------------------------------------

    public static void handleInput(ServerPlayer player, MeleeInputPayload in) {
        CombatState st = state(player);
        long now = player.level().getGameTime();
        MeleeInput input = MeleeInput.fromOrdinal(in.input());
        if (input == null || !st.allowInput(now) || in.seq() <= st.lastMeleeSeq) {
            reply(player, in.seq(), MeleeStatePayload.REJECTED, st);
            return;
        }
        st.lastMeleeSeq = in.seq();
        if (!canMelee(player, st, now)) {
            reply(player, in.seq(), MeleeStatePayload.REJECTED, st);
            return;
        }
        switch (st.melee.press(input, in.seq(), resolver(player))) {
            case STARTED -> {
                onStart(player, st, now);
                reply(player, in.seq(), MeleeStatePayload.STARTED, st);
            }
            case BUFFERED -> reply(player, in.seq(), MeleeStatePayload.BUFFERED, st);
            case REJECTED -> reply(player, in.seq(), MeleeStatePayload.REJECTED, st);
        }
    }

    /** Stance on, not ki charging, no skill phase (WINDUP/CHARGE/ACTIVE/RECOVERY all block, Addendum C.6), not stunned. */
    private static boolean canMelee(ServerPlayer p, CombatState st, long now) {
        return p.isAlive() && !p.isSpectator() && st.stance && !st.charging && !st.skillCharging() && !st.inHitstun(now)
                && CharacterGate.check(p);
    }

    private static MeleeMachine.Resolver resolver(ServerPlayer p) {
        return (input, from) -> {
            CombatMoves.Resolved r = CombatMoves.resolve(p.level().registryAccess(), input, from, p.onGround());
            if (r == null) return null;
            float cost = r.move().staminaCost();
            if (cost > 0 && !p.isCreative() && p.getData(ModAttachments.RESOURCES).stamina < cost) return null;
            return r;
        };
    }

    private static void onStart(ServerPlayer p, CombatState st, long now) {
        CombatMove mv = st.melee.move;
        st.meleeHits.clear();
        st.meleeSerial = st.melee.serial;
        if (mv.staminaCost() > 0 && !p.isCreative()) {
            CombatResources r = p.getData(ModAttachments.RESOURCES);
            r.stamina = Math.max(0f, r.stamina - mv.staminaCost());
            st.lastStaminaSpendTick = now;
        }
        // The attacker predicted the swing sound locally, so exclude them (first argument).
        SoundEvent swing = mv.sounds().swing().map(KiAttackParts.Present::soundOf).orElse(ModSounds.MELEE_SWING.get());
        p.level().playSound(p, p.getX(), p.getEyeY(), p.getZ(), swing, SoundSource.PLAYERS, 0.5f,
                0.95f + p.getRandom().nextFloat() * 0.15f);
        PacketDistributor.sendToPlayersTrackingEntity(p, new MeleeSwingPayload(p.getId(), st.melee.moveId.toString(), false));
    }

    private static void reply(ServerPlayer p, int seq, int result, CombatState st) {
        boolean started = result == MeleeStatePayload.STARTED && st.melee.moveId != null;
        PacketDistributor.sendToPlayer(p, new MeleeStatePayload(seq, result,
                started ? st.melee.moveId.toString() : "", started ? st.melee.chain : 0, 0));
    }

    // ---- tick (PlayerTickEvent.Post via PlayerEvents) -----------------------------------------------------------------

    public static void tick(ServerPlayer p, CombatState st, long now) {
        MeleeMachine m = st.melee;
        if (!m.busy() && m.buffered == null && m.chainGrace == 0) return;
        if (!p.isAlive() || p.isSpectator() || !st.stance || st.charging || st.skillCharging() || st.inHitstun(now)) {
            cancel(p, st, true);
            return;
        }
        m.tick(resolver(p), new MeleeMachine.Listener() {
            @Override
            public void started(int seq) {
                onStart(p, st, now);
                reply(p, seq, MeleeStatePayload.STARTED, st);
            }

            @Override
            public void expired(int seq) {
                reply(p, seq, MeleeStatePayload.REJECTED, st);
            }
        });
        if (m.active()) detectHits(p, st, now);
    }

    // ---- hit detection ------------------------------------------------------------------------------------------------

    private static void detectHits(ServerPlayer p, CombatState st, long now) {
        CombatMove mv = st.melee.move;
        if (mv == null) return;
        if (st.meleeSerial != st.melee.serial) {
            st.meleeHits.clear();
            st.meleeSerial = st.melee.serial;
        }
        CombatMove.Hitbox hb = mv.hitbox();
        float yaw = p.getYRot() * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(yaw);
        double fz = Mth.cos(yaw);
        double inf = HITBOX_LATENCY_INFLATE;
        double reach = hb.reach() + inf;
        double halfW = hb.width() / 2.0 + inf;
        double y0 = p.getY() + hb.y() - inf;
        double y1 = p.getY() + hb.y() + hb.height() + inf;
        Vec3 o = p.position();
        double r = reach + halfW;
        AABB search = new AABB(o.x - r, y0, o.z - r, o.x + r, y1, o.z + r);
        List<LivingEntity> found = p.level().getEntitiesOfClass(LivingEntity.class, search,
                e -> e != p && e.isAlive() && !e.isSpectator() && e.isPickable() && e.isAttackable()
                        && !st.meleeHits.contains(e.getId()));
        if (found.isEmpty()) return;
        Vec3 eye = p.getEyePosition();
        for (LivingEntity t : found) {
            double dx = t.getX() - o.x;
            double dz = t.getZ() - o.z;
            double along = dx * fx + dz * fz;          // distance ahead
            double side = Math.abs(dx * fz - dz * fx); // distance to the side
            double rad = t.getBbWidth() / 2.0;
            if (along < hb.forward() - rad - inf || along > reach + rad || side > halfW + rad) continue;
            AABB tb = t.getBoundingBox();
            if (tb.maxY < y0 || tb.minY > y1) continue;
            if (p.isAlliedTo(t)) continue;
            if (t instanceof Player tp && !p.canHarmPlayer(tp)) continue;
            // No hits through walls.
            if (p.level().clip(new ClipContext(eye, tb.getCenter(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p))
                    .getType() != HitResult.Type.MISS) continue;
            st.meleeHits.add(t.getId()); // each move hits each target at most once
            applyHit(p, st, t, mv, fx, fz, now);
        }
    }

    /** Design 3.3 + Addendum A: base x (1 + 1%/Martial level) x Strength/race/nature melee mult x combo scaling. */
    public static float damage(ServerPlayer p, CombatMove mv, int comboHit) {
        int martial = p.getData(ModAttachments.KI_PROGRESSION).level(Track.MARTIAL);
        return (float) (mv.damage() * (1.0 + MARTIAL_PER_LEVEL * martial) * StatEffects.meleeMultiplier(p) * comboScale(comboHit));
    }

    /** 1.0 for hits 1-3, then x0.9 per hit, floor 0.5 (design 3.3). */
    public static double comboScale(int comboHit) {
        return comboHit <= 3 ? 1.0 : Math.max(0.5, Math.pow(0.9, comboHit - 3));
    }

    private static void applyHit(ServerPlayer p, CombatState st, LivingEntity t, CombatMove mv, double fx, double fz, long now) {
        // Burst i-frames (K1) are respected: no damage, no stun, no knockback.
        if (t instanceof ServerPlayer tp && state(tp).invulnerable(now)) return;
        ServerLevel level = p.serverLevel();
        Victim v = VICTIMS.get(t);
        boolean comboing = v != null && now <= v.stunUntil + COMBO_GRACE_TICKS;
        int comboHit = comboing ? v.combo + 1 : 1;
        float dmg = damage(p, mv, comboHit);

        DamageSource src = level.damageSources().source(ModDamageTypes.MELEE, p, p);
        t.invulnerableTime = 0; // vanilla's 10-tick window would eat fast combo hits (design 4.2)
        if (!t.hurt(src, dmg)) return; // PvP off, creative, cancelled by an event ...

        applyHitstun(t, mv.hitstun(), now);
        Victim nv = VICTIMS.get(t);
        if (nv != null) nv.combo = comboHit;
        if (t instanceof TrainingDummyEntity dummy) dummy.noteCombo(comboHit);

        // Knockback after the damage (the melee type is in minecraft:no_knockback, so vanilla adds none).
        CombatMove.Knockback kb = mv.knockback();
        if (kb.forward() > 0f) t.knockback(kb.forward(), -fx, -fz);
        if (kb.up() != 0f) t.setDeltaMovement(t.getDeltaMovement().add(0, kb.up() * (1.0 - kbResist(t)), 0));
        t.hurtMarked = true; // sync the new motion to a player victim

        // Minimal feel (M6 replaces with CombatEventPayload: hit-stop, numbers, shake).
        boolean heavy = kb.forward() >= 0.5f;
        SoundEvent hit = mv.sounds().hit().map(KiAttackParts.Present::soundOf)
                .orElse(heavy ? ModSounds.MELEE_HIT_HEAVY.get() : ModSounds.MELEE_HIT.get());
        level.playSound(null, t.getX(), t.getY(0.6), t.getZ(), hit, SoundSource.PLAYERS, 0.9f,
                0.9f + p.getRandom().nextFloat() * 0.2f + Math.min(comboHit, 8) * 0.02f);
        level.sendParticles(ParticleTypes.CRIT, t.getX() - fx * t.getBbWidth() * 0.5, t.getY(0.6), t.getZ() - fz * t.getBbWidth() * 0.5,
                heavy ? 14 : 6, 0.15, 0.2, 0.15, 0.3);

        awardMartial(p, st, t, now);
    }

    private static double kbResist(LivingEntity t) {
        AttributeInstance a = t.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        return a == null ? 0.0 : Mth.clamp(a.getValue(), 0.0, 1.0);
    }

    private static void awardMartial(ServerPlayer p, CombatState st, LivingEntity t, long now) {
        if (t instanceof ArmorStand) return;
        boolean dummy = t instanceof TrainingDummyEntity;
        st.martialAccum += MARTIAL_XP_PER_HIT * (dummy ? DUMMY_XP_MULT : 1f);
        int whole = (int) st.martialAccum;
        if (whole > 0) {
            st.martialAccum -= whole;
            Progression.addMasteryXp(p, Track.MARTIAL, whole);
        }
        if (dummy) {
            if (now - st.dummyExpWindowStart >= HOUR_TICKS) {
                st.dummyExpWindowStart = now;
                st.dummyExpInWindow = 0;
            }
            if (st.dummyExpInWindow < DUMMY_EXP_CAP) {
                st.dummyExpInWindow++;
                Progression.addExp(p, 1, ExpSource.TRAINING);
            }
        }
    }

    // ---- hitstun --------------------------------------------------------------------------------------------------------

    /**
     * Puts any living entity in hitstun for {@code ticks} (extends, never shortens). Players: movement + jump locked, current
     * melee / skill wind-up / ki charge cancelled, inputs refused except usable_in_hitstun skills (Burst). Mobs: navigation
     * stopped + movement locked. Stunned entities deal no melee damage (ProgressionEvents). Public: ki attacks may call it (K9).
     */
    public static void applyHitstun(LivingEntity t, int ticks, long now) {
        if (ticks <= 0) return;
        Victim v = VICTIMS.computeIfAbsent(t, k -> new Victim());
        v.stunUntil = Math.max(v.stunUntil, now + ticks);
        lock(t, v);
        if (t instanceof ServerPlayer tp) {
            CombatState ts = state(tp);
            ts.hitstunUntil = v.stunUntil;
            cancel(tp, ts, false);
            if (ts.skillWindingUp() || ts.skillHolding()) KiSkills.cancel(tp);
            KiCharge.interrupt(tp);
            PacketDistributor.sendToPlayer(tp, new MeleeStatePayload(-1, MeleeStatePayload.STUNNED, "", 0,
                    (int) Math.min(Integer.MAX_VALUE, v.stunUntil - now)));
        } else if (t instanceof Mob mob) {
            mob.getNavigation().stop();
        }
    }

    public static boolean isStunned(LivingEntity e, long now) {
        Victim v = VICTIMS.get(e);
        return v != null && now < v.stunUntil;
    }

    /** Combo breaker (Burst cast in hitstun, C.8): ends the stun and the combo at once. */
    public static void breakCombo(ServerPlayer p) {
        long now = p.level().getGameTime();
        Victim v = VICTIMS.remove(p);
        if (v != null) unlock(p, v);
        state(p).hitstunUntil = now;
        PacketDistributor.sendToPlayer(p, new MeleeStatePayload(-1, MeleeStatePayload.STUNNED, "", 0, 0));
    }

    private static void lock(LivingEntity t, Victim v) {
        if (v.locked) return;
        v.locked = true;
        addMod(t.getAttribute(Attributes.MOVEMENT_SPEED), STUN_MOVE_ID);
        addMod(t.getAttribute(Attributes.JUMP_STRENGTH), STUN_JUMP_ID);
    }

    private static void unlock(LivingEntity t, Victim v) {
        if (!v.locked) return;
        v.locked = false;
        AttributeInstance a = t.getAttribute(Attributes.MOVEMENT_SPEED);
        if (a != null) a.removeModifier(STUN_MOVE_ID);
        AttributeInstance j = t.getAttribute(Attributes.JUMP_STRENGTH);
        if (j != null) j.removeModifier(STUN_JUMP_ID);
    }

    private static void addMod(AttributeInstance a, ResourceLocation id) {
        if (a != null && !a.hasModifier(id)) {
            // Transient: never saved, so a crash mid-stun can't leave anyone frozen.
            a.addTransientModifier(new AttributeModifier(id, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (VICTIMS.isEmpty()) return;
        Iterator<Map.Entry<LivingEntity, Victim>> it = VICTIMS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<LivingEntity, Victim> e = it.next();
            LivingEntity t = e.getKey();
            Victim v = e.getValue();
            if (t.isRemoved() || !t.isAlive()) {
                unlock(t, v);
                it.remove();
                continue;
            }
            long now = t.level().getGameTime();
            if (now >= v.stunUntil) unlock(t, v);
            if (now > v.stunUntil + COMBO_GRACE_TICKS) it.remove();
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        VICTIMS.clear();
    }

    // ---- interplay with skills / stance / lifecycle ----------------------------------------------------------------------

    /** Skills may start only while melee is IDLE or in its cancel window (Addendum C.6 / M5). */
    public static boolean allowsSkill(CombatState st) {
        return st.melee.interruptible();
    }

    /** A skill / ki charge is starting: drop the melee move (only legal in the cancel window) and any chain / buffer. */
    public static void cancelForAction(ServerPlayer p, CombatState st) {
        if (st.melee.busy() || st.melee.buffered != null || st.melee.chainGrace > 0) cancel(p, st, true);
    }

    /** Ends the current move. Rejects a buffered input; tells observers (and the attacker if {@code notifySelf}). */
    public static void cancel(ServerPlayer p, CombatState st, boolean notifySelf) {
        boolean was = st.melee.busy();
        int dropped = st.melee.cancel();
        if (dropped >= 0) reply(p, dropped, MeleeStatePayload.REJECTED, st);
        if (was) {
            if (notifySelf) PacketDistributor.sendToPlayer(p, new MeleeStatePayload(-1, MeleeStatePayload.CANCELLED, "", 0, 0));
            PacketDistributor.sendToPlayersTrackingEntity(p, new MeleeSwingPayload(p.getId(), "", true));
        }
    }

    /** Login / logout / death / respawn / dimension change: silent reset (no packets). */
    public static void reset(ServerPlayer p) {
        CombatState st = state(p);
        st.melee.cancel();
        st.meleeHits.clear();
        st.hitstunUntil = Long.MIN_VALUE / 2;
        Victim v = VICTIMS.remove(p);
        if (v != null) unlock(p, v);
        AttributeInstance a = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (a != null) a.removeModifier(STUN_MOVE_ID);
        AttributeInstance j = p.getAttribute(Attributes.JUMP_STRENGTH);
        if (j != null) j.removeModifier(STUN_JUMP_ID);
    }
}

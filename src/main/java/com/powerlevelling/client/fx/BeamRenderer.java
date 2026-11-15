package com.powerlevelling.client.fx;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.powerlevelling.PowerLevelling;
import com.powerlevelling.client.ClientOrbState;
import com.powerlevelling.client.render.KiOrbModel;
import com.powerlevelling.config.ClientConfig;
import com.powerlevelling.ki.KiAttack;
import com.powerlevelling.ki.KiAttacks;
import com.powerlevelling.network.BeamPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Quaternionf;

/**
 * Client side of beams (K7): keeps one {@link ClientBeam} per owner from {@link BeamPayload}s and draws it from a level render
 * stage like {@link KiWorldFx}: a core of white cubes and two rolling coloured shells cut into cube segments with a bright
 * pulse band scrolling down the beam, a muzzle flare at the hands and a pulsing flare + ring at the tip. The client tick adds
 * the particles (tip sparks, block-hit dust, muzzle / along-the-beam sparks), the screen shake for the shooter and nearby
 * players (KiShake respects the screenShake option), and, for beam charges (orb shape "beam"), the aura flare, the full-charge
 * flare and the slight camera zoom. Everything ages on the wall clock (20 ticks per second), so no partial-tick maths.
 * Client only.
 */
@EventBusSubscriber(modid = PowerLevelling.MODID, value = Dist.CLIENT)
public final class BeamRenderer {
    private static final double NS_PER_TICK = 50_000_000.0;
    private static final float FADE_IN_TICKS = 3f;
    private static final float FADE_OUT_TICKS = 4f;
    /** No UPDATE for this many ticks: the owner left / the stop packet got lost; end the beam. */
    private static final float STALE_TICKS = 8f;
    private static final double MAX_DIST_SQ = 128.0 * 128.0;
    private static final double FX_DIST_SQ = 64.0 * 64.0;
    private static final float FULL_CHARGE_ZOOM = 5f;
    private static final int MAX_SEGMENTS = 96;
    private static final Vec3 UP = new Vec3(0, 1, 0);

    private static final class ClientBeam {
        int ownerId;
        ResourceLocation attack;
        float radius;
        float charge;
        int color;
        int core;
        float shake;
        Vec3 dir = new Vec3(0, 0, 1);
        Vec3 prevDir = new Vec3(0, 0, 1);
        float length;
        float prevLength;
        boolean hit;
        long startNs;
        long updateNs;
        long stopNs = -1L;
    }

    private static final Map<Integer, ClientBeam> BEAMS = new HashMap<>();
    /** Owners whose full-charge flare already played for the current charge. */
    private static final Set<Integer> FLARED = new HashSet<>();
    private static long lastTickNs = System.nanoTime();
    private static int tickCounter;
    private static float zoomCur;

    private BeamRenderer() {
    }

    /** True while this owner's beam is firing (started, not stopped): drives the two-hand pose (KiPoseClient). */
    public static boolean isFiring(int ownerId) {
        ClientBeam b = BEAMS.get(ownerId);
        return b != null && b.stopNs < 0L;
    }

    public static void reset() {
        BEAMS.clear();
        FLARED.clear();
        zoomCur = 0f;
        KiShake.setZoom(0f);
    }

    // ---- network entry -----------------------------------------------------------------------------------------------

    /** BeamPayload (client thread). */
    public static void apply(BeamPayload p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        long now = System.nanoTime();
        Vec3 dir = new Vec3(p.dx(), p.dy(), p.dz());
        dir = dir.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : dir.normalize();

        ClientBeam b = BEAMS.get(p.ownerId());
        if (p.phase() == BeamPayload.PHASE_START || b == null) {
            if (b == null && p.phase() == BeamPayload.PHASE_STOP) return;
            b = new ClientBeam();
            b.ownerId = p.ownerId();
            b.attack = p.attack();
            b.startNs = now;
            b.dir = dir;
            b.prevDir = dir;
            b.length = p.length();
            b.prevLength = p.length();
            KiAttack def = KiAttacks.resolve(mc.level.registryAccess(), p.attack());
            b.shake = def == null ? 0.3f : def.common().present().shake();
            BEAMS.put(p.ownerId(), b);
        } else {
            b.prevDir = currentDir(b, now);
            b.prevLength = currentLength(b, now);
            b.dir = dir;
            b.length = p.length();
        }
        b.updateNs = now;
        b.radius = p.radius();
        b.charge = p.charge();
        b.hit = p.hit();
        int c = p.color();
        int cc = p.coreColor();
        if (ClientConfig.FORCE_DEFAULT_ATTACK_COLORS.get()) {
            KiAttack def = KiAttacks.resolve(mc.level.registryAccess(), p.attack());
            if (def != null) {
                c = def.common().present().color();
                cc = def.common().present().coreColor();
            }
        }
        b.color = c;
        b.core = cc;

        if (p.phase() == BeamPayload.PHASE_STOP && b.stopNs < 0) {
            b.stopNs = now;
            endBurst(mc, b, now);
        }
    }

    private static Vec3 currentDir(ClientBeam b, long now) {
        float t = (float) Mth.clamp((now - b.updateNs) / NS_PER_TICK, 0.0, 1.0);
        Vec3 d = b.prevDir.scale(1.0 - t).add(b.dir.scale(t));
        return d.lengthSqr() < 1.0E-6 ? b.dir : d.normalize();
    }

    private static float currentLength(ClientBeam b, long now) {
        float t = (float) Mth.clamp((now - b.updateNs) / NS_PER_TICK, 0.0, 1.0);
        return Mth.lerp(t, b.prevLength, b.length);
    }

    /** Hands position of the beam: a bit ahead of the eye and below it; first person keeps it low so the camera isn't inside it. */
    private static Vec3 originOf(Entity owner, Vec3 dir, float frac, boolean first) {
        Vec3 eye = new Vec3(Mth.lerp(frac, owner.xOld, owner.getX()), Mth.lerp(frac, owner.yOld, owner.getY()) + owner.getEyeHeight(),
                Mth.lerp(frac, owner.zOld, owner.getZ()));
        Vec3 right = dir.cross(UP);
        right = right.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : right.normalize();
        Vec3 up = right.cross(dir);
        return first ? eye.add(dir.scale(1.0)).add(up.scale(-0.32)) : eye.add(dir.scale(0.78)).add(up.scale(-0.25));
    }

    // ---- client tick: particles, shake, charge flare, zoom ---------------------------------------------------------------

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        lastTickNs = System.nanoTime();
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) {
            if (!BEAMS.isEmpty()) reset();
            return;
        }
        if (mc.isPaused()) return;
        tickCounter++;
        long now = lastTickNs;
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        boolean particles = KiFx.particlesAllowed(mc);
        float density = KiFx.density();
        RandomSource rand = level.random;
        TextureAtlasSprite sprite = particles ? KiFx.sprite(mc) : null;
        boolean selfFiring = false;

        Iterator<ClientBeam> it = BEAMS.values().iterator();
        while (it.hasNext()) {
            ClientBeam b = it.next();
            Entity owner = level.getEntity(b.ownerId);
            if (owner == null) {
                it.remove();
                continue;
            }
            if (b.stopNs < 0 && (now - b.updateNs) / NS_PER_TICK > STALE_TICKS) { // lost the stop packet
                b.stopNs = now;
            }
            if (b.stopNs >= 0) {
                if ((now - b.stopNs) / NS_PER_TICK > FADE_OUT_TICKS) it.remove();
                continue;
            }
            boolean self = owner == mc.player;
            boolean first = self && mc.options.getCameraType().isFirstPerson();
            if (self) selfFiring = true;
            Vec3 dir = b.dir;
            Vec3 origin = originOf(owner, dir, 1f, first);
            double distSq = origin.distanceToSqr(cam);

            // Screen shake: full for the shooter, fading with distance for everyone else.
            if (b.shake > 0f) {
                float k = self ? 1f : (float) Mth.clamp(1.0 - (Math.sqrt(distSq) - 4.0) / 28.0, 0.0, 1.0);
                KiShake.add(b.shake * k, 3);
            }
            if (!particles || distSq > FX_DIST_SQ) continue;

            Vec3 tip = origin.add(dir.scale(b.length));
            float r = b.radius;
            // Muzzle sparks.
            for (int i = 0; i < 2 && KiTintParticle.canSpawn(); i++) {
                if (rand.nextFloat() > density) continue;
                Vec3 v = randomDir(rand).scale(0.08 + rand.nextDouble() * 0.1).add(dir.scale(0.15));
                mc.particleEngine.add(new KiTintParticle(level, origin.x, origin.y, origin.z, v.x, v.y, v.z,
                        KiFx.mix(b.color, 0xFFFFFF, rand.nextFloat() * 0.7f), 0.05f + rand.nextFloat() * 0.07f,
                        6 + rand.nextInt(5), -0.2f, 0.9f, sprite));
            }
            // Sparks along the beam.
            int along = Math.min(4, Mth.floor(b.length / 8f));
            for (int i = 0; i < along && KiTintParticle.canSpawn(); i++) {
                if (rand.nextFloat() > density) continue;
                Vec3 pos = origin.add(dir.scale(rand.nextDouble() * b.length));
                Vec3 v = randomDir(rand).scale(0.05 + rand.nextDouble() * 0.06);
                mc.particleEngine.add(new KiTintParticle(level, pos.x + v.x * 6, pos.y + v.y * 6, pos.z + v.z * 6, v.x, v.y, v.z,
                        KiFx.mix(b.color, 0xFFFFFF, rand.nextFloat() * 0.5f), 0.05f + rand.nextFloat() * 0.05f,
                        8 + rand.nextInt(6), -0.1f, 0.92f, sprite));
            }
            if (!b.hit) continue;
            // Tip sparks, flung back along the beam and sideways.
            int sparks = Mth.floor((3f + r * 4f) * density + rand.nextFloat());
            for (int i = 0; i < sparks && KiTintParticle.canSpawn(); i++) {
                Vec3 v = randomDir(rand).scale(0.12 + rand.nextDouble() * 0.16).add(dir.scale(-0.12));
                mc.particleEngine.add(new KiTintParticle(level, tip.x, tip.y, tip.z, v.x, v.y, v.z,
                        KiFx.mix(b.color, 0xFFFFFF, rand.nextFloat() * 0.7f), 0.06f + rand.nextFloat() * 0.09f,
                        10 + rand.nextInt(8), -0.45f, 0.93f, sprite));
            }
            // Block-hit dust: the block just behind the tip, if there is one.
            BlockPos bp = BlockPos.containing(tip.add(dir.scale(0.2)));
            BlockState state = level.getBlockState(bp);
            if (!state.isAir()) {
                int dust = Mth.floor(2f * density + rand.nextFloat() * 2f);
                for (int i = 0; i < dust; i++) {
                    Vec3 v = randomDir(rand).scale(0.15).add(dir.scale(-0.1));
                    level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, state), tip.x, tip.y, tip.z, v.x, v.y, v.z);
                }
            }
        }

        // Charging beams (orb shape "beam"): aura flare, full-charge flare, the shooter's camera zoom.
        float zoomTarget = selfFiring ? FULL_CHARGE_ZOOM * 0.6f : 0f;
        long gameTime = level.getGameTime();
        for (Map.Entry<Integer, ClientOrbState.Orb> e : ClientOrbState.entries()) {
            ClientOrbState.Orb orb = e.getValue();
            if (!"beam".equals(orb.shape)) continue;
            Entity ent = level.getEntity(e.getKey());
            if (!(ent instanceof Player pl)) continue;
            float prog = orb.progress(orb.elapsed(gameTime, 0f));
            boolean self = pl == mc.player;
            if (self) zoomTarget = Math.max(zoomTarget, FULL_CHARGE_ZOOM * prog * prog);
            if (!particles || pl.distanceToSqr(cam.x, cam.y, cam.z) > FX_DIST_SQ) continue;
            boolean firstSelf = self && mc.options.getCameraType().isFirstPerson();

            if (tickCounter % 2 == 0) { // aura flare: a ring of sparks rising around the body, denser with charge
                int count = Mth.floor((2f + 9f * prog) * density * (firstSelf ? 0.4f : 1f) + rand.nextFloat());
                for (int i = 0; i < count && KiTintParticle.canSpawn(); i++) {
                    double ang = rand.nextDouble() * Mth.TWO_PI;
                    double rr = 0.55 + prog * 0.45 + rand.nextDouble() * 0.2;
                    mc.particleEngine.add(new KiTintParticle(level, pl.getX() + Math.cos(ang) * rr, pl.getY() + rand.nextDouble() * 0.4,
                            pl.getZ() + Math.sin(ang) * rr, 0, 0.07 + rand.nextDouble() * 0.12 + prog * 0.08, 0,
                            KiFx.mix(orb.color, 0xFFFFFF, rand.nextFloat() * 0.5f), 0.06f + rand.nextFloat() * 0.06f,
                            12 + rand.nextInt(8), 0.0f, 0.96f, sprite));
                }
            }
            if (prog >= 0.999f && FLARED.add(e.getKey())) { // full charge: one big flare
                KiShake.add(self ? 0.25f : 0.1f, 6);
                int n = Mth.floor(26 * density);
                for (int i = 0; i < n && KiTintParticle.canSpawn(); i++) {
                    Vec3 v = randomDir(rand).scale(0.15 + rand.nextDouble() * 0.2);
                    mc.particleEngine.add(new KiTintParticle(level, pl.getX(), pl.getY() + pl.getBbHeight() * 0.5, pl.getZ(),
                            v.x, v.y * 0.6, v.z, KiFx.mix(orb.color, 0xFFFFFF, rand.nextFloat() * 0.6f),
                            0.08f + rand.nextFloat() * 0.1f, 10 + rand.nextInt(8), -0.2f, 0.9f, sprite));
                }
            }
        }
        FLARED.removeIf(id -> ClientOrbState.get(id) == null);

        zoomCur = Mth.lerp(0.2f, zoomCur, zoomTarget);
        if (zoomCur < 0.02f && zoomTarget == 0f) zoomCur = 0f;
        KiShake.setZoom(zoomCur);
    }

    private static Vec3 randomDir(RandomSource rand) {
        Vec3 d = new Vec3(rand.nextGaussian(), rand.nextGaussian(), rand.nextGaussian());
        return d.lengthSqr() < 1.0E-6 ? UP : d.normalize();
    }

    /** The beam ends: a puff of sparks at the muzzle and at the tip. */
    private static void endBurst(Minecraft mc, ClientBeam b, long now) {
        ClientLevel level = mc.level;
        Entity owner = level == null ? null : level.getEntity(b.ownerId);
        if (owner == null || !KiFx.particlesAllowed(mc)) return;
        boolean first = owner == mc.player && mc.options.getCameraType().isFirstPerson();
        Vec3 origin = originOf(owner, b.dir, 1f, first);
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        if (origin.distanceToSqr(cam) > FX_DIST_SQ) return;
        TextureAtlasSprite sprite = KiFx.sprite(mc);
        RandomSource rand = level.random;
        int n = Mth.floor(10 * KiFx.density());
        for (int i = 0; i < n && KiTintParticle.canSpawn(); i++) {
            Vec3 v = randomDir(rand).scale(0.1 + rand.nextDouble() * 0.12);
            mc.particleEngine.add(new KiTintParticle(level, origin.x, origin.y, origin.z, v.x, v.y, v.z,
                    KiFx.mix(b.color, 0xFFFFFF, rand.nextFloat() * 0.6f), 0.06f + rand.nextFloat() * 0.06f,
                    8 + rand.nextInt(6), -0.2f, 0.9f, sprite));
        }
    }

    // ---- drawing -----------------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        if (BEAMS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        long now = System.nanoTime();
        float frac = (float) Mth.clamp((now - lastTickNs) / NS_PER_TICK, 0.0, 1.0);
        Vec3 cam = event.getCamera().getPosition();
        PoseStack ps = event.getPoseStack();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();

        ps.pushPose();
        ps.translate(-cam.x, -cam.y, -cam.z); // from here on, coordinates are world coordinates
        for (ClientBeam b : BEAMS.values()) {
            Entity owner = mc.level.getEntity(b.ownerId);
            if (owner == null) continue;
            draw(mc, ps, buf, b, owner, now, frac, cam);
        }
        ps.popPose();
        buf.endBatch(KiOrbModel.effectType());
    }

    private static void draw(Minecraft mc, PoseStack ps, MultiBufferSource buf, ClientBeam b, Entity owner, long now,
                             float frac, Vec3 cam) {
        float age = (float) ((now - b.startNs) / NS_PER_TICK);
        float fadeIn = Mth.clamp(age / FADE_IN_TICKS, 0f, 1f);
        float fadeOut = b.stopNs < 0 ? 1f : 1f - (float) ((now - b.stopNs) / NS_PER_TICK) / FADE_OUT_TICKS;
        if (fadeOut <= 0f) return;
        float vis = fadeIn * fadeOut;

        boolean first = owner == mc.player && mc.options.getCameraType().isFirstPerson();
        Vec3 dir = currentDir(b, now);
        Vec3 origin = originOf(owner, dir, frac, first);
        if (origin.distanceToSqr(cam) > MAX_DIST_SQ) return;
        float length = currentLength(b, now) * Mth.clamp(age / 2.5f, 0.15f, 1f); // shoots out in ~2.5 ticks
        if (length < 0.2f) return;
        float rad = b.radius * (0.35f + 0.65f * vis) * (first ? 0.55f : 1f);

        ps.pushPose();
        ps.translate(origin.x, origin.y, origin.z);
        // Local +Z along the beam.
        ps.mulPose(new Quaternionf().rotationTo(0f, 0f, 1f, (float) dir.x, (float) dir.y, (float) dir.z));

        float seg = Math.max(0.5f, length / MAX_SEGMENTS);
        int n = Math.max(1, (int) Math.ceil(length / seg));
        float coreR = rad * 0.4f;
        float shellR = rad * 0.95f;
        float glowR = rad * 1.5f;
        int pale = KiOrbModel.mix(b.color, 0xFFFFFF, 0.55f);
        for (int i = 0; i < n; i++) {
            float z0 = i * seg;
            float hl = Math.min(seg, length - z0) * 0.5f;
            if (hl <= 0f) break;
            float wave = age * 1.8f - i * 0.55f;
            float pulse = 0.5f + 0.5f * Mth.sin(wave);
            float band = Mth.sin(i * 0.35f - age * 1.6f);
            band = band > 0f ? band * band * band * band : 0f; // bright band scrolling away from the hands

            ps.pushPose();
            ps.translate(0f, 0f, z0 + hl);
            // Core: solid white cubes, breathing along the beam.
            KiOrbModel.renderBox(ps, buf, coreR * (0.85f + 0.3f * pulse), coreR * (0.85f + 0.3f * pulse), hl * 0.97f, b.core, 0.95f * vis);
            if (band > 0.05f) {
                KiOrbModel.renderBox(ps, buf, coreR * 1.5f, coreR * 1.5f, hl * 0.97f, pale, 0.55f * band * vis);
            }
            // Inner shell, rolling about the beam axis.
            ps.pushPose();
            ps.mulPose(Axis.ZP.rotationDegrees(age * 8f + i * 11f));
            float shell = shellR * (0.9f + 0.2f * (0.5f + 0.5f * Mth.sin(wave * 0.7f + 1f)));
            KiOrbModel.renderBox(ps, buf, shell, shell, hl * 0.95f, b.color, (0.34f + 0.2f * pulse) * vis);
            ps.popPose();
            // Outer glow, rolling the other way.
            ps.pushPose();
            ps.mulPose(Axis.ZP.rotationDegrees(-age * 5f + i * 17f + 20f));
            float glow = glowR * (1f + 0.1f * Mth.sin(wave * 0.5f + 2f));
            KiOrbModel.renderBox(ps, buf, glow, glow, hl * 0.9f, b.color, (0.10f + 0.07f * pulse) * vis);
            ps.popPose();
            
            // DRAGON BALL STYLE: Add camera-independent radial glow halos at intervals along the beam
            // These create the signature Dragon Ball "energy beam" look with soft spherical glows
            if (i % 3 == 0 && vis > 0.3f) { // Every 3rd segment, and only when visible enough
                float haloRadius = shell * 1.2f;
                float haloAlpha = (0.15f + 0.1f * pulse) * vis;
                KiOrbModel.renderGlowHalos(ps, buf, haloRadius, b.color, age + i * 0.5f, haloAlpha);
                
                // Energy crackle at intervals
                int crackleCount = 2 + (int) (rad * 2f);
                KiOrbModel.renderEnergySpikes(ps, buf, crackleCount, shell * 0.8f, shell * 1.5f, b.color, age + i * 0.7f, vis * 0.5f);
            }
            ps.popPose();
        }

        // DRAGON BALL STYLE: Enhanced muzzle flare with glow halos and crackle
        flare(ps, buf, rad * (1.1f + 0.2f * Mth.sin(age * 1.3f)), b, age, vis, false); // muzzle (z = 0)
        
        // Add glow halos at muzzle
        if (vis > 0.3f) {
            float muzzleHaloRad = rad * 1.8f;
            KiOrbModel.renderGlowHalos(ps, buf, muzzleHaloRad, b.color, age, vis * 0.4f);
            KiOrbModel.renderEnergySpikes(ps, buf, 4 + (int)(rad * 3f), rad * 0.6f, rad * 1.2f, b.color, age * 1.5f, vis * 0.6f);
        }
        
        ps.translate(0f, 0f, length);
        
        // DRAGON BALL STYLE: Enhanced tip with glow halos
        float tipSize = b.hit ? rad * (1.6f + 0.35f * Mth.sin(age * 1.7f)) : rad * 0.9f;
        flare(ps, buf, tipSize, b, age, vis, b.hit); // tip
        
        // Add glow halos at tip (especially when hitting something)
        if (vis > 0.3f) {
            float tipHaloRad = tipSize * 1.5f;
            float tipAlpha = b.hit ? vis * 0.6f : vis * 0.3f;
            KiOrbModel.renderGlowHalos(ps, buf, tipHaloRad, b.color, age * 1.3f, tipAlpha);
            if (b.hit) {
                KiOrbModel.renderEnergySpikes(ps, buf, 6 + (int)(rad * 4f), tipSize * 0.5f, tipSize * 1.8f, b.color, age * 2f, vis * 0.7f);
            }
        }
        
        ps.popPose();
    }

    /** Two cubes tumbling in each other + (at a tip that hits something) a ring facing back along the beam. */
    private static void flare(PoseStack ps, MultiBufferSource buf, float size, ClientBeam b, float age, float vis, boolean ring) {
        for (int k = 0; k < 2; k++) {
            ps.pushPose();
            ps.mulPose(Axis.YP.rotationDegrees(age * (9f + 5f * k) + 45f * k));
            ps.mulPose(Axis.XP.rotationDegrees(age * (7f - 4f * k) + 30f * k));
            float s = size * (k == 0 ? 0.55f : 0.85f);
            KiOrbModel.renderBox(ps, buf, s, s, s, k == 0 ? b.core : b.color, (k == 0 ? 0.9f : 0.4f) * vis);
            ps.popPose();
        }
        if (ring) {
            ps.pushPose();
            ps.mulPose(Axis.XP.rotationDegrees(90f)); // ring plane XZ -> XY, i.e. facing along the beam
            int segs = Mth.clamp((int) (size * 8f), 10, 40);
            KiOrbModel.renderRing(ps, buf, size * 1.4f, Math.max(0.12f, size * 0.22f), b.color, 0.7f * vis, segs, age * 14f);
            ps.popPose();
        }
    }
}

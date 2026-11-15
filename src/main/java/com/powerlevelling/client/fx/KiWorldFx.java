package com.powerlevelling.client.fx;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.powerlevelling.PowerLevelling;
import com.powerlevelling.client.render.KiOrbModel;
import com.powerlevelling.config.ClientConfig;
import com.powerlevelling.ki.KiOrb;
import com.powerlevelling.network.KiTracePayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Short-lived world-space cube effects drawn from a level render stage (K1): the needle's aim line and shot trace (fades over
 * 6 ticks), the burst's expanding ring + dome flash, and the sphere explosion's ground ring. Effects age on the wall clock
 * (20 ticks per second) so the renderer needs no tick hook and no partial-tick maths. Everything is additive cubes from
 * {@link KiOrbModel}; nothing here touches the network (KiFx / ModNetwork call in). Client only.
 */
@EventBusSubscriber(modid = PowerLevelling.MODID, value = Dist.CLIENT)
public final class KiWorldFx {
    private static final double NS_PER_TICK = 50_000_000.0;
    private static final float TRACE_FADE_TICKS = 6f;
    /** An aim line lives this long unless the next tick's aim line replaces it. */
    private static final float AIM_TICKS = 2.5f;
    private static final double MAX_DIST_SQ = 96.0 * 96.0;

    private static final class Line {
        Vec3 from;
        Vec3 to;
        int color;
        int core;
        long startNs;
        boolean aim;
    }

    private static final class Ring {
        Vec3 at;
        float radius;
        int color;
        int core;
        long startNs;
        float expandTicks;
        float lifeTicks;
        boolean dome; // true = burst (ring + dome), false = ground decal
    }

    private static final List<Line> LINES = new ArrayList<>();
    private static final Map<Integer, Line> AIMS = new HashMap<>();
    private static final List<Ring> RINGS = new ArrayList<>();

    private KiWorldFx() {
    }

    public static void reset() {
        LINES.clear();
        AIMS.clear();
        RINGS.clear();
    }

    // ---- entry points ------------------------------------------------------------------------------------------------

    /** KiTracePayload: AIM replaces that shooter's aim line, FIRE adds a trace and a few sparks along it. */
    public static void trace(KiTracePayload p) {
        Line l = new Line();
        l.from = p.from();
        l.to = p.to();
        int c = p.color();
        int cc = p.coreColor();
        if (ClientConfig.FORCE_DEFAULT_ATTACK_COLORS.get()) {
            var mc = Minecraft.getInstance();
            var atk = mc.level == null ? null : com.powerlevelling.ki.KiAttacks.resolve(mc.level.registryAccess(), p.attack());
            if (atk != null) {
                c = atk.common().present().color();
                cc = atk.common().present().coreColor();
            }
        }
        l.color = c;
        l.core = cc;
        l.startNs = System.nanoTime();
        if (p.phase() == KiTracePayload.PHASE_AIM) {
            l.aim = true;
            AIMS.put(p.shooterId(), l);
        } else {
            AIMS.remove(p.shooterId());
            LINES.add(l);
            sparksAlong(l);
        }
    }

    /** Burst: ring expands to {@code radius} over {@code expandTicks}, dome flashes, both fade out shortly after. */
    public static void burst(Vec3 at, float radius, int color, int core, int expandTicks) {
        Ring r = new Ring();
        r.at = at;
        r.radius = radius;
        r.color = color;
        r.core = core;
        r.startNs = System.nanoTime();
        r.expandTicks = Math.max(1, expandTicks);
        r.lifeTicks = r.expandTicks + 5f;
        r.dome = true;
        RINGS.add(r);
    }

    /** Sphere explosion decal: a flat ring that expands to {@code radius} in ~8 ticks and fades over ~16. */
    public static void groundRing(Vec3 at, float radius, int color) {
        Ring r = new Ring();
        r.at = at;
        r.radius = radius;
        r.color = color;
        r.core = 0xFFFFFF;
        r.startNs = System.nanoTime();
        r.expandTicks = 8f;
        r.lifeTicks = 16f;
        r.dome = false;
        RINGS.add(r);
    }

    /** Disc hit: two crossed, randomly tilted slash lines at {@code at} (fade like a trace). */
    public static void slash(Vec3 at, int color, int core) {
        java.util.concurrent.ThreadLocalRandom rnd = java.util.concurrent.ThreadLocalRandom.current();
        double base = rnd.nextDouble() * Math.PI;
        for (int i = 0; i < 2; i++) {
            double ang = base + i * (0.9 + rnd.nextDouble() * 0.5);
            double tilt = (rnd.nextDouble() - 0.5) * 1.2;
            Vec3 d = new Vec3(Math.cos(ang), tilt, Math.sin(ang)).normalize().scale(0.75);
            Line l = new Line();
            l.from = at.subtract(d);
            l.to = at.add(d);
            l.color = color;
            l.core = core;
            l.startNs = System.nanoTime();
            LINES.add(l);
        }
    }

    /** Bomb warning: a quick flat ring at the blast radius, redrawn on every blink. */
    public static void pulseRing(Vec3 at, float radius, int color) {
        Ring r = new Ring();
        r.at = at;
        r.radius = radius;
        r.color = color;
        r.core = 0xFFFFFF;
        r.startNs = System.nanoTime();
        r.expandTicks = 3f;
        r.lifeTicks = 9f;
        r.dome = false;
        RINGS.add(r);
    }

    private static void sparksAlong(Line l) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || !KiFx.particlesAllowed(mc)) return;
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        if (l.from.distanceToSqr(cam) > MAX_DIST_SQ) return;
        Vec3 d = l.to.subtract(l.from);
        double len = d.length();
        float wanted = (float) Math.min(24.0, len * 0.5) * KiFx.density();
        int count = Mth.floor(wanted);
        RandomSource rand = level.random;
        if (rand.nextFloat() < wanted - count) count++;
        TextureAtlasSprite sprite = KiFx.sprite(mc);
        for (int i = 0; i < count && KiTintParticle.canSpawn(); i++) {
            double t = rand.nextDouble();
            Vec3 pos = l.from.add(d.scale(t));
            mc.particleEngine.add(new KiTintParticle(level, pos.x, pos.y, pos.z,
                    (rand.nextDouble() - 0.5) * 0.04, (rand.nextDouble() - 0.5) * 0.04, (rand.nextDouble() - 0.5) * 0.04,
                    KiFx.mix(l.color, 0xFFFFFF, rand.nextFloat() * 0.6f), 0.04f + rand.nextFloat() * 0.04f,
                    6 + rand.nextInt(5), -0.3f, 0.9f, sprite));
        }
    }

    // ---- drawing -----------------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        if (LINES.isEmpty() && AIMS.isEmpty() && RINGS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        long now = System.nanoTime();
        Vec3 cam = event.getCamera().getPosition();
        PoseStack ps = event.getPoseStack();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();

        ps.pushPose();
        ps.translate(-cam.x, -cam.y, -cam.z); // from here on, coordinates are world coordinates

        Iterator<Line> li = LINES.iterator();
        while (li.hasNext()) {
            Line l = li.next();
            float age = (float) ((now - l.startNs) / NS_PER_TICK);
            if (age >= TRACE_FADE_TICKS) {
                li.remove();
                continue;
            }
            float k = 1f - age / TRACE_FADE_TICKS;
            KiOrbModel.renderLine(ps, buf, l.from, l.to, 0.16f * (0.4f + 0.6f * k), l.color, l.core, k);
        }

        Iterator<Line> ai = AIMS.values().iterator();
        while (ai.hasNext()) {
            Line l = ai.next();
            float age = (float) ((now - l.startNs) / NS_PER_TICK);
            if (age >= AIM_TICKS) {
                ai.remove();
                continue;
            }
            KiOrbModel.renderLine(ps, buf, l.from, l.to, 0.035f, l.color, l.core, 0.35f);
        }

        Iterator<Ring> ri = RINGS.iterator();
        while (ri.hasNext()) {
            Ring r = ri.next();
            float age = (float) ((now - r.startNs) / NS_PER_TICK);
            if (age >= r.lifeTicks) {
                ri.remove();
                continue;
            }
            float grow = KiOrb.easeOut(Math.min(1f, age / r.expandTicks));
            float fade = 1f - age / r.lifeTicks;
            ps.pushPose();
            ps.translate(r.at.x, r.at.y, r.at.z);
            if (r.dome) {
                float radius = Math.max(0.2f, r.radius * grow);
                int segs = Mth.clamp((int) (radius * 7f), 12, 56);
                KiOrbModel.renderRing(ps, buf, radius, 0.28f + 0.1f * fade, r.color, 0.9f * fade, segs, age * 20f);
                KiOrbModel.renderRing(ps, buf, radius * 0.93f, 0.18f, r.core, 0.6f * fade, segs, -age * 12f);
                KiOrbModel.renderDome(ps, buf, radius * 0.9f, 0.45f, r.color, 0.30f * fade * fade);
                
                // DRAGON BALL STYLE: Add radial glow halos at burst/dome center
                // Creates the signature Dragon Ball explosion "flash" look
                if (fade > 0.1f) {
                    float haloRad = radius * 1.3f;
                    KiOrbModel.renderGlowHalos(ps, buf, haloRad, r.color, age * 3f, fade * 0.5f);
                    int spikeCount = 8 + (int) (radius * 3f);
                    KiOrbModel.renderEnergySpikes(ps, buf, spikeCount, radius * 0.4f, radius * 1.2f, r.color, age * 2.5f, fade * 0.6f);
                }
            } else {
                float radius = Math.max(0.2f, r.radius * grow);
                int segs = Mth.clamp((int) (radius * 6f), 10, 48);
                ps.translate(0.0, 0.06, 0.0);
                KiOrbModel.renderRing(ps, buf, radius, 0.22f, r.color, 0.85f * fade, segs, age * 10f);
                
                // DRAGON BALL STYLE: Ground ring gets subtle glow halos too
                if (fade > 0.15f && radius > 1.5f) {
                    KiOrbModel.renderGlowHalos(ps, buf, radius * 0.8f, r.color, age * 1.5f, fade * 0.25f);
                }
            }
            ps.popPose();
        }

        ps.popPose();
        buf.endBatch(KiOrbModel.effectType());
    }
}

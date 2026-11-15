package com.powerlevelling.client;

import java.util.HashMap;
import java.util.Map;

import com.powerlevelling.ki.KiOrb;
import com.powerlevelling.network.OrbStatePayload;

import net.minecraft.world.phys.Vec3;

/**
 * Client cache of every visible player's forming ki orb (from OrbStatePayload). Radius is computed locally from the game
 * time. Plain data, client thread only, no client-only imports (referenced from the payload registration).
 */
public final class ClientOrbState {
    public static final class Orb {
        public final String attackId;
        public long start;
        public final int duration;
        public final int grow;
        public final float minRadius;
        public final float maxRadius;
        public final int color;
        public final int coreColor;
        public final int hand;
        public final int anchor;
        /** The attack's present.shape ("disc", "barrage", "homing", "bomb", ... or ""): picks the in-hand look (KiOrbStyles). */
        public final String shape;
        /** present.pose charge / fire ids ("" = none): KiPoseTable. */
        public final String poseCharge;
        public final String poseFire;
        private boolean aligned;
        /** Client ticks until the next chime (KiOrbFx). */
        public int soundCooldown;
        public boolean fullChimed;
        /** World position of the orb centre as seen by the last third-person render (null / stale = unknown). */
        public Vec3 renderedPos;
        public long renderedAt = Long.MIN_VALUE;

        Orb(OrbStatePayload p) {
            attackId = p.attackId();
            start = p.startGameTime();
            duration = p.durationTicks();
            grow = p.growTicks();
            minRadius = p.minRadius();
            maxRadius = p.maxRadius();
            int c = p.color();
            int cc = p.coreColor();
            if (com.powerlevelling.config.ClientConfig.FORCE_DEFAULT_ATTACK_COLORS.get()) {
                // Ignore the server-resolved player colour: look the attack's own colours up again.
                var mcLevel = net.minecraft.client.Minecraft.getInstance().level;
                var rl = net.minecraft.resources.ResourceLocation.tryParse(attackId);
                var atk = mcLevel == null || rl == null ? null
                        : com.powerlevelling.ki.KiAttacks.resolve(mcLevel.registryAccess(), rl);
                if (atk != null) {
                    c = atk.common().present().color();
                    cc = atk.common().present().coreColor();
                }
            }
            color = c;
            coreColor = cc;
            hand = p.hand();
            anchor = p.anchor();
            String sh = "";
            String pc = "";
            String pf = "";
            var lvl = net.minecraft.client.Minecraft.getInstance().level;
            var attackRl = net.minecraft.resources.ResourceLocation.tryParse(attackId);
            if (lvl != null && attackRl != null) {
                var a = com.powerlevelling.ki.KiAttacks.resolve(lvl.registryAccess(), attackRl);
                if (a != null) {
                    sh = a.common().present().shape();
                    pc = a.common().present().pose().charge();
                    pf = a.common().present().pose().fire();
                }
            }
            shape = sh;
            poseCharge = pc;
            poseFire = pf;
        }

        /** Ticks since the orb started (>= 0). A client clock that lags the server's start time is pulled back to "now". */
        public float elapsed(long gameTime, float partialTick) {
            if (!aligned) {
                start = Math.min(start, gameTime);
                aligned = true;
            }
            return Math.max(0f, (gameTime - start) + partialTick);
        }

        public float radius(float elapsed) {
            return KiOrb.radius(elapsed, duration, grow, minRadius, maxRadius);
        }

        public float progress(float elapsed) {
            return KiOrb.progress(elapsed, duration);
        }
    }

    private static final Map<Integer, Orb> ORBS = new HashMap<>();

    private ClientOrbState() {
    }

    public static void apply(OrbStatePayload p) {
        if (!p.active()) {
            com.powerlevelling.client.pose.KiPoseClient.onOrbEnded(p.entityId(), ORBS.remove(p.entityId()));
            if (p.entityId() == ClientVisualState.selfId) ClientSkillState.reset(); // server ended it (fired / cancelled)
            return;
        }
        ORBS.put(p.entityId(), new Orb(p));
    }

    public static Orb get(int entityId) {
        return ORBS.get(entityId);
    }

    public static boolean isEmpty() {
        return ORBS.isEmpty();
    }

    /** Live view for FX code (do not modify). */
    public static Iterable<Map.Entry<Integer, Orb>> entries() {
        return ORBS.entrySet();
    }

    public static void remove(int entityId) {
        ORBS.remove(entityId);
    }

    public static void reset() {
        ORBS.clear();
    }
}

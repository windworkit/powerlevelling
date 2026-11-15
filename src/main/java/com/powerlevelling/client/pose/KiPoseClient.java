package com.powerlevelling.client.pose;

import java.util.HashMap;
import java.util.Map;

import com.powerlevelling.PowerLevelling;
import com.powerlevelling.client.ClientMelee;
import com.powerlevelling.client.ClientOrbState;
import com.powerlevelling.client.fx.BeamRenderer;
import com.powerlevelling.network.OrbStatePayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.HumanoidArm;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Client ki poses for every player. The target pose is derived each tick from state the client already has: an active beam
 * -> two_hand_forward; a forming orb (ClientOrbState) -> its charge pose; for a few ticks after the orb ended -> its fire pose.
 * Poses blend in/out over their blend ticks, and between poses with a crossfade. {@link #apply} is called from
 * PlayerModelMixin at the tail of PlayerModel#setupAnim (so vanilla animation is already computed and we override on top).
 */
@EventBusSubscriber(modid = PowerLevelling.MODID, value = Dist.CLIENT)
public final class KiPoseClient {
    private static final class Track {
        KiPoseTable.Pose cur;
        KiPoseTable.Pose from;
        float t;
        float fromT;
        float weight, prevWeight;
        float mix = 1f, prevMix = 1f;
        String fireId = "";
        int token;
        long fireUntil = Long.MIN_VALUE;
    }

    private static final Map<Integer, Track> TRACKS = new HashMap<>();

    private KiPoseClient() {
    }

    // ---- state ---------------------------------------------------------------------------------------------------------

    /** Called by ClientOrbState when a player's orb ends (fired or cancelled): hold its fire pose briefly. */
    public static void onOrbEnded(int entityId, ClientOrbState.Orb orb) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || orb == null) return;
        KiPoseTable.Pose fire = fireOf(orb);
        if (fire == null) return;
        Track tr = TRACKS.computeIfAbsent(entityId, k -> new Track());
        tr.fireId = fire.id;
        tr.fireUntil = mc.level.getGameTime() + fire.hold();
    }

    private static KiPoseTable.Pose chargeOf(ClientOrbState.Orb o) {
        KiPoseTable.Pose p = KiPoseTable.get(o.poseCharge);
        return p != null ? p : KiPoseTable.get(fallback(o, false));
    }

    private static KiPoseTable.Pose fireOf(ClientOrbState.Orb o) {
        KiPoseTable.Pose p = KiPoseTable.get(o.poseFire);
        return p != null ? p : KiPoseTable.get(fallback(o, true));
    }

    /** Default pose by attack shape, then by hand / anchor, when the JSON pose id is missing or unknown. */
    private static String fallback(ClientOrbState.Orb o, boolean fire) {
        switch (o.shape) {
            case "disc": return fire ? "disc_throw" : "disc_raise";
            case "barrage": return "barrage_alternate";
            case "homing": return "homing_circle";
            case "bomb": return fire ? "bomb_lob" : "bomb_hold";
            case "needle": return "finger_point";
            case "ring": return fire ? "arms_spread" : "arms_cross_flex";
            case "beam": return fire ? "two_hand_forward" : "two_hand_hip_cup";
            case "orb_cage": return fire ? "overhead_throw" : "overhead_two_hand";
            default: break;
        }
        return switch (o.hand) {
            case OrbStatePayload.HAND_FINGER -> "finger_point";
            case OrbStatePayload.HAND_NONE -> fire ? "arms_spread" : "arms_cross_flex";
            case OrbStatePayload.HAND_OFF -> "palm_forward_off";
            case OrbStatePayload.HAND_BOTH -> o.anchor == OrbStatePayload.ANCHOR_HIP ? (fire ? "two_hand_forward" : "two_hand_hip_cup")
                    : o.anchor == OrbStatePayload.ANCHOR_OVERHEAD ? (fire ? "overhead_throw" : "overhead_two_hand")
                    : "two_hand_forward";
            default -> "palm_forward";
        };
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            TRACKS.clear();
            return;
        }
        long now = mc.level.getGameTime();
        for (AbstractClientPlayer p : mc.level.players()) {
            int id = p.getId();
            ClientOrbState.Orb orb = ClientOrbState.get(id);
            Track tr = TRACKS.get(id);
            boolean beam = BeamRenderer.isFiring(id);
            ClientMelee.Swing swing = ClientMelee.swing(id, now); // M5 melee swing (predicted for self, synced for others)
            if (tr == null) {
                if (orb == null && !beam && swing == null) continue;
                tr = new Track();
                TRACKS.put(id, tr);
            }
            KiPoseTable.Pose target = null;
            int token = 0;
            if (beam) target = KiPoseTable.get("two_hand_forward");
            else if (orb != null) target = chargeOf(orb);
            else if (swing != null) {
                target = swing.pose();
                token = swing.token();
            } else if (tr.fireUntil > now) target = KiPoseTable.get(tr.fireId);
            tick(tr, target, token);
        }
        TRACKS.entrySet().removeIf(e -> mc.level.getEntity(e.getKey()) == null || (e.getValue().cur == null
                && e.getValue().fireUntil <= now && ClientOrbState.get(e.getKey()) == null));
    }

    private static void tick(Track tr, KiPoseTable.Pose target, int token) {
        tr.prevWeight = tr.weight;
        tr.prevMix = tr.mix;
        if (target != null) {
            if (tr.cur != target || tr.token != token) { // a new token restarts the same pose (melee chain)
                tr.token = token;
                if (tr.cur != null && tr.weight > 0.02f) {
                    tr.from = tr.cur;
                    tr.fromT = tr.t;
                    tr.mix = 0f;
                    tr.prevMix = 0f;
                } else {
                    tr.from = null;
                    tr.mix = 1f;
                    tr.prevMix = 1f;
                }
                tr.cur = target;
                tr.t = 0f;
            }
            tr.weight = Math.min(1f, tr.weight + 1f / target.blendIn);
            if (tr.from != null) {
                tr.mix = Math.min(1f, tr.mix + 1f / target.blendIn);
                if (tr.mix >= 1f) tr.from = null;
            }
        } else if (tr.cur != null) {
            tr.weight = Math.max(0f, tr.weight - 1f / tr.cur.blendOut);
            if (tr.weight <= 0f) {
                tr.cur = null;
                tr.from = null;
            }
        }
        tr.t += 1f;
        if (tr.from != null) tr.fromT += 1f;
    }

    // ---- apply (called from PlayerModelMixin) ---------------------------------------------------------------------------

    private static float smooth(float x) {
        return x * x * (3f - 2f * x);
    }

    public static void apply(PlayerModel<?> m, AbstractClientPlayer player, float partial) {
        Track tr = TRACKS.get(player.getId());
        if (tr == null || tr.cur == null) return;
        if (player.isFallFlying() || player.isSleeping() || player.isVisuallyCrawling()) return;
        float w = smooth(Math.min(1f, Math.max(0f, tr.prevWeight + (tr.weight - tr.prevWeight) * partial)));
        if (w <= 0.001f) return;
        boolean rightMain = player.getMainArm() == HumanoidArm.RIGHT;

        KiPoseTable.Result c = new KiPoseTable.Result();
        tr.cur.eval(tr.t + partial, rightMain, c);
        KiPoseTable.Result f = null;
        float mix = 1f;
        if (tr.from != null) {
            f = new KiPoseTable.Result();
            tr.from.eval(tr.fromT + partial, rightMain, f);
            mix = smooth(Math.min(1f, Math.max(0f, tr.prevMix + (tr.mix - tr.prevMix) * partial)));
        }
        float pitch = m.head.xRot;
        float yaw = m.head.yRot;
        float body = (f == null ? c.body : f.body + (c.body - f.body) * mix) * w;

        blend(m.rightArm, c.rv, c.r, c.rf, f != null && f.rv, f == null ? null : f.r, f != null && f.rf, mix, w, pitch, yaw, body);
        blend(m.leftArm, c.lv, c.l, c.lf, f != null && f.lv, f == null ? null : f.l, f != null && f.lf, mix, w, pitch, yaw, body);
        m.body.yRot += body;
        // Legs (M5 kicks): never follow the head, no body twist.
        blend(m.rightLeg, c.rlv, c.rl, false, f != null && f.rlv, f == null ? null : f.rl, false, mix, w, pitch, yaw, 0f);
        blend(m.leftLeg, c.llv, c.ll, false, f != null && f.llv, f == null ? null : f.ll, false, mix, w, pitch, yaw, 0f);
        m.rightPants.copyFrom(m.rightLeg);
        m.leftPants.copyFrom(m.leftLeg);

        m.rightSleeve.copyFrom(m.rightArm);
        m.leftSleeve.copyFrom(m.leftArm);
        m.jacket.copyFrom(m.body);
    }

    private static void blend(ModelPart part, boolean cv, float[] c, boolean cf, boolean fv, float[] f, boolean ff,
                              float mix, float w, float pitch, float yaw, float body) {
        float vx = part.xRot, vy = part.yRot, vz = part.zRot;
        if (!cv && !fv) return; // arm untouched by both poses
        float tx = vx, ty = vy, tz = vz;
        if (cv) {
            tx = c[0] + (cf ? pitch : 0f);
            ty = c[1] + (cf ? yaw : 0f) + body;
            tz = c[2];
        }
        float sx = vx, sy = vy, sz = vz;
        if (f != null) {
            if (fv) {
                sx = f[0] + (ff ? pitch : 0f);
                sy = f[1] + (ff ? yaw : 0f) + body;
                sz = f[2];
            }
            tx = sx + (tx - sx) * mix;
            ty = sy + (ty - sy) * mix;
            tz = sz + (tz - sz) * mix;
        }
        part.xRot = vx + (tx - vx) * w;
        part.yRot = vy + (ty - vy) * w;
        part.zRot = vz + (tz - vz) * w;
    }
}

package com.powerlevelling.client.pose;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Code-defined ki pose table (design Addendum C, K5): pose id -> keyframed arm / body rotations (radians, ModelPart space:
 * arm xRot -PI/2 = straight forward, -PI = straight up). Plain data, no client-only imports.
 * <p>
 * "Relative" poses describe the MAIN arm as {@code a} and the off arm as {@code b} as if the player were right-handed; they are
 * mirrored for left-handed players. Absolute poses use a = right arm, b = left arm. A null arm is left untouched (vanilla).
 * An arm with {@code follow} adds the head's pitch and yaw so it keeps pointing where the player looks.
 * Ids in attack JSONs may carry a namespace ("powerlevelling:finger_point"); unknown ids -> {@link #get} returns null and the
 * caller falls back by attack shape / hand (KiPoseClient).
 */
public final class KiPoseTable {
    private static final float HP = 1.5707964f;

    public record Arm(float x, float y, float z, boolean follow) {
    }

    /**
     * One keyframe. {@code c} / {@code d} are optional legs (main-side / off-side for relative poses, right / left for absolute
     * ones; added in M5 for kicks). If the first key has a leg, every key of the pose must have it.
     */
    public record Key(float t, Arm a, Arm b, float body, Arm c, Arm d) {
        public Key(float t, Arm a, Arm b, float body) {
            this(t, a, b, body, null, null);
        }
    }

    /** Output of {@link Pose#eval}: real right / left arm after main-arm mirroring. */
    public static final class Result {
        public final float[] r = new float[3];
        public final float[] l = new float[3];
        public final float[] rl = new float[3];
        public final float[] ll = new float[3];
        public boolean rv, lv, rf, lf;
        /** Right / left leg posed. */
        public boolean rlv, llv;
        public float body;
    }

    public static final class Pose {
        public final String id;
        public final boolean relative;
        public final int blendIn;
        public final int blendOut;
        public final int loop;
        public final Key[] keys;

        Pose(String id, boolean relative, int blendIn, int blendOut, int loop, Key... keys) {
            this.id = id;
            this.relative = relative;
            this.blendIn = Math.max(1, blendIn);
            this.blendOut = Math.max(1, blendOut);
            this.loop = loop;
            this.keys = keys;
        }

        /** Ticks to keep this pose after the orb ends when it is used as a fire pose. */
        public int hold() {
            return loop > 0 ? 8 : (int) Math.ceil(keys[keys.length - 1].t) + 5;
        }

        public void eval(float t, boolean rightMain, Result out) {
            if (loop > 0) t = t % loop;
            int i = 0;
            while (i < keys.length - 1 && keys[i + 1].t <= t) i++;
            Key k0 = keys[i];
            Key k1 = keys[Math.min(i + 1, keys.length - 1)];
            float f = k1.t > k0.t ? Math.min(1f, Math.max(0f, (t - k0.t) / (k1.t - k0.t))) : 1f;
            f = f * f * (3f - 2f * f);
            boolean av = keys[0].a != null;
            boolean bv = keys[0].b != null;
            float[] a = new float[3];
            float[] b = new float[3];
            if (av) lerp(k0.a, k1.a, f, a);
            if (bv) lerp(k0.b, k1.b, f, b);
            boolean af = av && keys[0].a.follow;
            boolean bf = bv && keys[0].b.follow;
            float body = k0.body + (k1.body - k0.body) * f;
            boolean cv = keys[0].c != null;
            boolean dv = keys[0].d != null;
            float[] c = new float[3];
            float[] d = new float[3];
            if (cv) lerp(k0.c, k1.c, f, c);
            if (dv) lerp(k0.d, k1.d, f, d);
            if (!relative || rightMain) {
                System.arraycopy(a, 0, out.r, 0, 3);
                System.arraycopy(b, 0, out.l, 0, 3);
                System.arraycopy(c, 0, out.rl, 0, 3);
                System.arraycopy(d, 0, out.ll, 0, 3);
                out.rv = av; out.lv = bv; out.rf = af; out.lf = bf;
                out.rlv = cv; out.llv = dv;
                out.body = body;
            } else { // left-handed: main arm is the left one, mirror x-keeping, y/z negated
                out.l[0] = a[0]; out.l[1] = -a[1]; out.l[2] = -a[2];
                out.r[0] = b[0]; out.r[1] = -b[1]; out.r[2] = -b[2];
                out.ll[0] = c[0]; out.ll[1] = -c[1]; out.ll[2] = -c[2];
                out.rl[0] = d[0]; out.rl[1] = -d[1]; out.rl[2] = -d[2];
                out.lv = av; out.rv = bv; out.lf = af; out.rf = bf;
                out.llv = cv; out.rlv = dv;
                out.body = -body;
            }
        }

        private static void lerp(Arm p, Arm q, float f, float[] o) {
            if (q == null) q = p;
            o[0] = p.x + (q.x - p.x) * f;
            o[1] = p.y + (q.y - p.y) * f;
            o[2] = p.z + (q.z - p.z) * f;
        }
    }

    private static final Map<String, Pose> POSES = new HashMap<>();
    private static final Map<String, String> ALIASES = new HashMap<>();

    private static Arm arm(float x, float y, float z) {
        return new Arm(x, y, z, false);
    }

    /** Arm straight out along the look direction, inward angle y. */
    private static Arm fwd(float y) {
        return new Arm(-HP, y, 0f, true);
    }

    private static Key key(float t, Arm a, Arm b) {
        return new Key(t, a, b, 0f);
    }

    private static void def(Pose p) {
        POSES.put(p.id, p);
    }

    static {
        // One arm forward ("palm"): main-relative, and absolute right / left.
        def(new Pose("palm_forward", true, 3, 5, 0, key(0, fwd(-0.08f), null)));
        def(new Pose("palm_forward_off", true, 3, 5, 0, key(0, null, fwd(-0.08f))));
        def(new Pose("palm_forward_r", false, 3, 5, 0, key(0, fwd(-0.08f), null)));
        def(new Pose("palm_forward_l", false, 3, 5, 0, key(0, null, fwd(0.08f))));
        // Both arms out, hands together (beam fire).
        def(new Pose("two_hand_forward", false, 2, 6, 0, key(0, fwd(-0.38f), fwd(0.38f))));
        // Both hands cupped at the main-hand hip (beam charge).
        def(new Pose("two_hand_hip_cup", true, 4, 5, 0,
                key(0, arm(-0.55f, -0.12f, 0.05f), arm(-0.9f, 0.75f, -0.05f))));
        // Sphere: both arms over the head, then swung forward.
        def(new Pose("overhead_two_hand", false, 5, 6, 0, key(0, arm(-2.8f, 0f, 0.3f), arm(-2.8f, 0f, -0.3f))));
        def(new Pose("overhead_throw", false, 1, 7, 0,
                key(0, arm(-2.8f, 0f, 0.3f), arm(-2.8f, 0f, -0.3f)),
                key(3, arm(-3.0f, 0f, 0.1f), arm(-3.0f, 0f, -0.1f)),
                key(8, arm(-0.6f, -0.1f, 0.05f), arm(-0.6f, 0.1f, -0.05f)),
                key(14, arm(-0.6f, -0.1f, 0.05f), arm(-0.6f, 0.1f, -0.05f))));
        // Needle: one arm out, slight body twist (the arm keeps the aim: the twist is added to its yaw in apply).
        def(new Pose("finger_point", true, 3, 5, 0, new Key(0, fwd(-0.05f), null, -0.15f)));
        // Burst: arms crossed (windup) -> flung out (release).
        def(new Pose("arms_cross_flex", false, 4, 6, 0,
                key(0, arm(-0.8f, -0.5f, 0f), arm(-0.8f, 0.5f, 0f)),
                key(8, arm(-1.0f, -1.0f, 0f), arm(-1.0f, 1.0f, 0f))));
        def(new Pose("arms_spread", false, 1, 8, 0,
                key(0, arm(-1.0f, -1.0f, 0f), arm(-1.0f, 1.0f, 0f)),
                key(4, arm(-0.1f, 0f, 1.5f), arm(-0.1f, 0f, -1.5f)),
                key(14, arm(-0.1f, 0f, 1.5f), arm(-0.1f, 0f, -1.5f))));
        // Disc: arm up with a flat palm -> side-arm sweep across the body.
        def(new Pose("disc_raise", true, 4, 5, 0, key(0, arm(-2.6f, 0f, 0.25f), null)));
        def(new Pose("disc_throw", true, 1, 6, 0,
                key(0, arm(-2.6f, 0f, 0.25f), null),
                key(2, arm(-HP, 0.9f, 0.1f), null),
                key(6, arm(-HP, -0.6f, 0f), null),
                key(12, arm(-HP, -0.6f, 0f), null)));
        // Barrage: arms alternate punching forward (8-tick loop; phase = time in pose).
        def(new Pose("barrage_alternate", false, 2, 6, 8,
                key(0, fwd(-0.1f), arm(-0.7f, 0.3f, -0.1f)),
                key(4, arm(-0.7f, -0.3f, 0.1f), fwd(0.1f)),
                key(8, fwd(-0.1f), arm(-0.7f, 0.3f, -0.1f))));
        // Homing: one arm out with a small circling wobble (24-tick loop).
        def(new Pose("homing_circle", true, 3, 5, 24,
                key(0, new Arm(-HP, -0.08f, 0.10f, true), null),
                key(6, new Arm(-HP + 0.10f, -0.08f, 0f, true), null),
                key(12, new Arm(-HP, -0.08f, -0.10f, true), null),
                key(18, new Arm(-HP - 0.10f, -0.08f, 0f, true), null),
                key(24, new Arm(-HP, -0.08f, 0.10f, true), null)));
        // Bomb: hold the orb up, then arm back and lob.
        def(new Pose("bomb_hold", true, 4, 5, 0, key(0, arm(-1.3f, -0.1f, 0.1f), null)));
        def(new Pose("bomb_lob", true, 1, 6, 0,
                key(0, arm(-1.3f, -0.1f, 0.1f), null),
                key(3, arm(0.7f, 0f, 0.1f), null),
                key(7, arm(-1.9f, -0.1f, 0f), null),
                key(14, arm(-1.6f, -0.1f, 0f), null)));

        // ---- Melee (M5, combat_move "pose"): absolute right / left, snappy blend-in, 3-tick blend-out (reject = clean cancel).
        // Timed for 3 startup + 2 active ticks (impact around t = 3..5); the last key is held until the move ends.
        Arm guardR = arm(-1.2f, -0.35f, 0f);
        Arm guardL = arm(-1.2f, 0.35f, 0f);
        def(new Pose("jab_r", false, 1, 3, 0,
                new Key(0, guardR, guardL, 0f),
                new Key(2, arm(-1.35f, -0.1f, 0f), guardL, -0.1f),
                new Key(3, arm(-HP, -0.05f, 0f), arm(-1.2f, 0.45f, 0f), -0.25f),
                new Key(6, arm(-HP, -0.05f, 0f), arm(-1.2f, 0.45f, 0f), -0.25f),
                new Key(12, guardR, guardL, 0f)));
        def(new Pose("cross_l", false, 1, 3, 0,
                new Key(0, guardR, guardL, 0f),
                new Key(2, guardR, arm(-1.35f, 0.1f, 0f), 0.1f),
                new Key(3, arm(-1.2f, -0.45f, 0f), arm(-HP, 0.05f, 0f), 0.25f),
                new Key(6, arm(-1.2f, -0.45f, 0f), arm(-HP, 0.05f, 0f), 0.25f),
                new Key(12, guardR, guardL, 0f)));
        def(new Pose("hook_r", false, 1, 3, 0,
                new Key(0, guardR, guardL, 0f),
                new Key(2, arm(-1.3f, 0.6f, 0.35f), guardL, 0.15f),
                new Key(4, arm(-HP, -0.7f, 0.1f), arm(-1.2f, 0.45f, 0f), -0.45f),
                new Key(7, arm(-HP, -0.7f, 0.1f), arm(-1.2f, 0.45f, 0f), -0.45f),
                new Key(13, guardR, guardL, 0f)));
        // Front kick with the right leg (legs: c = right, d = left).
        Arm legRest = arm(0f, 0f, 0f);
        def(new Pose("kick", false, 1, 3, 0,
                new Key(0, guardR, guardL, 0f, legRest, legRest),
                new Key(3, guardR, guardL, 0f, arm(-0.9f, 0f, 0.05f), arm(0.1f, 0f, 0f)),
                new Key(5, arm(-0.9f, -0.3f, 0.3f), arm(-0.9f, 0.3f, -0.3f), -0.1f, arm(-1.55f, 0f, 0.1f), arm(0.2f, 0f, 0f)),
                new Key(9, arm(-0.9f, -0.3f, 0.3f), arm(-0.9f, 0.3f, -0.3f), -0.1f, arm(-1.55f, 0f, 0.1f), arm(0.2f, 0f, 0f)),
                new Key(15, guardR, guardL, 0f, legRest, legRest)));

        // Ids used by earlier JSONs / the design doc.
        ALIASES.put("cupped_hip", "two_hand_hip_cup");
        ALIASES.put("beam_charge", "two_hand_hip_cup");
        ALIASES.put("beam_fire", "two_hand_forward");
        ALIASES.put("throw_down", "overhead_throw");
        ALIASES.put("crouch_gather", "arms_cross_flex");
        ALIASES.put("arms_out_flex", "arms_spread");
        ALIASES.put("palm_up", "disc_raise");
        ALIASES.put("side_throw", "disc_throw");
        ALIASES.put("alternating_palms", "barrage_alternate");
        ALIASES.put("underhand_lob", "bomb_lob");
        ALIASES.put("point_forward", "palm_forward");
        ALIASES.put("finisher_kick", "kick");
    }

    private KiPoseTable() {
    }

    /** Canonical pose for an id from data (namespace and case ignored, aliases resolved), or null if unknown / empty. */
    public static Pose get(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        String id = raw.substring(raw.indexOf(':') + 1).toLowerCase(Locale.ROOT);
        id = ALIASES.getOrDefault(id, id);
        return POSES.get(id);
    }
}

package com.powerlevelling.ki;

import com.powerlevelling.ki.KiAttackParts.Charge;
import com.powerlevelling.ki.KiAttackParts.Common;
import com.powerlevelling.ki.KiAttackParts.FloatRange;
import com.powerlevelling.character.CharacterData;
import com.powerlevelling.character.CharacterService;
import com.powerlevelling.config.ServerConfig;
import com.powerlevelling.data.CombatState;
import com.powerlevelling.network.ModNetwork;
import com.powerlevelling.network.OrbStatePayload;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Server side of the reusable "ki forms in your hand" orb (the base of most ki attacks): builds the OrbStatePayload when a
 * wind-up / charge starts, tells self + trackers when it stops, and computes where the orb is in the world (so the blast
 * spawns exactly where the orb was). The clients draw and grow the orb themselves (client/render/Ki*Orb*).
 * Server thread only.
 */
public final class KiOrbs {
    /** Blast spawn is pushed this far (per block of radius) along the aim so a big blast doesn't swallow the camera. */
    private static final double FORWARD_PER_RADIUS = 0.6;

    private KiOrbs() {
    }

    // ---- hooks (design B.5: the player's ki colour will tint the orb and the blast) -------------------------------------

    /**
     * HOOK: shell colour of the orb (and, later, of the blast). B.5: return the player's ki colour here when the attack's
     * visual.color_source is "player" (and server allowCustomKiColors is on). For now: the attack's own colour.
     */
    public static int resolveOrbColor(ServerPlayer player, KiAttack attack) {
        int ki = playerKiColor(player, attack);
        return ki >= 0 ? ki : attack.common().visual().color();
    }

    /** The shooter's ki colour if this attack takes player colours and it is allowed and set; otherwise -1 (use attack colours). */
    public static int playerKiColor(ServerPlayer player, KiAttack attack) {
        if (!attack.common().visual().usesPlayerColor() || !ServerConfig.ALLOW_CUSTOM_KI_COLORS.get()) return -1;
        CharacterData d = CharacterService.get(player);
        if (d == null || !d.created || d.appearance.kiColor() < 0) return -1;
        return d.appearance.kiColor() & 0xFFFFFF;
    }

    /** core = mix(kiColor, white, 0.75). */
    public static int coreOf(int kiColor) {
        float t = 0.75f;
        int r = (int) Mth.lerp(t, (kiColor >> 16) & 0xFF, 0xFF);
        int g = (int) Mth.lerp(t, (kiColor >> 8) & 0xFF, 0xFF);
        int b = (int) Mth.lerp(t, kiColor & 0xFF, 0xFF);
        return (r << 16) | (g << 8) | b;
    }

    /** HOOK: core colour; B.5 says core = mix(kiColor, white, 0.75) for player colours. For now: the attack's core colour. */
    public static int resolveOrbCoreColor(ServerPlayer player, KiAttack attack) {
        int ki = playerKiColor(player, attack);
        return ki >= 0 ? coreOf(ki) : attack.common().visual().coreColor();
    }

    /** Which hand forms the orb: OrbStatePayload.HAND_* (present.orb.hand; default main). */
    public static int handFor(KiAttack attack) {
        return attack.common().present().orb().handId();
    }

    /** Where the orb sits: OrbStatePayload.ANCHOR_* (present.orb.anchor, else derived from the hand). */
    public static int anchorFor(KiAttack attack) {
        return attack.common().present().orb().anchorId();
    }

    // ---- start / stop --------------------------------------------------------------------------------------------------

    /**
     * Starts the orb for the skill state that was just begun and broadcasts it. {@code windup}: tap attack forming for
     * attack.windupTicks(); otherwise a hold charge growing for charge.max_ticks.
     */
    public static void start(ServerPlayer player, CombatState st, KiAttack attack, ResourceLocation id, boolean windup, long now) {
        Common c = attack.common();
        FloatRange radii = attack.orbRadiusRange();
        int duration;
        int grow;
        float minRadius;
        float maxRadius;
        if (windup) {
            duration = Math.max(1, attack.windupTicks());
            grow = duration;
            minRadius = radii.lerp(0f);
            maxRadius = minRadius;
        } else {
            Charge ch = c.charge();
            duration = Math.max(1, ch.maxTicks());
            grow = Math.min(ch.minTicks(), duration);
            minRadius = radii.min();
            maxRadius = radii.max();
        }
        OrbStatePayload p = new OrbStatePayload(player.getId(), id.toString(), now, duration, grow, minRadius, maxRadius,
                resolveOrbColor(player, attack), resolveOrbCoreColor(player, attack), handFor(attack), anchorFor(attack), true);
        st.orb = p;
        ModNetwork.sendOrb(player, p);
    }

    /** Ends the orb (fired / cancelled / reset). Safe to call when there is none. */
    public static void stop(ServerPlayer player, CombatState st) {
        if (st.orb == null) return;
        st.orb = null;
        ModNetwork.sendOrb(player, OrbStatePayload.inactive(player.getId()));
    }

    // ---- where the orb is ----------------------------------------------------------------------------------------------

    /** {@link #orbWorldPos(ServerPlayer, Vec3, int, int, float)} with the anchor derived from the hand. */
    public static Vec3 orbWorldPos(ServerPlayer player, Vec3 look, int hand, float radius) {
        int anchor = switch (hand) {
            case OrbStatePayload.HAND_BOTH -> OrbStatePayload.ANCHOR_CHEST;
            case OrbStatePayload.HAND_FINGER -> OrbStatePayload.ANCHOR_FINGERTIP;
            case OrbStatePayload.HAND_NONE -> OrbStatePayload.ANCHOR_BODY;
            default -> OrbStatePayload.ANCHOR_PALM;
        };
        return orbWorldPos(player, look, hand, anchor, radius);
    }

    /**
     * Approximate world position of the orb centre. Palm / chest: eye + look * 0.6 + right * 0.35 * side - up * 0.25
     * (side = +1 right hand, -1 left hand, 0 = both hands: centred), pushed forward by {@code radius * 0.6}. Overhead (two
     * hands above the head): above the eye by 0.55 + 0.6 * radius, a little forward. Fingertip: ~0.9 ahead, to the main-hand
     * side. Body: the body centre. Pulled back if a block is in the way.
     */
    public static Vec3 orbWorldPos(ServerPlayer player, Vec3 look, int hand, int anchor, float radius) {
        Vec3 eye = player.getEyePosition();
        Vec3 dir = look.lengthSqr() < 1.0E-6 ? player.getLookAngle() : look.normalize();

        // Same convention as KiFx / the client: right = look x up.
        Vec3 right = dir.cross(new Vec3(0, 1, 0));
        if (right.lengthSqr() < 1.0E-4) { // looking straight up/down: use the body yaw
            float yaw = player.getYRot() * Mth.DEG_TO_RAD;
            right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
        }
        right = right.normalize();
        Vec3 up = right.cross(dir);

        double side = switch (hand) {
            case OrbStatePayload.HAND_OFF -> player.getMainArm() == HumanoidArm.RIGHT ? -1.0 : 1.0;
            case OrbStatePayload.HAND_BOTH -> 0.0;
            default -> player.getMainArm() == HumanoidArm.RIGHT ? 1.0 : -1.0; // main, finger
        };
        Vec3 pos;
        switch (anchor) {
            case OrbStatePayload.ANCHOR_OVERHEAD ->
                    pos = eye.add(0, 0.55 + radius * 0.6, 0).add(dir.x * 0.25, 0, dir.z * 0.25);
            case OrbStatePayload.ANCHOR_FINGERTIP ->
                    pos = eye.add(dir.scale(0.9)).add(right.scale(0.3 * side)).add(up.scale(-0.2));
            case OrbStatePayload.ANCHOR_BODY ->
                    pos = player.position().add(0, player.getBbHeight() * 0.5, 0);
            case OrbStatePayload.ANCHOR_HIP -> {
                // Beside the hip on the main-hand side, a little ahead of the body (two hands cupped).
                Vec3 flat = new Vec3(dir.x, 0, dir.z);
                flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : flat.normalize();
                Vec3 sideV = flat.cross(new Vec3(0, 1, 0)).normalize();
                double mainSide = player.getMainArm() == HumanoidArm.RIGHT ? 1.0 : -1.0;
                pos = player.position().add(0, player.getBbHeight() * 0.5, 0)
                        .add(sideV.scale(0.42 * mainSide)).add(flat.scale(0.2 + radius * 0.5));
            }
            default ->
                    pos = eye.add(dir.scale(0.6 + radius * FORWARD_PER_RADIUS)).add(right.scale(0.35 * side)).add(up.scale(-0.25));
        }

        if (anchor != OrbStatePayload.ANCHOR_BODY && player.level() instanceof ServerLevel level) {
            BlockHitResult hit = level.clip(new ClipContext(eye, pos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (hit.getType() != HitResult.Type.MISS) pos = hit.getLocation().subtract(dir.scale(0.1));
        }
        return pos;
    }
}

package com.powerlevelling.client.render;

import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.powerlevelling.character.CharacterRegistries;
import com.powerlevelling.character.CharacterSnapshot;
import com.powerlevelling.character.CosmeticPart;
import com.powerlevelling.character.Race;
import com.powerlevelling.character.TailState;
import com.powerlevelling.client.ClientCharacters;
import com.powerlevelling.client.ClientVisualState;
import com.powerlevelling.network.VisualStatePayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Draws the character's tail (Addendum B.4 / B.5) on every player: the race's tail cosmetic part (race.appearance.tail.model, a
 * cosmetic_part id), or, when the tail is worn wrapped, the part with the same id plus "_wrapped". Anchored to the body ModelPart
 * (geo authored relative to the body pivot, see geo_authoring.md), tinted with the hair color (or the race's fixed tail color)
 * unless the part is not tintable. Tails ignore helmets and armor. Added to the WIDE and SLIM renderers in ClientEvents.
 *
 * <h2>Procedural animation (free tail only)</h2>
 * The geo has a bone chain {@code seg1..seg4} (each the child of the previous; {@code seg_1..seg_N} is accepted too, up to 8).
 * Every frame each segment gets a yaw sway and a pitch added on top of its authored rest rotation (rotations accumulate down the
 * chain), and the rest rotation is restored right after drawing so the shared baked model is never polluted.
 * <pre>
 *   idle    sway = sin(phase + i*0.7) * (6 deg + 10 deg * limbSwingAmount), droop -16 deg in total
 *   stance  amplitude x0.3, horizontal (0 deg)
 *   charge  amplitude x2, phase speed x2, raised +45 deg in total, small high-frequency jitter
 *   stream  (flight, not wired yet: see {@link #isStreaming}) almost no sway, horizontal
 * </pre>
 * phase advances 0.08 rad per tick (scaled by the state's speed), so changing state never jumps the wave. The per-entity current
 * angles ease towards the target by 20% per frame at 60 fps (frame-rate independent), so state changes blend instead of snapping.
 * Time comes from the wall clock and is frozen while the game is paused, so several renders of one player in one frame (world +
 * GUI preview) do not speed it up.
 */
public class TailLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final int MAX_SEGMENTS = 8;
    private static final float BASE_PHASE_SPEED = 0.08f;    // rad per tick
    private static final float SEGMENT_PHASE_STEP = 0.7f;   // rad between neighbouring segments
    private static final float BASE_YAW_DEG = 6f;
    private static final float LIMB_YAW_DEG = 10f;
    private static final double EASE_PER_FRAME = 0.8;       // fraction NOT closed per frame at 60 fps (= lerp 0.2)
    private static final int MAX_TRACKED = 512;

    /** One animation pose target. totalPitch is in degrees, positive = tip up. */
    private record Pose(float amp, float speed, float totalPitchDeg, float jitterDeg) {
    }

    private static final Pose IDLE = new Pose(1f, 1f, -16f, 0f);
    private static final Pose STANCE = new Pose(0.3f, 1f, 0f, 0f);
    private static final Pose CHARGING = new Pose(2f, 2f, 45f, 1.5f);
    private static final Pose STREAM = new Pose(0.15f, 1.5f, 0f, 0.5f);

    /** Per-entity eased state. Render thread only. */
    private static final class Anim {
        float phase;
        long lastNanos;
        final float[] yaw = new float[MAX_SEGMENTS];    // degrees
        final float[] pitch = new float[MAX_SEGMENTS];  // degrees
        boolean initialised;
    }

    private static final Map<Integer, Anim> ANIMS = new HashMap<>();

    public TailLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    // ---- lifecycle (called from ClientEvents) ---------------------------------------------------------------------

    public static void forget(int entityId) {
        ANIMS.remove(entityId);
    }

    public static void reset() {
        ANIMS.clear();
    }

    // ---- render ---------------------------------------------------------------------------------------------------

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw,
                       float headPitch) {
        if (player.isInvisible() || player.isSpectator()) return;
        CharacterSnapshot snap = ClientCharacters.effective(player.getId()); // the screen's preview for the local player
        if (snap == null || snap.tail() != TailState.PRESENT) return;
        boolean preview = snap == ClientCharacters.previewOverride;
        if (!snap.created() && !preview) return;

        RegistryAccess access = player.level().registryAccess();
        Race race = CharacterRegistries.getRace(access, snap.race());
        if (race == null) return;
        Race.Tail tailDef = race.appearance().tail();
        ResourceLocation baseId = tailDef.model().orElse(null);
        if (baseId == null) return;

        CosmeticPart part = null;
        boolean wrapped = false;
        if (snap.tailWrapped()) {
            part = CharacterRegistries.getPart(access,
                    ResourceLocation.fromNamespaceAndPath(baseId.getNamespace(), baseId.getPath() + "_wrapped"));
            wrapped = part != null; // no wrapped art for this race: fall back to the free tail
        }
        if (part == null) part = CharacterRegistries.getPart(access, baseId);
        if (part == null) return;
        GeoModelLoader.GeoModel geo = GeoModelLoader.get(part.model());
        if (geo == null) return; // no art yet / failed to load: draw nothing

        int color = part.tintable() ? 0xFF000000 | (tintRgb(tailDef, snap) & 0xFFFFFF) : 0xFFFFFFFF;
        PlayerModel<AbstractClientPlayer> model = getParentModel();
        ModelPart anchor = part.anchor() == CosmeticPart.Anchor.HEAD ? model.head : model.body;
        VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(part.texture()));

        ModelPart[] segs = new ModelPart[MAX_SEGMENTS];
        float[] restX = new float[MAX_SEGMENTS];
        float[] restY = new float[MAX_SEGMENTS];
        float[] restZ = new float[MAX_SEGMENTS];
        int n = 0;
        if (!wrapped) {
            n = findSegments(geo, segs);
            for (int i = 0; i < n; i++) {
                restX[i] = segs[i].xRot;
                restY[i] = segs[i].yRot;
                restZ[i] = segs[i].zRot;
            }
        }

        poseStack.pushPose();
        try {
            anchor.translateAndRotate(poseStack);
            if (n > 0) animate(player, segs, n, restX, restY, restZ, limbSwingAmount);
            geo.root().render(poseStack, vc, packedLight, OverlayTexture.NO_OVERLAY, color);
        } finally {
            for (int i = 0; i < n; i++) { // never leave the shared baked model posed
                segs[i].xRot = restX[i];
                segs[i].yRot = restY[i];
                segs[i].zRot = restZ[i];
            }
            poseStack.popPose();
        }
    }

    /** Race default_color: "hair" follows the hair color, "#RRGGBB" is a fixed color. */
    private static int tintRgb(Race.Tail tail, CharacterSnapshot snap) {
        String c = tail.defaultColor();
        if (c != null && c.length() == 7 && c.charAt(0) == '#') {
            try {
                return Integer.parseInt(c.substring(1), 16);
            } catch (NumberFormatException ignored) {
                // fall through to the hair color
            }
        }
        return snap.hairColor();
    }

    /** seg1..segN (or seg_1..seg_N) in chain order; returns how many were found (stops at the first gap). */
    private static int findSegments(GeoModelLoader.GeoModel geo, ModelPart[] out) {
        int n = 0;
        for (int i = 1; i <= MAX_SEGMENTS; i++) {
            ModelPart p = geo.bones().get("seg" + i);
            if (p == null) p = geo.bones().get("seg_" + i);
            if (p == null) break;
            out[n++] = p;
        }
        return n;
    }

    // ---- procedural animation -------------------------------------------------------------------------------------

    /**
     * TODO(flight): return true while the player flies fast so the tail streams straight back. There is no flight state in
     * the mod yet (CharacterGate has the same TODO), so this is the single place to hook it up (e.g. a flight tier in
     * ClientVisualState).
     */
    private static boolean isStreaming(AbstractClientPlayer player) {
        return false;
    }

    private static void animate(AbstractClientPlayer player, ModelPart[] segs, int n, float[] restX, float[] restY,
                                float[] restZ, float limbSwingAmount) {
        int id = player.getId();
        Pose pose;
        if (isStreaming(player)) pose = STREAM;
        else if (ClientVisualState.has(id, VisualStatePayload.CHARGING)) pose = CHARGING;
        else if (ClientVisualState.has(id, VisualStatePayload.STANCE)) pose = STANCE;
        else pose = IDLE;

        if (ANIMS.size() > MAX_TRACKED) ANIMS.clear(); // safety net; entities are normally forgotten on leave
        Anim a = ANIMS.computeIfAbsent(id, k -> new Anim());

        long now = System.nanoTime();
        float dtSeconds = a.lastNanos == 0L ? 0f : Math.min(0.25f, (now - a.lastNanos) / 1.0e9f);
        a.lastNanos = now;
        if (Minecraft.getInstance().isPaused()) dtSeconds = 0f;
        a.phase += BASE_PHASE_SPEED * pose.speed() * dtSeconds * 20f;
        if (a.phase > 1.0e4f) a.phase -= 1.0e4f; // keep float precision (the wave is periodic; a tiny phase hop is invisible)
        float alpha = 1f - (float) Math.pow(EASE_PER_FRAME, dtSeconds * 60.0);

        float limb = Mth.clamp(limbSwingAmount, 0f, 1f);
        float yawAmp = (BASE_YAW_DEG + LIMB_YAW_DEG * limb) * pose.amp();
        float bobAmp = 1.5f * Math.min(1f, pose.amp());
        for (int i = 0; i < n; i++) {
            float yawT = Mth.sin(a.phase + i * SEGMENT_PHASE_STEP) * yawAmp;
            float pitchT = pose.totalPitchDeg() / n + bobAmp * Mth.sin(a.phase * 0.6f + i * 0.9f);
            if (pose.jitterDeg() > 0f) {
                yawT += pose.jitterDeg() * Mth.sin(a.phase * 7.3f + i * 3.1f);
                pitchT += pose.jitterDeg() * Mth.sin(a.phase * 9.1f + i * 5.3f);
            }
            if (!a.initialised) { // first sight: start at the target
                a.yaw[i] = yawT;
                a.pitch[i] = pitchT;
            } else {
                a.yaw[i] += (yawT - a.yaw[i]) * alpha;
                a.pitch[i] += (pitchT - a.pitch[i]) * alpha;
            }
            // Model space is y-down and the tail points to +z, so a positive xRot lifts the tip.
            segs[i].xRot = restX[i] + a.pitch[i] * Mth.DEG_TO_RAD;
            segs[i].yRot = restY[i] + a.yaw[i] * Mth.DEG_TO_RAD;
            segs[i].zRot = restZ[i];
        }
        a.initialised = true;
    }
}

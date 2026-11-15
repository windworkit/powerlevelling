package com.powerlevelling.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.powerlevelling.PowerLevelling;
import com.powerlevelling.character.CharacterSnapshot;
import com.powerlevelling.client.ClientCharacters;
import com.powerlevelling.progression.StatEffects;

import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

/**
 * THE single place for whole-body render transforms of a player (Addendum B.4 body size now; the design's 8.9 body tilt later):
 * {@link #apply} is called between the pose push in RenderPlayerEvent.Pre and the pop in RenderPlayerEvent.Post.
 * <p>
 * Body size (B.10: cosmetic only): the pose origin at that point is the entity's feet, so a plain scale of (build, height, build)
 * scales about the feet. Hitbox, reach and camera are untouched. Name tags are drawn inside the entity render and scale with it.
 * <p>
 * Balance: the push happens at the LOWEST priority and only when no earlier handler canceled the event (a canceled Pre never gets
 * a Post); the pop happens at the HIGHEST priority of Post, guarded by a flag, so every push we make is popped. If a Post never
 * arrives (another mod canceling after us, or an exception mid-render) the stale flag is dropped on the next Pre; the entity
 * dispatcher's own pop still restores the stack depth it pushed, only our extra level would leak, which is why the push is the
 * very last thing before rendering.
 * <p>
 * Interaction with sizeAffectsHitbox: when the server applies Attributes.SCALE (modifier {@link StatEffects#BODY_SCALE_ID}, value
 * avg(height, build) - 1), vanilla already scales the model uniformly by that, so only the leftover (build/s, height/s, build/s) is
 * applied here; otherwise the size would be applied twice. Equal height and build then need no transform at all.
 */
@EventBusSubscriber(modid = PowerLevelling.MODID, value = Dist.CLIENT)
public final class PlayerRenderTransforms {
    private static final float EPS = 1.0e-3f;

    /** True between our push in Pre and the matching pop in Post. */
    private static boolean pushed;
    private static boolean warnedStale;

    private PlayerRenderTransforms() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onPre(RenderPlayerEvent.Pre event) {
        if (pushed) { // the previous render never reached Post; do not trust the flag
            pushed = false;
            if (!warnedStale) {
                warnedStale = true;
                PowerLevelling.LOGGER.warn("[render] a player render started before the previous one finished (another mod canceled it mid-render?)");
            }
        }
        if (event instanceof ICancellableEvent c && c.isCanceled()) return; // no Post will follow
        PoseStack poseStack = event.getPoseStack();
        float[] s = scaleFor(event.getEntity());
        if (s == null) return;
        poseStack.pushPose();
        pushed = true;
        apply(poseStack, event.getEntity(), event.getPartialTick(), s);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onPost(RenderPlayerEvent.Post event) {
        if (!pushed) return;
        pushed = false;
        event.getPoseStack().popPose();
    }

    /** All whole-body transforms, applied to the freshly pushed pose (origin = feet). Future: body tilt (8.9) goes here. */
    private static void apply(PoseStack poseStack, Player player, float partialTick, float[] size) {
        poseStack.scale(size[0], size[1], size[2]);
    }

    /** (x, y, z) scale for the player's body size, or null for "no transform". */
    private static float[] scaleFor(Player player) {
        CharacterSnapshot snap = ClientCharacters.effective(player.getId());
        if (snap == null) return null;
        boolean preview = snap == ClientCharacters.previewOverride;
        if (!snap.created() && !preview) return null;
        float h = sane(snap.height());
        float b = sane(snap.build());

        // sizeAffectsHitbox: vanilla already applies the uniform part (the synced body_scale modifier), do not double it.
        float uniform = 1f;
        AttributeInstance scale = player.getAttribute(Attributes.SCALE);
        if (scale != null) {
            AttributeModifier m = scale.getModifier(StatEffects.BODY_SCALE_ID);
            if (m != null) uniform = Math.max(0.0625f, 1f + (float) m.amount());
        }
        float sx = b / uniform;
        float sy = h / uniform;
        if (Math.abs(sx - 1f) < EPS && Math.abs(sy - 1f) < EPS) return null;
        return new float[]{sx, sy, sx};
    }

    private static float sane(float v) {
        return Float.isNaN(v) || v <= 0f ? 1f : v;
    }
}

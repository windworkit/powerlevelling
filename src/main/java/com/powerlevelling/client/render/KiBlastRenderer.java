package com.powerlevelling.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.powerlevelling.entity.KiBlastEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Flying ki blast. The model itself (plasma shell, hot core, orbit bands, filament and smooth tail) lives in {@link KiOrbModel}, shared with the
 * "ki forms in your hand" orb; this class only positions it at the entity and points the tail against the velocity.
 * All sizes scale with the synced radius, so a charged blast (bigger radius) is simply a bigger model.
 */
public class KiBlastRenderer extends EntityRenderer<KiBlastEntity> {
    /** Kept for existing references; the texture is owned by {@link KiOrbModel}. */
    public static final ResourceLocation TEXTURE = KiOrbModel.TEXTURE;

    public KiBlastRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0f;
    }

    @Override
    public ResourceLocation getTextureLocation(KiBlastEntity entity) {
        return TEXTURE;
    }

    /** The entity's own box is tiny (0.3), but the model is bigger and has a tail: cull against an inflated box. */
    @Override
    public boolean shouldRender(KiBlastEntity entity, Frustum frustum, double camX, double camY, double camZ) {
        if (!entity.shouldRender(camX, camY, camZ)) return false;
        float diameter = entity.getBlastRadius() * 2f;
        AABB box = entity.getBoundingBox().inflate(entity.getBlastRadius() + diameter * 2.5f + 0.5);
        return frustum.isVisible(box);
    }

    @Override
    public void render(KiBlastEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        // Direction of travel (world space; the pose stack only holds the entity translation at this point).
        Vec3 vel = entity.getDeltaMovement();
        if (vel.lengthSqr() < 1.0E-6) {
            vel = new Vec3(entity.getX() - entity.xOld, entity.getY() - entity.yOld, entity.getZ() - entity.zOld);
        }
        final boolean moving = vel.lengthSqr() > 1.0E-6;
        final Vec3 back = moving ? vel.normalize().scale(-1.0) : Vec3.ZERO;

        poseStack.pushPose();
        poseStack.translate(0.0, entity.getBbHeight() / 2.0, 0.0);
        if (entity.getStyle() == KiBlastEntity.STYLE_BOMB) {
            // Grenade: dark core, no tail; the shell blinks hot (faster and faster, see KiBlastEntity.fusePhase).
            float phase = KiBlastEntity.fusePhase(entity.tickCount + partialTick, entity.getFuseTotal());
            boolean on = phase - Mth.floor(phase) < 0.5f;
            int shell = on ? KiOrbModel.mix(entity.getColor(), 0xFF3010, 0.6f) : entity.getColor();
            int core = on ? 0xFFFFFF : KiOrbModel.mix(entity.getColor(), 0x000000, 0.85f);
            KiOrbModel.render(poseStack, buffer, entity.getBlastRadius() * (on ? 1.12f : 0.96f), shell, core,
                    entity.tickCount, partialTick, 1f, false, null);
        } else {
            KiOrbModel.render(poseStack, buffer, entity.getBlastRadius(), entity.getColor(), entity.getCoreColor(),
                    entity.tickCount, partialTick, 1f, moving, back);
        }
        poseStack.popPose();
    }
}

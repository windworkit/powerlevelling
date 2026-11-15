package com.powerlevelling.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.powerlevelling.entity.KiDiscEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/** Flying ki disc: the flat spinning cube saw of {@link KiOrbModel#renderDisc}, spinning faster on the way out. */
public class KiDiscRenderer extends EntityRenderer<KiDiscEntity> {
    public KiDiscRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0f;
    }

    @Override
    public ResourceLocation getTextureLocation(KiDiscEntity entity) {
        return KiOrbModel.TEXTURE;
    }

    @Override
    public void render(KiDiscEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        poseStack.translate(0.0, entity.getBbHeight() / 2.0, 0.0);
        KiOrbModel.renderDisc(poseStack, buffer, entity.getDiscRadius(), entity.getThickness(), entity.getColor(),
                entity.getCoreColor(), entity.tickCount, partialTick, 1f, entity.isReturning() ? 1.3f : 1.0f);
        poseStack.popPose();
    }
}

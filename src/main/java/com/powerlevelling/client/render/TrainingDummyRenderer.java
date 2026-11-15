package com.powerlevelling.client.render;

import com.powerlevelling.PowerLevelling;
import com.powerlevelling.entity.TrainingDummyEntity;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/** Blocky Training Dummy: the vanilla humanoid model (player layer) with a straw-and-target skin. */
public class TrainingDummyRenderer extends HumanoidMobRenderer<TrainingDummyEntity, HumanoidModel<TrainingDummyEntity>> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(PowerLevelling.MODID, "textures/entity/training_dummy.png");

    public TrainingDummyRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER)), 0.5f);
    }

    @Override
    public ResourceLocation getTextureLocation(TrainingDummyEntity entity) {
        return TEXTURE;
    }
}

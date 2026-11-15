package com.powerlevelling.mixin;

import com.powerlevelling.client.pose.KiPoseClient;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies ki poses (KiPoseClient) at the tail of PlayerModel#setupAnim, after vanilla (and the sleeve copy) ran. Armor layers
 * copy the model's rotations afterwards (copyPropertiesTo), so they follow. Client-only mixin (see powerlevelling.mixins.json).
 */
@Mixin(PlayerModel.class)
public class PlayerModelMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void powerlevelling$kiPose(LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                                       float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (entity instanceof AbstractClientPlayer player) {
            // ageInTicks = tickCount + partialTick for players, so the partial tick falls out of it.
            KiPoseClient.apply((PlayerModel<?>) (Object) this, player, ageInTicks - player.tickCount);
        }
    }
}

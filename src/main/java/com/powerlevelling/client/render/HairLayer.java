package com.powerlevelling.client.render;

import java.util.Set;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.powerlevelling.character.Appearance;
import com.powerlevelling.character.CharacterRegistries;
import com.powerlevelling.character.CharacterSnapshot;
import com.powerlevelling.character.CosmeticPart;
import com.powerlevelling.client.ClientCharacters;
import com.powerlevelling.client.ClientVisualState;
import com.powerlevelling.config.ClientConfig;
import com.powerlevelling.network.VisualStatePayload;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;

/**
 * Draws the character's hair cosmetic part (Addendum B.5) on every player: the geo model from {@link GeoModelLoader}, anchored
 * to the head (or body) ModelPart, textured with the part's grayscale texture and tinted with the synced hair color. Added to
 * the WIDE and SLIM player renderers in ClientEvents. Looks everything up by id each frame, so resource reloads need no
 * re-wiring. First person: layers do not run for the local player's own body there, so nothing to special-case.
 */
public class HairLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    public HairLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw,
                       float headPitch) {
        if (player.isInvisible() || player.isSpectator()) return;
        CharacterSnapshot snap = ClientCharacters.effective(player.getId()); // the screen's preview for the local player
        if (snap == null) return;
        boolean preview = snap == ClientCharacters.previewOverride;
        if (!snap.created() && !preview) return;
        ResourceLocation style = snap.hairStyle();
        if (style == null || style.equals(Appearance.NO_HAIR)) return;

        CosmeticPart part = CharacterRegistries.getPart(player.level().registryAccess(), style);
        if (part == null) return;
        GeoModelLoader.GeoModel geo = GeoModelLoader.get(part.model());
        if (geo == null) return; // no art yet / failed to load: draw nothing

        // Helmet rule: the client config can override the part's own setting.
        Set<String> onlyBones = null;
        if (!player.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
            CosmeticPart.HelmetMode mode = switch (ClientConfig.HAIR_WITH_HELMET.get()) {
                case ALWAYS_HIDE -> CosmeticPart.HelmetMode.HIDE;
                case ALWAYS_SHOW -> CosmeticPart.HelmetMode.SHOW;
                case RESPECT_PART -> part.helmet();
            };
            if (mode == CosmeticPart.HelmetMode.HIDE) return;
            if (mode == CosmeticPart.HelmetMode.PARTIAL) {
                if (part.helmetVisibleBones().isEmpty()) return;
                onlyBones = Set.copyOf(part.helmetVisibleBones());
            }
        }

        PlayerModel<AbstractClientPlayer> model = getParentModel();
        ModelPart anchor = part.anchor() == CosmeticPart.Anchor.BODY ? model.body : model.head;
        
        // SSJ: override hair color to gold
        boolean isSSJ = ClientVisualState.hasSuperSaiyan(player.getId());
        int color = part.tintable() ? 0xFF000000 | (isSSJ ? 0xFFD700 : (snap.hairColor() & 0xFFFFFF)) : 0xFFFFFFFF;
        
        VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(part.texture()));

        poseStack.pushPose();
        try {
            anchor.translateAndRotate(poseStack);
            if (onlyBones != null) geo.showOnly(onlyBones);
            geo.root().render(poseStack, vc, packedLight, OverlayTexture.NO_OVERLAY, color);
        } finally {
            if (onlyBones != null) geo.showAll();
            poseStack.popPose();
        }
    }
}

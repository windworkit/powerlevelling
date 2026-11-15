package com.powerlevelling.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.powerlevelling.PowerLevelling;
import com.powerlevelling.character.CharacterSnapshot;
import com.powerlevelling.client.ClientCharacters;
import com.powerlevelling.client.ClientVisualState;
import com.powerlevelling.config.ClientConfig;
import com.powerlevelling.network.VisualStatePayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Optional eye overlay (Addendum B.4, eyeStyle 1-3 = round / narrow / sharp; 0 = the skin's own eyes, nothing drawn).
 * Two small quads on the front of the head, over the hat layer: pixel columns 1-2 and 5-6, rows 4-5 of the 8x8 face (Steve's eye
 * rows). Each quad shows one 8x8 layer of {@code textures/entity/eyes/eyes_N.png} (16x16: sclera layer on the left half, iris layer
 * on the right half, see tools/gen_eye_textures.py). The sclera is drawn untinted and the iris drawn right after it multiplied
 * by the eye color, so only the iris is tinted. The right eye uses the mirrored UVs. One RenderType (entityCutoutNoCull) and one
 * buffer, so the draw order is the submit order (sclera, then iris on top).
 * <p>
 * Model space (the pose after head.translateAndRotate): units are 1/16 block, y down, the face looks towards -z and the face
 * texture's left-to-right is model x increasing. Head cube spans x -4..4, y -8..0, z -4..4; the hat layer is +0.5.
 */
public class EyeLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    /** Gap in model pixels between the hat layer's front (z = -4.5) and the eye quads. Raise it if the eyes z-fight at a distance. */
    private static final float OUT_OF_HAT_PX = 0.01f;
    /** The iris sits this much further out than the sclera so equal-depth rounding can never put the sclera on top. */
    private static final float IRIS_EXTRA_PX = 0.005f;
    private static final float HAT_FRONT_PX = 4.5f;
    private static final float PX = 1f / 16f;

    // Eye rectangles in model pixels (x: -4 + face column, y: -8 + face row). Left = the viewer's left.
    private static final float LEFT_X0 = -4f + 1f;
    private static final float LEFT_X1 = -4f + 3f;
    private static final float RIGHT_X0 = -4f + 5f;
    private static final float RIGHT_X1 = -4f + 7f;
    private static final float Y0 = -8f + 4f;
    private static final float Y1 = -8f + 6f;

    private static final ResourceLocation[] TEXTURES = new ResourceLocation[3];

    static {
        for (int i = 0; i < TEXTURES.length; i++) {
            TEXTURES[i] = ResourceLocation.fromNamespaceAndPath(PowerLevelling.MODID, "textures/entity/eyes/eyes_" + (i + 1) + ".png");
        }
    }

    public EyeLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
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
        int style = snap.eyeStyle();
        if (style < 1 || style > TEXTURES.length) return; // 0 = skin's own eyes; unknown styles draw nothing
        if (player != Minecraft.getInstance().player && !ClientConfig.RENDER_EYE_OVERLAY_ON_OTHERS.get()) return;

        VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURES[style - 1]));
        // SSJ: override eye color to blue
        boolean isSSJ = ClientVisualState.hasSuperSaiyan(player.getId());
        int iris = 0xFF000000 | (isSSJ ? 0x00BFFF : (snap.eyeColor() & 0xFFFFFF));
        float zSclera = -(HAT_FRONT_PX + OUT_OF_HAT_PX) * PX;
        float zIris = -(HAT_FRONT_PX + OUT_OF_HAT_PX + IRIS_EXTRA_PX) * PX;

        poseStack.pushPose();
        try {
            getParentModel().head.translateAndRotate(poseStack);
            PoseStack.Pose pose = poseStack.last();
            // Pass 1: sclera layer (u 0..8, v 0..8 of 16), untinted. The right eye is the mirror image (u swapped).
            quad(vc, pose, LEFT_X0, LEFT_X1, zSclera, 0f, 0.5f, 0xFFFFFFFF, packedLight);
            quad(vc, pose, RIGHT_X0, RIGHT_X1, zSclera, 0.5f, 0f, 0xFFFFFFFF, packedLight);
            // Pass 2: iris layer (u 8..16), tinted.
            quad(vc, pose, LEFT_X0, LEFT_X1, zIris, 0.5f, 1f, iris, packedLight);
            quad(vc, pose, RIGHT_X0, RIGHT_X1, zIris, 1f, 0.5f, iris, packedLight);
        } finally {
            poseStack.popPose();
        }
    }

    /**
     * One eye quad from x0..x1 (model pixels) on rows Y0..Y1 at depth z (blocks), showing texture u from uLeft (at x0) to uRight
     * (at x1) and v 0..0.5 (the top half of the 16x16 file). Passing uLeft > uRight mirrors it.
     */
    private static void quad(VertexConsumer vc, PoseStack.Pose pose, float x0, float x1, float z, float uLeft, float uRight,
                             int color, int light) {
        float ax = x0 * PX;
        float bx = x1 * PX;
        float ty = Y0 * PX;
        float by = Y1 * PX;
        vertex(vc, pose, ax, ty, z, uLeft, 0f, color, light);
        vertex(vc, pose, bx, ty, z, uRight, 0f, color, light);
        vertex(vc, pose, bx, by, z, uRight, 0.5f, color, light);
        vertex(vc, pose, ax, by, z, uLeft, 0.5f, color, light);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float u, float v, int color,
                               int light) {
        vc.addVertex(pose, x, y, z)
                .setColor(color)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, 0f, 0f, -1f);
    }
}

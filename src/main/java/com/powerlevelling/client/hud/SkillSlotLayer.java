package com.powerlevelling.client.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.powerlevelling.client.ClientCooldowns;
import com.powerlevelling.client.ClientPlayerData;
import com.powerlevelling.client.ClientSkillState;
import com.powerlevelling.client.ClientVisualState;
import com.powerlevelling.config.ClientConfig;
import com.powerlevelling.data.KiProgression;
import com.powerlevelling.ki.KiAttack;
import com.powerlevelling.ki.KiAttacks;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Skill slots 1-4 (design section 7): a compact row of boxes under the resource panel, shown only in Combat Stance.
 * Each box: slot number, an orb in the attack's colour, the ki cost, a cooldown wipe, and a charge bar while that slot's
 * key is held. Same pixel frame and sprites as the resource panel (HudSprites), same scale/offset config.
 */
public class SkillSlotLayer implements LayeredDraw.Layer {
    private static final int SLOT = 22;       // box size (frame corner 3 -> 16x16 interior)
    private static final int GAP = 2;
    private static final int TOP_GAP = 7;     // gap below the panel's EXP strip
    private static final float TINY = 0.5f;   // text scale for number and cost

    private static final int COLOR_NUM = 0xFFB0B8C8;
    private static final int COLOR_COST = 0xFFB8F0FF;
    private static final int COLOR_COST_LOW = 0xFFFF7060;

    @Override
    public void render(GuiGraphics g, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.options.hideGui || player.isSpectator()) return;
        if (!ClientVisualState.selfStance()) return;

        Font font = mc.font;
        float time = System.nanoTime() / 1.0e9f;
        float scale = ClientConfig.HUD_SCALE.get().floatValue();

        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate((float) ClientConfig.HUD_X.get(), (float) ClientConfig.HUD_Y.get(), 0f);
        pose.scale(scale, scale, 1f);
        RenderSystem.enableBlend();

        int y = ResourceHudLayer.PANEL_H + TOP_GAP;
        for (int i = 0; i < KiProgression.LOADOUT_SLOTS; i++) {
            int x = i * (SLOT + GAP);
            ResourceLocation id = ClientPlayerData.loadout[i];
            KiAttack attack = id == null ? null : KiAttacks.resolve(mc.level.registryAccess(), id);
            drawSlot(g, font, i, x, y, attack, id, time);
        }

        pose.popPose();
    }

    private static void drawSlot(GuiGraphics g, Font font, int slot, int x, int y, KiAttack attack, ResourceLocation id, float time) {
        HudSprites.frame(g, x, y, SLOT, SLOT);
        // Interior is (x+3, y+3, 16, 16).
        drawScaled(g, font, Integer.toString(slot + 1), x + 3.5f, y + 3f, TINY, COLOR_NUM);

        if (attack == null) { // empty slot: a dim dash
            HudSprites.flash(g, x + 8, y + 10, 6, 1, 0.6f, 0.65f, 0.75f, 0.35f);
            return;
        }

        int color = attack.common().visual().color();
        int core = attack.common().visual().coreColor();
        orb(g, x + 7, y + 4, color, core);

        // Ki cost, tiny, red when you can't afford it.
        // base + max-ki fraction (burst), minus the per-attack mastery tier discount.
        int cost = Math.round(attack.baseCost(ClientPlayerData.maxKi) * KiProgression.tierCostMult(ClientPlayerData.attackTier(id)));
        boolean affordable = ClientPlayerData.ki >= cost && !ClientPlayerData.kiBreak;
        String costText = Integer.toString(cost);
        float cw = font.width(costText) * TINY;
        drawScaled(g, font, costText, x + SLOT / 2f - cw / 2f, y + 13f, TINY, affordable ? COLOR_COST : COLOR_COST_LOW);

        // Cooldown wipe: a dark overlay that shrinks from full height to nothing, anchored at the bottom.
        float cd = ClientCooldowns.fraction(slot);
        if (cd > 0f) {
            int h = Math.max(1, Math.round(16 * cd));
            HudSprites.flash(g, x + 3, y + 3 + 16 - h, 16, h, 0f, 0f, 0f, 0.62f);
            // Seconds left for longer cooldowns, centred over the box.
            int remaining = ClientCooldowns.remaining(slot);
            if (remaining >= 20) {
                String sec = Integer.toString((remaining + 19) / 20);
                float sw = font.width(sec) * 0.75f;
                drawScaled(g, font, sec, x + SLOT / 2f - sw / 2f, y + 7.5f, 0.75f, 0xFFFFFFFF);
            }
        }

        // Charge / wind-up bar while this slot is charging (key held) or its orb is forming.
        if (ClientSkillState.heldSlot == slot) {
            float frac = Mth.clamp(ClientSkillState.fraction(slot), 0f, 1f);
            HudSprites.thinBar(g, HudSprites.FILL_KI, x + 3, y + 17, 16, 2, frac);
            if (frac >= 1f) { // full: pulsing border
                float p = 0.5f + 0.5f * Mth.sin(time * Mth.TWO_PI / 0.5f);
                float a = 0.35f + 0.55f * p;
                HudSprites.flash(g, x, y, SLOT, 1, 1f, 0.85f, 0.3f, a);
                HudSprites.flash(g, x, y + SLOT - 1, SLOT, 1, 1f, 0.85f, 0.3f, a);
                HudSprites.flash(g, x, y + 1, 1, SLOT - 2, 1f, 0.85f, 0.3f, a);
                HudSprites.flash(g, x + SLOT - 1, y + 1, 1, SLOT - 2, 1f, 0.85f, 0.3f, a);
            }
        }
    }

    /** 8x8 pixel orb: octagon in the attack colour with a small bright core. */
    private static void orb(GuiGraphics g, int x, int y, int rgb, int coreRgb) {
        float r = ((rgb >> 16) & 0xFF) / 255f;
        float gr = ((rgb >> 8) & 0xFF) / 255f;
        float b = (rgb & 0xFF) / 255f;
        HudSprites.flash(g, x + 2, y, 4, 8, r, gr, b, 1f);
        HudSprites.flash(g, x, y + 2, 8, 4, r, gr, b, 1f);
        HudSprites.flash(g, x + 1, y + 1, 6, 6, r, gr, b, 1f);
        float cr = ((coreRgb >> 16) & 0xFF) / 255f;
        float cg = ((coreRgb >> 8) & 0xFF) / 255f;
        float cb = (coreRgb & 0xFF) / 255f;
        HudSprites.flash(g, x + 3, y + 3, 2, 2, cr, cg, cb, 0.95f);
    }

    private static void drawScaled(GuiGraphics g, Font font, String s, float x, float y, float scale, int color) {
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(x, y, 0f);
        pose.scale(scale, scale, 1f);
        g.drawString(font, s, 0, 0, color, true);
        pose.popPose();
    }
}

package com.powerlevelling.client.hud;

import java.util.Locale;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.powerlevelling.client.ClientCharacters;
import com.powerlevelling.client.ClientPlayerData;
import com.powerlevelling.client.ClientVisualState;
import com.powerlevelling.client.ModKeys;
import com.powerlevelling.config.ClientConfig;
import com.powerlevelling.client.hud.HudSprites.Sprite;
import com.powerlevelling.network.VisualStatePayload;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Top-left HUD panel (design section 7 / A.7): level badge + Power Level, then HP / KI / ST rows
 * (icon, 10-segment bar, small right-aligned numbers) and a thin EXP strip under the panel.
 * All graphics are blits from the hud.png atlas (see {@link HudSprites}); only text uses the vanilla font.
 */
public class ResourceHudLayer implements LayeredDraw.Layer {
    // ---- layout (unscaled HUD pixels, relative to the panel's top-left) ----------------------------------------
    private static final int PAD = 5;
    private static final int BADGE = 15;            // star badge size
    private static final int ROW_Y0 = PAD + BADGE + 3;
    private static final int ROW_PITCH = 11;
    private static final int ICON = 9;
    private static final int BAR_X = PAD + ICON + 3;
    private static final int BAR_W = 90;
    private static final int BAR_H = 7;
    private static final int NUM_GAP = 4;
    private static final int NUM_MIN_W = 34;
    static final int PANEL_H = ROW_Y0 + 2 * ROW_PITCH + ICON + 4; // package-private: SkillSlotLayer sits below it
    private static final float NUM_SCALE = 0.75f;
    private static final int SEGMENTS = 10;

    private static final int COLOR_PL = 0xFFFFD84A;
    private static final int COLOR_NUM = 0xFFFFFFFF;

    // ---- animation state -----------------------------------------------------------------------------------------
    private final Trail hp = new Trail();
    private final Trail ki = new Trail();
    private final Trail st = new Trail();
    private long lastNanos = 0L;

    @Override
    public void render(GuiGraphics g, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.options.hideGui || player.isSpectator()) return;

        // Frame timing from the wall clock (independent of tick rate and of DeltaTracker API details).
        long now = System.nanoTime();
        float dt = lastNanos == 0L ? 0f : Mth.clamp((now - lastNanos) / 1.0e9f, 0f, 0.1f);
        lastNanos = now;
        float time = (now / 1.0e9f);

        Font font = mc.font;

        // Values
        float maxHp = Math.max(1f, player.getMaxHealth());
        float curHp = Mth.clamp(player.getHealth(), 0f, maxHp);
        float maxKi = Math.max(1f, ClientPlayerData.maxKi);
        float curKi = Mth.clamp(ClientPlayerData.ki, 0f, maxKi);
        float fullSt = Math.max(1f, ClientPlayerData.fullMaxStamina(player.level().registryAccess()));
        float curSt = Mth.clamp(ClientPlayerData.stamina, 0f, fullSt);
        boolean kiBreak = ClientPlayerData.kiBreak;

        hp.update(curHp, maxHp, dt, now);
        ki.update(curKi, maxKi, dt, now);
        st.update(curSt, fullSt, dt, now);

        String hpText = (int) Math.ceil(curHp) + "/" + (int) Math.ceil(maxHp);
        String kiText = Math.round(curKi) + "/" + Math.round(maxKi);
        String stText = Math.round(curSt) + "/" + Math.round(ClientPlayerData.maxStamina);

        int numW = NUM_MIN_W;
        numW = Math.max(numW, smallWidth(font, hpText));
        numW = Math.max(numW, smallWidth(font, kiText));
        numW = Math.max(numW, smallWidth(font, stText));
        int panelW = BAR_X + BAR_W + NUM_GAP + numW + PAD;

        float scale = ClientConfig.HUD_SCALE.get().floatValue();
        int ox = ClientConfig.HUD_X.get();
        int oy = ClientConfig.HUD_Y.get();

        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate((float) ox, (float) oy, 0f);
        pose.scale(scale, scale, 1f);
        RenderSystem.enableBlend();

        // ---- panel + thin EXP strip ----
        HudSprites.frameWithAccent(g, 0, 0, panelW, PANEL_H);
        boolean maxed = ClientPlayerData.level >= ClientPlayerData.levelCap;
        float expFrac = maxed ? 1f : ClientPlayerData.exp / (float) Math.max(1, ClientPlayerData.expToNext);
        HudSprites.thinBar(g, HudSprites.FILL_EXP, 2, PANEL_H + 1, panelW - 4, 2, expFrac);

        // ---- row 1: level badge + PL ----
        HudSprites.sprite(g, HudSprites.ICON_STAR, PAD, PAD);
        String lv = Integer.toString(ClientPlayerData.level);
        float lvScale = lv.length() >= 3 ? 0.5f : NUM_SCALE;
        float lvW = font.width(lv) * lvScale;
        drawScaled(g, font, lv, PAD + BADGE / 2f - lvW / 2f + 0.25f, PAD + 8f - 3.5f * lvScale, lvScale, 0xFFFFFFFF);

        String pl = Component.translatable("hud.powerlevelling.power_level",
                String.format(Locale.ROOT, "%,d", ClientPlayerData.powerLevel())).getString();
        g.drawString(font, pl, PAD + BADGE + 4, PAD + 4, COLOR_PL, true);

        // Combat Stance indicator: small orange label, right-aligned on the same row, gently pulsing.
        if (ClientVisualState.has(player.getId(), VisualStatePayload.STANCE)) {
            String stanceText = Component.translatable("hud.powerlevelling.stance").getString();
            float sp = 0.5f + 0.5f * Mth.sin(time * Mth.TWO_PI / 1.6f);
            int stanceColor = 0xFF000000 | (255 << 16) | ((int) (140 + 40 * sp) << 8) | (int) (40 + 60 * sp);
            float sw = font.width(stanceText) * NUM_SCALE;
            drawScaled(g, font, stanceText, panelW - PAD - sw, PAD + 5f, NUM_SCALE, stanceColor);
        }

        // ---- row 2: HP ----
        int rowY = ROW_Y0;
        int numRight = panelW - PAD;
        HudSprites.sprite(g, HudSprites.ICON_HEART, PAD, rowY);
        float hpFrac = curHp / maxHp;
        bar(g, BAR_X, rowY + 1, hp, maxHp, HudSprites.FILL_HP, HudSprites.GHOST_HP, -1f);
        if (hpFrac < 0.2f && hpFrac > 0f) { // gentle low-HP pulse
            float p = 0.5f + 0.5f * Mth.sin(time * Mth.TWO_PI / 1.4f);
            int fw = HudSprites.pixels(BAR_W, hp.shown / maxHp);
            HudSprites.flash(g, BAR_X + 1, rowY + 2, fw, BAR_H - 2, 1f, 0.55f, 0.55f, 0.08f + 0.22f * p);
        }
        rightNumber(g, font, hpText, numRight, rowY, COLOR_NUM);

        // ---- row 3: KI ----
        rowY += ROW_PITCH;
        HudSprites.sprite(g, HudSprites.ICON_FLAME, PAD, rowY);
        bar(g, BAR_X, rowY + 1, ki, maxKi, kiBreak ? HudSprites.FILL_KI_BREAK : HudSprites.FILL_KI,
                HudSprites.GHOST_KI, 0.25f);
        int numColor = COLOR_NUM;
        if (ClientVisualState.has(player.getId(), VisualStatePayload.CHARGING)) { // charge shimmer: pulse + sweeping glint
            float p = 0.5f + 0.5f * Mth.sin(time * Mth.TWO_PI / 0.5f);
            int fw = HudSprites.pixels(BAR_W, ki.shown / maxKi);
            HudSprites.flash(g, BAR_X + 1, rowY + 2, fw, BAR_H - 2, 0.75f, 1f, 1f, 0.12f + 0.28f * p);
            // A bright band sweeping left to right across the filled part.
            int band = 10;
            int sweepLen = Math.max(1, fw + band);
            int bx = (int) ((time * 70f) % sweepLen) - band;
            int x0 = Math.max(0, bx);
            int x1 = Math.min(fw, bx + band);
            HudSprites.flash(g, BAR_X + 1 + x0, rowY + 2, x1 - x0, BAR_H - 2, 1f, 1f, 1f, 0.45f);
            numColor = 0xFFB8F0FF;
        }
        if (kiBreak) { // red-orange pulse: overlay on the fill, glowing border, reddish number
            float p = 0.5f + 0.5f * Mth.sin(time * Mth.TWO_PI / 0.8f);
            int fw = HudSprites.pixels(BAR_W, ki.shown / maxKi);
            HudSprites.flash(g, BAR_X + 1, rowY + 2, fw, BAR_H - 2, 1f, 0.75f, 0.6f, 0.10f + 0.30f * p);
            float ba = 0.25f + 0.65f * p;
            int by = rowY + 1;
            HudSprites.flash(g, BAR_X, by, BAR_W, 1, 1f, 0.2f, 0.15f, ba);
            HudSprites.flash(g, BAR_X, by + BAR_H - 1, BAR_W, 1, 1f, 0.2f, 0.15f, ba);
            HudSprites.flash(g, BAR_X, by + 1, 1, BAR_H - 2, 1f, 0.2f, 0.15f, ba);
            HudSprites.flash(g, BAR_X + BAR_W - 1, by + 1, 1, BAR_H - 2, 1f, 0.2f, 0.15f, ba);
            numColor = 0xFF000000 | (255 << 16) | ((int) (110 + 100 * (1f - p)) << 8) | (int) (90 + 80 * (1f - p));
        }
        rightNumber(g, font, kiText, numRight, rowY, numColor);

        // ---- row 4: ST (bar spans full stamina; the part above current max is hatched) ----
        rowY += ROW_PITCH;
        HudSprites.sprite(g, HudSprites.ICON_BOLT, PAD, rowY);
        bar(g, BAR_X, rowY + 1, st, fullSt, HudSprites.FILL_STAMINA, null, -1f); // no ghost: sprint drain would flicker
        int lostFrom = Mth.clamp((int) Math.floor((BAR_W - 2) * ClientPlayerData.maxStamina / fullSt), 0, BAR_W - 2);
        if (lostFrom < BAR_W - 2) {
            HudSprites.hatch(g, BAR_X, rowY + 1, BAR_W, BAR_H, lostFrom, BAR_W - 2);
        }
        rightNumber(g, font, stText, numRight, rowY, COLOR_NUM);

        // ---- "Press K: create character" badge (pulsing orange) under the panel, until the character exists ----
        if (ClientCharacters.selfKnown() && !ClientCharacters.selfCreated()) {
            String badge = Component.translatable("hud.powerlevelling.create_prompt",
                    ModKeys.OPEN_STATS.getTranslatedKeyMessage()).getString();
            int badgeW = (int) Math.ceil(font.width(badge) * NUM_SCALE) + 2 * PAD;
            int badgeH = 13;
            // Below the skill-slot row when that is showing (stance), otherwise right under the EXP strip.
            int badgeY = PANEL_H + 7 + (ClientVisualState.has(player.getId(), VisualStatePayload.STANCE) ? 26 : 0);
            HudSprites.frame(g, 0, badgeY, badgeW, badgeH);
            float bp = 0.5f + 0.5f * Mth.sin(time * Mth.TWO_PI / 1.6f);
            int badgeColor = 0xFF000000 | (255 << 16) | ((int) (140 + 40 * bp) << 8) | (int) (40 + 60 * bp);
            drawScaled(g, font, badge, PAD, badgeY + 3.5f, NUM_SCALE, badgeColor);
        }

        pose.popPose();
    }

    // ---- pieces --------------------------------------------------------------------------------------------------

    /**
     * One segmented bar at (x, y): track, ghost trail (shown..ghost), live fill, segment gaps and an optional
     * tick (tickFrac in 0..1, or negative for none). Segments and tick are drawn last so they cut through everything.
     */
    private static void bar(GuiGraphics g, int x, int y, Trail t, float max, Sprite fill, Sprite ghost, float tickFrac) {
        HudSprites.track(g, x, y, BAR_W, BAR_H);
        int fw = HudSprites.pixels(BAR_W, t.shown / max);
        int gw = HudSprites.pixels(BAR_W, t.ghost / max);
        if (ghost != null && gw > fw) HudSprites.fillRange(g, ghost, x, y, BAR_W, BAR_H, fw, gw);
        HudSprites.fillRange(g, fill, x, y, BAR_W, BAR_H, 0, fw);
        HudSprites.segments(g, x, y, BAR_W, BAR_H, SEGMENTS);
        if (tickFrac >= 0f) HudSprites.tick(g, x, y, BAR_W, BAR_H, Math.round((BAR_W - 2) * tickFrac));
    }

    private static int smallWidth(Font font, String s) {
        return (int) Math.ceil(font.width(s) * NUM_SCALE);
    }

    /** Small (0.75x) number, right-aligned to {@code right}, vertically centred on the row's bar. */
    private static void rightNumber(GuiGraphics g, Font font, String s, int right, int rowY, int color) {
        float w = font.width(s) * NUM_SCALE;
        drawScaled(g, font, s, right - w, rowY + 2f, NUM_SCALE, color);
    }

    private static void drawScaled(GuiGraphics g, Font font, String s, float x, float y, float scale, int color) {
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(x, y, 0f);
        pose.scale(scale, scale, 1f);
        g.drawString(font, s, 0, 0, color, true);
        pose.popPose();
    }

    /** Smoothly animated value with a fighting-game style "ghost" trail that lingers after a drop. */
    private static final class Trail {
        private static final long HOLD_NANOS = 450_000_000L;
        float shown;
        float ghost;
        private float last;
        private long holdUntil;
        private boolean init;

        void update(float value, float max, float dt, long now) {
            float eps = max * 0.0005f;
            if (!init) {
                shown = ghost = last = value;
                init = true;
                return;
            }
            // A fresh drop starts the hold timer (not extended while a trail is already showing).
            if (value < last - eps && ghost - shown < max * 0.004f) holdUntil = now + HOLD_NANOS;
            last = value;

            shown += (value - shown) * (1f - (float) Math.exp(-dt * 14f));
            if (Math.abs(value - shown) < eps) shown = value;

            if (ghost < shown) ghost = shown;
            if (ghost > value && now >= holdUntil) {
                ghost += (value - ghost) * (1f - (float) Math.exp(-dt * 3f));
                if (ghost - value < eps) ghost = value;
            }
            if (ghost < shown) ghost = shown;
        }
    }
}

package com.powerlevelling.client.screen;

import net.minecraft.client.gui.GuiGraphics;

/** Pixel-style glow / flame / orb drawn with plain fills (character screen preview and Ki tab). */
final class PreviewFx {
    private static final long START = System.nanoTime();

    private PreviewFx() {
    }

    static float time() {
        return (System.nanoTime() - START) / 1.0e9f;
    }

    /** Filled ellipse as horizontal bands of {@code step} px. */
    static void ellipse(GuiGraphics g, float cx, float cy, float rx, float ry, int argb, int step) {
        if (rx <= 0f || ry <= 0f) return;
        for (float dy = -ry; dy < ry; dy += step) {
            float k = (dy + step / 2f) / ry;
            if (k <= -1f || k >= 1f) continue;
            float hw = rx * (float) Math.sqrt(1f - k * k);
            int y0 = Math.round(cy + dy);
            int y1 = Math.max(y0 + 1, Math.round(cy + dy + step));
            g.fill(Math.round(cx - hw), y0, Math.round(cx + hw), y1, argb);
        }
    }

    /** Soft glow plus a flickering two-layer flame between topY and footY, centered on cx. */
    static void aura(GuiGraphics g, float cx, float footY, float topY, float halfW, int rgb, float t) {
        float midY = (footY + topY) / 2f;
        float halfH = (footY - topY) / 2f;
        for (int i = 0; i < 4; i++) {
            float f = 1.25f - i * 0.2f;
            ellipse(g, cx, midY, halfW * 1.35f * f, halfH * 1.05f * f, PixelUi.argb(0x16 + i * 0x0C, rgb), 3);
        }
        int rows = 26;
        float rowH = (footY - topY) / rows;
        for (int pass = 0; pass < 2; pass++) {
            float wScale = pass == 0 ? 1f : 0.58f;
            int col = pass == 0 ? rgb : PixelUi.mix(rgb, 0xFFFFFF, 0.55f);
            int alpha = pass == 0 ? 0x50 : 0x78;
            for (int r = 0; r < rows; r++) {
                float u = (r + 0.5f) / rows; // 0 = tip .. 1 = base
                float prof = (float) Math.sin(Math.PI * Math.pow(u, 1.5));
                prof = Math.max(prof, 0.35f * u * u * u);
                float flick = 1f + 0.10f * (float) Math.sin(t * 9f + r * 0.9f + pass * 1.7f);
                float sway = (float) Math.sin(t * 5f + u * 4f + pass) * halfW * 0.10f * (1f - u);
                float hw = halfW * wScale * prof * flick;
                if (hw < 0.5f) continue;
                int y0 = Math.round(topY + r * rowH);
                int y1 = Math.max(y0 + 1, Math.round(topY + (r + 1) * rowH));
                g.fill(Math.round(cx + sway - hw), y0, Math.round(cx + sway + hw), y1, PixelUi.argb(alpha, col));
            }
        }
    }

    /** Pixel ki orb: glow, shell, bright core (white-mixed like the in-game blast core), orbiting sparks. */
    static void orb(GuiGraphics g, float cx, float cy, float r, int ki, float t) {
        float rr = r * (1f + 0.06f * (float) Math.sin(t * 5f));
        ellipse(g, cx, cy, rr * 1.45f, rr * 1.45f, PixelUi.argb(0x28, ki), 2);
        ellipse(g, cx, cy, rr, rr, PixelUi.argb(0x60, ki), 2);
        ellipse(g, cx, cy, rr * 0.72f, rr * 0.72f, PixelUi.argb(0xA0, PixelUi.mix(ki, 0xFFFFFF, 0.25f)), 2);
        ellipse(g, cx, cy, rr * 0.42f, rr * 0.42f, 0xFF000000 | PixelUi.mix(ki, 0xFFFFFF, 0.75f), 1);
        for (int i = 0; i < 5; i++) {
            float a = t * 1.7f + i * 1.2566f;
            float d = rr * (1.2f + 0.25f * (float) Math.sin(t * 3f + i));
            int sx = Math.round(cx + (float) Math.cos(a) * d);
            int sy = Math.round(cy + (float) Math.sin(a) * d * 0.8f);
            g.fill(sx, sy, sx + 2, sy + 2, PixelUi.argb(0xC0, PixelUi.mix(ki, 0xFFFFFF, 0.5f)));
        }
    }
}

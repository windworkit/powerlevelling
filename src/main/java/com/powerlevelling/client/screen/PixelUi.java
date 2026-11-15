package com.powerlevelling.client.screen;

import com.powerlevelling.client.hud.HudSprites;
import com.powerlevelling.client.hud.HudSprites.Sprite;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Helpers shared by the mod's screens. Panels and bars are drawn from the same atlas sprites as the in-game HUD
 * ({@link HudSprites}); only text colours and small text helpers live here.
 */
final class PixelUi {
    static final int BODY = 0xFF1B1F3A;          // dark navy
    static final int BORDER_LIGHT = 0xFFE8E8F0;
    static final int BORDER_DARK = 0xFF5A6090;   // thin separators
    static final int ORANGE = 0xFFF28C28;        // accent
    static final int ORANGE_LIGHT = 0xFFFFB45A;
    static final int GOLD = 0xFFFFD84A;
    static final int TEXT = 0xFFFFFFFF;
    static final int TEXT_DIM = 0xFF9AA0C8;
    static final int ROW_BG = 0xFF232850;
    static final int BAR_BG = 0xFF10142A;
    static final int BAR_EDGE = 0xFF0A0C1C;

    private PixelUi() {
    }

    /** Window frame (9-slice from the atlas). */
    static void frame(GuiGraphics g, int x, int y, int w, int h) {
        HudSprites.frame(g, x, y, w, h);
    }

    /** Sunken row background (9-slice). */
    static void inset(GuiGraphics g, int x, int y, int w, int h) {
        HudSprites.inset(g, x, y, w, h);
    }

    /** 2px orange divider. */
    static void divider(GuiGraphics g, int x, int y, int w) {
        HudSprites.accentLine(g, x, y, w);
    }

    /** Horizontal bar: atlas track + gradient fill; fraction 0..1. Needs w >= 4 and h >= 4. */
    static void bar(GuiGraphics g, int x, int y, int w, int h, float fraction, Sprite fill) {
        HudSprites.bar(g, x, y, w, h, fraction, fill);
    }

    /** Icon sprite at 1:1. */
    static void icon(GuiGraphics g, Sprite icon, int x, int y) {
        HudSprites.sprite(g, icon, x, y);
    }

    /** Text drawn at 0.75 scale (x/y are in unscaled GUI pixels). */
    static void small(GuiGraphics g, Font font, Component text, int x, int y, int color) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0f);
        g.pose().scale(0.75f, 0.75f, 1f);
        g.drawString(font, text, 0, 0, color, false);
        g.pose().popPose();
    }

    static int smallWidth(Font font, Component text) {
        return (int) Math.ceil(font.width(text) * 0.75f);
    }

    /** Small text centered on cx. */
    static void smallCentered(GuiGraphics g, Font font, Component text, int cx, int y, int color) {
        small(g, font, text, cx - smallWidth(font, text) / 2, y, color);
    }

    // ---- C8: character screen widgets ---------------------------------------------------------------------------------
    static final int BTN_HOVER = 0xFF2F3770;
    static final int TEXT_OFF = 0xFF5A6090;
    static final int GOOD = 0xFF6CE08A;
    static final int BAD = 0xFFFF6B6B;
    static final int SEL_BG = 0xFF7A4313;

    static int argb(int alpha, int rgb) {
        return (alpha << 24) | (rgb & 0xFFFFFF);
    }

    /** 1px outline only. */
    static void outline(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y + 1, x + 1, y + h - 1, color);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    /** Filled rectangle with a 1px border. */
    static void box(GuiGraphics g, int x, int y, int w, int h, int fill, int border) {
        g.fill(x, y, x + w, y + h, border);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
    }

    /** Text centered on cx; scaled down (never up) so it fits maxW. */
    static void fitCentered(GuiGraphics g, Font font, Component text, int cx, int y, int maxW, int color, boolean shadow) {
        int tw = font.width(text);
        if (tw <= maxW || maxW <= 0) {
            g.drawString(font, text, cx - tw / 2, y, color, shadow);
            return;
        }
        float sc = maxW / (float) tw;
        g.pose().pushPose();
        g.pose().translate(cx - maxW / 2f, y + 4f * (1f - sc), 0f);
        g.pose().scale(sc, sc, 1f);
        g.drawString(font, text, 0, 0, color, shadow);
        g.pose().popPose();
    }

    /** Pixel-style button: normal / hover / selected (orange) / disabled. */
    static void button(GuiGraphics g, Font font, int x, int y, int w, int h, Component label,
                       boolean hover, boolean enabled, boolean selected) {
        int fill;
        int border;
        int text;
        if (!enabled) {
            fill = BAR_BG;
            border = BAR_EDGE;
            text = TEXT_OFF;
        } else if (selected) {
            fill = hover ? ORANGE_LIGHT : ORANGE;
            border = BORDER_LIGHT;
            text = BODY;
        } else if (hover) {
            fill = BTN_HOVER;
            border = ORANGE_LIGHT;
            text = TEXT;
        } else {
            fill = ROW_BG;
            border = BORDER_DARK;
            text = TEXT;
        }
        box(g, x, y, w, h, fill, border);
        fitCentered(g, font, label, x + w / 2, y + (h - 8) / 2, w - 4, text, enabled && !selected);
    }

    /** 7x9 padlock. */
    static void lock(GuiGraphics g, int x, int y, int color) {
        g.fill(x + 1, y, x + 6, y + 1, color);
        g.fill(x + 1, y + 1, x + 2, y + 4, color);
        g.fill(x + 5, y + 1, x + 6, y + 4, color);
        g.fill(x, y + 4, x + 7, y + 9, color);
        g.fill(x + 3, y + 5, x + 4, y + 7, BODY);
    }

    /** Linear mix of two RGB ints. */
    static int mix(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 255) * (1f - t) + ((b >> 16) & 255) * t);
        int gr = Math.round(((a >> 8) & 255) * (1f - t) + ((b >> 8) & 255) * t);
        int bl = Math.round((a & 255) * (1f - t) + (b & 255) * t);
        return (r << 16) | (gr << 8) | bl;
    }

    /** h, s, v in 0..1; returns packed RGB. */
    static int hsvToRgb(float h, float s, float v) {
        h = h - (float) Math.floor(h);
        float hp = h * 6f;
        int i = (int) hp;
        float f = hp - i;
        float p = v * (1f - s);
        float q = v * (1f - s * f);
        float t = v * (1f - s * (1f - f));
        float r;
        float gr;
        float b;
        switch (i) {
            case 0 -> { r = v; gr = t; b = p; }
            case 1 -> { r = q; gr = v; b = p; }
            case 2 -> { r = p; gr = v; b = t; }
            case 3 -> { r = p; gr = q; b = v; }
            case 4 -> { r = t; gr = p; b = v; }
            default -> { r = v; gr = p; b = q; }
        }
        return (Math.round(r * 255f) << 16) | (Math.round(gr * 255f) << 8) | Math.round(b * 255f);
    }

    /** Returns {h, s, v}, each 0..1. */
    static float[] rgbToHsv(int rgb) {
        float r = ((rgb >> 16) & 255) / 255f;
        float gr = ((rgb >> 8) & 255) / 255f;
        float b = (rgb & 255) / 255f;
        float max = Math.max(r, Math.max(gr, b));
        float min = Math.min(r, Math.min(gr, b));
        float d = max - min;
        float h;
        if (d == 0f) h = 0f;
        else if (max == r) h = ((gr - b) / d) % 6f;
        else if (max == gr) h = (b - r) / d + 2f;
        else h = (r - gr) / d + 4f;
        h /= 6f;
        if (h < 0f) h += 1f;
        return new float[]{h, max == 0f ? 0f : d / max, max};
    }
}

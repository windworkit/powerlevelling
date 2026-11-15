package com.powerlevelling.client.screen;

import java.util.function.IntConsumer;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * Modal custom color picker: 64x64 saturation/value square, hue strip, hex box, live swatch, OK / Cancel.
 * With {@code minV} > 0 (ki / aura) the dark band of the square is blocked and every value is clamped to it.
 * Drawn and hit-tested through {@link UiCanvas} (virtual coordinates); the EditBox is rendered by hand, not a screen child.
 */
final class ColorPicker {
    static final int W = 190;
    static final int H = 124;
    private static final String P = "gui.powerlevelling.character.";

    private final Font font;
    private final EditBox hex;
    private boolean open;
    private float h;
    private float s;
    private float v;
    private int rgb;
    private float minV;
    private boolean clamped;
    private boolean validText = true;
    private boolean updatingHex;
    private Component title = Component.empty();
    private IntConsumer onOk = c -> { };

    ColorPicker(Font font) {
        this.font = font;
        this.hex = new EditBox(font, 0, 0, 78, 14, Component.translatable(P + "picker.hex"));
        this.hex.setMaxLength(7);
        this.hex.setFilter(str -> str.matches("#?[0-9a-fA-F]{0,6}"));
        this.hex.setResponder(this::onHexTyped);
    }

    boolean isOpen() {
        return open;
    }

    void open(Component title, int color, float minV, IntConsumer onOk) {
        this.title = title;
        this.minV = minV;
        this.onOk = onOk;
        this.clamped = false;
        float[] hsv = PixelUi.rgbToHsv(color & 0xFFFFFF);
        h = hsv[0];
        s = hsv[1];
        v = hsv[2];
        rgb = color & 0xFFFFFF;
        if (v < minV) {
            v = minV;
            rgb = PixelUi.hsvToRgb(h, s, v);
        }
        open = true;
        hex.setFocused(false);
        setHexText();
    }

    private void close() {
        open = false;
        hex.setFocused(false);
    }

    /** Called before a click is routed: a click elsewhere drops the hex box focus (the hex hit re-focuses it). */
    void preClick() {
        hex.setFocused(false);
    }

    private void setHexText() {
        updatingHex = true;
        hex.setValue(String.format("#%06X", rgb));
        updatingHex = false;
        validText = true;
    }

    private void onHexTyped(String text) {
        if (updatingHex) return;
        String t = text.startsWith("#") ? text.substring(1) : text;
        if (t.length() != 6) {
            validText = false;
            return;
        }
        validText = true;
        int parsed = Integer.parseInt(t, 16);
        float[] hsv = PixelUi.rgbToHsv(parsed);
        if (hsv[1] > 0f && hsv[2] > 0f) h = hsv[0];
        s = hsv[1];
        v = hsv[2];
        clamped = false;
        rgb = parsed;
        if (v < minV) {
            v = minV;
            rgb = PixelUi.hsvToRgb(h, s, v);
            clamped = true;
            setHexText();
        }
    }

    private void setSv(double dx, double dy) {
        s = (float) Math.max(0.0, Math.min(1.0, dx / 63.0));
        v = (float) Math.max(0.0, Math.min(1.0, 1.0 - dy / 63.0));
        if (v < minV) v = minV;
        clamped = false;
        rgb = PixelUi.hsvToRgb(h, s, v);
        setHexText();
    }

    private void setHue(double dy) {
        h = (float) Math.max(0.0, Math.min(0.9999, dy / 63.0));
        clamped = false;
        rgb = PixelUi.hsvToRgb(h, s, v);
        setHexText();
    }

    private void ok() {
        close();
        onOk.accept(rgb);
    }

    boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            if (validText) ok();
            return true;
        }
        if (hex.isFocused()) hex.keyPressed(key, scan, mods);
        return true;
    }

    boolean charTyped(char ch, int mods) {
        if (hex.isFocused()) hex.charTyped(ch, mods);
        return true;
    }

    /** Draws the modal centered in a panelW x panelH panel (the caller has translated z above the 3D preview). */
    void render(UiCanvas c, int panelW, int panelH) {
        GuiGraphics g = c.g;
        int x0 = (panelW - W) / 2;
        int y0 = (panelH - H) / 2;
        g.fill(0, 0, panelW, panelH, 0xB0000000);
        PixelUi.frame(g, x0, y0, W, H);
        g.drawString(font, title, x0 + 8, y0 + 6, PixelUi.ORANGE, true);

        int sx = x0 + 12;
        int sy = y0 + 22;
        PixelUi.outline(g, sx - 1, sy - 1, 66, 66, PixelUi.BORDER_DARK);
        for (int i = 0; i < 64; i++) {
            int top = 0xFF000000 | PixelUi.hsvToRgb(h, i / 63f, 1f);
            g.fillGradient(sx + i, sy, sx + i + 1, sy + 64, top, 0xFF000000);
        }
        if (minV > 0f) { // blocked dark band
            int by = sy + Math.round((1f - minV) * 64f);
            g.fill(sx, by, sx + 64, sy + 64, 0xC8101428);
            g.fill(sx, by, sx + 64, by + 1, PixelUi.BORDER_DARK);
        }
        int px = sx + Math.round(s * 63f);
        int py = sy + Math.round((1f - v) * 63f);
        g.fill(px - 3, py - 3, px + 4, py + 4, 0xFFFFFFFF);
        g.fill(px - 2, py - 2, px + 3, py + 3, 0xFF000000 | rgb);
        c.drag(sx, sy, 64, 64, (x, y) -> setSv(x - sx, y - sy));

        int hx = sx + 70;
        PixelUi.outline(g, hx - 1, sy - 1, 12, 66, PixelUi.BORDER_DARK);
        int[] b = {0, 11, 21, 32, 43, 53, 64};
        for (int i = 0; i < 6; i++) {
            g.fillGradient(hx, sy + b[i], hx + 10, sy + b[i + 1],
                    0xFF000000 | PixelUi.hsvToRgb(i / 6f, 1f, 1f), 0xFF000000 | PixelUi.hsvToRgb((i + 1) / 6f, 1f, 1f));
        }
        int hy = sy + Math.round(h * 63f);
        g.fill(hx - 2, hy - 1, hx + 12, hy + 2, 0xFFFFFFFF);
        g.fill(hx - 1, hy, hx + 11, hy + 1, 0xFF000000);
        c.drag(hx, sy, 10, 64, (x, y) -> setHue(y - sy));

        int rx = x0 + 100;
        PixelUi.box(g, rx, sy, 78, 20, 0xFF000000 | rgb, PixelUi.BORDER_LIGHT);
        PixelUi.small(g, font, Component.translatable(P + "picker.hex"), rx, sy + 24, PixelUi.TEXT_DIM);
        hex.setPosition(rx, sy + 32);
        hex.setTextColor(validText ? 0xFFE0E0E0 : 0xFFFF6B6B);
        hex.render(g, (int) c.mx, (int) c.my, 0f);
        c.drag(rx, sy + 32, 78, 14, (x, y) -> {
            hex.setFocused(true);
            hex.mouseClicked(x, y, 0);
        });
        if (minV > 0f) {
            int y = sy + 50;
            java.util.List<net.minecraft.util.FormattedCharSequence> lines =
                    font.split(Component.translatable(P + "min_brightness"), (int) (78 / 0.75f));
            g.pose().pushPose();
            g.pose().translate(rx, y, 0f);
            g.pose().scale(0.75f, 0.75f, 1f);
            for (int i = 0; i < Math.min(3, lines.size()); i++) {
                g.drawString(font, lines.get(i), 0, i * 10, clamped ? PixelUi.BAD : PixelUi.TEXT_DIM, false);
            }
            g.pose().popPose();
        }

        int by = y0 + 100;
        c.button(x0 + 12, by, 78, 16, Component.translatable(P + "picker.ok"), validText, true, null, this::ok);
        c.button(x0 + 100, by, 78, 16, Component.translatable(P + "picker.cancel"), true, false, null, this::close);
    }
}

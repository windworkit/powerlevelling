package com.powerlevelling.client.screen;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleConsumer;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Tiny immediate-mode UI helper for the character screen: every frame the screen draws its controls through this class,
 * which also records their hit regions; input events use the regions of the last frame. All coordinates are "virtual"
 * (the panel's own pixels, before the screen's uniform shrink-to-fit scale). Clip rects (scrolling areas) cut both the
 * scissor and the hit regions. While {@link #blocked} is set (a modal is open) nothing registers and nothing hovers.
 */
final class UiCanvas {
    interface Click {
        void on(double x, double y);
    }

    private record Hit(int x, int y, int w, int h, Click click, boolean draggable) {
    }

    private record Area(int x, int y, int w, int h, DoubleConsumer scroll) {
    }

    GuiGraphics g;
    Font font;
    double mx;
    double my;
    boolean blocked;
    /** Tooltip of the topmost hovered control this frame (drawn by the screen after the panel). */
    List<Component> tip;

    private float scale = 1f;
    private float offX;
    private float offY;
    private final ArrayList<Hit> hits = new ArrayList<>();
    private final ArrayList<Area> areas = new ArrayList<>();
    private final ArrayDeque<int[]> clips = new ArrayDeque<>();
    private Hit active;

    void begin(GuiGraphics g, Font font, double mx, double my, float scale, float offX, float offY) {
        this.g = g;
        this.font = font;
        this.mx = mx;
        this.my = my;
        this.scale = scale;
        this.offX = offX;
        this.offY = offY;
        this.blocked = false;
        this.tip = null;
        hits.clear();
        areas.clear();
        clips.clear();
    }

    // ---- clipping -------------------------------------------------------------------------------------------------

    void pushClip(int x, int y, int w, int h) {
        int[] c = {x, y, x + w, y + h};
        if (!clips.isEmpty()) {
            int[] p = clips.peek();
            c[0] = Math.max(c[0], p[0]);
            c[1] = Math.max(c[1], p[1]);
            c[2] = Math.max(c[0], Math.min(c[2], p[2]));
            c[3] = Math.max(c[1], Math.min(c[3], p[3]));
        }
        clips.push(c);
        int rx0 = (int) Math.floor(offX + c[0] * scale);
        int ry0 = (int) Math.floor(offY + c[1] * scale);
        int rx1 = (int) Math.ceil(offX + c[2] * scale);
        int ry1 = (int) Math.ceil(offY + c[3] * scale);
        g.enableScissor(rx0, ry0, Math.max(rx0, rx1), Math.max(ry0, ry1));
    }

    void popClip() {
        if (clips.isEmpty()) return;
        clips.pop();
        g.disableScissor();
    }

    /** Rect clipped to the current clip, as {x0, y0, x1, y1}; null when nothing is left. */
    private int[] clip(int x, int y, int w, int h) {
        int x0 = x;
        int y0 = y;
        int x1 = x + w;
        int y1 = y + h;
        if (!clips.isEmpty()) {
            int[] p = clips.peek();
            x0 = Math.max(x0, p[0]);
            y0 = Math.max(y0, p[1]);
            x1 = Math.min(x1, p[2]);
            y1 = Math.min(y1, p[3]);
        }
        return (x1 <= x0 || y1 <= y0) ? null : new int[]{x0, y0, x1, y1};
    }

    // ---- hover / registration -------------------------------------------------------------------------------------

    boolean hover(int x, int y, int w, int h) {
        if (blocked) return false;
        int[] r = clip(x, y, w, h);
        return r != null && mx >= r[0] && mx < r[2] && my >= r[1] && my < r[3];
    }

    private void add(int x, int y, int w, int h, Click c, boolean draggable) {
        if (blocked) return;
        int[] r = clip(x, y, w, h);
        if (r == null) return;
        hits.add(new Hit(r[0], r[1], r[2] - r[0], r[3] - r[1], c, draggable));
    }

    void hit(int x, int y, int w, int h, Runnable r) {
        add(x, y, w, h, (a, b) -> r.run(), false);
    }

    /** Receives the absolute virtual mouse position on press and on every drag until release. */
    void drag(int x, int y, int w, int h, Click c) {
        add(x, y, w, h, c, true);
    }

    void scrollArea(int x, int y, int w, int h, DoubleConsumer scroll) {
        if (blocked) return;
        int[] r = clip(x, y, w, h);
        if (r == null) return;
        areas.add(new Area(r[0], r[1], r[2] - r[0], r[3] - r[1], scroll));
    }

    void tooltip(int x, int y, int w, int h, List<Component> lines) {
        if (lines != null && hover(x, y, w, h)) tip = lines;
    }

    void tooltip(int x, int y, int w, int h, Component line) {
        if (line != null) tooltip(x, y, w, h, List.of(line));
    }

    // ---- controls -------------------------------------------------------------------------------------------------

    void button(int x, int y, int w, int h, Component label, boolean enabled, boolean selected, Component tooltip, Runnable r) {
        PixelUi.button(g, font, x, y, w, h, label, enabled && hover(x, y, w, h), enabled, selected);
        if (enabled) hit(x, y, w, h, r);
        tooltip(x, y, w, h, tooltip);
    }

    void checkbox(int x, int y, int w, int h, Component label, boolean checked, boolean enabled, Component tooltip, Runnable r) {
        boolean hov = enabled && hover(x, y, w, h);
        PixelUi.box(g, x, y, w, h, hov ? PixelUi.BTN_HOVER : PixelUi.ROW_BG, hov ? PixelUi.ORANGE_LIGHT : PixelUi.BORDER_DARK);
        int bx = x + 3;
        int by = y + (h - 8) / 2;
        PixelUi.box(g, bx, by, 8, 8, PixelUi.BAR_BG, enabled ? PixelUi.BORDER_LIGHT : PixelUi.BORDER_DARK);
        if (checked) g.fill(bx + 2, by + 2, bx + 6, by + 6, enabled ? PixelUi.ORANGE : PixelUi.TEXT_OFF);
        g.drawString(font, label, x + 15, y + (h - 8) / 2, enabled ? PixelUi.TEXT : PixelUi.TEXT_OFF, enabled);
        if (enabled) hit(x, y, w, h, r);
        tooltip(x, y, w, h, tooltip);
    }

    void swatch(int x, int y, int size, int rgb, boolean selected, boolean enabled, List<Component> tooltip, Runnable r) {
        boolean hov = enabled && hover(x, y, size, size);
        int border = selected ? PixelUi.TEXT : hov ? PixelUi.ORANGE_LIGHT : PixelUi.BAR_EDGE;
        g.fill(x - 1, y - 1, x + size + 1, y + size + 1, border);
        g.fill(x, y, x + size, y + size, 0xFF000000 | (rgb & 0xFFFFFF));
        if (selected) PixelUi.outline(g, x, y, size, size, PixelUi.BAR_EDGE);
        if (!enabled) g.fill(x, y, x + size, y + size, 0xA0101428);
        if (enabled) hit(x - 1, y - 1, size + 2, size + 2, r);
        tooltip(x, y, size, size, tooltip);
    }

    // ---- input (virtual coordinates) ------------------------------------------------------------------------------

    boolean mouseClicked(double vx, double vy, int button) {
        if (button != 0) return false;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (vx >= h.x && vx < h.x + h.w && vy >= h.y && vy < h.y + h.h) {
                active = h.draggable ? h : null;
                h.click.on(vx, vy);
                return true;
            }
        }
        return false;
    }

    boolean mouseDragged(double vx, double vy) {
        if (active == null) return false;
        active.click.on(vx, vy);
        return true;
    }

    void mouseReleased() {
        active = null;
    }

    boolean mouseScrolled(double vx, double vy, double delta) {
        for (int i = areas.size() - 1; i >= 0; i--) {
            Area a = areas.get(i);
            if (vx >= a.x && vx < a.x + a.w && vy >= a.y && vy < a.y + a.h) {
                a.scroll.accept(delta);
                return true;
            }
        }
        return false;
    }
}

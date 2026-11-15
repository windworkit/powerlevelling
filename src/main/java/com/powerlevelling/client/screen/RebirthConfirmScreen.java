package com.powerlevelling.client.screen;

import com.powerlevelling.network.RespecConfirmPayload;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** Yes/No confirmation for the Rebirth Orb. "Yes" asks the server to refund all spent stat points. */
public class RebirthConfirmScreen extends Screen {
    private static final int W = 220;
    private static final int H = 84;

    public RebirthConfirmScreen() {
        super(Component.translatable("gui.powerlevelling.rebirth.title"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        int left = (this.width - W) / 2;
        int top = (this.height - H) / 2;
        this.addRenderableWidget(Button.builder(Component.translatable("gui.powerlevelling.rebirth.yes"), b -> {
            PacketDistributor.sendToServer(new RespecConfirmPayload());
            this.onClose();
        }).bounds(left + 24, top + H - 28, 80, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.powerlevelling.rebirth.no"),
                b -> this.onClose()).bounds(left + W - 24 - 80, top + H - 28, 80, 20).build());
    }

    /** Screen.render() calls renderBackground(), which in 1.21 blurs everything drawn so far (our panel included).
     *  Overriding it with a no-op keeps the panel sharp; we draw our own dim/panel in render(). */
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Own dim instead of renderBackground(): avoids vanilla's blur (which may only run once per frame).
        g.fill(0, 0, this.width, this.height, 0x80000000);
        g.flush(); // the dim is buffered; the atlas frame below is drawn immediately, so push the dim out first
        int left = (this.width - W) / 2;
        int top = (this.height - H) / 2;
        PixelUi.frame(g, left, top, W, H);

        g.drawCenteredString(this.font, this.title, left + W / 2, top + 9, PixelUi.ORANGE);
        g.drawCenteredString(this.font, Component.translatable("gui.powerlevelling.rebirth.line1"), left + W / 2, top + 26, PixelUi.TEXT);
        g.drawCenteredString(this.font, Component.translatable("gui.powerlevelling.rebirth.line2"), left + W / 2, top + 38, PixelUi.TEXT_DIM);

        super.render(g, mouseX, mouseY, partialTick); // buttons
    }
}

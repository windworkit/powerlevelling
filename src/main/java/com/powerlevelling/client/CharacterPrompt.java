package com.powerlevelling.client;

import com.powerlevelling.character.CharacterService;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

/**
 * The first-join prompt (Addendum B.6): 40 ticks after joining a world without a created character, a toast and one chat
 * line with a clickable [Create]. Armed by ClientEvents on LoggingIn, ticked from the client tick. Waits (up to 10 s) for
 * the server's character sync so it never nags a player whose character already exists.
 */
public final class CharacterPrompt {
    private static final int DELAY_TICKS = 40;
    private static final int MAX_WAIT_TICKS = 200;

    private static int countdown = -1; // -1 = idle
    private static int waited;

    private CharacterPrompt() {
    }

    public static void arm() {
        countdown = DELAY_TICKS;
        waited = 0;
    }

    public static void reset() {
        countdown = -1;
        waited = 0;
    }

    public static void tick(Minecraft mc) {
        if (countdown < 0 || mc.player == null) return;
        if (countdown > 0) {
            countdown--;
            return;
        }
        if (!ClientCharacters.selfKnown()) {
            if (++waited > MAX_WAIT_TICKS) countdown = -1;
            return;
        }
        countdown = -1;
        if (!ClientCharacters.selfCreated()) show(mc);
    }

    private static void show(Minecraft mc) {
        Component key = ModKeys.OPEN_STATS.getTranslatedKeyMessage();
        SystemToast.addOrUpdate(mc.getToasts(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                Component.translatable("toast.powerlevelling.character.title"),
                Component.translatable("toast.powerlevelling.character.body", key));
        mc.gui.getChat().addMessage(Component.translatable("message.powerlevelling.character.prompt",
                key, CharacterService.createLink()));
    }

    /** Success toast after a CREATE went through. */
    public static void toastCreated(Minecraft mc, Component race, Component nature) {
        SystemToast.addOrUpdate(mc.getToasts(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                Component.translatable("toast.powerlevelling.character.created.title"),
                Component.translatable("toast.powerlevelling.character.created.body", race, nature));
    }

    /** Success toast after a STYLE / NATURE / FULL change went through. */
    public static void toastChanged(Minecraft mc, Component race, Component nature) {
        SystemToast.addOrUpdate(mc.getToasts(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                Component.translatable("toast.powerlevelling.character.changed.title"),
                Component.translatable("toast.powerlevelling.character.changed.body", race, nature));
    }
}

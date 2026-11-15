package com.powerlevelling.client;

import com.powerlevelling.client.screen.CharacterScreen;
import com.powerlevelling.client.screen.RebirthConfirmScreen;
import com.powerlevelling.client.screen.StatsScreen;
import com.powerlevelling.network.CharacterSubmitPayload.Mode;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Entry points for opening screens; keeps client-only classes out of common code paths. */
public final class ClientScreens {
    private ClientScreens() {
    }

    public static void openRebirthConfirm() {
        Minecraft.getInstance().setScreen(new RebirthConfirmScreen());
    }

    public static void openStats() {
        Minecraft.getInstance().setScreen(new StatsScreen());
    }

    /** True when creating a character is possible right now (not created yet, or a free re-creation is open). */
    public static boolean canCreateCharacter() {
        return !ClientCharacters.selfCreated() || ClientCharacters.selfFreeRecreate();
    }

    /** K: the character screen until the character is created (or while a free re-creation is open), then the stats screen. */
    public static void openMain() {
        if (canCreateCharacter()) openCharacter();
        else openStats();
    }

    /** Opens the character creation screen (K routing, join prompt, chat [Create], command). */
    public static void openCharacter() {
        openCharacter(Mode.CREATE);
    }

    /**
     * Opens the character screen in a mode: CREATE (not created yet / free re-creation) or an edit mode (STYLE, NATURE, FULL;
     * the change items open these in C10). Does nothing if it is already open; tells the player when it cannot open.
     */
    public static void openCharacter(Mode mode) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen instanceof CharacterScreen) return;
        if (mode == Mode.CREATE) {
            if (!canCreateCharacter()) {
                mc.player.displayClientMessage(Component.translatable("gui.powerlevelling.character.already"), true);
                return;
            }
        } else if (!ClientCharacters.selfCreated() || ClientCharacters.selfSnapshot() == null) {
            mc.player.displayClientMessage(Component.translatable("gui.powerlevelling.character.not_created"), true);
            return;
        }
        mc.setScreen(new CharacterScreen(mode));
    }
}

package com.powerlevelling.character;

import com.powerlevelling.config.ServerConfig;
import com.powerlevelling.data.CombatState;
import com.powerlevelling.registry.ModAttachments;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Addendum B.7: mod abilities need a created character (unless the requireCharacter config is off). Vanilla play is never
 * blocked, and EXP, kills, Zenkai and mastery keep accruing. Checked in CombatInputHandler (every action), KiCharge.tryStart,
 * KiSkills.press and Progression.handleSpend.
 * TODO(flight): call {@link #check} when flight start exists.
 */
public final class CharacterGate {
    /** The "create your character first" hint is shown at most this often. */
    public static final int HINT_COOLDOWN_TICKS = 40;

    private CharacterGate() {
    }

    public static boolean isReady(ServerPlayer player) {
        return CharacterService.get(player).created || !ServerConfig.REQUIRE_CHARACTER.get();
    }

    /** True when the player may use mod abilities; otherwise shows the (rate-limited) hint and returns false. */
    public static boolean check(ServerPlayer player) {
        if (isReady(player)) return true;
        hint(player);
        return false;
    }

    public static void hint(ServerPlayer player) {
        CombatState st = player.getData(ModAttachments.COMBAT_STATE);
        long now = player.level().getGameTime();
        if (st.lastGateHintTick > now) st.lastGateHintTick = Long.MIN_VALUE / 2; // game time went backwards (world swap)
        if (now - st.lastGateHintTick < HINT_COOLDOWN_TICKS) return;
        st.lastGateHintTick = now;
        player.displayClientMessage(Component.translatable("message.powerlevelling.character.required")
                .withStyle(ChatFormatting.RED), true); // action bar
    }
}

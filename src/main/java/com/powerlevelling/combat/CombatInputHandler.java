package com.powerlevelling.combat;

import com.powerlevelling.PowerLevelling;
import com.powerlevelling.character.CharacterGate;
import com.powerlevelling.character.CharacterRegistries;
import com.powerlevelling.character.Race;
import com.powerlevelling.config.ServerConfig;
import com.powerlevelling.data.CombatState;
import com.powerlevelling.data.CombatResources;
import com.powerlevelling.ki.KiSkills;
import com.powerlevelling.network.ActionAckPayload;
import com.powerlevelling.network.CombatInputPayload;
import com.powerlevelling.network.ModNetwork;
import com.powerlevelling.network.VisualStatePayload;
import com.powerlevelling.registry.ModAttachments;
import com.powerlevelling.registry.ModSounds;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Server side of the input pipeline: validates one CombatInputPayload, applies what exists in M2 (stance toggle,
 * ki charge, M3 ki skills) and always answers with an ActionAckPayload. Moves/guard just get a validity check for now;
 * M5+ replaces those branches with the state machine.
 */
public final class CombatInputHandler {
    private CombatInputHandler() {
    }

    public static void handle(ServerPlayer player, CombatInputPayload in) {
        boolean accepted = process(player, in);
        PacketDistributor.sendToPlayer(player, new ActionAckPayload(in.seq(), accepted));
    }

    private static boolean process(ServerPlayer player, CombatInputPayload in) {
        CombatAction action = in.action();
        Phase phase = in.phase();
        if (action == null || phase == null) return false;
        if (!Float.isFinite(in.yaw()) || !Float.isFinite(in.pitch()) || !Float.isFinite(in.moveYaw())) return false;

        CombatState st = player.getData(ModAttachments.COMBAT_STATE);
        if (!st.allowInput(player.level().getGameTime())) return false;
        if (!player.isAlive() || player.isSpectator()) return false;
        // Addendum B.7: no mod abilities before the character is created (every action is refused, with a rate-limited hint).
        if (!CharacterGate.check(player)) return false;

        boolean press = phase == Phase.PRESS;
        switch (action) {
            case STANCE_TOGGLE -> {
                if (press) {
                    boolean entering = !st.stance;
                    st.stance = !st.stance;
                    if (!st.stance) {
                        KiSkills.cancel(player); // leaving stance drops a held skill charge
                        MeleeEngine.cancel(player, st, true); // ... and the melee move
                        Transform.cancelHold(player); // ... and a half-finished transformation hold
                    }
                    player.level().playSound(null, player.blockPosition(),
                            entering ? ModSounds.STANCE_ENTER.get() : ModSounds.STANCE_EXIT.get(),
                            SoundSource.PLAYERS, 0.6f, 1.0f);
                    ModNetwork.syncVisual(player);
                }
                return true;
            }
            case CHARGE_KI -> {
                return press ? KiCharge.tryStart(player) : releaseCharge(player);
            }
            case SKILL_1, SKILL_2, SKILL_3, SKILL_4 -> {
                return KiSkills.handle(player, st, action, phase, in.yaw(), in.pitch());
            }
            case LIGHT, HEAVY -> {
                // M5: melee presses travel as MeleeInputPayload (MeleeEngine). Edges still arriving here (older clients,
                // releases) are harmless no-ops.
                return true;
            }
            case GUARD -> {
                if (!ServerConfig.GUARD_ENABLED.get()) return false;
                return Guard.handle(player, st, press);
            }
            case DASH -> {
                return press && Dash.tryDash(player, st, in.moveYaw());
            }
            case LOADOUT -> {
                return true; // later milestones
            }
            case LOCK_ON -> {
                return LockOn.handle(player, st, press);
            }
            case TRANSFORM -> {
                return press ? Transform.startTransform(player) : Transform.stopTransform(player);
            }
            case TURBO -> {
                // Toggle: each press flips the state
                if (press) {
                    if (st.turbo) {
                        stopTurbo(player);
                    } else {
                        // Config: turbo requires charge, or works standalone
                        boolean requiresCharge = ServerConfig.TURBO_REQUIRES_CHARGE.get();
                        if (requiresCharge && !st.charging) return false;
                        if (!requiresCharge && st.charging) return false; // can't turbo while charging in standalone mode
                        startTurbo(player);
                    }
                }
                return true;
            }
        }
        return false;
    }

    private static boolean releaseCharge(ServerPlayer player) {
        KiCharge.stop(player);
        return true;
    }

    private static void startTurbo(ServerPlayer player) {
        CombatState st = player.getData(ModAttachments.COMBAT_STATE);
        if (st.turbo) return; // already active
        st.turbo = true;
        applyTurboSpeed(player, st);
        ModNetwork.syncVisual(player);
    }

    private static void stopTurbo(ServerPlayer player) {
        CombatState st = player.getData(ModAttachments.COMBAT_STATE);
        if (!st.turbo) return;
        st.turbo = false;
        removeTurboSpeed(player, st);
        ModNetwork.syncVisual(player);
    }

    private static void applyTurboSpeed(ServerPlayer player, CombatState st) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null || st.turboSpeedApplied) return;
        double bonus = ServerConfig.TURBO_SPEED_BONUS.get();
        speed.addOrReplacePermanentModifier(
                new AttributeModifier(ResourceLocation.fromNamespaceAndPath(PowerLevelling.MODID, "turbo_speed"), bonus, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        st.turboSpeedApplied = true;
    }

    private static void removeTurboSpeed(ServerPlayer player, CombatState st) {
        if (!st.turboSpeedApplied) return;
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(ResourceLocation.fromNamespaceAndPath(PowerLevelling.MODID, "turbo_speed"));
        st.turboSpeedApplied = false;
    }
}

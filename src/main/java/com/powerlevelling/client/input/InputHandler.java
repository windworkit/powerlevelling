package com.powerlevelling.client.input;

import java.util.Arrays;

import com.powerlevelling.PowerLevelling;
import com.powerlevelling.client.ClientCombat;
import com.powerlevelling.client.ClientMelee;
import com.powerlevelling.client.ClientVisualState;
import com.powerlevelling.client.ModKeys;
import com.powerlevelling.client.fx.KiFx;
import com.powerlevelling.combat.CombatAction;
import com.powerlevelling.combat.MeleeInput;
import com.powerlevelling.combat.Phase;
import com.powerlevelling.network.CombatInputPayload;
import com.powerlevelling.network.VisualStatePayload;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Turns key state into CombatActions (design 8.8) and sends CombatInputPayload edges to the server.
 *
 * Everything is polled in ClientTickEvent.Pre, i.e. BEFORE vanilla handles keys this tick. That is what lets us
 * swallow vanilla clicks in stance (attack, use, hotbar 1-4, swap hands, pick block) so nothing vanilla happens.
 * Vanilla's "attack/use key held" state is cleared for the rest of the tick and restored in Post, so holding LMB
 * in stance can't keep mining either.
 */
@EventBusSubscriber(modid = PowerLevelling.MODID, value = Dist.CLIENT)
public final class InputHandler {
    private static final boolean[] HELD = new boolean[CombatAction.values().length];
    private static boolean suppressed;
    private static boolean savedAttackDown;
    private static boolean savedUseDown;
    private static long clientTick;

    private InputHandler() {
    }

    @SubscribeEvent
    public static void onClientTickPre(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            Arrays.fill(HELD, false);
            suppressed = false;
            ClientVisualState.selfId = -1;
            return;
        }
        clientTick++;
        ClientVisualState.selfId = player.getId();
        ClientCombat.tick(clientTick);
        ClientMelee.tick(player);

        Options o = mc.options;
        boolean canInput = mc.screen == null && player.isAlive() && !player.isSpectator();

        // ---- stance toggle (press only; consume every click so none pile up) ----
        boolean stanceClick = click(ModKeys.STANCE);
        if (canInput && stanceClick) {
            ClientVisualState.flipSelfStance(); // prediction; the ack rolls it back if refused
            send(player, CombatAction.STANCE_TOGGLE, Phase.PRESS);
        }
        boolean stance = ClientVisualState.selfStance();

        // ---- swallow vanilla clicks that share a key with a stance binding ----
        if (stance) {
            for (int i = 0; i < 4; i++) {
                if (sameKey(ModKeys.SKILLS[i], o.keyHotbarSlots[i])) click(o.keyHotbarSlots[i]);
            }
            if (sameKey(ModKeys.GUARD, o.keySwapOffhand)) click(o.keySwapOffhand);
            if (sameKey(ModKeys.LOCK_ON, o.keyPickItem)) click(o.keyPickItem);
        }

        // ---- light / heavy = vanilla attack / use keys while in stance ----
        boolean attackDown = o.keyAttack.isDown();
        boolean useDown = o.keyUse.isDown();
        boolean attackClick = false;
        boolean useClick = false;
        if (stance && canInput) {
            attackClick = click(o.keyAttack);
            useClick = click(o.keyUse);
            // Hide the held state from vanilla for this tick (continueAttack would otherwise keep mining).
            savedAttackDown = attackDown;
            savedUseDown = useDown;
            suppressed = true;
            o.keyAttack.setDown(false);
            o.keyUse.setDown(false);
        }
        // M5: melee presses go through ClientMelee (prediction + MeleeInputPayload), not CombatInputPayload.
        if (stance && canInput) {
            if (attackClick) ClientMelee.press(player, MeleeInput.LIGHT, (int) clientTick);
            if (useClick) ClientMelee.press(player, MeleeInput.HEAVY, (int) clientTick);
        }

        // ---- our own keys ----
        trackKey(player, CombatAction.CHARGE_KI, ModKeys.CHARGE_KI, canInput);
        trackKey(player, CombatAction.GUARD, ModKeys.GUARD, canInput && stance);
        for (int i = 0; i < 4; i++) {
            trackKey(player, CombatAction.skill(i), ModKeys.SKILLS[i], canInput && stance);
        }
        trackKey(player, CombatAction.LOADOUT, ModKeys.LOADOUT, canInput);
        pressOnly(player, CombatAction.DASH, ModKeys.DASH, canInput);
        pressOnly(player, CombatAction.LOCK_ON, ModKeys.LOCK_ON, canInput && stance);
        // TRANSFORM (hold) - only in stance, not while charging or skill charging
        boolean transformEnabled = stance && !ClientVisualState.selfCharging() && !ClientCombat.selfSkillCharging();
        trackKey(player, CombatAction.TRANSFORM, ModKeys.TRANSFORM, transformEnabled);
        // TURBO (toggle) - works both in and out of stance, no ki charge
        boolean turboEnabled = canInput && !ClientVisualState.selfCharging();
        pressOnly(player, CombatAction.TURBO, ModKeys.TURBO, turboEnabled);
    }

    /** Gives vanilla back the real held state of attack/use. */
    @SubscribeEvent
    public static void onClientTickPost(ClientTickEvent.Post event) {
        if (!suppressed) return;
        suppressed = false;
        Minecraft mc = Minecraft.getInstance();
        mc.options.keyAttack.setDown(savedAttackDown);
        mc.options.keyUse.setDown(savedUseDown);
    }

    /** Safety net: in stance vanilla attack/use is cancelled even if something triggers it outside our polling. */
    @SubscribeEvent
    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if (!ClientVisualState.selfStance()) return;
        if (event.isAttack() || event.isUseItem()) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    /** Disconnect: forget held keys and pending inputs. */
    public static void reset() {
        Arrays.fill(HELD, false);
        suppressed = false;
        clientTick = 0;
    }

    // ---- edge tracking -------------------------------------------------------------------------------------------

    /** Hold-type action driven by one of our key mappings. */
    private static void trackKey(LocalPlayer player, CombatAction action, KeyMapping key, boolean enabled) {
        boolean clicked = click(key);
        track(player, action, clicked, key.isDown(), enabled);
    }

    /**
     * Emits PRESS on the rising edge and RELEASE on the falling edge. A click shorter than one tick (clicked but
     * not down any more) still produces PRESS then RELEASE. Disabling the action releases it.
     */
    private static void track(LocalPlayer player, CombatAction action, boolean clicked, boolean down, boolean enabled) {
        int i = action.ordinal();
        if (!HELD[i] && enabled && (down || clicked)) {
            HELD[i] = true;
            predict(player, action, Phase.PRESS);
            send(player, action, Phase.PRESS);
        }
        if (HELD[i] && !(enabled && down)) {
            HELD[i] = false;
            predict(player, action, Phase.RELEASE);
            send(player, action, Phase.RELEASE);
        }
    }

    /** Tap-type action (dash, lock-on toggle): one PRESS per click. */
    private static void pressOnly(LocalPlayer player, CombatAction action, KeyMapping key, boolean enabled) {
        boolean clicked = click(key);
        if (enabled && clicked) send(player, action, Phase.PRESS);
    }

    /** Local prediction applied the moment an input is sent (the server's ack/visual state corrects it). */
    private static void predict(LocalPlayer player, CombatAction action, Phase phase) {
        if (action == CombatAction.CHARGE_KI) {
            ClientVisualState.setSelfFlag(VisualStatePayload.CHARGING, phase == Phase.PRESS);
        } else if (action == CombatAction.TRANSFORM) {
            ClientVisualState.setSelfFlag(VisualStatePayload.TRANSFORMING, phase == Phase.PRESS);
        } else if (action == CombatAction.TURBO) {
            ClientVisualState.setSelfFlag(VisualStatePayload.TURBO, phase == Phase.PRESS);
        } else if (action.isSkill()) {
            // Muzzle flash / charging orb, local only. The server spawns the real blast.
            KiFx.predictSkill(player, action.ordinal() - CombatAction.SKILL_1.ordinal(), phase);
        }
    }

    private static void send(LocalPlayer player, CombatAction action, Phase phase) {
        int seq = ClientCombat.register(action, phase, clientTick);
        PacketDistributor.sendToServer(new CombatInputPayload(action, phase, seq, player.getYRot(), player.getXRot(),
                moveYaw(player)));
    }

    /**
     * World yaw of the player's movement input (W/S/A/D), or the look yaw when no direction is held. Only a dash uses
     * it, but sending it with every edge keeps the payload free of per-action variants.
     */
    private static float moveYaw(LocalPlayer player) {
        if (player.input == null) return player.getYRot();
        float forward = player.input.forwardImpulse;
        float left = player.input.leftImpulse;
        if (Math.abs(forward) < 1e-4f && Math.abs(left) < 1e-4f) return player.getYRot();

        double yaw = Math.toRadians(player.getYRot());
        double lookX = -Math.sin(yaw);
        double lookZ = Math.cos(yaw);
        double rightX = -lookZ;
        double rightZ = lookX;
        double x = lookX * forward - rightX * left; // leftImpulse is positive to the left
        double z = lookZ * forward - rightZ * left;
        return (float) Math.toDegrees(Math.atan2(-x, z));
    }

    // ---- helpers -------------------------------------------------------------------------------------------------

    /** Consumes all pending clicks of a mapping; true if there was at least one. */
    private static boolean click(KeyMapping key) {
        boolean any = false;
        while (key.consumeClick()) any = true;
        return any;
    }

    private static boolean sameKey(KeyMapping a, KeyMapping b) {
        return a.getKey().getType() == b.getKey().getType() && a.getKey().getValue() == b.getKey().getValue();
    }
}

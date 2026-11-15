package com.powerlevelling.combat;

import com.powerlevelling.PowerLevelling;
import com.powerlevelling.character.CharacterRegistries;
import com.powerlevelling.character.CharacterService;
import com.powerlevelling.character.Race;
import com.powerlevelling.config.ServerConfig;
import com.powerlevelling.data.CombatResources;
import com.powerlevelling.data.CombatState;
import com.powerlevelling.network.ModNetwork;
import com.powerlevelling.registry.ModAttachments;
import com.powerlevelling.registry.ModSounds;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Handles Super Saiyan and other form transformations.
 * Server-authoritative: clients predict locally but server validates.
 *
 * <p>The damage bonus is applied through {@link com.powerlevelling.progression.StatEffects} (so it covers the mod's
 * melee and ki damage), not through the vanilla attack attribute. Only the movement-speed bonus is an attribute.
 * All numbers live in {@link ServerConfig}; whether a race may transform is read from its {@code hooks.forms} list.
 */
public final class Transform {
    public static final ResourceLocation SSJ_FORM = ResourceLocation.fromNamespaceAndPath(PowerLevelling.MODID, "super_saiyan");
    public static final ResourceLocation BASE_FORM = ResourceLocation.fromNamespaceAndPath(PowerLevelling.MODID, "base");

    // Attribute modifier IDs. SSJ_DAMAGE_ID is no longer applied; it is still stripped on login to clean up saves
    // made before the damage bonus moved into StatEffects.
    private static final ResourceLocation SSJ_DAMAGE_ID = ResourceLocation.fromNamespaceAndPath(PowerLevelling.MODID, "ssj_damage");
    private static final ResourceLocation SSJ_SPEED_ID = ResourceLocation.fromNamespaceAndPath(PowerLevelling.MODID, "ssj_speed");

    private Transform() {}

    /** Tries to start the transform hold. Returns true if the input was accepted (the form still only changes at the end). */
    public static boolean startTransform(ServerPlayer player) {
        CombatState st = player.getData(ModAttachments.COMBAT_STATE);
        if (!st.stance) return false; // must be in stance

        // Already transformed: the same hold powers back down, so no race/ki gate here.
        if (isSuperSaiyan(st)) {
            beginHold(player, st);
            return true;
        }

        if (st.charging) return false; // cannot transform while charging ki
        if (st.skillCharging()) return false; // cannot transform while charging a skill

        // Fail fast (with a reason) instead of letting the player hold the key for two seconds for nothing.
        if (!raceAllowsForm(player)) {
            player.displayClientMessage(Component.translatable("message.powerlevelling.transform.race")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        float minKi = ServerConfig.TRANSFORM_MIN_KI.get();
        if (player.getData(ModAttachments.RESOURCES).ki < minKi) {
            player.displayClientMessage(Component.translatable("message.powerlevelling.transform.need_ki",
                    Math.round(minKi)).withStyle(ChatFormatting.RED), true);
            return false;
        }

        beginHold(player, st);
        return true;
    }

    private static void beginHold(ServerPlayer player, CombatState st) {
        if (st.transforming) return;
        st.transforming = true;
        st.transformTicks = 0;
        ModNetwork.syncVisual(player);
    }

    /** Called on key release: cancels the transform hold if not yet complete. Returns true if processed. */
    public static boolean stopTransform(ServerPlayer player) {
        CombatState st = player.getData(ModAttachments.COMBAT_STATE);
        if (st.transforming) {
            st.transforming = false;
            st.transformTicks = 0;
            ModNetwork.syncVisual(player);
        }
        return true;
    }

    /** Drops an in-progress hold without touching the active form (death, logout, dimension change, stance off). */
    public static void cancelHold(ServerPlayer player) {
        CombatState st = player.getData(ModAttachments.COMBAT_STATE);
        if (!st.transforming && st.transformTicks == 0) return;
        st.transforming = false;
        st.transformTicks = 0;
        // Silent: every caller already syncs (or is about to).
    }

    /**
     * Called each server tick while transforming.
     * Increments transformTicks and fires the actual transformation when the threshold is reached.
     */
    public static void tickTransform(ServerPlayer player) {
        CombatState st = player.getData(ModAttachments.COMBAT_STATE);
        if (!st.transforming) return;

        // Losing the stance, dying or getting stunned mid-hold cancels it (the client sends a release too, this is the lock).
        if (!st.stance || !player.isAlive() || player.isSpectator()) {
            cancelHold(player);
            ModNetwork.syncVisual(player);
            return;
        }

        st.transformTicks++;
        if (st.transformTicks >= ServerConfig.TRANSFORM_HOLD_TICKS.get()) {
            // Hold complete: trigger the actual transformation
            st.transforming = false;
            st.transformTicks = 0;
            doTransform(player);
            ModNetwork.syncVisual(player);
        }
    }

    private static void doTransform(ServerPlayer player) {
        CombatState st = player.getData(ModAttachments.COMBAT_STATE);
        if (isSuperSaiyan(st)) {
            revert(player);
        } else {
            tryEnterSSJ(player);
        }
    }

    private static void tryEnterSSJ(ServerPlayer player) {
        CombatState st = player.getData(ModAttachments.COMBAT_STATE);
        CombatResources r = player.getData(ModAttachments.RESOURCES);

        // Re-checked at the end of the hold (the race or ki may have changed while holding).
        if (!raceAllowsForm(player)) {
            player.displayClientMessage(Component.translatable("message.powerlevelling.transform.race")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        float minKi = ServerConfig.TRANSFORM_MIN_KI.get();
        if (r.ki < minKi) {
            player.displayClientMessage(Component.translatable("message.powerlevelling.transform.need_ki",
                    Math.round(minKi)).withStyle(ChatFormatting.RED), true);
            return;
        }

        // Apply form
        st.form = SSJ_FORM;
        applySSJModifiers(player);
        r.ki -= minKi * 0.1f; // small initial cost

        player.level().playSound(null, player.blockPosition(), ModSounds.STANCE_ENTER.get(), SoundSource.PLAYERS, 0.8f, 1.2f);
        player.displayClientMessage(Component.translatable("message.powerlevelling.transform.enter")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);

        // Sync visual state to all trackers
        ModNetwork.syncVisual(player);
    }

    public static void revert(ServerPlayer player) {
        CombatState st = player.getData(ModAttachments.COMBAT_STATE);
        if (!isSuperSaiyan(st)) return;

        st.form = BASE_FORM;
        st.transforming = false;
        st.transformTicks = 0;
        removeSSJModifiers(player);

        player.level().playSound(null, player.blockPosition(), ModSounds.STANCE_EXIT.get(), SoundSource.PLAYERS, 0.6f, 0.8f);
        player.displayClientMessage(Component.translatable("message.powerlevelling.transform.exit")
                .withStyle(ChatFormatting.GRAY), true);

        ModNetwork.syncVisual(player);
    }

    public static boolean isSuperSaiyan(CombatState st) {
        return st.form != null && st.form.equals(SSJ_FORM);
    }

    /** True if this player's race lists {@code powerlevelling:super_saiyan} in its hooks.forms. */
    public static boolean raceAllowsForm(ServerPlayer player) {
        Race race = CharacterRegistries.getRace(player.level().registryAccess(), CharacterService.get(player).race);
        return race != null && race.hooks().forms().contains(SSJ_FORM);
    }

    private static void applySSJModifiers(ServerPlayer player) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.addOrReplacePermanentModifier(new AttributeModifier(SSJ_SPEED_ID,
                    ServerConfig.TRANSFORM_SPEED_BONUS.get(), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    private static void removeSSJModifiers(ServerPlayer player) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(SSJ_SPEED_ID);
    }

    /**
     * Strips transformation modifiers unconditionally. Call on login: the form itself is transient, so a save made
     * during a crash must not keep the speed bonus.
     */
    public static void clearStaleModifiers(ServerPlayer player) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(SSJ_SPEED_ID);
            speed.removeModifier(SSJ_DAMAGE_ID); // legacy: pre-config builds put the (now unused) damage bonus here
        }
        AttributeInstance attack = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attack != null) attack.removeModifier(SSJ_DAMAGE_ID);
    }

    /** Called each server tick to drain ki while transformed. */
    public static void tickKiDrain(ServerPlayer player) {
        CombatState st = player.getData(ModAttachments.COMBAT_STATE);
        if (!isSuperSaiyan(st)) return;

        CombatResources r = player.getData(ModAttachments.RESOURCES);
        r.ki = Math.max(0, r.ki - ServerConfig.TRANSFORM_KI_DRAIN.get().floatValue());

        if (r.ki <= 0) {
            revert(player);
        }
    }
}

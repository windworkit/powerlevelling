package com.powerlevelling.combat;

import com.powerlevelling.PowerLevelling;
import com.powerlevelling.character.CharacterRegistries;
import com.powerlevelling.character.CharacterService;
import com.powerlevelling.character.Race;
import com.powerlevelling.data.CombatResources;
import com.powerlevelling.data.CombatState;
import com.powerlevelling.network.ModNetwork;
import com.powerlevelling.registry.ModAttachments;
import com.powerlevelling.registry.ModSounds;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Handles Super Saiyan and other form transformations.
 * Server-authoritative: clients predict locally but server validates.
 */
public final class Transform {
    private static final ResourceLocation SSJ_FORM = ResourceLocation.fromNamespaceAndPath(PowerLevelling.MODID, "super_saiyan");
    private static final ResourceLocation BASE_FORM = ResourceLocation.fromNamespaceAndPath(PowerLevelling.MODID, "base");

    // SSJ attribute modifier IDs
    private static final ResourceLocation SSJ_DAMAGE_ID = ResourceLocation.fromNamespaceAndPath(PowerLevelling.MODID, "ssj_damage");
    private static final ResourceLocation SSJ_SPEED_ID = ResourceLocation.fromNamespaceAndPath(PowerLevelling.MODID, "ssj_speed");

    // SSJ constants (tunable via config later)
    private static final double SSJ_DAMAGE_MULT = 0.5;      // +50% melee/ki damage
    private static final double SSJ_SPEED_MULT = 0.25;      // +25% movement speed
    private static final float SSJ_KI_COST_PER_TICK = 2.0f; // ki drained per tick while transformed
    private static final int MIN_KI_TO_TRANSFORM = 500;     // minimum ki to enter SSJ

    private Transform() {}

    /** Tries to toggle Super Saiyan. Returns true if the input was processed (not necessarily successful). */
    public static boolean startTransform(ServerPlayer player) {
        CombatState st = player.getData(ModAttachments.COMBAT_STATE);
        if (!st.stance) return false;
        if (st.charging) return false;

        // Visual: start charging
        st.transforming = true;
        ModNetwork.syncVisual(player);
        return true;
    }

    public static boolean stopTransform(ServerPlayer player) {
        CombatState st = player.getData(ModAttachments.COMBAT_STATE);
        if (st.transforming) {
            st.transforming = false;
            ModNetwork.syncVisual(player);
        }
        return true;
    }

    // Existing tryToggle...
    public static boolean tryToggle(ServerPlayer player) {
        // ... (This should now be called by startTransform after a timer, or directly by a specialized timer)
        // Actually, maybe I should combine them: startTransform triggers a timer on server?
        // Or better: client sends START_TRANSFORM, server starts a timer (e.g. 2 seconds), then triggers tryToggle if released?
        // No, keep it simpler: CLIENT side handles the delay, sends "TRANSFORM" when timer finishes.
        // Wait, that's dangerous (cheating).
        // Server MUST handle the delay.
    }

    private static void tryEnterSSJ(ServerPlayer player) {
        CombatState st = player.getData(ModAttachments.COMBAT_STATE);
        CombatResources r = player.getData(ModAttachments.RESOURCES);

        // Check race (only Saiyans for now)
        Race race = CharacterRegistries.getRace(player.level().registryAccess(), CharacterService.get(player).race);
        if (race == null || !CharacterService.get(player).race.equals(ResourceLocation.fromNamespaceAndPath(PowerLevelling.MODID, "saiyan"))) {
            return;
        }

        // Check ki
        if (r.ki < MIN_KI_TO_TRANSFORM) return;

        // Apply form
        st.form = SSJ_FORM;
        applySSJModifiers(player);
        r.ki -= MIN_KI_TO_TRANSFORM * 0.1f; // small initial cost

        // Play transform sound
        player.level().playSound(null, player.blockPosition(), ModSounds.STANCE_ENTER.get(), SoundSource.PLAYERS, 0.8f, 1.2f);

        // Sync visual state to all trackers
        ModNetwork.syncVisual(player);
    }

    public static void revert(ServerPlayer player) {
        CombatState st = player.getData(ModAttachments.COMBAT_STATE);
        if (!isSuperSaiyan(st)) return;

        st.form = BASE_FORM;
        removeSSJModifiers(player);

        // Play revert sound
        player.level().playSound(null, player.blockPosition(), ModSounds.STANCE_EXIT.get(), SoundSource.PLAYERS, 0.6f, 0.8f);

        ModNetwork.syncVisual(player);
    }

    public static boolean isSuperSaiyan(CombatState st) {
        return st.form != null && st.form.equals(SSJ_FORM);
    }

    private static void applySSJModifiers(ServerPlayer player) {
        // Damage multiplier (melee + ki)
        AttributeInstance meleeDamage = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (meleeDamage != null) {
            meleeDamage.addOrReplacePermanentModifier(
                    new AttributeModifier(SSJ_DAMAGE_ID, SSJ_DAMAGE_MULT, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }

        // Movement speed
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.addOrReplacePermanentModifier(
                    new AttributeModifier(SSJ_SPEED_ID, SSJ_SPEED_MULT, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    private static void removeSSJModifiers(ServerPlayer player) {
        AttributeInstance meleeDamage = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (meleeDamage != null) meleeDamage.removeModifier(SSJ_DAMAGE_ID);

        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(SSJ_SPEED_ID);
    }

    /** Called each server tick to drain ki while transformed. */
    public static void tickKiDrain(ServerPlayer player) {
        CombatState st = player.getData(ModAttachments.COMBAT_STATE);
        if (!isSuperSaiyan(st)) return;

        CombatResources r = player.getData(ModAttachments.RESOURCES);
        r.ki = Math.max(0, r.ki - SSJ_KI_COST_PER_TICK);

        if (r.ki <= 0) {
            revert(player);
        }
    }
}
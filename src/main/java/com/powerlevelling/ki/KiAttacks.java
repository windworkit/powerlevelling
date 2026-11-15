package com.powerlevelling.ki;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * The one place that turns an attack id into a {@link KiAttack}: aliases first, then the datapack registry, then (later)
 * the player's custom attacks (Addendum C.4, ids powerlevelling:custom/&lt;slot&gt;). Use this instead of
 * {@link KiAttackRegistry#get} wherever a player's loadout id is resolved (KiSkills, HUD, FX).
 */
public final class KiAttacks {
    /** old id -> new id, tried when the id itself is not in the registry (K6 fills this for renamed attacks). */
    private static final Map<ResourceLocation, ResourceLocation> ALIASES = new HashMap<>();

    /**
     * HOOK for the custom attack creator (K10): given the player (server or client side) and an id, returns the player's
     * custom attack or null. Called last; null = unknown id.
     */
    private static volatile BiFunction<Player, ResourceLocation, KiAttack> customResolver = null;

    static {
        // K3 rename plan: rapid_blast -> hailstorm. A rapid_blast.json in the registry still wins (resolve tries it first).
        ALIASES.put(ResourceLocation.parse("powerlevelling:rapid_blast"), ResourceLocation.parse("powerlevelling:hailstorm"));
    }

    private KiAttacks() {
    }

    /** Registers an alias (old id resolves to the new attack). */
    public static void alias(ResourceLocation oldId, ResourceLocation newId) {
        ALIASES.put(oldId, newId);
    }

    public static void setCustomResolver(BiFunction<Player, ResourceLocation, KiAttack> resolver) {
        customResolver = resolver;
    }

    /** Server or client: resolves {@code id} for {@code player}. Null if unknown. */
    public static KiAttack resolve(Player player, ResourceLocation id) {
        if (id == null) return null;
        KiAttack a = resolve(player.level().registryAccess(), id);
        if (a != null) return a;
        BiFunction<Player, ResourceLocation, KiAttack> custom = customResolver;
        return custom == null ? null : custom.apply(player, id);
    }

    /** Registry + aliases only (no player: events that carry just a registry access, e.g. an impact packet). */
    public static KiAttack resolve(RegistryAccess access, ResourceLocation id) {
        if (id == null) return null;
        KiAttack a = KiAttackRegistry.get(access, id);
        if (a == null) {
            ResourceLocation target = ALIASES.get(id);
            if (target != null) a = KiAttackRegistry.get(access, target);
        }
        return a;
    }
}

package com.powerlevelling.character;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToIntFunction;

import com.mojang.serialization.Codec;
import com.powerlevelling.PowerLevelling;

import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;

/**
 * The three character datapack registries (synced to clients, same codec for disk and network):
 * powerlevelling:race, powerlevelling:ki_nature, powerlevelling:cosmetic_part. Files live at
 * data/&lt;ns&gt;/powerlevelling/&lt;registry&gt;/&lt;id&gt;.json
 */
@EventBusSubscriber(modid = PowerLevelling.MODID)
public final class CharacterRegistries {
    public static final ResourceKey<Registry<Race>> RACE = key("race");
    public static final ResourceKey<Registry<KiNature>> KI_NATURE = key("ki_nature");
    public static final ResourceKey<Registry<CosmeticPart>> COSMETIC_PART = key("cosmetic_part");

    /** A registry entry with its id. */
    public record Entry<T>(ResourceLocation id, T value) {
    }

    private CharacterRegistries() {
    }

    private static <T> ResourceKey<Registry<T>> key(String path) {
        return ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath(PowerLevelling.MODID, path));
    }

    @SubscribeEvent
    public static void onNewRegistry(DataPackRegistryEvent.NewRegistry event) {
        Codec<Race> race = Race.CODEC;
        event.dataPackRegistry(RACE, race, race);
        event.dataPackRegistry(KI_NATURE, KiNature.CODEC, KiNature.CODEC);
        event.dataPackRegistry(COSMETIC_PART, CosmeticPart.CODEC, CosmeticPart.CODEC);
    }

    // ---- lookups ----------------------------------------------------------------------------------------------

    /** Null if the id is null or not registered. */
    public static <T> T get(RegistryAccess access, ResourceKey<Registry<T>> key, ResourceLocation id) {
        if (id == null) return null;
        return access.registry(key).map(r -> r.get(id)).orElse(null);
    }

    public static Race getRace(RegistryAccess access, ResourceLocation id) {
        return get(access, RACE, id);
    }

    public static KiNature getNature(RegistryAccess access, ResourceLocation id) {
        return get(access, KI_NATURE, id);
    }

    public static CosmeticPart getPart(RegistryAccess access, ResourceLocation id) {
        return get(access, COSMETIC_PART, id);
    }

    public static <T> Collection<ResourceLocation> ids(RegistryAccess access, ResourceKey<Registry<T>> key) {
        return access.registry(key).<Collection<ResourceLocation>>map(Registry::keySet).orElse(List.of());
    }

    /** All entries of a registry sorted by {@code sort} then id. */
    public static <T> List<Entry<T>> all(RegistryAccess access, ResourceKey<Registry<T>> key, ToIntFunction<T> sort) {
        return access.registry(key).map(r -> r.keySet().stream()
                .map(id -> new Entry<>(id, r.get(id)))
                .filter(e -> e.value() != null)
                .sorted(Comparator.<Entry<T>>comparingInt(e -> sort.applyAsInt(e.value())).thenComparing(e -> e.id().toString()))
                .toList()).orElse(List.of());
    }

    public static List<Entry<Race>> allRaces(RegistryAccess access) {
        return all(access, RACE, Race::sort);
    }

    public static List<Entry<KiNature>> allNatures(RegistryAccess access) {
        return all(access, KI_NATURE, KiNature::sort);
    }

    /** Parts sorted by slot, then id (cosmetic parts have no sort field). */
    public static List<Entry<CosmeticPart>> allParts(RegistryAccess access) {
        return all(access, COSMETIC_PART, p -> 0).stream()
                .sorted(Comparator.<Entry<CosmeticPart>, String>comparing(e -> e.value().slot())
                        .thenComparing(e -> e.id().toString()))
                .toList();
    }
}

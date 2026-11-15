package com.powerlevelling.ki;

import java.util.Collection;
import java.util.List;

import com.powerlevelling.PowerLevelling;

import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;

/**
 * The powerlevelling:ki_attack datapack registry (synced to clients). Entries live at
 * data/&lt;namespace&gt;/powerlevelling/ki_attack/&lt;id&gt;.json
 */
@EventBusSubscriber(modid = PowerLevelling.MODID)
public final class KiAttackRegistry {
    public static final ResourceKey<Registry<KiAttack>> KEY = ResourceKey.createRegistryKey(
            ResourceLocation.fromNamespaceAndPath(PowerLevelling.MODID, "ki_attack"));

    public static final ResourceLocation DEFAULT_SLOT_1 = ResourceLocation.fromNamespaceAndPath(PowerLevelling.MODID, "ki_blast");
    public static final ResourceLocation DEFAULT_SLOT_2 = ResourceLocation.fromNamespaceAndPath(PowerLevelling.MODID, "meteor_core");
    public static final ResourceLocation DEFAULT_SLOT_3 = ResourceLocation.fromNamespaceAndPath(PowerLevelling.MODID, "pinpoint_lance");
    public static final ResourceLocation DEFAULT_SLOT_4 = ResourceLocation.fromNamespaceAndPath(PowerLevelling.MODID, "repulse_wave");

    private KiAttackRegistry() {
    }

    @SubscribeEvent
    public static void onNewRegistry(DataPackRegistryEvent.NewRegistry event) {
        // Same codec for disk and network: clients get the full definition (colors, sounds, costs for the HUD).
        event.dataPackRegistry(KEY, KiAttackTypes.CODEC, KiAttackTypes.CODEC);
    }

    /** Looks an attack up in the given registry access (server: level.registryAccess(); client: level.registryAccess()). */
    public static KiAttack get(RegistryAccess access, ResourceLocation id) {
        if (id == null) return null;
        return access.registry(KEY).map(r -> r.get(id)).orElse(null);
    }

    public static Collection<ResourceLocation> ids(RegistryAccess access) {
        return access.registry(KEY).<Collection<ResourceLocation>>map(Registry::keySet).orElse(List.of());
    }
}

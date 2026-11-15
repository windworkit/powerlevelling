package com.powerlevelling.combat;

import java.util.Map;

import com.powerlevelling.PowerLevelling;

import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;

/**
 * The powerlevelling:combat_move datapack registry (synced to clients so they can predict moves). Entries live at
 * data/&lt;namespace&gt;/powerlevelling/combat_move/&lt;id&gt;.json and are loaded at world start.
 * {@link #resolve} is the ONE place that decides which move an input starts; server and client prediction both call it,
 * so they agree as long as they see the same registry.
 */
@EventBusSubscriber(modid = PowerLevelling.MODID)
public final class CombatMoves {
    public static final ResourceKey<Registry<CombatMove>> KEY = ResourceKey.createRegistryKey(
            ResourceLocation.fromNamespaceAndPath(PowerLevelling.MODID, "combat_move"));

    /** A move picked for an input. */
    public record Resolved(ResourceLocation id, CombatMove move) {
    }

    private CombatMoves() {
    }

    @SubscribeEvent
    public static void onNewRegistry(DataPackRegistryEvent.NewRegistry event) {
        event.dataPackRegistry(KEY, CombatMove.CODEC, CombatMove.CODEC);
    }

    public static CombatMove get(RegistryAccess access, ResourceLocation id) {
        if (id == null) return null;
        return access.registry(KEY).map(r -> r.get(id)).orElse(null);
    }

    /**
     * Move started by {@code input}. If {@code chainFrom} (the move whose cancel window / chain grace we are in) has a
     * follow-up for the input that fits the ground/air state, that wins; otherwise the entry move for the input (if several
     * fit, the smallest id, so every side picks the same one). Null = nothing to start.
     */
    public static Resolved resolve(RegistryAccess access, MeleeInput input, ResourceLocation chainFrom, boolean onGround) {
        Registry<CombatMove> reg = access.registry(KEY).orElse(null);
        if (reg == null || input == null) return null;
        if (chainFrom != null) {
            CombatMove prev = reg.get(chainFrom);
            if (prev != null) {
                ResourceLocation next = prev.followUps().get(input);
                CombatMove m = next == null ? null : reg.get(next);
                if (m != null && m.requires().test(onGround)) return new Resolved(next, m);
            }
        }
        ResourceLocation best = null;
        CombatMove bestMove = null;
        for (Map.Entry<ResourceKey<CombatMove>, CombatMove> e : reg.entrySet()) {
            CombatMove m = e.getValue();
            if (m.entry().orElse(null) != input || !m.requires().test(onGround)) continue;
            ResourceLocation id = e.getKey().location();
            if (best == null || id.toString().compareTo(best.toString()) < 0) {
                best = id;
                bestMove = m;
            }
        }
        return best == null ? null : new Resolved(best, bestMove);
    }
}

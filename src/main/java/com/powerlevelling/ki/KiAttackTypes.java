package com.powerlevelling.ki;

import java.util.HashMap;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;

import net.minecraft.resources.ResourceLocation;

/**
 * String-keyed registry of ki attack types and the dispatch codec built from it. To add a type (beam, barrage, disc ...):
 * write a record implementing {@link KiAttack} with a MapCodec, then call {@link #register} below.
 * The JSON "type" field picks the codec; the common fields come from {@link KiAttackParts.Common}.
 */
public final class KiAttackTypes {
    private static final Map<ResourceLocation, MapCodec<? extends KiAttack>> TYPES = new HashMap<>();

    static {
        register(BlastAttack.TYPE, BlastAttack.CODEC);
        register(SphereAttack.TYPE, SphereAttack.CODEC);
        // v1 name: decodes into a SphereAttack (and is written back as "sphere" on the network).
        register(SphereAttack.ALIAS_CHARGED_BLAST, SphereAttack.CODEC);
        register(NeedleAttack.TYPE, NeedleAttack.CODEC);
        register(BurstAttack.TYPE, BurstAttack.CODEC);
        register(DiscAttack.TYPE, DiscAttack.CODEC);
        register(BarrageAttack.TYPE, BarrageAttack.CODEC);
        register(HomingAttack.TYPE, HomingAttack.CODEC);
        register(BombAttack.TYPE, BombAttack.CODEC);
        register(BeamAttack.TYPE, BeamAttack.CODEC);
        // M14: barrier
    }

    private KiAttackTypes() {
    }

    public static void register(ResourceLocation id, MapCodec<? extends KiAttack> codec) {
        TYPES.put(id, codec);
    }

    private static final Codec<ResourceLocation> TYPE_ID = ResourceLocation.CODEC.validate(
            id -> TYPES.containsKey(id) ? DataResult.success(id)
                    : DataResult.error(() -> "Unknown ki_attack type '" + id + "'"));

    /** Dispatch codec: reads "type", then the matching type's fields. Used for both disk and network. */
    public static final Codec<KiAttack> CODEC = TYPE_ID.dispatch("type", KiAttack::typeId, TYPES::get);
}

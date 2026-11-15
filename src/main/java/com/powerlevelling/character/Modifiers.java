package com.powerlevelling.character;

import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.powerlevelling.data.KiProgression.Stat;

/**
 * The shared {@code modifiers} block of races and ki natures (Addendum B.1).
 * {@code mult}: per-channel multiplier (1.0 = no change). {@code flat}: per-channel additive.
 * {@code stat_scaling}: multiplies the per-point effect of a stat. All three are optional.
 */
public record Modifiers(Map<Channel, Float> mult, Map<Channel, Float> flat, Map<Stat, Float> statScaling) {
    public static final Modifiers NONE = new Modifiers(Map.of(), Map.of(), Map.of());

    /** Stat <-> its id string ("strength", "ki_power", ...). KiProgression.Stat is not StringRepresentable, so adapt it here. */
    public static final Codec<Stat> STAT_CODEC = Codec.STRING.comapFlatMap(
            s -> {
                Stat st = Stat.byId(s);
                return st != null ? DataResult.success(st) : DataResult.error(() -> "Unknown stat '" + s + "'");
            },
            st -> st.id);

    public static final Codec<Modifiers> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(Channel.CODEC, Codec.FLOAT).optionalFieldOf("mult", Map.of()).forGetter(Modifiers::mult),
            Codec.unboundedMap(Channel.CODEC, Codec.FLOAT).optionalFieldOf("flat", Map.of()).forGetter(Modifiers::flat),
            Codec.unboundedMap(STAT_CODEC, Codec.FLOAT).optionalFieldOf("stat_scaling", Map.of()).forGetter(Modifiers::statScaling)
    ).apply(i, Modifiers::new));
}

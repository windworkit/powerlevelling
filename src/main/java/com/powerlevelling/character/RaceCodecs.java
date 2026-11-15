package com.powerlevelling.character;

import java.util.List;

import com.mojang.serialization.Codec;
import com.powerlevelling.ki.KiAttackParts;
import com.powerlevelling.ki.KiAttackParts.FloatRange;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Shared codecs/defaults for {@link Race}. Kept in their own class (no reference back to Race) so the nested
 * record codecs can never read a not-yet-initialised static of Race.
 */
final class RaceCodecs {
    static final FloatRange DEFAULT_HEIGHT = new FloatRange(0.92f, 1.08f);
    static final FloatRange DEFAULT_BUILD = new FloatRange(0.94f, 1.06f);

    /** [min, max], each clamped to 0.8-1.2. */
    static final Codec<FloatRange> SIZE_RANGE = FloatRange.CODEC.xmap(
            r -> new FloatRange(Mth.clamp(r.min(), Race.SIZE_MIN, Race.SIZE_MAX), Mth.clamp(r.max(), Race.SIZE_MIN, Race.SIZE_MAX)),
            r -> r);

    static final Codec<AnyOr<ResourceLocation>> STYLES = AnyOr.codec(ResourceLocation.CODEC, "*");
    static final Codec<AnyOr<Integer>> COLORS = AnyOr.codec(KiAttackParts.COLOR, "any");
    static final Codec<List<Integer>> COLOR_LIST = KiAttackParts.COLOR.listOf();

    private RaceCodecs() {
    }
}

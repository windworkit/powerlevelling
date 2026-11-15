package com.powerlevelling.character;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;

/** A ki nature (datapack registry powerlevelling:ki_nature, Addendum B.3). Only {@code name} is required. */
public record KiNature(String name, String description, Optional<ResourceLocation> icon, int sort, boolean enabled,
                       Modifiers modifiers) {
    public static final Codec<KiNature> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("name").forGetter(KiNature::name),
            Codec.STRING.optionalFieldOf("description", "").forGetter(KiNature::description),
            ResourceLocation.CODEC.optionalFieldOf("icon").forGetter(KiNature::icon),
            Codec.INT.optionalFieldOf("sort", 0).forGetter(KiNature::sort),
            Codec.BOOL.optionalFieldOf("enabled", true).forGetter(KiNature::enabled),
            Modifiers.CODEC.optionalFieldOf("modifiers", Modifiers.NONE).forGetter(KiNature::modifiers)
    ).apply(i, KiNature::new));
}

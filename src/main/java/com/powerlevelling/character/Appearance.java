package com.powerlevelling.character;

import java.util.List;

import com.google.gson.JsonPrimitive;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.powerlevelling.PowerLevelling;
import com.powerlevelling.ki.KiAttackParts;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * A player's chosen look. Immutable (replace the whole record on change). Colors are packed RGB ints, saved as "#RRGGBB".
 * auraColor / skinColor use -1 as "none" (aura matches ki color / vanilla skin), saved as the string "none".
 * Unknown ids (hairStyle, parts) are kept as-is; the renderer just draws nothing for them.
 */
public record Appearance(ResourceLocation hairStyle, int hairColor, int eyeColor, int kiColor, int auraColor,
                         int skinColor, float height, float build, List<ResourceLocation> parts, int eyeStyle) {

    public static final ResourceLocation DEFAULT_HAIR = ResourceLocation.fromNamespaceAndPath(PowerLevelling.MODID, "flare");
    /** Hair style sentinel: draw no hair layer, show the skin's own hair. Always allowed for races whose hair is allowed. */
    public static final ResourceLocation NO_HAIR = ResourceLocation.fromNamespaceAndPath(PowerLevelling.MODID, "none");
    public static final Appearance DEFAULT = new Appearance(DEFAULT_HAIR, 0x1A1A1A, 0x202020, 0x4FC3FF, -1, -1,
            1.0f, 1.0f, List.of(), 0);

    /** "#RRGGBB" or "none" (-1). */
    public static final Codec<Integer> COLOR_OR_NONE = Codec.STRING.comapFlatMap(s -> {
        if (s.isEmpty() || "none".equals(s)) return DataResult.success(-1);
        return KiAttackParts.COLOR.parse(JsonOps.INSTANCE, new JsonPrimitive(s));
    }, c -> c < 0 ? "none" : String.format("#%06X", c & 0xFFFFFF));

    /** Float clamped to 0.8-1.2 on decode (NaN becomes 1.0). */
    public static final Codec<Float> SIZE = Codec.FLOAT.xmap(
            f -> Float.isNaN(f) ? 1.0f : Mth.clamp(f, Race.SIZE_MIN, Race.SIZE_MAX), f -> f);

    public static final Codec<Appearance> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.optionalFieldOf("hair_style", DEFAULT.hairStyle).forGetter(Appearance::hairStyle),
            KiAttackParts.COLOR.optionalFieldOf("hair_color", DEFAULT.hairColor).forGetter(Appearance::hairColor),
            KiAttackParts.COLOR.optionalFieldOf("eye_color", DEFAULT.eyeColor).forGetter(Appearance::eyeColor),
            KiAttackParts.COLOR.optionalFieldOf("ki_color", DEFAULT.kiColor).forGetter(Appearance::kiColor),
            COLOR_OR_NONE.optionalFieldOf("aura_color", -1).forGetter(Appearance::auraColor),
            COLOR_OR_NONE.optionalFieldOf("skin_color", -1).forGetter(Appearance::skinColor),
            SIZE.optionalFieldOf("height", 1.0f).forGetter(Appearance::height),
            SIZE.optionalFieldOf("build", 1.0f).forGetter(Appearance::build),
            ResourceLocation.CODEC.listOf().optionalFieldOf("parts", List.of()).forGetter(Appearance::parts),
            Codec.INT.optionalFieldOf("eye_style", 0).forGetter(Appearance::eyeStyle)
    ).apply(i, Appearance::new));
}

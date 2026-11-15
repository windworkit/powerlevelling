package com.powerlevelling.character;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;

/**
 * A cosmetic part (datapack registry powerlevelling:cosmetic_part, Addendum B.4).
 * Required: slot, name, model, texture. slot is one of hair | tail | antenna | horn | head_dome (kept a free string
 * so other packs can add slots). model is a geo path id: assets/&lt;ns&gt;/powerlevelling/geo/&lt;path&gt;.geo.json.
 */
public record CosmeticPart(String slot, String name, ResourceLocation model, ResourceLocation texture, boolean tintable,
                           Anchor anchor, HelmetMode helmet, List<String> helmetVisibleBones,
                           Map<ResourceLocation, FormVariant> formVariants) {

    public static final List<String> SLOTS = List.of("hair", "tail", "antenna", "horn", "head_dome");

    public enum Anchor implements StringRepresentable {
        HEAD("head"), BODY("body");

        public static final Codec<Anchor> CODEC = StringRepresentable.fromEnum(Anchor::values);
        private final String id;

        Anchor(String id) {
            this.id = id;
        }

        @Override
        public String getSerializedName() {
            return id;
        }
    }

    /** What happens when the player wears a helmet: hide the part, keep showing it, or only show helmet_visible_bones. */
    public enum HelmetMode implements StringRepresentable {
        HIDE("hide"), SHOW("show"), PARTIAL("partial");

        public static final Codec<HelmetMode> CODEC = StringRepresentable.fromEnum(HelmetMode::values);
        private final String id;

        HelmetMode(String id) {
            this.id = id;
        }

        @Override
        public String getSerializedName() {
            return id;
        }
    }

    /** Replacement model and/or texture while the player is in a form (key = form id, e.g. powerlevelling:super_saiyan). */
    public record FormVariant(Optional<ResourceLocation> model, Optional<ResourceLocation> texture) {
        public static final Codec<FormVariant> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.optionalFieldOf("model").forGetter(FormVariant::model),
                ResourceLocation.CODEC.optionalFieldOf("texture").forGetter(FormVariant::texture)
        ).apply(i, FormVariant::new));
    }

    public static final Codec<CosmeticPart> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("slot").forGetter(CosmeticPart::slot),
            Codec.STRING.fieldOf("name").forGetter(CosmeticPart::name),
            ResourceLocation.CODEC.fieldOf("model").forGetter(CosmeticPart::model),
            ResourceLocation.CODEC.fieldOf("texture").forGetter(CosmeticPart::texture),
            Codec.BOOL.optionalFieldOf("tintable", true).forGetter(CosmeticPart::tintable),
            Anchor.CODEC.optionalFieldOf("anchor", Anchor.HEAD).forGetter(CosmeticPart::anchor),
            HelmetMode.CODEC.optionalFieldOf("helmet", HelmetMode.HIDE).forGetter(CosmeticPart::helmet),
            Codec.STRING.listOf().optionalFieldOf("helmet_visible_bones", List.of()).forGetter(CosmeticPart::helmetVisibleBones),
            Codec.unboundedMap(ResourceLocation.CODEC, FormVariant.CODEC).optionalFieldOf("form_variants", Map.of()).forGetter(CosmeticPart::formVariants)
    ).apply(i, CosmeticPart::new));
}

package com.powerlevelling.character;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.powerlevelling.PowerLevelling;
import com.powerlevelling.ki.KiAttackParts;
import com.powerlevelling.ki.KiAttackParts.FloatRange;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.StringRepresentable;

/**
 * A race (datapack registry powerlevelling:race, Addendum B.2). Only {@code name} is required.
 * {@code name}/{@code lore} are translation keys (or plain text).
 */
public record Race(String name, String lore, Optional<ResourceLocation> icon, int sort, boolean playable, boolean hidden,
                   Modifiers modifiers, Zenkai zenkai, RaceAppearance appearance, Hooks hooks) {

    public static final Codec<Race> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("name").forGetter(Race::name),
            Codec.STRING.optionalFieldOf("lore", "").forGetter(Race::lore),
            ResourceLocation.CODEC.optionalFieldOf("icon").forGetter(Race::icon),
            Codec.INT.optionalFieldOf("sort", 0).forGetter(Race::sort),
            Codec.BOOL.optionalFieldOf("playable", true).forGetter(Race::playable),
            Codec.BOOL.optionalFieldOf("hidden", false).forGetter(Race::hidden),
            Modifiers.CODEC.optionalFieldOf("modifiers", Modifiers.NONE).forGetter(Race::modifiers),
            Zenkai.CODEC.optionalFieldOf("zenkai", Zenkai.DEFAULT).forGetter(Race::zenkai),
            RaceAppearance.CODEC.optionalFieldOf("appearance", RaceAppearance.DEFAULT).forGetter(Race::appearance),
            Hooks.CODEC.optionalFieldOf("hooks", Hooks.DEFAULT).forGetter(Race::hooks)
    ).apply(i, Race::new));

    // ---- zenkai ------------------------------------------------------------------------------------------------

    /** bonus_points: extra stat points on a Zenkai (added to the base 2). cooldown_mult scales the config cooldown. */
    public record Zenkai(int bonusPoints, float cooldownMult, float expBonusMult) {
        public static final Zenkai DEFAULT = new Zenkai(0, 1f, 1f);
        public static final Codec<Zenkai> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("bonus_points", 0).forGetter(Zenkai::bonusPoints),
                Codec.FLOAT.optionalFieldOf("cooldown_mult", 1f).forGetter(Zenkai::cooldownMult),
                Codec.FLOAT.optionalFieldOf("exp_bonus_mult", 1f).forGetter(Zenkai::expBonusMult)
        ).apply(i, Zenkai::new));
    }

    // ---- appearance rules ---------------------------------------------------------------------------------------

    public static final float SIZE_MIN = 0.8f;
    public static final float SIZE_MAX = 1.2f;

    public enum TailMode implements StringRepresentable {
        /** No tail ever. */
        NONE("none"),
        /** Present at creation; the player may remove it. */
        DEFAULT_ON("default_on"),
        /** Player chooses; the {@code default} field is the initial state. */
        OPTIONAL("optional"),
        /** Always present, cannot be removed. */
        FORCED("forced");

        public static final Codec<TailMode> CODEC = StringRepresentable.fromEnum(TailMode::values);
        private final String id;

        TailMode(String id) {
            this.id = id;
        }

        @Override
        public String getSerializedName() {
            return id;
        }
    }

    public enum SkinMode implements StringRepresentable {
        VANILLA("vanilla"),
        PALETTE("palette");

        public static final Codec<SkinMode> CODEC = StringRepresentable.fromEnum(SkinMode::values);
        private final String id;

        SkinMode(String id) {
            this.id = id;
        }

        @Override
        public String getSerializedName() {
            return id;
        }
    }

    /** allowed=false hides hair entirely. styles: "*" or list of cosmetic_part ids (slot hair). colors: "any" or list. */
    public record Hair(boolean allowed, AnyOr<ResourceLocation> styles, Optional<ResourceLocation> defaultStyle,
                       AnyOr<Integer> colors, List<Integer> suggestedColors) {
        public static final Hair DEFAULT = new Hair(true, AnyOr.all(), Optional.empty(), AnyOr.all(), List.of());
        public static final Codec<Hair> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.BOOL.optionalFieldOf("allowed", true).forGetter(Hair::allowed),
                RaceCodecs.STYLES.optionalFieldOf("styles", AnyOr.all()).forGetter(Hair::styles),
                ResourceLocation.CODEC.optionalFieldOf("default_style").forGetter(Hair::defaultStyle),
                RaceCodecs.COLORS.optionalFieldOf("colors", AnyOr.all()).forGetter(Hair::colors),
                RaceCodecs.COLOR_LIST.optionalFieldOf("suggested_colors", List.of()).forGetter(Hair::suggestedColors)
        ).apply(i, Hair::new));
    }

    public record Eyes(AnyOr<Integer> colors, List<Integer> suggestedColors) {
        public static final Eyes DEFAULT = new Eyes(AnyOr.all(), List.of());
        public static final Codec<Eyes> CODEC = RecordCodecBuilder.create(i -> i.group(
                RaceCodecs.COLORS.optionalFieldOf("colors", AnyOr.all()).forGetter(Eyes::colors),
                RaceCodecs.COLOR_LIST.optionalFieldOf("suggested_colors", List.of()).forGetter(Eyes::suggestedColors)
        ).apply(i, Eyes::new));
    }

    /**
     * mode: see {@link TailMode}. defaultOn ("default" in JSON): initial state for mode optional.
     * model: cosmetic_part id of the tail. defaultColor: "hair" (follow hair color) or "#RRGGBB".
     */
    public record Tail(TailMode mode, boolean defaultOn, Optional<ResourceLocation> model, String defaultColor) {
        public static final Tail DEFAULT = new Tail(TailMode.NONE, false, Optional.empty(), "hair");
        public static final Codec<Tail> CODEC = RecordCodecBuilder.create(i -> i.group(
                TailMode.CODEC.optionalFieldOf("mode", TailMode.NONE).forGetter(Tail::mode),
                Codec.BOOL.optionalFieldOf("default", false).forGetter(Tail::defaultOn),
                ResourceLocation.CODEC.optionalFieldOf("model").forGetter(Tail::model),
                Codec.STRING.optionalFieldOf("default_color", "hair").forGetter(Tail::defaultColor)
        ).apply(i, Tail::new));

        /** Tail state a freshly created / reincarnated character of this race starts with. */
        public TailState initialState() {
            return switch (mode) {
                case NONE -> TailState.NONE;
                case DEFAULT_ON, FORCED -> TailState.PRESENT;
                case OPTIONAL -> defaultOn ? TailState.PRESENT : TailState.NONE;
            };
        }
    }

    /** slots: slot name (antenna, horn, head_dome, ...) -> allowed cosmetic_part ids. */
    public record Parts(Map<String, List<ResourceLocation>> slots) {
        public static final Parts DEFAULT = new Parts(Map.of());
        public static final Codec<Parts> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.unboundedMap(Codec.STRING, ResourceLocation.CODEC.listOf()).optionalFieldOf("slots", Map.of()).forGetter(Parts::slots)
        ).apply(i, Parts::new));
    }

    /** mode vanilla: player's own skin. palette: pick from {@code palette} (race-tinted skin). */
    public record Skin(SkinMode mode, List<Integer> palette) {
        public static final Skin DEFAULT = new Skin(SkinMode.VANILLA, List.of());
        public static final Codec<Skin> CODEC = RecordCodecBuilder.create(i -> i.group(
                SkinMode.CODEC.optionalFieldOf("mode", SkinMode.VANILLA).forGetter(Skin::mode),
                RaceCodecs.COLOR_LIST.optionalFieldOf("palette", List.of()).forGetter(Skin::palette)
        ).apply(i, Skin::new));
    }

    public record RaceAppearance(Hair hair, Eyes eyes, Tail tail, Parts parts, Skin skin, FloatRange height, FloatRange build) {
        public static final RaceAppearance DEFAULT = new RaceAppearance(Hair.DEFAULT, Eyes.DEFAULT, Tail.DEFAULT,
                Parts.DEFAULT, Skin.DEFAULT, RaceCodecs.DEFAULT_HEIGHT, RaceCodecs.DEFAULT_BUILD);
        public static final Codec<RaceAppearance> CODEC = RecordCodecBuilder.create(i -> i.group(
                Hair.CODEC.optionalFieldOf("hair", Hair.DEFAULT).forGetter(RaceAppearance::hair),
                Eyes.CODEC.optionalFieldOf("eyes", Eyes.DEFAULT).forGetter(RaceAppearance::eyes),
                Tail.CODEC.optionalFieldOf("tail", Tail.DEFAULT).forGetter(RaceAppearance::tail),
                Parts.CODEC.optionalFieldOf("parts", Parts.DEFAULT).forGetter(RaceAppearance::parts),
                Skin.CODEC.optionalFieldOf("skin", Skin.DEFAULT).forGetter(RaceAppearance::skin),
                RaceCodecs.SIZE_RANGE.optionalFieldOf("height", RaceCodecs.DEFAULT_HEIGHT).forGetter(RaceAppearance::height),
                RaceCodecs.SIZE_RANGE.optionalFieldOf("build", RaceCodecs.DEFAULT_BUILD).forGetter(RaceAppearance::build)
        ).apply(i, RaceAppearance::new));
    }

    // ---- hooks (used by later updates: forms, Oozaru) ---------------------------------------------------------

    public record Hooks(boolean oozaruCapable, List<ResourceLocation> forms, ResourceLocation baseForm) {
        public static final ResourceLocation DEFAULT_BASE_FORM = ResourceLocation.fromNamespaceAndPath(PowerLevelling.MODID, "base");
        public static final Hooks DEFAULT = new Hooks(false, List.of(), DEFAULT_BASE_FORM);
        public static final Codec<Hooks> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.BOOL.optionalFieldOf("oozaru_capable", false).forGetter(Hooks::oozaruCapable),
                ResourceLocation.CODEC.listOf().optionalFieldOf("forms", List.of()).forGetter(Hooks::forms),
                ResourceLocation.CODEC.optionalFieldOf("base_form", DEFAULT_BASE_FORM).forGetter(Hooks::baseForm)
        ).apply(i, Hooks::new));
    }
}

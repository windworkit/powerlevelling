package com.powerlevelling.ki;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.powerlevelling.network.KiImpactPayload;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;

/**
 * The building blocks of a ki_attack JSON file (design 5.5, Addendum C.3, schema v2). Every type shares {@link Common};
 * type-specific extras live in the type's own record under a block named after the type (needle{}, burst{}, sphere{}).
 * <p>
 * Schema v2 groups fields into nested sub-records: cost, input, damage, guard, present{pose, orb, sounds}. The v1 shape
 * (top-level "charge", "windup_ticks", "visual") is still accepted and converted by {@link Common#normalize}; every
 * field except "name", "cost" and "damage" has a default, so data packs can stay short.
 */
public final class KiAttackParts {
    public static final ResourceLocation KI_SPARK_ID = ResourceLocation.parse("powerlevelling:ki_spark");

    private KiAttackParts() {
    }

    /** "#RRGGBB" (or "RRGGBB") string <-> packed RGB int. */
    public static final Codec<Integer> COLOR = Codec.STRING.comapFlatMap(s -> {
        String hex = s.startsWith("#") ? s.substring(1) : s;
        if (hex.length() != 6) return DataResult.error(() -> "Color must look like #RRGGBB, got '" + s + "'");
        try {
            return DataResult.success(Integer.parseInt(hex, 16));
        } catch (NumberFormatException e) {
            return DataResult.error(() -> "Bad color '" + s + "'");
        }
    }, c -> String.format("#%06X", c & 0xFFFFFF));

    /** [min, max] pair, e.g. "charge_mult": [1.0, 3.5]. max is forced to be >= min. */
    public record FloatRange(float min, float max) {
        public static final Codec<FloatRange> CODEC = Codec.FLOAT.listOf().comapFlatMap(
                l -> l.size() == 2 ? DataResult.success(new FloatRange(l.get(0), l.get(1)))
                        : DataResult.error(() -> "Expected [min, max]"),
                r -> List.of(r.min(), r.max()));

        public FloatRange {
            if (max < min) max = min;
        }

        public static FloatRange of(float v) {
            return new FloatRange(v, v);
        }

        /** t is clamped to 0..1. */
        public float lerp(float t) {
            return min + (max - min) * Mth.clamp(t, 0f, 1f);
        }
    }

    /** [from, to] pair that MAY decrease (e.g. sphere speed [1.6, 0.9]); {@link FloatRange} cannot. */
    public record Ramp(float from, float to) {
        public static final Codec<Ramp> CODEC = Codec.FLOAT.listOf().comapFlatMap(
                l -> l.size() == 2 ? DataResult.success(new Ramp(l.get(0), l.get(1)))
                        : DataResult.error(() -> "Expected [from, to]"),
                r -> List.of(r.from(), r.to()));

        public static Ramp of(float v) {
            return new Ramp(v, v);
        }

        /** t is clamped to 0..1. */
        public float at(float t) {
            return from + (to - from) * Mth.clamp(t, 0f, 1f);
        }

        public float max() {
            return Math.max(from, to);
        }
    }

    // ---- cost / damage -------------------------------------------------------------------------------------------------

    /**
     * v2 cost. {@code base} defaults to 0 now (the burst costs only a fraction of max ki). Total on press:
     * base + maxKiFraction * maxKi (see {@link KiAttack#baseCost}); perChargeTick while charging; perActiveTick / perShot are
     * for later archetypes (beam, barrage).
     */
    public record Cost(float base, float perChargeTick, float perActiveTick, float perShot, float maxKiFraction) {
        public static final Codec<Cost> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.floatRange(0f, 1_000_000f).optionalFieldOf("base", 0f).forGetter(Cost::base),
                Codec.floatRange(0f, 1_000_000f).optionalFieldOf("per_charge_tick", 0f).forGetter(Cost::perChargeTick),
                Codec.floatRange(0f, 1_000_000f).optionalFieldOf("per_active_tick", 0f).forGetter(Cost::perActiveTick),
                Codec.floatRange(0f, 1_000_000f).optionalFieldOf("per_shot", 0f).forGetter(Cost::perShot),
                Codec.floatRange(0f, 1f).optionalFieldOf("max_ki_fraction", 0f).forGetter(Cost::maxKiFraction)
        ).apply(i, Cost::new));
    }

    public record Damage(float base, FloatRange chargeMult, int hitInterval) {
        public static final Codec<Damage> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.floatRange(0f, 1_000_000f).fieldOf("base").forGetter(Damage::base),
                FloatRange.CODEC.optionalFieldOf("charge_mult", FloatRange.of(1f)).forGetter(Damage::chargeMult),
                Codec.intRange(0, 200).optionalFieldOf("hit_interval", 0).forGetter(Damage::hitInterval)
        ).apply(i, Damage::new));
    }

    /** Named ProjectileSpec so it doesn't clash with net.minecraft.world.entity.projectile.Projectile. */
    public record ProjectileSpec(float speed, FloatRange radius, float range, int lifetime) {
        public static final ProjectileSpec DEFAULT = new ProjectileSpec(2.0f, FloatRange.of(0.3f), 48f, 100);
        public static final Codec<ProjectileSpec> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.floatRange(0.01f, 16f).optionalFieldOf("speed", 2.0f).forGetter(ProjectileSpec::speed),
                FloatRange.CODEC.optionalFieldOf("radius", FloatRange.of(0.3f)).forGetter(ProjectileSpec::radius),
                Codec.floatRange(1f, 512f).optionalFieldOf("range", 48f).forGetter(ProjectileSpec::range),
                Codec.intRange(1, 1200).optionalFieldOf("lifetime", 100).forGetter(ProjectileSpec::lifetime)
        ).apply(i, ProjectileSpec::new));
    }

    /** Charge view of {@link Input} (min/max ticks and the slowdown while charging). Derived, not stored. */
    public record Charge(int minTicks, int maxTicks, float moveSpeedMult) {
        public static final Charge NONE = new Charge(0, 0, 1f);
        /** The v1 "charge" block (still accepted, converted into {@link Input}). */
        public static final Codec<Charge> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(0, 1200).optionalFieldOf("min_ticks", 0).forGetter(Charge::minTicks),
                Codec.intRange(0, 1200).optionalFieldOf("max_ticks", 0).forGetter(Charge::maxTicks),
                Codec.floatRange(0f, 1f).optionalFieldOf("move_speed_mult", 1f).forGetter(Charge::moveSpeedMult)
        ).apply(i, Charge::new));

        public Charge {
            if (maxTicks < minTicks) maxTicks = minTicks;
        }
    }

    public record Explosion(float radius) {
        public static final Codec<Explosion> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.floatRange(0f, 32f).fieldOf("radius").forGetter(Explosion::radius)
        ).apply(i, Explosion::new));
    }

    // ---- input ---------------------------------------------------------------------------------------------------------

    /** How the slot key drives the attack (C.3 input.mode). */
    public enum InputMode {
        TAP("tap"), HOLD_CHARGE("hold_charge"), CHANNEL("channel"), HOLD_STREAM("hold_stream");

        public final String id;

        InputMode(String id) {
            this.id = id;
        }

        public static final Codec<InputMode> CODEC = Codec.STRING.comapFlatMap(s -> {
            for (InputMode m : values()) {
                if (m.id.equals(s)) return DataResult.success(m);
            }
            return DataResult.error(() -> "Unknown input mode '" + s + "'");
        }, m -> m.id);
    }

    /** What letting go of a hold-charge early does (C.3 input.early_release). Stored; only "fire" is implemented. */
    public enum EarlyRelease {
        FIRE("fire"), CANCEL_REFUND50("cancel_refund50");

        public final String id;

        EarlyRelease(String id) {
            this.id = id;
        }

        public static final Codec<EarlyRelease> CODEC = Codec.STRING.comapFlatMap(s -> {
            for (EarlyRelease m : values()) {
                if (m.id.equals(s)) return DataResult.success(m);
            }
            return DataResult.error(() -> "Unknown early_release '" + s + "'");
        }, m -> m.id);
    }

    /**
     * v2 "input". {@code mode} empty = the attack type's own default (blast/needle/burst tap, sphere hold_charge).
     * {@code windupTicks} -1 ({@link Common#WINDUP_DEFAULT}) = the type's default windup. Hold-to-charge attacks ignore the
     * windup (their charge is the wind-up). {@code recoveryTicks}: skills and melee are blocked this long after firing.
     */
    public record Input(Optional<InputMode> mode, int windupTicks, int minTicks, int maxTicks, float moveSpeedMult,
                        int recoveryTicks, EarlyRelease earlyRelease) {
        public static final Input DEFAULT = new Input(Optional.empty(), Common.WINDUP_DEFAULT, 0, 0, 1f, 0, EarlyRelease.FIRE);

        public static final Codec<Input> CODEC = RecordCodecBuilder.create(i -> i.group(
                InputMode.CODEC.optionalFieldOf("mode").forGetter(Input::mode),
                Codec.intRange(0, 200).optionalFieldOf("windup_ticks", Common.WINDUP_DEFAULT).forGetter(Input::windupTicks),
                Codec.intRange(0, 1200).optionalFieldOf("min_ticks", 0).forGetter(Input::minTicks),
                Codec.intRange(0, 1200).optionalFieldOf("max_ticks", 0).forGetter(Input::maxTicks),
                Codec.floatRange(0f, 1f).optionalFieldOf("move_speed_mult", 1f).forGetter(Input::moveSpeedMult),
                Codec.intRange(0, 400).optionalFieldOf("recovery_ticks", 0).forGetter(Input::recoveryTicks),
                EarlyRelease.CODEC.optionalFieldOf("early_release", EarlyRelease.FIRE).forGetter(Input::earlyRelease)
        ).apply(i, Input::new));

        public Input {
            if (maxTicks < minTicks) maxTicks = minTicks;
        }

        public Input withCharge(Charge c) {
            return new Input(mode, windupTicks, c.minTicks(), c.maxTicks(), c.moveSpeedMult(), recoveryTicks, earlyRelease);
        }

        public Input withWindup(int w) {
            return new Input(mode, w, minTicks, maxTicks, moveSpeedMult, recoveryTicks, earlyRelease);
        }
    }

    // ---- guard (stored only until the guard milestones) ----------------------------------------------------------------

    /**
     * v2 "guard": how this attack interacts with Guard (design 4.3 / C.6). Nothing applies it yet except the accessor
     * {@link KiAttack#guardMult()}; the guard milestone does. {@code deflect}: "always", "below_charge:x" or "never".
     */
    public record Guard(float mult, String deflect, float chipMult) {
        public static final Guard DEFAULT = new Guard(1f, "always", 1f);
        public static final Codec<Guard> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.floatRange(0f, 10f).optionalFieldOf("mult", 1f).forGetter(Guard::mult),
                Codec.STRING.optionalFieldOf("deflect", "always").forGetter(Guard::deflect),
                Codec.floatRange(0f, 10f).optionalFieldOf("chip_mult", 1f).forGetter(Guard::chipMult)
        ).apply(i, Guard::new));
    }

    // ---- present (everything the player sees / hears) -------------------------------------------------------------------

    /** Pose ids (data only until the pose table milestone; "" = none). */
    public record Pose(String charge, String fire) {
        public static final Pose NONE = new Pose("", "");
        public static final Codec<Pose> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.optionalFieldOf("charge", "").forGetter(Pose::charge),
                Codec.STRING.optionalFieldOf("fire", "").forGetter(Pose::fire)
        ).apply(i, Pose::new));
    }

    /**
     * In-hand orb placement. hand: main | off | both | finger | none ("" = main); anchor: palm | chest | overhead | fingertip |
     * body ("" = derived from the hand). See {@link #handId()} / {@link #anchorId()}.
     */
    public record Orb(String hand, String anchor, float spin) {
        public static final Orb DEFAULT = new Orb("main", "", 0f);
        public static final Codec<Orb> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.optionalFieldOf("hand", "main").forGetter(Orb::hand),
                Codec.STRING.optionalFieldOf("anchor", "").forGetter(Orb::anchor),
                Codec.FLOAT.optionalFieldOf("spin", 0f).forGetter(Orb::spin)
        ).apply(i, Orb::new));

        /** OrbStatePayload.HAND_* (0 main, 1 off, 2 both, 3 finger, 4 none). */
        public int handId() {
            return switch (hand) {
                case "off" -> 1;
                case "both" -> 2;
                case "finger" -> 3;
                case "none" -> 4;
                default -> 0;
            };
        }

        /** OrbStatePayload.ANCHOR_*: 0 palm, 1 chest, 2 overhead, 3 fingertip, 4 body, 5 hip. */
        public int anchorId() {
            return switch (anchor) {
                case "palm" -> 0;
                case "chest" -> 1;
                case "overhead" -> 2;
                case "fingertip" -> 3;
                case "body" -> 4;
                case "hip" -> 5;
                default -> switch (handId()) {
                    case 2 -> 1;  // both hands: in front of the chest
                    case 3 -> 3;  // finger
                    case 4 -> 4;  // none: body
                    default -> 0; // palm
                };
            };
        }
    }

    /** present.sounds. fire / impact have the old v1 defaults; charge_loop / loop are optional. */
    public record Sounds(Optional<ResourceLocation> chargeLoop, ResourceLocation fire, Optional<ResourceLocation> loop,
                         ResourceLocation impact) {
        public static final ResourceLocation DEFAULT_FIRE = ResourceLocation.parse("minecraft:entity.blaze.shoot");
        public static final ResourceLocation DEFAULT_IMPACT = ResourceLocation.parse("minecraft:entity.firework_rocket.blast");
        public static final Sounds DEFAULT = new Sounds(Optional.empty(), DEFAULT_FIRE, Optional.empty(), DEFAULT_IMPACT);

        public static final Codec<Sounds> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.optionalFieldOf("charge_loop").forGetter(Sounds::chargeLoop),
                ResourceLocation.CODEC.optionalFieldOf("fire", DEFAULT_FIRE).forGetter(Sounds::fire),
                ResourceLocation.CODEC.optionalFieldOf("loop").forGetter(Sounds::loop),
                ResourceLocation.CODEC.optionalFieldOf("impact", DEFAULT_IMPACT).forGetter(Sounds::impact)
        ).apply(i, Sounds::new));
    }

    /**
     * v2 "present". Replaces the v1 "visual" block (v1 fire_sound / impact_sound live in {@link Sounds} now). The accessor
     * methods below keep the old Visual API (color, coreColor, usesPlayerColor, fireSoundEvent ...).
     * shape / trail / impact are ids the renderers switch on; shake (0..4, 1 = strong) and hitstop (ticks) are feel levers
     * the client reads on impact; fovPunch is degrees of FOV added briefly for the caster.
     */
    public record Present(String shape, int color, int coreColor, String colorSource, String trail, String impact,
                          float shake, int hitstop, float fovPunch, Pose pose, Orb orb, Sounds sounds,
                          ResourceLocation impactParticle) {
        /** color_source: "player" = tinted with the shooter's ki colour (default), "attack" = always the attack's own colours. */
        public static final String SOURCE_PLAYER = "player";
        public static final String SOURCE_ATTACK = "attack";

        public static final Present DEFAULT = new Present("", 0x4FC3FF, 0xFFFFFF, SOURCE_PLAYER, "", "", 0f, 0, 0f,
                Pose.NONE, Orb.DEFAULT, Sounds.DEFAULT, KI_SPARK_ID);

        public static final Codec<Present> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.optionalFieldOf("shape", "").forGetter(Present::shape),
                COLOR.optionalFieldOf("color", DEFAULT.color()).forGetter(Present::color),
                COLOR.optionalFieldOf("core_color", DEFAULT.coreColor()).forGetter(Present::coreColor),
                Codec.STRING.optionalFieldOf("color_source", SOURCE_PLAYER).forGetter(Present::colorSource),
                Codec.STRING.optionalFieldOf("trail", "").forGetter(Present::trail),
                Codec.STRING.optionalFieldOf("impact", "").forGetter(Present::impact),
                Codec.floatRange(0f, 4f).optionalFieldOf("shake", 0f).forGetter(Present::shake),
                Codec.intRange(0, 40).optionalFieldOf("hitstop", 0).forGetter(Present::hitstop),
                Codec.floatRange(0f, 30f).optionalFieldOf("fov_punch", 0f).forGetter(Present::fovPunch),
                Pose.CODEC.optionalFieldOf("pose", Pose.NONE).forGetter(Present::pose),
                Orb.CODEC.optionalFieldOf("orb", Orb.DEFAULT).forGetter(Present::orb),
                Sounds.CODEC.optionalFieldOf("sounds", Sounds.DEFAULT).forGetter(Present::sounds),
                ResourceLocation.CODEC.optionalFieldOf("impact_particle", KI_SPARK_ID).forGetter(Present::impactParticle)
        ).apply(i, Present::new));

        public boolean usesPlayerColor() {
            return !SOURCE_ATTACK.equals(colorSource);
        }

        /** KiImpactPayload.STYLE_* for this attack's impact visual ("explosion_ring", "ring", "pierce", otherwise default). */
        public int impactStyle() {
            return switch (impact) {
                case "explosion_ring" -> KiImpactPayload.STYLE_EXPLOSION_RING;
                case "ring" -> KiImpactPayload.STYLE_RING;
                case "pierce" -> KiImpactPayload.STYLE_PIERCE;
                case "spark_small" -> KiImpactPayload.STYLE_SPARK_SMALL;
                case "slice" -> KiImpactPayload.STYLE_SLICE;
                default -> KiImpactPayload.STYLE_DEFAULT;
            };
        }

        public ResourceLocation fireSound() {
            return sounds.fire();
        }

        public ResourceLocation impactSound() {
            return sounds.impact();
        }

        /** Registered sound if the id exists, otherwise a variable-range event (plays whatever sounds.json maps it to). */
        public SoundEvent fireSoundEvent() {
            return soundOf(sounds.fire());
        }

        public SoundEvent impactSoundEvent() {
            return soundOf(sounds.impact());
        }

        public static SoundEvent soundOf(ResourceLocation id) {
            return BuiltInRegistries.SOUND_EVENT.getOptional(id).orElseGet(() -> SoundEvent.createVariableRangeEvent(id));
        }
    }

    /** The v1 "visual" block, kept only as an accepted input shape (converted to {@link Present} by normalize). */
    public record LegacyVisual(int color, int coreColor, ResourceLocation fireSound, ResourceLocation impactSound,
                               ResourceLocation impactParticle, String colorSource) {
        public static final Codec<LegacyVisual> CODEC = RecordCodecBuilder.create(i -> i.group(
                COLOR.optionalFieldOf("color", Present.DEFAULT.color()).forGetter(LegacyVisual::color),
                COLOR.optionalFieldOf("core_color", Present.DEFAULT.coreColor()).forGetter(LegacyVisual::coreColor),
                ResourceLocation.CODEC.optionalFieldOf("fire_sound", Sounds.DEFAULT_FIRE).forGetter(LegacyVisual::fireSound),
                ResourceLocation.CODEC.optionalFieldOf("impact_sound", Sounds.DEFAULT_IMPACT).forGetter(LegacyVisual::impactSound),
                ResourceLocation.CODEC.optionalFieldOf("impact_particle", KI_SPARK_ID).forGetter(LegacyVisual::impactParticle),
                Codec.STRING.optionalFieldOf("color_source", Present.SOURCE_PLAYER).forGetter(LegacyVisual::colorSource)
        ).apply(i, LegacyVisual::new));

        Present toPresent() {
            Present d = Present.DEFAULT;
            return new Present(d.shape(), color, coreColor, colorSource, d.trail(), d.impact(), d.shake(), d.hitstop(),
                    d.fovPunch(), d.pose(), d.orb(), new Sounds(Optional.empty(), fireSound, Optional.empty(), impactSound),
                    impactParticle);
        }
    }

    // ---- Common ---------------------------------------------------------------------------------------------------------

    /**
     * Fields shared by every ki attack type (schema v2). Required: name, cost, damage. {@code projectile} is optional here
     * (hitscan / radial types have none); projectile types demand it through {@link #REQUIRING_PROJECTILE}.
     * The old accessors still work: {@link #projectile()}, {@link #charge()}, {@link #visual()}, {@link #windupTicks()}.
     */
    public record Common(String name, Optional<String> description, Cost cost, Input input, Damage damage, Guard guard,
                         Present present, Optional<ProjectileSpec> projectileSpec, int cooldown, float knockback,
                         boolean blockDamage, int unlockLevel) {
        /** "windup_ticks" not present in the JSON: the attack type's own default applies. */
        public static final int WINDUP_DEFAULT = -1;

        /**
         * 15 JSON keys = 12 v2 fields + the 3 v1 keys ("charge", "windup_ticks", "visual") that {@link #normalize} folds into
         * input / present. Encoding writes v2 only (the v1 getters return empty), and decoding v2 output is the identity.
         */
        public static final MapCodec<Common> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.fieldOf("name").forGetter(Common::name),
                Codec.STRING.optionalFieldOf("description").forGetter(Common::description),
                Cost.CODEC.fieldOf("cost").forGetter(Common::cost),
                Input.CODEC.optionalFieldOf("input").forGetter(c -> Optional.of(c.input())),
                Damage.CODEC.fieldOf("damage").forGetter(Common::damage),
                Guard.CODEC.optionalFieldOf("guard").forGetter(c -> Optional.of(c.guard())),
                Present.CODEC.optionalFieldOf("present").forGetter(c -> Optional.of(c.present())),
                ProjectileSpec.CODEC.optionalFieldOf("projectile").forGetter(Common::projectileSpec),
                Codec.intRange(0, 72000).optionalFieldOf("cooldown", 0).forGetter(Common::cooldown),
                Codec.floatRange(0f, 10f).optionalFieldOf("knockback", 0.4f).forGetter(Common::knockback),
                Codec.BOOL.optionalFieldOf("block_damage", false).forGetter(Common::blockDamage),
                // K7: character level needed to cast it (0 = no gate); checked by KiSkills on press.
                Codec.intRange(0, 1000).optionalFieldOf("unlock_level", 0).forGetter(Common::unlockLevel),
                // ---- v1 input shapes (decode only) ----
                Charge.CODEC.optionalFieldOf("charge").forGetter(c -> Optional.<Charge>empty()),
                Codec.intRange(0, 200).optionalFieldOf("windup_ticks").forGetter(c -> Optional.<Integer>empty()),
                LegacyVisual.CODEC.optionalFieldOf("visual").forGetter(c -> Optional.<LegacyVisual>empty())
        ).apply(i, Common::normalize));

        /** {@link #MAP_CODEC} that also fails when there is no "projectile" (blast, sphere, later barrage / homing ...). */
        public static final MapCodec<Common> REQUIRING_PROJECTILE = MAP_CODEC.flatXmap(
                c -> c.projectileSpec().isPresent() ? DataResult.success(c)
                        : DataResult.error(() -> "This ki attack type needs a \"projectile\" block"),
                c -> DataResult.success(c));

        /** v1 -> v2: charge -> input.(min/max/move_speed), windup_ticks -> input.windup_ticks, visual -> present. */
        private static Common normalize(String name, Optional<String> description, Cost cost, Optional<Input> input,
                                        Damage damage, Optional<Guard> guard, Optional<Present> present,
                                        Optional<ProjectileSpec> projectile, int cooldown, float knockback,
                                        boolean blockDamage, int unlockLevel, Optional<Charge> legacyCharge, Optional<Integer> legacyWindup,
                                        Optional<LegacyVisual> legacyVisual) {
            Input in = input.orElse(Input.DEFAULT);
            if (input.isEmpty()) { // an explicit v2 "input" wins over the v1 keys
                if (legacyCharge.isPresent()) in = in.withCharge(legacyCharge.get());
                if (legacyWindup.isPresent()) in = in.withWindup(legacyWindup.get());
            }
            Present pr = present.orElseGet(() -> legacyVisual.map(LegacyVisual::toPresent).orElse(Present.DEFAULT));
            return new Common(name, description, cost, in, damage, guard.orElse(Guard.DEFAULT), pr, projectile, cooldown,
                    knockback, blockDamage, unlockLevel);
        }

        /** The projectile block, or a harmless default for types that have none (never null). */
        public ProjectileSpec projectile() {
            return projectileSpec.orElse(ProjectileSpec.DEFAULT);
        }

        public Charge charge() {
            return new Charge(input.minTicks(), input.maxTicks(), input.moveSpeedMult());
        }

        /** Old name of {@link #present()}. */
        public Present visual() {
            return present;
        }

        public int windupTicks() {
            return input.windupTicks();
        }
    }
}

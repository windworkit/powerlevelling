package com.powerlevelling.combat;

import java.util.Map;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;

/**
 * One melee move from the powerlevelling:combat_move datapack registry (design 4.2). The registry key is the move id
 * (data/&lt;ns&gt;/powerlevelling/combat_move/&lt;id&gt;.json). All timings in ticks.
 * <p>
 * Timeline: STARTUP ({@code startup}) -> ACTIVE ({@code active}, hit detection every tick) -> RECOVERY ({@code recovery}).
 * From recovery tick {@code cancel_from} (-1 = never) a follow-up input starts the next move at once ("cancel window").
 * {@code entry}: input that starts this move from neutral ({@code requires} ground / air / any). {@code follow_ups}:
 * input -> next move while this move is in its cancel window or within the chain grace after it.
 *
 * @param entry       input that starts this move with no chain running (empty = only reachable as a follow-up)
 * @param requires    ground / air / any, checked when the move starts
 * @param startup     wind-up ticks
 * @param active      ticks the hitbox is live
 * @param recovery    ticks after the hitbox
 * @param cancelFrom  recovery tick from which a follow-up / skill may cancel the move, -1 = no cancel window
 * @param damage      base damage before Martial / Strength / combo scaling
 * @param hitstun     ticks the target is stunned
 * @param hitbox      oriented box in front of the attacker
 * @param knockback   push applied to the target (blocks; LivingEntity#knockback strength + upward velocity)
 * @param staminaCost combat stamina spent when the move starts
 * @param pose        KiPoseTable pose id played by the attacker (empty = vanilla swing only)
 * @param sounds      swing / hit sound event ids (optional)
 * @param followUps   input -> next move id
 */
public record CombatMove(Optional<MeleeInput> entry, Requires requires, int startup, int active, int recovery, int cancelFrom,
                         float damage, int hitstun, Hitbox hitbox, Knockback knockback, float staminaCost, String pose,
                         Sounds sounds, Map<MeleeInput, ResourceLocation> followUps) {

    public enum Requires implements StringRepresentable {
        ANY("any"), GROUND("ground"), AIR("air");

        public static final StringRepresentable.EnumCodec<Requires> CODEC = StringRepresentable.fromEnum(Requires::values);
        private final String name;

        Requires(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }

        public boolean test(boolean onGround) {
            return this == ANY || (this == GROUND) == onGround;
        }
    }

    /**
     * Box in the attacker's yaw frame: from {@code forward} to {@code reach} blocks ahead of the feet, {@code width} wide,
     * {@code height} tall starting {@code y} above the feet.
     */
    public record Hitbox(float reach, float width, float height, float forward, float y) {
        public static final Hitbox DEFAULT = new Hitbox(3.0f, 1.4f, 2.0f, 0f, 0f);
        public static final Codec<Hitbox> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.floatRange(0f, 16f).optionalFieldOf("reach", 3.0f).forGetter(Hitbox::reach),
                Codec.floatRange(0f, 16f).optionalFieldOf("width", 1.4f).forGetter(Hitbox::width),
                Codec.floatRange(0f, 16f).optionalFieldOf("height", 2.0f).forGetter(Hitbox::height),
                Codec.floatRange(-4f, 16f).optionalFieldOf("forward", 0f).forGetter(Hitbox::forward),
                Codec.floatRange(-8f, 8f).optionalFieldOf("y", 0f).forGetter(Hitbox::y)
        ).apply(i, Hitbox::new));
    }

    /** {@code forward}: horizontal knockback strength along the attacker's facing; {@code up}: added upward velocity. */
    public record Knockback(float forward, float up) {
        public static final Knockback DEFAULT = new Knockback(0.15f, 0f);
        public static final Codec<Knockback> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.floatRange(0f, 10f).optionalFieldOf("forward", 0.15f).forGetter(Knockback::forward),
                Codec.floatRange(-10f, 10f).optionalFieldOf("up", 0f).forGetter(Knockback::up)
        ).apply(i, Knockback::new));
    }

    public record Sounds(Optional<ResourceLocation> swing, Optional<ResourceLocation> hit) {
        public static final Sounds NONE = new Sounds(Optional.empty(), Optional.empty());
        public static final Codec<Sounds> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.optionalFieldOf("swing").forGetter(Sounds::swing),
                ResourceLocation.CODEC.optionalFieldOf("hit").forGetter(Sounds::hit)
        ).apply(i, Sounds::new));
    }

    public static final Codec<CombatMove> CODEC = RecordCodecBuilder.create(i -> i.group(
            MeleeInput.CODEC.optionalFieldOf("entry").forGetter(CombatMove::entry),
            Requires.CODEC.optionalFieldOf("requires", Requires.ANY).forGetter(CombatMove::requires),
            Codec.intRange(0, 200).fieldOf("startup").forGetter(CombatMove::startup),
            Codec.intRange(1, 200).fieldOf("active").forGetter(CombatMove::active),
            Codec.intRange(0, 200).fieldOf("recovery").forGetter(CombatMove::recovery),
            Codec.intRange(-1, 200).optionalFieldOf("cancel_from", -1).forGetter(CombatMove::cancelFrom),
            Codec.floatRange(0f, 10000f).fieldOf("damage").forGetter(CombatMove::damage),
            Codec.intRange(0, 200).optionalFieldOf("hitstun", 12).forGetter(CombatMove::hitstun),
            Hitbox.CODEC.optionalFieldOf("hitbox", Hitbox.DEFAULT).forGetter(CombatMove::hitbox),
            Knockback.CODEC.optionalFieldOf("knockback", Knockback.DEFAULT).forGetter(CombatMove::knockback),
            Codec.floatRange(0f, 1000f).optionalFieldOf("stamina_cost", 0f).forGetter(CombatMove::staminaCost),
            Codec.STRING.optionalFieldOf("pose", "").forGetter(CombatMove::pose),
            Sounds.CODEC.optionalFieldOf("sounds", Sounds.NONE).forGetter(CombatMove::sounds),
            Codec.unboundedMap(MeleeInput.CODEC, ResourceLocation.CODEC).optionalFieldOf("follow_ups", Map.of())
                    .forGetter(CombatMove::followUps)
    ).apply(i, CombatMove::new));

    /** Total length in ticks (startup + active + recovery). */
    public int totalTicks() {
        return startup + active + recovery;
    }
}

package com.powerlevelling.character;

import com.mojang.serialization.Codec;

import net.minecraft.util.StringRepresentable;

/** The modifier channels a race / ki nature can touch (Addendum B.1). Serialized as the lowercase name. */
public enum Channel implements StringRepresentable {
    MELEE_DAMAGE("melee_damage"),
    KI_DAMAGE("ki_damage"),
    MAX_KI("max_ki"),
    KI_REGEN("ki_regen"),
    KI_CHARGE_RATE("ki_charge_rate"),
    KI_COST("ki_cost"),
    MAX_HP("max_hp"),
    DAMAGE_TAKEN("damage_taken"),
    DEFENSE_CAP("defense_cap"),
    MOVE_SPEED("move_speed"),
    MAX_STAMINA("max_stamina"),
    STAMINA_REGEN("stamina_regen"),
    EXP_GAIN("exp_gain"),
    MASTERY_XP("mastery_xp"),
    SPEED_CAP("speed_cap");

    public static final Codec<Channel> CODEC = StringRepresentable.fromEnum(Channel::values);

    public final String id;

    Channel(String id) {
        this.id = id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}

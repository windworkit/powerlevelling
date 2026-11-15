package com.powerlevelling.combat;

import net.minecraft.util.StringRepresentable;

/**
 * Melee inputs a combat_move can be started / chained by. ORDER IS THE WIRE FORMAT (MeleeInputPayload sends the ordinal):
 * append only. JSON uses the lowercase names ("light", "heavy").
 */
public enum MeleeInput implements StringRepresentable {
    LIGHT("light"),
    HEAVY("heavy");

    public static final StringRepresentable.EnumCodec<MeleeInput> CODEC = StringRepresentable.fromEnum(MeleeInput::values);

    private final String name;

    MeleeInput(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    /** Null for an unknown id (bad packet), so the server can reject it. */
    public static MeleeInput fromOrdinal(int id) {
        MeleeInput[] v = values();
        return id >= 0 && id < v.length ? v[id] : null;
    }
}

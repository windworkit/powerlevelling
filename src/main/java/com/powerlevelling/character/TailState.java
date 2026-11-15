package com.powerlevelling.character;

import com.mojang.serialization.Codec;

import net.minecraft.util.StringRepresentable;

/** NONE: this character has no tail. PRESENT: has one (wrapped or free). LOST: cut off, regrows at tailRegrowAt. */
public enum TailState implements StringRepresentable {
    NONE("none"), PRESENT("present"), LOST("lost");

    public static final Codec<TailState> CODEC = StringRepresentable.fromEnum(TailState::values);
    private final String id;

    TailState(String id) {
        this.id = id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}

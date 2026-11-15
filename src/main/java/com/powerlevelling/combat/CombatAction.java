package com.powerlevelling.combat;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Every abstract input the combat system understands (design 4.5 / 8.8). Keyboard, mouse and a future controller
 * adapter only have to emit these. ORDER IS THE WIRE FORMAT: append new entries at the end, never reorder.
 * Most are unused until later milestones; the server just acks them for now.
 */
public enum CombatAction {
    STANCE_TOGGLE,
    LIGHT,
    HEAVY,
    GUARD,
    DASH,
    CHARGE_KI,
    SKILL_1,
    SKILL_2,
    SKILL_3,
    SKILL_4,
    LOADOUT,
    LOCK_ON,
    TRANSFORM,
    TURBO;

    public static final StreamCodec<ByteBuf, CombatAction> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(CombatAction::fromOrdinal, CombatAction::ordinal);

    /** Returns null for an unknown id so the server can reject it instead of guessing. */
    public static CombatAction fromOrdinal(int id) {
        CombatAction[] v = values();
        return id >= 0 && id < v.length ? v[id] : null;
    }

    public boolean isSkill() {
        return this == SKILL_1 || this == SKILL_2 || this == SKILL_3 || this == SKILL_4;
    }

    /** SKILL_1..SKILL_4 for index 0..3. */
    public static CombatAction skill(int index) {
        return values()[SKILL_1.ordinal() + index];
    }
}

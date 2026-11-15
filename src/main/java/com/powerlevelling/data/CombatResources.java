package com.powerlevelling.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Per-player combat resources. Mutable; saved through {@link #CODEC}. Not copied on death. */
public class CombatResources {
    public static final float BASE_MAX_STAMINA = 100f;
    public static final float MAX_ENDURANCE = 100f;

    public static final Codec<CombatResources> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.FLOAT.optionalFieldOf("ki", 1000f).forGetter(r -> r.ki),
            Codec.FLOAT.optionalFieldOf("stamina", BASE_MAX_STAMINA).forGetter(r -> r.stamina),
            Codec.FLOAT.optionalFieldOf("endurance", MAX_ENDURANCE).forGetter(r -> r.endurance),
            Codec.BOOL.optionalFieldOf("ki_break", false).forGetter(r -> r.kiBreak)
    ).apply(i, CombatResources::new));

    public float ki;
    public float stamina;
    public float endurance;
    public boolean kiBreak;

    /** Defaults: full ki (at base max), full stamina, full endurance. */
    public CombatResources() {
        this(1000f, BASE_MAX_STAMINA, MAX_ENDURANCE, false);
    }

    public CombatResources(float ki, float stamina, float endurance, boolean kiBreak) {
        this.ki = ki;
        this.stamina = stamina;
        this.endurance = endurance;
        this.kiBreak = kiBreak;
    }

    /**
     * Raw max stamina without race/nature. Use StatEffects.maxStamina instead.
     * @deprecated since C3
     */
    @Deprecated
    public float maxStamina(float bonus) {
        return 50f + 50f * endurance / MAX_ENDURANCE + bonus;
    }
}

package com.powerlevelling.character;

import java.util.List;

import com.powerlevelling.data.KiProgression.Stat;

/**
 * Race + ki nature modifiers combined into plain arrays (computed server-side at login/change; the client computes
 * the same from the synced registries). mult = product, flat = sum, stat scaling = product.
 * effect(stat) = (1 + perPoint * points * scaling(stat)) * mult(channel) + flat(channel); caps are clamped by the caller.
 */
public final class CharacterMods {
    public static final CharacterMods NEUTRAL = new CharacterMods(newMult(), new float[Channel.values().length], newScaling());

    private final float[] mult;
    private final float[] flat;
    private final float[] scaling;

    private CharacterMods(float[] mult, float[] flat, float[] scaling) {
        this.mult = mult;
        this.flat = flat;
        this.scaling = scaling;
    }

    private static float[] newMult() {
        float[] a = new float[Channel.values().length];
        java.util.Arrays.fill(a, 1f);
        return a;
    }

    private static float[] newScaling() {
        float[] a = new float[Stat.values().length];
        java.util.Arrays.fill(a, 1f);
        return a;
    }

    public static CharacterMods combine(List<Modifiers> sources) {
        float[] m = newMult();
        float[] f = new float[Channel.values().length];
        float[] s = newScaling();
        for (Modifiers mod : sources) {
            if (mod == null) continue;
            mod.mult().forEach((c, v) -> m[c.ordinal()] *= v);
            mod.flat().forEach((c, v) -> f[c.ordinal()] += v);
            mod.statScaling().forEach((st, v) -> s[st.ordinal()] *= v);
        }
        return new CharacterMods(m, f, s);
    }

    public float mult(Channel c) {
        return mult[c.ordinal()];
    }

    public float flat(Channel c) {
        return flat[c.ordinal()];
    }

    public float scaling(Stat s) {
        return scaling[s.ordinal()];
    }

    /** True when this channel is at its neutral value (mult 1, flat 0). */
    public boolean isNeutral(Channel c) {
        return mult[c.ordinal()] == 1f && flat[c.ordinal()] == 0f;
    }
}

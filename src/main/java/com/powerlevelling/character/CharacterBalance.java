package com.powerlevelling.character;

import java.util.ArrayList;
import java.util.List;

import com.powerlevelling.progression.StatEffects;

import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;

/**
 * Developer sanity check for the B.3.2 budget rule: every enabled ki nature should land within +-25% of Balanced on
 * (damage multiplier x effective-HP multiplier) at 0 / 100 / 200 points. Pure maths over the loaded registries
 * (nature modifiers only; race is ignored). Used by {@code /powerlevelling character balance}.
 *
 * <p>Model: every stat sits at P points. damage = the better of melee and ki damage multiplier (a player picks one
 * style); effective HP = max HP / damage-taken multiplier. Ki cost, regen, stamina and speed are not scored.
 */
public final class CharacterBalance {
    public static final int[] LEVELS = {0, 100, 200};
    public static final double LIMIT = 0.25;

    /** One nature at one point level. rel* values are relative to Balanced (1.0 = equal). */
    public record Row(ResourceLocation nature, int points, boolean kiStyle, double relDamage, double relEhp,
                      double relTotal, boolean within) {
    }

    private record Score(double damage, boolean kiStyle, double ehp) {
    }

    private CharacterBalance() {
    }

    private static Score score(CharacterMods m, int points, double baseBonusHealth) {
        double melee = StatEffects.meleeMultiplier(points, m);
        double ki = StatEffects.kiDamageMultiplier(points, m);
        double hp = StatEffects.maxHp(points, m, baseBonusHealth);
        double taken = Math.max(0.01, StatEffects.damageTakenMultiplier(points, m));
        return new Score(Math.max(melee, ki), ki > melee, hp / taken);
    }

    /** Rows for every enabled nature x level; empty if the Balanced reference nature is not loaded. */
    public static List<Row> compute(RegistryAccess access, double baseBonusHealth) {
        KiNature reference = CharacterRegistries.getNature(access, CharacterData.DEFAULT_NATURE);
        if (reference == null) return List.of();
        CharacterMods refMods = CharacterMods.combine(List.of(reference.modifiers()));

        List<Row> rows = new ArrayList<>();
        for (var e : CharacterRegistries.allNatures(access)) {
            if (!e.value().enabled()) continue;
            CharacterMods mods = CharacterMods.combine(List.of(e.value().modifiers()));
            for (int points : LEVELS) {
                Score s = score(mods, points, baseBonusHealth);
                Score ref = score(refMods, points, baseBonusHealth);
                double relDamage = s.damage() / ref.damage();
                double relEhp = s.ehp() / ref.ehp();
                double total = relDamage * relEhp;
                rows.add(new Row(e.id(), points, s.kiStyle(), relDamage, relEhp, total, Math.abs(total - 1.0) <= LIMIT));
            }
        }
        return rows;
    }
}

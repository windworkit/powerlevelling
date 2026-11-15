package com.powerlevelling.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.powerlevelling.character.Channel;
import com.powerlevelling.character.Modifiers;
import com.powerlevelling.character.Race;
import com.powerlevelling.data.KiProgression.Stat;

import net.minecraft.network.chat.Component;

/** Turns race / nature modifiers into the green (good) and red (bad) lines of the character screen. */
final class CharacterModText {
    record Line(Component text, boolean good) {
    }

    private static final String P = "gui.powerlevelling.character.";

    private CharacterModText() {
    }

    /** Lower is better for these channels. */
    private static boolean lowerIsBetter(Channel c) {
        return c == Channel.KI_COST || c == Channel.DAMAGE_TAKEN;
    }

    /** Flat values of these channels are fractions shown as percent points. */
    private static boolean flatIsPercent(Channel c) {
        return c == Channel.DEFENSE_CAP || c == Channel.SPEED_CAP;
    }

    private static String num(double v) {
        double r = Math.round(v * 10.0) / 10.0;
        return r == Math.rint(r) ? Long.toString((long) r) : String.format(Locale.ROOT, "%.1f", r);
    }

    private static String signed(double v) {
        return (v > 0 ? "+" : "") + num(v);
    }

    /**
     * Lines for {@code m}; with a {@code base} they are the differences to it (multipliers as ratio, flats as difference).
     * Order: channels in enum order, then stat scalings.
     */
    static List<Line> lines(Modifiers m, Modifiers base) {
        List<Line> out = new ArrayList<>();
        for (Channel c : Channel.values()) {
            double ratio = m.mult().getOrDefault(c, 1f) / (double) (base == null ? 1f : base.mult().getOrDefault(c, 1f));
            double pct = (ratio - 1.0) * 100.0;
            if (Math.abs(pct) >= 0.05) {
                out.add(new Line(Component.translatable(P + "mod.line", signed(pct) + "%", Component.translatable(P + "mod." + c.id)),
                        lowerIsBetter(c) ? pct < 0 : pct > 0));
            }
            double flat = m.flat().getOrDefault(c, 0f) - (base == null ? 0f : base.flat().getOrDefault(c, 0f));
            double shown = flatIsPercent(c) ? flat * 100.0 : flat;
            if (Math.abs(shown) >= 0.05) {
                out.add(new Line(Component.translatable(P + "mod.line", signed(shown) + (flatIsPercent(c) ? "%" : ""),
                        Component.translatable(P + "mod." + c.id)), lowerIsBetter(c) ? flat < 0 : flat > 0));
            }
        }
        for (Stat s : Stat.values()) {
            double ratio = m.statScaling().getOrDefault(s, 1f) / (double) (base == null ? 1f : base.statScaling().getOrDefault(s, 1f));
            double pct = (ratio - 1.0) * 100.0;
            if (Math.abs(pct) >= 0.05) {
                out.add(new Line(Component.translatable(P + "mod.scaling", signed(pct) + "%",
                        Component.translatable("stat.powerlevelling." + s.id)), pct > 0));
            }
        }
        return out;
    }

    /** Zenkai lines of a race. */
    static List<Line> zenkai(Race.Zenkai z) {
        List<Line> out = new ArrayList<>();
        if (z.bonusPoints() != 0) {
            out.add(new Line(Component.translatable(P + (Math.abs(z.bonusPoints()) == 1 ? "zenkai.point" : "zenkai.points"),
                    (z.bonusPoints() > 0 ? "+" : "") + z.bonusPoints()), z.bonusPoints() > 0));
        }
        double cd = (z.cooldownMult() - 1.0) * 100.0;
        if (Math.abs(cd) >= 0.05) {
            out.add(new Line(Component.translatable(P + "zenkai.cooldown", signed(cd) + "%"), cd < 0));
        }
        double xp = (z.expBonusMult() - 1.0) * 100.0;
        if (Math.abs(xp) >= 0.05) {
            out.add(new Line(Component.translatable(P + "zenkai.exp", signed(xp) + "%"), xp > 0));
        }
        return out;
    }
}

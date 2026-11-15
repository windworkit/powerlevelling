package com.powerlevelling.data;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.powerlevelling.PowerLevelling;
import com.powerlevelling.ki.KiAttackRegistry;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Per-player progression (Addendum A): character level / EXP, stat points, the 6 stats, the 4 mastery tracks,
 * race/form and the Zenkai cooldown. Mutable; saved through {@link #CODEC} (every new field is optional,
 * so M1 saves still load).
 */
public class KiProgression {
    public static final int MAX_LEVEL = 50; // mastery track cap
    public static final int POINTS_PER_LEVEL = 3;
    public static final int POINTS_PER_MASTERY_MILESTONE = 1;
    public static final int MASTERY_MILESTONE_EVERY = 5;
    /** Hard sanity bound for stats loaded from disk; the real cap is the statCap config. */
    private static final int STAT_HARD_MAX = 1000;

    /** Skill slots 1-4 (index 0-3). */
    public static final int LOADOUT_SLOTS = 4;
    /** Default loadout of a NEW player (K1): ki_blast, meteor_core, pinpoint_lance, repulse_wave. Saved loadouts are untouched. */
    private static final List<String> DEFAULT_LOADOUT = List.of(
            KiAttackRegistry.DEFAULT_SLOT_1.toString(), KiAttackRegistry.DEFAULT_SLOT_2.toString(),
            KiAttackRegistry.DEFAULT_SLOT_3.toString(), KiAttackRegistry.DEFAULT_SLOT_4.toString());

    // ---- per-attack mastery (K0, C.8): uses -> tier 1-4, each tier +5% damage and -5% ki cost ----
    /** Uses needed for tier 1, 2, 3, 4. */
    public static final int[] ATTACK_TIER_USES = {50, 150, 400, 1000};
    public static final float TIER_DAMAGE_PER_TIER = 0.05f;
    public static final float TIER_COST_PER_TIER = 0.05f;

    /** Tier (0..4) for a use count. Shared with the client cache. */
    public static int tierForUses(int uses) {
        int tier = 0;
        for (int need : ATTACK_TIER_USES) {
            if (uses >= need) tier++;
        }
        return tier;
    }

    public static float tierDamageMult(int tier) {
        return 1f + TIER_DAMAGE_PER_TIER * tier;
    }

    public static float tierCostMult(int tier) {
        return 1f - TIER_COST_PER_TIER * tier;
    }

    public static final ResourceLocation DEFAULT_RACE = ResourceLocation.fromNamespaceAndPath(PowerLevelling.MODID, "human");
    public static final ResourceLocation DEFAULT_FORM = ResourceLocation.fromNamespaceAndPath(PowerLevelling.MODID, "base");

    /** Mastery tracks. Ki Capacity was replaced by Mobility (old "ki_capacity" saves are simply ignored). */
    public enum Track {
        MARTIAL("martial"),
        KI_CONTROL("ki_control"),
        TOUGHNESS("toughness"),
        MOBILITY("mobility");

        public final String id;

        Track(String id) {
            this.id = id;
        }

        /** Returns null for an unknown id. */
        public static Track byId(String id) {
            for (Track t : values()) {
                if (t.id.equals(id)) return t;
            }
            return null;
        }

        public static List<String> ids() {
            return Arrays.stream(values()).map(t -> t.id).toList();
        }
    }

    /** The six spendable stats (Addendum A.4). */
    public enum Stat {
        STRENGTH("strength"),
        KI_POWER("ki_power"),
        KI_CAPACITY("ki_capacity"),
        VITALITY("vitality"),
        DEFENSE("defense"),
        SPEED("speed");

        public final String id;

        Stat(String id) {
            this.id = id;
        }

        /** Returns null for an unknown id. */
        public static Stat byId(String id) {
            for (Stat s : values()) {
                if (s.id.equals(id)) return s;
            }
            return null;
        }

        public static List<String> ids() {
            return Arrays.stream(values()).map(s -> s.id).toList();
        }
    }

    /** One mastery track: level, xp towards the next level, and how many 5-level milestones already paid a point. */
    public static class MasteryTrack {
        public static final Codec<MasteryTrack> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("level", 0).forGetter(t -> t.level),
                Codec.INT.optionalFieldOf("xp", 0).forGetter(t -> t.xp),
                Codec.INT.optionalFieldOf("milestones", 0).forGetter(t -> t.milestones)
        ).apply(i, MasteryTrack::new));

        public int level;
        public int xp;
        /** Milestones (level/5) already rewarded; prevents double-paying after commands or level changes. */
        public int milestones;

        public MasteryTrack(int level, int xp, int milestones) {
            this.level = Mth.clamp(level, 0, MAX_LEVEL);
            this.xp = Math.max(0, xp);
            this.milestones = Math.max(0, milestones);
        }
    }

    // Maps are keyed by id so adding/removing entries later doesn't break old saves.
    public static final Codec<KiProgression> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.optionalFieldOf("race", DEFAULT_RACE).forGetter(p -> p.race),
            ResourceLocation.CODEC.optionalFieldOf("form", DEFAULT_FORM).forGetter(p -> p.form),
            Codec.unboundedMap(Codec.STRING, MasteryTrack.CODEC).optionalFieldOf("tracks", Map.of()).forGetter(KiProgression::trackMap),
            Codec.INT.optionalFieldOf("level", 1).forGetter(p -> p.level),
            Codec.INT.optionalFieldOf("exp", 0).forGetter(p -> p.exp),
            Codec.INT.optionalFieldOf("unspent_points", 0).forGetter(p -> p.unspentPoints),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("stats", Map.of()).forGetter(KiProgression::statMap),
            Codec.LONG.optionalFieldOf("zenkai_cooldown_until", 0L).forGetter(p -> p.zenkaiCooldownUntil),
            // Added in M3. Absent in older saves -> the default loadout.
            Codec.STRING.listOf().optionalFieldOf("loadout", DEFAULT_LOADOUT).forGetter(KiProgression::loadoutList),
            // Added in K0 (per-attack mastery). Absent in older saves -> no uses yet.
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("attack_uses", Map.of()).forGetter(p -> p.attackUses)
    ).apply(i, KiProgression::new));

    // DEPRECATED mirror: the source of truth is CharacterData.race (character package); CharacterService keeps this in sync.
    public ResourceLocation race;
    public ResourceLocation form;
    public int level = 1;
    public int exp = 0;
    public int unspentPoints = 0;
    /** Game time (ticks) when Zenkai can trigger again; 0 = ready. */
    public long zenkaiCooldownUntil = 0L;
    private final MasteryTrack[] tracks = new MasteryTrack[Track.values().length];
    private final int[] stats = new int[Stat.values().length]; // Stat.ordinal() order
    /** ki_attack ids for skill slots 1-4; null = empty slot. */
    private final ResourceLocation[] loadout = new ResourceLocation[LOADOUT_SLOTS];
    /** Uses per ki attack id (string key so unknown / removed attacks survive in the save). */
    private final Map<String, Integer> attackUses = new HashMap<>();

    public KiProgression() {
        this(DEFAULT_RACE, DEFAULT_FORM, Map.of(), 1, 0, 0, Map.of(), 0L, DEFAULT_LOADOUT, Map.of());
    }

    private KiProgression(ResourceLocation race, ResourceLocation form, Map<String, MasteryTrack> savedTracks,
                          int level, int exp, int unspent, Map<String, Integer> savedStats, long zenkaiCooldownUntil,
                          List<String> savedLoadout, Map<String, Integer> savedUses) {
        this.race = race;
        this.form = form;
        this.level = Math.max(1, level);
        this.exp = Math.max(0, exp);
        this.unspentPoints = Math.max(0, unspent);
        this.zenkaiCooldownUntil = zenkaiCooldownUntil;
        for (Track t : Track.values()) {
            MasteryTrack s = savedTracks.get(t.id);
            // Copy so we never share instances with the decoded map.
            tracks[t.ordinal()] = s == null ? new MasteryTrack(0, 0, 0) : new MasteryTrack(s.level, s.xp, s.milestones);
        }
        for (Stat s : Stat.values()) {
            stats[s.ordinal()] = Mth.clamp(savedStats.getOrDefault(s.id, 0), 0, STAT_HARD_MAX);
        }
        for (int i = 0; i < LOADOUT_SLOTS; i++) {
            String id = i < savedLoadout.size() ? savedLoadout.get(i) : "";
            loadout[i] = id.isEmpty() ? null : ResourceLocation.tryParse(id); // tryParse: null if malformed
        }
        for (Map.Entry<String, Integer> e : savedUses.entrySet()) {
            if (e.getValue() > 0) attackUses.put(e.getKey(), e.getValue());
        }
    }

    // ---- Per-attack mastery (K0) -----------------------------------------------------------------------------------

    public int attackUses(ResourceLocation id) {
        return id == null ? 0 : attackUses.getOrDefault(id.toString(), 0);
    }

    /** Mastery tier 0..4 of an attack. */
    public int attackTier(ResourceLocation id) {
        return tierForUses(attackUses(id));
    }

    /** Counts one use; returns true if that reached a new tier. */
    public boolean recordAttackUse(ResourceLocation id) {
        String key = id.toString();
        int before = attackUses.getOrDefault(key, 0);
        int after = before == Integer.MAX_VALUE ? before : before + 1;
        attackUses.put(key, after);
        return tierForUses(after) > tierForUses(before);
    }

    /** Copy for the sync payload. */
    public Map<String, Integer> attackUsesCopy() {
        return new HashMap<>(attackUses);
    }

    public void clearAttackUses() {
        attackUses.clear();
    }

    private Map<String, MasteryTrack> trackMap() {
        Map<String, MasteryTrack> m = new HashMap<>();
        for (Track t : Track.values()) m.put(t.id, tracks[t.ordinal()]);
        return m;
    }

    private Map<String, Integer> statMap() {
        Map<String, Integer> m = new HashMap<>();
        for (Stat s : Stat.values()) m.put(s.id, stats[s.ordinal()]);
        return m;
    }

    // ---- Ki attack loadout (M3) -----------------------------------------------------------------------------------

    /** The attack id in skill slot {@code slot} (0-3), or null if the slot is empty. */
    public ResourceLocation loadout(int slot) {
        return slot >= 0 && slot < LOADOUT_SLOTS ? loadout[slot] : null;
    }

    /** Sets (or with null clears) a slot. The caller checks that the attack exists. */
    public void setLoadout(int slot, ResourceLocation id) {
        if (slot >= 0 && slot < LOADOUT_SLOTS) loadout[slot] = id;
    }

    /** Slot ids as strings ("" = empty) for saving and for the sync payload. */
    public String[] loadoutIds() {
        String[] a = new String[LOADOUT_SLOTS];
        for (int i = 0; i < a.length; i++) a[i] = loadout[i] == null ? "" : loadout[i].toString();
        return a;
    }

    private List<String> loadoutList() {
        return Arrays.asList(loadoutIds());
    }

    // ---- Level / EXP -------------------------------------------------------------------------------------------

    /** EXP needed to go from level L to L+1: 50 + 25L + 2L^2. */
    public static int expToNext(int level) {
        return 50 + 25 * level + 2 * level * level;
    }

    /** Mastery track XP needed to go from level n to n+1 (design 3.2): 100 + 40n. */
    public static int masteryXpToNext(int trackLevel) {
        return 100 + 40 * trackLevel;
    }

    // ---- Mastery ------------------------------------------------------------------------------------------------

    public MasteryTrack track(Track t) {
        return tracks[t.ordinal()];
    }

    public int level(Track t) {
        return tracks[t.ordinal()].level;
    }

    public int sumLevels() {
        int sum = 0;
        for (MasteryTrack t : tracks) sum += t.level;
        return sum;
    }

    /** Total stat points that mastery milestones have paid out so far. */
    public int pointsFromMastery() {
        int sum = 0;
        for (MasteryTrack t : tracks) sum += t.milestones * POINTS_PER_MASTERY_MILESTONE;
        return sum;
    }

    // ---- Stats --------------------------------------------------------------------------------------------------

    public int stat(Stat s) {
        return stats[s.ordinal()];
    }

    public void setStat(Stat s, int value) {
        stats[s.ordinal()] = Mth.clamp(value, 0, STAT_HARD_MAX);
    }

    /** Total points currently spent on stats. */
    public int spentPoints() {
        int sum = 0;
        for (int v : stats) sum += v;
        return sum;
    }

    public void clearStats() {
        Arrays.fill(stats, 0);
    }

    /**
     * Raw 1000 + 25 per Ki Capacity point, WITHOUT race/nature. Do not use for gameplay: call
     * StatEffects.maxKi(player) (or maxKi(prog, mods)) instead.
     * @deprecated since C3
     */
    @Deprecated
    public int maxKi() {
        return 1000 + 25 * stat(Stat.KI_CAPACITY);
    }

    /**
     * Raw +0.5 per Speed point, WITHOUT race/nature. Use StatEffects.maxStamina instead.
     * @deprecated since C3
     */
    @Deprecated
    public float staminaBonus() {
        return 0.5f * stat(Stat.SPEED);
    }

    // ---- Power Level --------------------------------------------------------------------------------------------

    public int powerLevel() {
        return powerLevelFor(level, spentPoints(), sumLevels());
    }

    /** Addendum A.4: round(50 * (level + pointsSpent/3 + masteryLevels/4)^1.35). Shared with the client cache. */
    public static int powerLevelFor(int level, int pointsSpent, int masteryLevels) {
        double base = level + pointsSpent / 3.0 + masteryLevels / 4.0;
        return (int) Math.round(50.0 * Math.pow(base, 1.35));
    }

    // ---- Arrays for the sync payload (Track / Stat ordinal order) -----------------------------------------------

    public int[] levelArray() {
        int[] a = new int[tracks.length];
        for (int i = 0; i < a.length; i++) a[i] = tracks[i].level;
        return a;
    }

    public int[] xpArray() {
        int[] a = new int[tracks.length];
        for (int i = 0; i < a.length; i++) a[i] = tracks[i].xp;
        return a;
    }

    public int[] statArray() {
        return stats.clone();
    }
}

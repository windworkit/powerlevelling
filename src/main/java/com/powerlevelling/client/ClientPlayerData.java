package com.powerlevelling.client;

import java.util.List;

import com.powerlevelling.character.CharacterData;
import com.powerlevelling.character.CharacterMods;
import com.powerlevelling.character.CharacterRegistries;
import com.powerlevelling.character.KiNature;
import com.powerlevelling.character.Race;
import com.powerlevelling.data.KiProgression;
import com.powerlevelling.network.ProgressionSyncPayload;
import com.powerlevelling.network.ResourceSyncPayload;
import com.powerlevelling.progression.StatEffects;

import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;

/**
 * Client-side cache of the local player's synced data. Only touched on the client thread.
 * Deliberately imports no client-only Minecraft classes so it is safe to reference from common code.
 */
public final class ClientPlayerData {
    public static float ki = 1000f;
    public static float maxKi = 1000f;
    public static float stamina = 100f;
    public static float maxStamina = 100f;
    public static float endurance = 100f;
    public static boolean kiBreak = false;

    public static ResourceLocation race = KiProgression.DEFAULT_RACE;
    public static ResourceLocation form = KiProgression.DEFAULT_FORM;
    public static int[] levels = new int[KiProgression.Track.values().length];
    public static int[] xp = new int[KiProgression.Track.values().length];

    // Character progression (Addendum A)
    public static int level = 1;
    public static int exp = 0;
    public static int expToNext = KiProgression.expToNext(1);
    public static int unspent = 0;
    public static int levelCap = 150;
    public static int statCap = 200;
    public static int[] stats = new int[KiProgression.Stat.values().length];
    /** ki_attack ids in skill slots 1-4 (null = empty). */
    public static ResourceLocation[] loadout = new ResourceLocation[KiProgression.LOADOUT_SLOTS];
    /** Uses per ki attack id as of the last progression sync (K0 per-attack mastery; no HUD display yet). */
    public static java.util.Map<String, Integer> attackUses = new java.util.HashMap<>();
    // Character (C3): ids synced with the progression, mods derived lazily from the synced datapack registries.
    public static ResourceLocation characterRace = CharacterData.DEFAULT_RACE;
    public static ResourceLocation characterNature = CharacterData.DEFAULT_NATURE;
    public static boolean characterCreated = false;
    /** baseBonusHealth server config as synced (needed for the vitality / max HP preview). */
    public static float baseBonusHealth = (float) StatEffects.DEFAULT_BASE_BONUS_HEALTH;
    private static CharacterMods cachedMods = CharacterMods.NEUTRAL;
    private static ResourceLocation cachedRace;
    private static ResourceLocation cachedNature;
    private static RegistryAccess cachedAccess;
    private static int zenkaiRemainingTicks = 0;
    private static long zenkaiSyncedAtMs = 0L;

    private ClientPlayerData() {
    }

    public static void apply(ResourceSyncPayload p) {
        ki = p.ki();
        maxKi = p.maxKi();
        stamina = p.stamina();
        maxStamina = p.maxStamina();
        endurance = p.endurance();
        kiBreak = p.kiBreak();
    }

    public static void apply(ProgressionSyncPayload p) {
        race = p.race();
        form = p.form();
        levels = p.levels().clone();
        xp = p.xp().clone();
        level = p.level();
        exp = p.exp();
        expToNext = p.expToNext();
        unspent = p.unspent();
        stats = p.stats().clone();
        levelCap = p.levelCap();
        statCap = p.statCap();
        ResourceLocation[] lo = new ResourceLocation[KiProgression.LOADOUT_SLOTS];
        for (int i = 0; i < lo.length && i < p.loadout().length; i++) {
            lo[i] = p.loadout()[i].isEmpty() ? null : ResourceLocation.tryParse(p.loadout()[i]);
        }
        loadout = lo;
        attackUses = new java.util.HashMap<>(p.attackUses());
        ResourceLocation cr = ResourceLocation.tryParse(p.characterRace());
        ResourceLocation cn = ResourceLocation.tryParse(p.characterNature());
        characterRace = cr != null ? cr : CharacterData.DEFAULT_RACE;
        characterNature = cn != null ? cn : CharacterData.DEFAULT_NATURE;
        characterCreated = p.characterCreated();
        baseBonusHealth = p.baseBonusHealth();
        zenkaiRemainingTicks = p.zenkaiCooldownTicks();
        zenkaiSyncedAtMs = System.currentTimeMillis();
    }

    /**
     * Race + nature modifiers for the local player, built from the synced registries (same combine as the server's
     * CharacterService.recomputeMods: neutral if either id is missing). Cached per (race, nature, registry access).
     */
    public static CharacterMods mods(RegistryAccess access) {
        if (access == cachedAccess && characterRace.equals(cachedRace) && characterNature.equals(cachedNature)) {
            return cachedMods;
        }
        Race race = CharacterRegistries.getRace(access, characterRace);
        KiNature nature = CharacterRegistries.getNature(access, characterNature);
        cachedMods = race == null || nature == null
                ? CharacterMods.NEUTRAL
                : CharacterMods.combine(List.of(race.modifiers(), nature.modifiers()));
        cachedAccess = access;
        cachedRace = characterRace;
        cachedNature = characterNature;
        return cachedMods;
    }

    /** Per-attack mastery tier 0..4 (from the synced use counts; exact at every tier-up, which triggers a sync). */
    public static int attackTier(ResourceLocation id) {
        return id == null ? 0 : KiProgression.tierForUses(attackUses.getOrDefault(id.toString(), 0));
    }

    public static int stat(KiProgression.Stat s) {
        return stats[s.ordinal()];
    }

    public static int spentPoints() {
        int sum = 0;
        for (int v : stats) sum += v;
        return sum;
    }

    public static int masterySum() {
        int sum = 0;
        for (int l : levels) sum += l;
        return sum;
    }

    public static int powerLevel() {
        return KiProgression.powerLevelFor(level, spentPoints(), masterySum());
    }

    /** Whole seconds until Zenkai is ready (0 = ready). Counts down locally between syncs. */
    public static int zenkaiRemainingSeconds() {
        long remainingMs = zenkaiRemainingTicks * 50L - (System.currentTimeMillis() - zenkaiSyncedAtMs);
        return remainingMs <= 0 ? 0 : (int) ((remainingMs + 999) / 1000);
    }

    /** Max stamina if endurance were full, incl. race/nature (used to size the stamina bar). */
    public static float fullMaxStamina(RegistryAccess access) {
        return StatEffects.maxStamina(stat(KiProgression.Stat.SPEED), 100f, mods(access));
    }

    /** Back to defaults (called on disconnect so the next world doesn't show stale values). */
    public static void reset() {
        ki = maxKi = 1000f;
        stamina = maxStamina = 100f;
        endurance = 100f;
        kiBreak = false;
        race = KiProgression.DEFAULT_RACE;
        form = KiProgression.DEFAULT_FORM;
        levels = new int[KiProgression.Track.values().length];
        xp = new int[KiProgression.Track.values().length];
        level = 1;
        exp = 0;
        expToNext = KiProgression.expToNext(1);
        unspent = 0;
        attackUses = new java.util.HashMap<>();
        levelCap = 150;
        statCap = 200;
        stats = new int[KiProgression.Stat.values().length];
        loadout = new ResourceLocation[KiProgression.LOADOUT_SLOTS];
        characterRace = CharacterData.DEFAULT_RACE;
        characterNature = CharacterData.DEFAULT_NATURE;
        characterCreated = false;
        baseBonusHealth = (float) StatEffects.DEFAULT_BASE_BONUS_HEALTH;
        cachedMods = CharacterMods.NEUTRAL;
        cachedRace = null;
        cachedNature = null;
        cachedAccess = null;
        zenkaiRemainingTicks = 0;
    }
}

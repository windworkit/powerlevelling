package com.powerlevelling.progression;

import com.powerlevelling.PowerLevelling;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Awards the mod's advancements. The JSON files use {@code minecraft:impossible} criteria, so the mod is the only
 * thing that grants them; every method here is safe to call repeatedly (vanilla ignores an already-earned criterion).
 */
public final class Advancements {
    private static final String ROOT = "root";
    private static final String FIRST_CHARACTER = "first_character";
    private static final String FIRST_CHARGE = "first_ki_charge";
    private static final String FIRST_BLAST = "first_ki_blast";
    private static final String KI_KILL = "ki_kill";
    private static final String KI_KILL_STREAK = "ki_kill_streak";
    private static final String LEVEL_10 = "level_10";
    private static final String LEVEL_25 = "level_25";
    private static final String LEVEL_50 = "level_50";
    private static final String MASTERY_5 = "mastery_5";
    private static final String MASTERY_25 = "mastery_25";
    private static final String MASTERY_MAX = "mastery_max";
    private static final String ZENKAI = "zenkai_triggered";

    /** A ki kill streak of this many kills earns {@code ki_kill_streak}. */
    public static final int KILL_STREAK_TARGET = 3;

    private Advancements() {
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(PowerLevelling.MODID, path);
    }

    /** Grants one advancement criterion. No-op when the advancement is missing (e.g. removed by a datapack). */
    public static void award(ServerPlayer player, String advancement, String criterion) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        AdvancementHolder holder = server.getAdvancements().get(id(advancement));
        if (holder != null) player.getAdvancements().award(holder, criterion);
    }

    /** After a character is created / changed. */
    public static void characterCreated(ServerPlayer player) {
        award(player, ROOT, "has_character");
        award(player, FIRST_CHARACTER, "first_character");
    }

    /** First successful ki charge (hold C). */
    public static void firstKiCharge(ServerPlayer player) {
        award(player, FIRST_CHARGE, "first_charge");
    }

    /** First ki attack fired. */
    public static void firstKiBlast(ServerPlayer player) {
        award(player, FIRST_BLAST, "first_blast");
    }

    /** A kill credited to a ki attack. */
    public static void kiKill(ServerPlayer player) {
        award(player, KI_KILL, "ki_kill");
    }

    /** A run of {@link #KILL_STREAK_TARGET} ki kills; the counter is reset when the player dies. */
    public static void kiKillStreak(ServerPlayer player, int streak) {
        if (streak >= KILL_STREAK_TARGET) award(player, KI_KILL_STREAK, "streak");
    }

    /** Character level thresholds. */
    public static void levelReached(ServerPlayer player, int level) {
        if (level >= 10) award(player, LEVEL_10, "level_10");
        if (level >= 25) award(player, LEVEL_25, "level_25");
        if (level >= 50) award(player, LEVEL_50, "level_50");
    }

    /** Mastery track thresholds (the track that just levelled up). */
    public static void masteryReached(ServerPlayer player, int trackLevel) {
        if (trackLevel >= 5) award(player, MASTERY_5, "mastery_5");
        if (trackLevel >= 25) award(player, MASTERY_25, "mastery_25");
        if (trackLevel >= 50) award(player, MASTERY_MAX, "mastery_max");
    }

    /** Zenkai boost triggered. */
    public static void zenkai(ServerPlayer player) {
        award(player, ZENKAI, "zenkai");
    }
}

package com.powerlevelling.config;

import java.util.List;

import net.neoforged.neoforge.common.ModConfigSpec;

// Server-side settings (synced from the server to clients that join). Placeholder values for now.
public class ServerConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.DoubleValue PROGRESSION_RATE = BUILDER
            .comment("Power level gain multiplier; default 1.0, range 0.0–10.0.")
            .defineInRange("progressionRate", 1.0, 0.0, 10.0);

    public static final ModConfigSpec.BooleanValue KI_BLOCK_DAMAGE = BUILDER
            .comment("Master toggle for ki explosions breaking blocks; default false.")
            .define("kiBlockDamage", false);

    public static final ModConfigSpec.BooleanValue MOB_SCALING_ENABLED = BUILDER
            .comment("Enable mob health/damage scaling via the multipliers below; default false.")
            .define("mobScalingEnabled", false);

    public static final ModConfigSpec.DoubleValue MOB_HEALTH_MULTIPLIER = BUILDER
            .comment("Health multiplier for scaled mobs; default 1.0, range 0.1–100.0.")
            .defineInRange("mobHealthMultiplier", 1.0, 0.1, 100.0);

    public static final ModConfigSpec.DoubleValue MOB_DAMAGE_MULTIPLIER = BUILDER
            .comment("Damage multiplier for scaled mobs; default 1.0, range 0.1–100.0.")
            .defineInRange("mobDamageMultiplier", 1.0, 0.1, 100.0);

    public static final ModConfigSpec.DoubleValue EXP_RATE = BUILDER
            .comment("Battle EXP gain multiplier from kills/training; default 1.0, range 0.0–100.0.")
            .defineInRange("expRate", 1.0, 0.0, 100.0);

    public static final ModConfigSpec.IntValue LEVEL_CAP = BUILDER
            .comment("Maximum character level; default 150, range 1–1000.")
            .defineInRange("levelCap", 150, 1, 1000);

    public static final ModConfigSpec.IntValue STAT_CAP = BUILDER
            .comment("Maximum points in any single stat; default 200, range 1–1000.")
            .defineInRange("statCap", 200, 1, 1000);

    public static final ModConfigSpec.IntValue ZENKAI_COOLDOWN_SECONDS = BUILDER
            .comment("Zenkai trigger cooldown in seconds; default 600 (10 min), range 0–86400.")
            .defineInRange("zenkaiCooldownSeconds", 600, 0, 86400);

    public static final ModConfigSpec.DoubleValue BASE_BONUS_HEALTH = BUILDER
            .comment("Flat max health added on top of vanilla 20 HP; default 80.0 (100 HP base), range 0.0–10000.0.")
            .defineInRange("baseBonusHealth", 80.0, 0.0, 10000.0);

    public static final ModConfigSpec.DoubleValue KI_CHARGE_RATE = BUILDER
            .comment("Ki gained per tick while charging (hold C) as fraction of max ki; default 0.012 (~4 s to full), range 0.0–1.0.")
            .defineInRange("kiChargeRate", 0.012, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue CHARGE_DAMAGE_TAKEN_MULT = BUILDER
            .comment("Incoming damage multiplier while charging ki; default 1.25 (+25%), range 0.0–10.0.")
            .defineInRange("chargeDamageTakenMult", 1.25, 0.0, 10.0);

    public static final ModConfigSpec.DoubleValue CHARGE_MOVE_MULT = BUILDER
            .comment("Movement speed multiplier while charging ki; default 0.3 (30% speed), range 0.0–1.0.")
            .defineInRange("chargeMoveMult", 0.3, 0.0, 1.0);

    public static final ModConfigSpec.IntValue MAX_BLASTS_PER_PLAYER = BUILDER
            .comment("Max live ki blasts per player; default 8, range 1–256.")
            .defineInRange("maxBlastsPerPlayer", 8, 1, 256);

    public static final ModConfigSpec.IntValue MAX_BLASTS_PER_LEVEL = BUILDER
            .comment("Max live ki blasts per dimension; default 128, range 1–4096.")
            .defineInRange("maxBlastsPerLevel", 128, 1, 4096);

    public static final ModConfigSpec.BooleanValue CREATIVE_FREE_KI = BUILDER
            .comment("Creative players don't spend ki on attacks; default false.")
            .define("creativeFreeKi", false);

    // ---- Character system (Addendum B) ----

    // ---- Transformations (Super Saiyan) ----

    public static final ModConfigSpec.DoubleValue TRANSFORM_DAMAGE_BONUS = BUILDER
            .comment("Damage bonus while transformed, applied to melee and ki; default 0.5 (+50%), range 0.0-10.0.")
            .defineInRange("transformDamageBonus", 0.5, 0.0, 10.0);

    public static final ModConfigSpec.DoubleValue TRANSFORM_SPEED_BONUS = BUILDER
            .comment("Movement speed bonus while transformed; default 0.25 (+25%), range 0.0-10.0.")
            .defineInRange("transformSpeedBonus", 0.25, 0.0, 10.0);

    public static final ModConfigSpec.DoubleValue TRANSFORM_KI_DRAIN = BUILDER
            .comment("Ki drained per tick while transformed; default 2.0, range 0.0-1000.0.")
            .defineInRange("transformKiDrain", 2.0, 0.0, 1000.0);

    public static final ModConfigSpec.IntValue TRANSFORM_MIN_KI = BUILDER
            .comment("Minimum ki required to enter a transformation; default 500, range 0-1000000.")
            .defineInRange("transformMinKi", 500, 0, 1_000_000);

    public static final ModConfigSpec.IntValue TRANSFORM_HOLD_TICKS = BUILDER
            .comment("Ticks the transform key must be held to transform; default 40 (2 s), range 1-200.")
            .defineInRange("transformHoldTicks", 40, 1, 200);

    // ---- Dash / dodge ----

    public static final ModConfigSpec.DoubleValue DASH_STAMINA_COST = BUILDER
            .comment("Stamina spent per dash; default 25.0, range 0.0-10000.0.")
            .defineInRange("dashStaminaCost", 25.0, 0.0, 10_000.0);

    public static final ModConfigSpec.DoubleValue DASH_IMPULSE = BUILDER
            .comment("Dash speed in blocks per tick; default 1.1, range 0.0-10.0.")
            .defineInRange("dashImpulse", 1.1, 0.0, 10.0);

    public static final ModConfigSpec.IntValue DASH_IFRAME_TICKS = BUILDER
            .comment("Ticks of damage immunity granted by a dash; default 8 (0.4 s), range 0-200.")
            .defineInRange("dashIframeTicks", 8, 0, 200);

    public static final ModConfigSpec.IntValue DASH_COOLDOWN_TICKS = BUILDER
            .comment("Ticks before another dash can start; default 20 (1 s), range 0-1200.")
            .defineInRange("dashCooldownTicks", 20, 0, 1200);

    public static final ModConfigSpec.BooleanValue REQUIRE_CHARACTER = BUILDER
            .comment("Require a created character for mod abilities (combat, ki, charging, stat spending); default true.")
            .define("requireCharacter", true);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> DISABLED_RACES = BUILDER
            .comment("Race IDs (e.g. powerlevelling:saiyan) banned from selection even if playable; default empty list.")
            .defineListAllowEmpty("disabledRaces", List.of(), () -> "", o -> o instanceof String);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> EXTRA_PLAYABLE_RACES = BUILDER
            .comment("Race IDs forced playable even if race file says playable=false; default empty list.")
            .defineListAllowEmpty("extraPlayableRaces", List.of(), () -> "", o -> o instanceof String);

    public static final ModConfigSpec.BooleanValue HIDE_LOCKED_RACES = BUILDER
            .comment("Hide unplayable races from character screen (vs showing dimmed); default false.")
            .define("hideLockedRaces", false);

    public static final ModConfigSpec.BooleanValue SIZE_AFFECTS_HITBOX = BUILDER
            .comment("If true, height/build also scale hitbox via Attributes.SCALE; default false (cosmetic only).")
            .define("sizeAffectsHitbox", false);

    public static final ModConfigSpec.BooleanValue OPEN_CREATION_ON_JOIN = BUILDER
            .comment("Auto-open character creation on join for players without a character; default false.")
            .define("openCreationOnJoin", false);

    public static final ModConfigSpec.BooleanValue ALLOW_CUSTOM_KI_COLORS = BUILDER
            .comment("If false, ki attacks use their own default colors instead of player's ki color; default true.")
            .define("allowCustomKiColors", true);

    // ---- Turbo Mode ----
    public static final ModConfigSpec.DoubleValue TURBO_SPEED_BONUS = BUILDER
            .comment("Movement speed bonus while turbo is active; default 1.0 (+100% = double speed), range 0.0-5.0.")
            .defineInRange("turboSpeedBonus", 1.0, 0.0, 5.0);

    public static final ModConfigSpec.DoubleValue TURBO_KI_DRAIN = BUILDER
            .comment("Ki drained per tick while turbo is active; default 5.0, range 0.0-100.0.")
            .defineInRange("turboKiDrain", 5.0, 0.0, 100.0);

    public static final ModConfigSpec.BooleanValue TURBO_REQUIRES_CHARGE = BUILDER
            .comment("Require ki charge (C key) to activate turbo; default false.",
                     "When true, you must be charging ki to use turbo, simulating 'pushing your limits'.")
            .define("turboRequiresCharge", false);

    // ---- Guard / Block ----
    public static final ModConfigSpec.BooleanValue GUARD_ENABLED = BUILDER
            .comment("Enable the guard/block mechanic; default true.")
            .define("guard.enabled", true);

    public static final ModConfigSpec.IntValue GUARD_STAMINA_PER_TICK = BUILDER
            .comment("Stamina drained per tick while guarding; default 2, range 0-100.")
            .defineInRange("guard.staminaPerTick", 2, 0, 100);

    public static final ModConfigSpec.DoubleValue GUARD_DAMAGE_REDUCTION = BUILDER
            .comment("Damage reduction while guarding (0.0 = none, 1.0 = immune); default 0.75 (75% reduction), range 0.0-1.0.")
            .defineInRange("guard.damageReduction", 0.75, 0.0, 1.0);

    public static final ModConfigSpec.IntValue GUARD_PERFECT_WINDOW_TICKS = BUILDER
            .comment("Ticks of the perfect guard (parry) window at guard start; default 8 (0.4s), range 0-100.")
            .defineInRange("guard.perfectWindowTicks", 8, 0, 100);

    public static final ModConfigSpec.IntValue GUARD_COOLDOWN_TICKS = BUILDER
            .comment("Ticks before guard can be used again after ending or breaking; default 20 (1s), range 0-1200.")
            .defineInRange("guard.cooldownTicks", 20, 0, 1200);

    public static final ModConfigSpec.IntValue GUARD_BREAK_STUN_TICKS = BUILDER
            .comment("Ticks of stun applied when guard breaks from stamina depletion; default 10 (0.5s), range 0-200.")
            .defineInRange("guard.breakStunTicks", 10, 0, 200);

    // ---- Lock-On Targeting (MT-002) ----
    public static final ModConfigSpec.BooleanValue LOCKON_ENABLED = BUILDER
            .comment("Enable the lock-on targeting mechanic; default true.")
            .define("lockon.enabled", true);

    public static final ModConfigSpec.DoubleValue LOCKON_RANGE = BUILDER
            .comment("Maximum range for lock-on targeting in blocks; default 16.0, range 1.0-64.0.")
            .defineInRange("lockon.range", 16.0, 1.0, 64.0);

    public static final ModConfigSpec.DoubleValue LOCKON_CONE_ANGLE = BUILDER
            .comment("Cone angle for lock-on targeting in degrees; default 45.0, range 10.0-180.0.")
            .defineInRange("lockon.coneAngle", 45.0, 10.0, 180.0);

    public static final ModConfigSpec.BooleanValue LOCKON_REQUIRE_LINE_OF_SIGHT = BUILDER
            .comment("Require line of sight for lock-on targeting; default false.")
            .define("lockon.requireLineOfSight", false);

    public static final ModConfigSpec.IntValue LOCKON_RELEASE_GRACE_TICKS = BUILDER
            .comment("Ticks of grace period after releasing the lock-on key before lock breaks; default 20 (1s), range 0-120.")
            .defineInRange("lockon.releaseGraceTicks", 20, 0, 120);

    public static final ModConfigSpec SPEC = BUILDER.build();
}

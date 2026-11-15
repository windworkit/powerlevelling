package com.powerlevelling.config;

import net.neoforged.neoforge.common.ModConfigSpec;

// Client-only presentation settings. Placeholder values for now.
public class ClientConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.DoubleValue HUD_SCALE = BUILDER
            .comment("HUD scale factor; default 1.0, range 0.25–4.0.")
            .defineInRange("hudScale", 1.0, 0.25, 4.0);

    public static final ModConfigSpec.IntValue HUD_X = BUILDER
            .comment("Horizontal HUD offset from left edge in GUI pixels (before scale); default 4, range 0–4096.")
            .defineInRange("hudX", 4, 0, 4096);

    public static final ModConfigSpec.IntValue HUD_Y = BUILDER
            .comment("Vertical HUD offset from top edge in GUI pixels (before scale); default 4, range 0–4096.")
            .defineInRange("hudY", 4, 0, 4096);

    public static final ModConfigSpec.DoubleValue SCREEN_SHAKE = BUILDER
            .comment("Screen shake strength; default 1.0, range 0.0 (off)–1.0 (full).")
            .defineInRange("screenShake", 1.0, 0.0, 1.0);

    public static final ModConfigSpec.BooleanValue HIDE_VANILLA_HEARTS = BUILDER
            .comment("Hide vanilla hearts; Power Levelling HP bar replaces them; default true.")
            .define("hideVanillaHearts", true);

    public static final ModConfigSpec.DoubleValue PARTICLE_DENSITY = BUILDER
            .comment("Ki aura particle density; default 1.0 (full), range 0.25–1.0.")
            .defineInRange("particleDensity", 1.0, 0.25, 1.0);

    public static final ModConfigSpec.DoubleValue AURA_PARTICLE_DENSITY = BUILDER
            .comment("Ki combat aura particle density; default 1.0 (full), range 0.0 (off)–1.0.")
            .defineInRange("auraParticleDensity", 1.0, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue AURA_SCALE = BUILDER
            .comment("Overall aura scale multiplier; default 0.6 (smaller than old defaults), range 0.1–2.0.")
            .defineInRange("auraScale", 0.6, 0.1, 2.0);

    public static final ModConfigSpec.DoubleValue AURA_OPACITY = BUILDER
            .comment("Aura opacity/brightness multiplier. Lower = subtler and less blinding; default 0.30, range 0.0 (off)–1.0.")
            .defineInRange("auraOpacity", 0.30, 0.0, 1.0);

    // --- Aura Particle Counts (per-state) ---
    public static final ModConfigSpec.IntValue AURA_PARTICLE_WISPS_CHARGING = BUILDER
            .comment("Max energy wisps while charging (particle system); default 0 (disabled - AuraLayer handles wisps), range 0–30.")
            .defineInRange("auraParticleWispsCharging", 0, 0, 30);
    public static final ModConfigSpec.IntValue AURA_PARTICLE_WISPS_SSJ = BUILDER
            .comment("Max energy wisps in Super Saiyan (particle system); default 0 (disabled - AuraLayer handles wisps), range 0–30.")
            .defineInRange("auraParticleWispsSSJ", 0, 0, 30);
    public static final ModConfigSpec.IntValue AURA_PARTICLE_WISPS_TRANSFORMING = BUILDER
            .comment("Max energy wisps while transforming (particle system); default 0 (disabled - AuraLayer handles wisps), range 0–40.")
            .defineInRange("auraParticleWispsTransforming", 0, 0, 40);
    public static final ModConfigSpec.IntValue AURA_PARTICLE_WISPS_STANCE = BUILDER
            .comment("Max energy wisps in combat stance (particle system); default 0 (disabled - AuraLayer handles wisps), range 0–10.")
            .defineInRange("auraParticleWispsStance", 0, 0, 10);

    public static final ModConfigSpec.IntValue AURA_PARTICLE_ROCKS_CHARGING = BUILDER
            .comment("Max ground rocks while charging; default 2 (subtle lifting debris), range 0–15.")
            .defineInRange("auraParticleRocksCharging", 2, 0, 15);
    public static final ModConfigSpec.IntValue AURA_PARTICLE_ROCKS_TRANSFORMING = BUILDER
            .comment("Max ground rocks while transforming; default 3, range 0–20.")
            .defineInRange("auraParticleRocksTransforming", 3, 0, 20);

    public static final ModConfigSpec.IntValue AURA_PARTICLE_LARGE_ROCKS_CHARGING = BUILDER
            .comment("Max large rocks while charging (high intensity only); default 0, range 0–5.")
            .defineInRange("auraParticleLargeRocksCharging", 0, 0, 5);
    public static final ModConfigSpec.IntValue AURA_PARTICLE_LARGE_ROCKS_TRANSFORMING = BUILDER
            .comment("Max large rocks while transforming; default 1, range 0–8.")
            .defineInRange("auraParticleLargeRocksTransforming", 1, 0, 8);

    public static final ModConfigSpec.IntValue AURA_PARTICLE_GROUND_RINGS_CHARGING = BUILDER
            .comment("Ground ring spawn chance per tick while charging (0-100); default 0 (AuraLayer handles rings), range 0–100.")
            .defineInRange("auraParticleGroundRingsCharging", 0, 0, 100);
    public static final ModConfigSpec.IntValue AURA_PARTICLE_GROUND_RINGS_TRANSFORMING = BUILDER
            .comment("Ground ring spawn chance per tick while transforming (0-100); default 0 (AuraLayer handles rings), range 0–100.")
            .defineInRange("auraParticleGroundRingsTransforming", 0, 0, 100);
    public static final ModConfigSpec.IntValue AURA_PARTICLE_GROUND_RINGS_SSJ = BUILDER
            .comment("Ground ring spawn chance per tick in SSJ (0-100); default 0 (AuraLayer handles rings), range 0–100.")
            .defineInRange("auraParticleGroundRingsSSJ", 0, 0, 100);

    public static final ModConfigSpec.IntValue AURA_PARTICLE_DEBRIS_BURST = BUILDER
            .comment("Max debris particles on initial charge start; default 0 (AuraLayer handles ground flare), range 0–25.")
            .defineInRange("auraParticleDebrisBurst", 0, 0, 25);

    // --- Aura Particle Orbit Radii (blocks) ---
    public static final ModConfigSpec.DoubleValue AURA_PARTICLE_WISP_RADIUS_BASE = BUILDER
            .comment("Base orbit radius for energy wisps (blocks); default 0.35.")
            .defineInRange("auraParticleWispRadiusBase", 0.35, 0.1, 2.0);
    public static final ModConfigSpec.DoubleValue AURA_PARTICLE_WISP_RADIUS_RAMP = BUILDER
            .comment("Orbit radius increase at full charge (blocks); default 0.35.")
            .defineInRange("auraParticleWispRadiusRamp", 0.35, 0.0, 2.0);
    public static final ModConfigSpec.DoubleValue AURA_PARTICLE_ROCK_RADIUS_BASE = BUILDER
            .comment("Base orbit radius for rocks (blocks); default 0.5.")
            .defineInRange("auraParticleRockRadiusBase", 0.5, 0.1, 2.0);
    public static final ModConfigSpec.DoubleValue AURA_PARTICLE_ROCK_RADIUS_RAMP = BUILDER
            .comment("Orbit radius increase for rocks at full charge (blocks); default 0.4.")
            .defineInRange("auraParticleRockRadiusRamp", 0.4, 0.0, 2.0);

    // --- Aura Particle Alpha ---
    public static final ModConfigSpec.DoubleValue AURA_PARTICLE_WISP_ALPHA = BUILDER
            .comment("Base alpha for energy wisps (0-1); default 0.35.")
            .defineInRange("auraParticleWispAlpha", 0.35, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue AURA_PARTICLE_ROCK_ALPHA = BUILDER
            .comment("Base alpha for rocks (0-1); default 0.45.")
            .defineInRange("auraParticleRockAlpha", 0.45, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue AURA_PARTICLE_DEBRIS_ALPHA = BUILDER
            .comment("Base alpha for debris burst (0-1); default 0.5.")
            .defineInRange("auraParticleDebrisAlpha", 0.5, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue AURA_PARTICLE_GROUND_RING_ALPHA = BUILDER
            .comment("Base alpha for ground rings (0-1); default 0.3.")
            .defineInRange("auraParticleGroundRingAlpha", 0.3, 0.0, 1.0);

    // --- Aura LOD (Level of Detail) ---
    public static final ModConfigSpec.BooleanValue AURA_LOD_ENABLED = BUILDER
            .comment("Enable aura level-of-detail (reduce complexity at distance); default true.")
            .define("auraLodEnabled", true);

    public static final ModConfigSpec.DoubleValue AURA_LOD_DISTANCE_1 = BUILDER
            .comment("LOD distance tier 1 (full detail); default 16 blocks.")
            .defineInRange("auraLodDistance1", 16.0, 1.0, 256.0);

    public static final ModConfigSpec.DoubleValue AURA_LOD_DISTANCE_2 = BUILDER
            .comment("LOD distance tier 2 (reduced wisps/rings); default 32 blocks.")
            .defineInRange("auraLodDistance2", 32.0, 1.0, 256.0);

    public static final ModConfigSpec.DoubleValue AURA_LOD_DISTANCE_3 = BUILDER
            .comment("LOD distance tier 3 (minimal - glow only); default 64 blocks.")
            .defineInRange("auraLodDistance3", 64.0, 1.0, 256.0);

    public static final ModConfigSpec.IntValue AURA_LOD_WISPS_1 = BUILDER
            .comment("Wisp count at tier 1 (full); default 20.")
            .defineInRange("auraLodWisps1", 20, 0, 32);

    public static final ModConfigSpec.IntValue AURA_LOD_WISPS_2 = BUILDER
            .comment("Wisp count at tier 2; default 10.")
            .defineInRange("auraLodWisps2", 10, 0, 32);

    public static final ModConfigSpec.IntValue AURA_LOD_WISPS_3 = BUILDER
            .comment("Wisp count at tier 3 (minimal); default 4.")
            .defineInRange("auraLodWisps3", 4, 0, 32);

    public static final ModConfigSpec.BooleanValue AURA_LOD_FLAME_SHELL_1 = BUILDER
            .comment("Flame shell enabled at tier 1; default true.")
            .define("auraLodFlameShell1", true);

    public static final ModConfigSpec.BooleanValue AURA_LOD_FLAME_SHELL_2 = BUILDER
            .comment("Flame shell enabled at tier 2; default false.")
            .define("auraLodFlameShell2", false);

    public static final ModConfigSpec.BooleanValue AURA_LOD_LIGHTNING_1 = BUILDER
            .comment("Lightning crackle enabled at tier 1; default true.")
            .define("auraLodLightning1", true);

    public static final ModConfigSpec.BooleanValue AURA_LOD_LIGHTNING_2 = BUILDER
            .comment("Lightning crackle enabled at tier 2; default false.")
            .define("auraLodLightning2", false);

    /** What hair does while a helmet is worn (C5). RESPECT_PART uses each cosmetic part's own helmet setting. */
    public enum HairWithHelmet { RESPECT_PART, ALWAYS_HIDE, ALWAYS_SHOW }

    public static final ModConfigSpec.EnumValue<HairWithHelmet> HAIR_WITH_HELMET = BUILDER
            .comment("Hair under helmets: RESPECT_PART (per-part), ALWAYS_HIDE, or ALWAYS_SHOW; default RESPECT_PART.")
            .defineEnum("hairWithHelmet", HairWithHelmet.RESPECT_PART);

    public static final ModConfigSpec.BooleanValue RENDER_EYE_OVERLAY_ON_OTHERS = BUILDER
            .comment("Draw tinted eye overlay on other players (your own always drawn); default true.")
            .define("renderEyeOverlayOnOthers", true);

    public static final ModConfigSpec.BooleanValue FORCE_DEFAULT_ATTACK_COLORS = BUILDER
            .comment("Force attack default colors for blasts/orbs/auras instead of custom ki/aura colors; default false.")
            .define("forceDefaultAttackColors", false);

    // --- Turbo Mode ---
    public static final ModConfigSpec.DoubleValue TURBO_SPEED_MULT = BUILDER
            .comment("Turbo movement speed multiplier (1.0 = no change, 2.0 = double speed); default 2.0.")
            .defineInRange("turboSpeedMult", 2.0, 1.1, 5.0);

    public static final ModConfigSpec.DoubleValue TURBO_KI_DRAIN_MULT = BUILDER
            .comment("Turbo ki drain multiplier per tick; default 3.0 (3x faster than normal charge).")
            .defineInRange("turboKiDrainMult", 3.0, 1.0, 10.0);

    public static final ModConfigSpec.DoubleValue TURBO_AURA_SCALE_MULT = BUILDER
            .comment("Aura scale multiplier when turbo is active (applied on top of existing scale); default 1.5.")
            .defineInRange("turboAuraScaleMult", 1.5, 1.0, 3.0);

    public static final ModConfigSpec.DoubleValue TURBO_AURA_ALPHA_MULT = BUILDER
            .comment("Aura alpha/brightness multiplier when turbo is active; default 1.8.")
            .defineInRange("turboAuraAlphaMult", 1.8, 1.0, 3.0);

    public static final ModConfigSpec SPEC = BUILDER.build();
}

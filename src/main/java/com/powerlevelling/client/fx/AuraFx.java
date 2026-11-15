package com.powerlevelling.client.fx;

import java.util.Map;

import com.powerlevelling.PowerLevelling;
import com.powerlevelling.character.CharacterSnapshot;
import com.powerlevelling.client.ClientCharacters;
import com.powerlevelling.client.ClientVisualState;
import com.powerlevelling.config.ClientConfig;
import com.powerlevelling.network.VisualStatePayload;
import com.powerlevelling.registry.ModParticles;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Client-only ki charge aura - Dragon Ball style powering up effects.
 * <p>
 * Features:
 * - Energy wisps: flowing, spiraling energy strands that rise and orbit
 * - Ground rocks: rocks and debris lifted from the ground, orbiting the character
 * - Debris burst: initial explosion of fragments when charging starts
 * - Large rocks: dramatic chunks for high-intensity charging
 * - Ground rings: expanding energy rings from the character's feet
 * - SSJ aura: golden energy wisps with distinctive golden color
 * - All effects respect particle density settings, distance culling, and per-state config limits
 */
@EventBusSubscriber(modid = PowerLevelling.MODID, value = Dist.CLIENT)
public final class AuraFx {
    private static final float RAMP_TICKS = 40f;          // ~2 s to full intensity
    private static final double MAX_PARTICLE_DISTANCE_SQ = 48.0 * 48.0;
    private static final double MAX_SOUND_DISTANCE_SQ = 32.0 * 32.0;
    private static final int SOUND_INTERVAL_TICKS = 20;
    private static final int DEBRIS_BURST_COOLDOWN = 200; // ticks between debris bursts

    private AuraFx() {
    }

    private static boolean particleAlphasInitialized = false;

    /** Call once on client startup to sync particle alphas from config. */
    public static void initParticleAlphas() {
        if (particleAlphasInitialized) return;
        KiAuraWispParticle.setMaxAlpha(ClientConfig.AURA_PARTICLE_WISP_ALPHA.get().floatValue());
        KiRockParticle.setMaxAlpha(ClientConfig.AURA_PARTICLE_ROCK_ALPHA.get().floatValue());
        KiRockLargeParticle.setMaxAlpha(0.5f); // Large rocks use fixed slightly higher alpha
        KiDebrisParticle.setMaxAlpha(ClientConfig.AURA_PARTICLE_DEBRIS_ALPHA.get().floatValue());
        // Ground rings use per-spawn alpha, no global max needed
        particleAlphasInitialized = true;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        initParticleAlphas();
        
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || mc.isPaused()) return;

        float density = ClientConfig.PARTICLE_DENSITY.get().floatValue();
        float auraDensity = ClientConfig.AURA_PARTICLE_DENSITY.get().floatValue();
        boolean firstPerson = mc.options.getCameraType().isFirstPerson();
        long gameTime = level.getGameTime();

        for (Map.Entry<Integer, ClientVisualState.State> e : ClientVisualState.entries()) {
            ClientVisualState.State s = e.getValue();
            
            // Handle CHARGING state (ki charge) - DRAGON BALL STYLE
            if (s.has(VisualStatePayload.CHARGING)) {
                s.chargeTicks += 1f;

                Entity entity = level.getEntity(e.getKey());
                if (!(entity instanceof Player player) || player.isRemoved() || player.isSpectator()) continue;

                double distSq = mc.player.distanceToSqr(player);
                float t = Mth.clamp(s.chargeTicks / RAMP_TICKS, 0f, 1f);

                if (distSq <= MAX_PARTICLE_DISTANCE_SQ) {
                    float selfScale = (player == mc.player && firstPerson) ? 0.4f : 1f;
                    spawnChargingAura(level, player, t, density * selfScale, auraDensity * selfScale, gameTime);
                }
                if (--s.soundCooldown <= 0) {
                    s.soundCooldown = SOUND_INTERVAL_TICKS;
                    if (distSq <= MAX_SOUND_DISTANCE_SQ) {
                        // Low-volume hum whose pitch climbs with the charge.
                        level.playLocalSound(player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_AMBIENT,
                                SoundSource.PLAYERS, 0.12f, 0.7f + 0.6f * t, false);
                    }
                }
            }
            
            // Handle SSJ aura (passive, always on when transformed) - GOLDEN ENERGY
            if (s.has(VisualStatePayload.SUPER_SAIYAN)) {
                Entity entity = level.getEntity(e.getKey());
                if (!(entity instanceof Player player) || player.isRemoved() || player.isSpectator()) continue;

                double distSq = mc.player.distanceToSqr(player);
                if (distSq <= MAX_PARTICLE_DISTANCE_SQ) {
                    float selfScale = (player == mc.player && firstPerson) ? 0.3f : 1f;
                    spawnSSJAura(level, player, gameTime, density * selfScale, auraDensity * selfScale);
                }
            }
            
            // Handle TRANSFORMING state (transformation windup) - INTENSE CHARGING
            if (s.has(VisualStatePayload.TRANSFORMING)) {
                s.chargeTicks += 1f;
                Entity entity = level.getEntity(e.getKey());
                if (!(entity instanceof Player player) || player.isRemoved() || player.isSpectator()) continue;

                double distSq = mc.player.distanceToSqr(player);
                float t = Mth.clamp(s.chargeTicks / RAMP_TICKS, 0f, 1f);

                if (distSq <= MAX_PARTICLE_DISTANCE_SQ) {
                    float selfScale = (player == mc.player && firstPerson) ? 0.4f : 1f;
                    spawnTransformingAura(level, player, t, density * selfScale, auraDensity * selfScale, gameTime);
                }
            }
            
            // Handle STANCE aura (combat stance - subtle energy)
            if (s.has(VisualStatePayload.STANCE) && 
                !s.has(VisualStatePayload.CHARGING) && 
                !s.has(VisualStatePayload.SUPER_SAIYAN) && 
                !s.has(VisualStatePayload.TRANSFORMING)) {
                Entity entity = level.getEntity(e.getKey());
                if (!(entity instanceof Player player) || player.isRemoved() || player.isSpectator()) continue;

                double distSq = mc.player.distanceToSqr(player);
                if (distSq <= MAX_PARTICLE_DISTANCE_SQ * 0.5) {
                    float selfScale = (player == mc.player && firstPerson) ? 0.2f : 1f;
                    spawnStanceAura(level, player, gameTime, density * selfScale, auraDensity * selfScale);
                }
            }
        }
    }

    /** Aura colour of a player from the synced character (auraColor, -1 -> kiColor); {@code fallback} if none / forceDefaultAttackColors. */
    static int auraColorOf(Player player, int fallback) {
        if (ClientConfig.FORCE_DEFAULT_ATTACK_COLORS.get()) return fallback;
        CharacterSnapshot s = ClientCharacters.effective(player.getId());
        if (s == null || !s.created()) return fallback;
        int c = s.auraColor() >= 0 ? s.auraColor() : s.kiColor();
        return c < 0 ? fallback : c & 0xFFFFFF;
    }

    /** DRAGON BALL STYLE: Main charging aura - energy wisps + ground rocks + rings */
    private static void spawnChargingAura(ClientLevel level, Player player, float t, float density, float auraDensity, long gameTime) {
        if (auraDensity <= 0f) return;
        RandomSource rand = level.random;
        int tint = auraColorOf(player, -1);
        if (tint < 0) tint = 0xFF0000;
        
        double height = player.getBbHeight();
        double yCenter = player.getY() + height * 0.5;
        
        // ===== ENERGY WISPS (flowing energy strands) =====
        int maxWisps = ClientConfig.AURA_PARTICLE_WISPS_CHARGING.get();
        int wispCount = Math.max(1, Mth.floor(maxWisps * auraDensity * (0.5f + 0.5f * t)));
        
        float wispRadiusBase = (float) ClientConfig.AURA_PARTICLE_WISP_RADIUS_BASE.get().floatValue();
        float wispRadiusRamp = (float) ClientConfig.AURA_PARTICLE_WISP_RADIUS_RAMP.get().floatValue();
        float wispAlpha = (float) ClientConfig.AURA_PARTICLE_WISP_ALPHA.get().floatValue();
        
        for (int i = 0; i < wispCount && KiAuraWispParticle.canSpawn(); i++) {
            // Orbit radius increases with charge (much smaller now, matches aura layer scale)
            float orbitRadius = wispRadiusBase + t * wispRadiusRamp + rand.nextFloat() * 0.15f;
            // Orbit speed varies
            float orbitSpeed = (0.03f + 0.05f * t + rand.nextFloat() * 0.04f) * (rand.nextBoolean() ? 1f : -1f);
            float startAngle = rand.nextFloat() * Mth.TWO_PI;
            // Vertical position along body
            float verticalOffset = -0.3f + rand.nextFloat() * (float)height * 1.1f;
            // Rise speed
            float riseSpeed = 0.02f + 0.04f * t + rand.nextFloat() * 0.03f;
            // Some spiral inward
            boolean spiralInward = rand.nextFloat() < 0.3f + 0.4f * t;
            
            double x = player.getX();
            double z = player.getZ();
            
            if (KiFx.particlesAllowed(mc())) {
                level.addParticle(ModParticles.KI_AURA_WISP.get(), x, yCenter, z,
                        orbitRadius, verticalOffset, orbitSpeed);
            }
        }
        
        // ===== GROUND ROCKS (rocks lifted from ground) =====
        int maxRocks = ClientConfig.AURA_PARTICLE_ROCKS_CHARGING.get();
        int rockCount = Math.max(1, Mth.floor(maxRocks * auraDensity * (0.5f + 0.5f * t * t)));
        
        float rockRadiusBase = (float) ClientConfig.AURA_PARTICLE_ROCK_RADIUS_BASE.get().floatValue();
        float rockRadiusRamp = (float) ClientConfig.AURA_PARTICLE_ROCK_RADIUS_RAMP.get().floatValue();
        float rockAlpha = (float) ClientConfig.AURA_PARTICLE_ROCK_ALPHA.get().floatValue();
        
        for (int i = 0; i < rockCount && KiRockParticle.canSpawn(); i++) {
            float orbitRadius = rockRadiusBase + t * rockRadiusRamp + rand.nextFloat() * 0.2f;
            float orbitSpeed = (0.015f + 0.025f * t + rand.nextFloat() * 0.02f) * (rand.nextBoolean() ? 1f : -1f);
            float startAngle = rand.nextFloat() * Mth.TWO_PI;
            // Rocks start from ground level
            float verticalOffset = -(float)height * 0.5f + rand.nextFloat() * (float)height * 0.3f;
            float riseSpeed = 0.01f + 0.02f * t + rand.nextFloat() * 0.015f;
            
            double x = player.getX();
            double z = player.getZ();
            
            if (KiFx.particlesAllowed(mc())) {
                level.addParticle(ModParticles.KI_ROCK.get(), x, player.getY(), z,
                        orbitRadius, verticalOffset, orbitSpeed);
            }
        }
        
        // ===== LARGE ROCKS (dramatic chunks for high charge) =====
        if (t > 0.5f) {
            int maxLargeRocks = ClientConfig.AURA_PARTICLE_LARGE_ROCKS_CHARGING.get();
            int largeRockCount = Math.max(1, Mth.floor(maxLargeRocks * auraDensity * (t - 0.5f) * 2f));
            
            for (int i = 0; i < largeRockCount && KiRockLargeParticle.canSpawn(); i++) {
                float orbitRadius = rockRadiusBase * 1.5f + t * rockRadiusRamp * 1.2f + rand.nextFloat() * 0.2f;
                float orbitSpeed = (0.008f + 0.012f * t + rand.nextFloat() * 0.01f) * (rand.nextBoolean() ? 1f : -1f);
                float startAngle = rand.nextFloat() * Mth.TWO_PI;
                float verticalOffset = -(float)height * 0.4f + rand.nextFloat() * (float)height * 0.4f;
                float riseSpeed = 0.005f + 0.01f * t;
                
                double x = player.getX();
                double z = player.getZ();
                
                if (KiFx.particlesAllowed(mc())) {
                    level.addParticle(ModParticles.KI_ROCK_LARGE.get(), x, player.getY(), z,
                            orbitRadius, verticalOffset, riseSpeed);
                }
            }
        }
        
        // ===== GROUND ENERGY RINGS (expanding from feet) =====
        int ringChance = ClientConfig.AURA_PARTICLE_GROUND_RINGS_CHARGING.get();
        if (rand.nextInt(100) < ringChance * auraDensity * (0.5f + t)) {
            float maxRadius = 0.8f + t * 1.2f;
            float expandSpeed = 0.05f + 0.1f * t;
            float alpha = (float) ClientConfig.AURA_PARTICLE_GROUND_RING_ALPHA.get().floatValue() * (0.5f + 0.5f * t);
            
            if (KiFx.particlesAllowed(mc())) {
                level.addParticle(ModParticles.KI_GROUND_RING.get(), 
                        player.getX(), player.getY() + 0.05, player.getZ(),
                        maxRadius, expandSpeed, alpha);
            }
        }
        
        // ===== INITIAL DEBRIS BURST (when first starting to charge) =====
        if (t < 0.125f && rand.nextFloat() < 0.5f * auraDensity) { // roughly first 5 ticks of 40-tick ramp
            spawnDebrisBurst(level, player, tint, t, auraDensity);
        }
    }
    
    /** DRAGON BALL STYLE: Transformation windup - intense version of charging */
    private static void spawnTransformingAura(ClientLevel level, Player player, float t, float density, float auraDensity, long gameTime) {
        if (auraDensity <= 0f) return;
        RandomSource rand = level.random;
        int tint = auraColorOf(player, -1);
        if (tint < 0) tint = 0xFF0000;
        
        double height = player.getBbHeight();
        double yCenter = player.getY() + height * 0.5;
        
        // Energy wisps
        int maxWisps = ClientConfig.AURA_PARTICLE_WISPS_TRANSFORMING.get();
        int wispCount = Math.max(1, Mth.floor(maxWisps * auraDensity * (0.5f + 0.5f * t)));
        
        float wispRadiusBase = (float) ClientConfig.AURA_PARTICLE_WISP_RADIUS_BASE.get().floatValue();
        float wispRadiusRamp = (float) ClientConfig.AURA_PARTICLE_WISP_RADIUS_RAMP.get().floatValue();
        
        for (int i = 0; i < wispCount && KiAuraWispParticle.canSpawn(); i++) {
            float orbitRadius = wispRadiusBase + t * wispRadiusRamp + rand.nextFloat() * 0.2f;
            float orbitSpeed = (0.04f + 0.08f * t + rand.nextFloat() * 0.06f) * (rand.nextBoolean() ? 1f : -1f);
            float startAngle = rand.nextFloat() * Mth.TWO_PI;
            float verticalOffset = -0.4f + rand.nextFloat() * (float)height * 1.2f;
            float riseSpeed = 0.03f + 0.06f * t + rand.nextFloat() * 0.04f;
            boolean spiralInward = rand.nextFloat() < 0.4f + 0.4f * t;
            
            if (KiFx.particlesAllowed(mc())) {
                level.addParticle(ModParticles.KI_AURA_WISP.get(), 
                        player.getX(), yCenter, player.getZ(),
                        orbitRadius, verticalOffset, orbitSpeed);
            }
        }
        
        // Rocks
        int maxRocks = ClientConfig.AURA_PARTICLE_ROCKS_TRANSFORMING.get();
        int rockCount = Math.max(1, Mth.floor(maxRocks * auraDensity * (0.5f + 0.5f * t * t)));
        
        float rockRadiusBase = (float) ClientConfig.AURA_PARTICLE_ROCK_RADIUS_BASE.get().floatValue();
        float rockRadiusRamp = (float) ClientConfig.AURA_PARTICLE_ROCK_RADIUS_RAMP.get().floatValue();
        
        for (int i = 0; i < rockCount && KiRockParticle.canSpawn(); i++) {
            float orbitRadius = rockRadiusBase + t * rockRadiusRamp + rand.nextFloat() * 0.25f;
            float orbitSpeed = (0.02f + 0.04f * t + rand.nextFloat() * 0.03f) * (rand.nextBoolean() ? 1f : -1f);
            float startAngle = rand.nextFloat() * Mth.TWO_PI;
            float verticalOffset = -(float)height * 0.5f + rand.nextFloat() * (float)height * 0.5f;
            float riseSpeed = 0.015f + 0.03f * t + rand.nextFloat() * 0.02f;
            
            if (KiFx.particlesAllowed(mc())) {
                level.addParticle(ModParticles.KI_ROCK.get(), 
                        player.getX(), player.getY(), player.getZ(),
                        orbitRadius, verticalOffset, orbitSpeed);
            }
        }
        
        // Large rocks always during transformation
        int maxLargeRocks = ClientConfig.AURA_PARTICLE_LARGE_ROCKS_TRANSFORMING.get();
        int largeRockCount = Math.max(1, Mth.floor(maxLargeRocks * auraDensity * (0.5f + 0.5f * t)));
        
        for (int i = 0; i < largeRockCount && KiRockLargeParticle.canSpawn(); i++) {
            float orbitRadius = rockRadiusBase * 1.5f + t * rockRadiusRamp * 1.5f + rand.nextFloat() * 0.3f;
            float orbitSpeed = (0.01f + 0.02f * t + rand.nextFloat() * 0.015f) * (rand.nextBoolean() ? 1f : -1f);
            float startAngle = rand.nextFloat() * Mth.TWO_PI;
            float verticalOffset = -(float)height * 0.4f + rand.nextFloat() * (float)height * 0.6f;
            float riseSpeed = 0.008f + 0.015f * t;
            
            if (KiFx.particlesAllowed(mc())) {
                level.addParticle(ModParticles.KI_ROCK_LARGE.get(), 
                        player.getX(), player.getY(), player.getZ(),
                        orbitRadius, verticalOffset, riseSpeed);
            }
        }
        
        // Frequent ground rings
        int ringChance = ClientConfig.AURA_PARTICLE_GROUND_RINGS_TRANSFORMING.get();
        if (rand.nextInt(100) < ringChance * auraDensity * (0.5f + t)) {
            float maxRadius = 1.0f + t * 1.8f;
            float expandSpeed = 0.08f + 0.15f * t;
            float alpha = (float) ClientConfig.AURA_PARTICLE_GROUND_RING_ALPHA.get().floatValue() * (0.5f + 0.5f * t);
            
            if (KiFx.particlesAllowed(mc())) {
                level.addParticle(ModParticles.KI_GROUND_RING.get(), 
                        player.getX(), player.getY() + 0.05, player.getZ(),
                        maxRadius, expandSpeed, alpha);
            }
        }
        
        // Debris burst periodically during transformation
        if (rand.nextFloat() < 0.03f * auraDensity) {
            spawnDebrisBurst(level, player, tint, t, auraDensity);
        }
    }
    
    /** DRAGON BALL STYLE: Golden SSJ aura - pure energy wisps, golden color */
    private static void spawnSSJAura(ClientLevel level, Player player, long gameTime, float density, float auraDensity) {
        if (auraDensity <= 0f) return;
        RandomSource rand = level.random;
        double height = player.getBbHeight();
        double yCenter = player.getY() + height * 0.5;
        double time = gameTime / 20.0; // seconds
        
        // Golden energy wisps - constant flow
        int maxWisps = ClientConfig.AURA_PARTICLE_WISPS_SSJ.get();
        int wispCount = Math.max(1, Mth.floor(maxWisps * auraDensity));
        
        float wispRadiusBase = (float) ClientConfig.AURA_PARTICLE_WISP_RADIUS_BASE.get().floatValue();
        
        for (int i = 0; i < wispCount && KiAuraWispParticle.canSpawn(); i++) {
            // SSJ wisps orbit closer and faster
            float orbitRadius = wispRadiusBase + rand.nextFloat() * 0.15f;
            float orbitSpeed = (0.05f + rand.nextFloat() * 0.05f) * (rand.nextBoolean() ? 1f : -1f);
            float startAngle = rand.nextFloat() * Mth.TWO_PI + (float)(time * 0.8f);
            float verticalOffset = -0.2f + rand.nextFloat() * (float)height * 0.9f;
            float riseSpeed = 0.04f + rand.nextFloat() * 0.05f;
            boolean spiralInward = rand.nextFloat() < 0.3f;
            
            if (KiFx.particlesAllowed(mc())) {
                level.addParticle(ModParticles.KI_AURA_WISP.get(), 
                        player.getX(), yCenter, player.getZ(),
                        orbitRadius, verticalOffset, orbitSpeed);
            }
        }
        
        // Occasional golden ground ring
        int ringChance = ClientConfig.AURA_PARTICLE_GROUND_RINGS_SSJ.get();
        if (rand.nextInt(100) < ringChance * auraDensity) {
            float alpha = (float) ClientConfig.AURA_PARTICLE_GROUND_RING_ALPHA.get().floatValue();
            if (KiFx.particlesAllowed(mc())) {
                level.addParticle(ModParticles.KI_GROUND_RING.get(), 
                        player.getX(), player.getY() + 0.05, player.getZ(),
                        1.2f, 0.12f, alpha);
            }
        }
        
        // Rare large golden rock (pure energy manifesting as matter)
        if (rand.nextFloat() < 0.005f * auraDensity && KiRockLargeParticle.canSpawn()) {
            float orbitRadius = wispRadiusBase * 1.5f + rand.nextFloat() * 0.2f;
            float orbitSpeed = (0.015f + rand.nextFloat() * 0.02f) * (rand.nextBoolean() ? 1f : -1f);
            float verticalOffset = -0.3f + rand.nextFloat() * (float)height * 0.6f;
            float riseSpeed = 0.01f;
            
            if (KiFx.particlesAllowed(mc())) {
                level.addParticle(ModParticles.KI_ROCK_LARGE.get(), 
                        player.getX(), player.getY(), player.getZ(),
                        orbitRadius, verticalOffset, riseSpeed);
            }
        }
    }
    
    /** DRAGON BALL STYLE: Subtle combat stance aura - just a few energy wisps */
    private static void spawnStanceAura(ClientLevel level, Player player, long gameTime, float density, float auraDensity) {
        if (auraDensity <= 0f) return;
        RandomSource rand = level.random;
        int tint = auraColorOf(player, -1);
        if (tint < 0) return;
        
        double height = player.getBbHeight();
        double yCenter = player.getY() + height * 0.5;
        
        // Very few wisps
        int maxWisps = ClientConfig.AURA_PARTICLE_WISPS_STANCE.get();
        int wispCount = Math.max(1, Mth.floor(maxWisps * auraDensity));
        
        float wispRadiusBase = (float) ClientConfig.AURA_PARTICLE_WISP_RADIUS_BASE.get().floatValue();
        
        for (int i = 0; i < wispCount && KiAuraWispParticle.canSpawn(); i++) {
            float orbitRadius = wispRadiusBase + rand.nextFloat() * 0.1f;
            float orbitSpeed = (0.02f + rand.nextFloat() * 0.02f) * (rand.nextBoolean() ? 1f : -1f);
            float startAngle = rand.nextFloat() * Mth.TWO_PI;
            float verticalOffset = -0.1f + rand.nextFloat() * (float)height * 0.7f;
            float riseSpeed = 0.015f + rand.nextFloat() * 0.015f;
            boolean spiralInward = false;
            
            if (KiFx.particlesAllowed(mc())) {
                level.addParticle(ModParticles.KI_AURA_WISP.get(), 
                        player.getX(), yCenter, player.getZ(),
                        orbitRadius, verticalOffset, orbitSpeed);
            }
        }
    }
    
    /** DRAGON BALL STYLE: Initial debris burst when charging starts - ground breaks up */
    private static void spawnDebrisBurst(ClientLevel level, Player player, int color, float intensity, float auraDensity) {
        if (auraDensity <= 0f) return;
        RandomSource rand = level.random;
        
        // Check if there's solid ground below
        BlockPos belowPos = BlockPos.containing(player.getX(), player.getY() - 0.1, player.getZ());
        BlockState state = level.getBlockState(belowPos);
        if (state.isAir()) return; // No ground to break
        
        int maxDebris = ClientConfig.AURA_PARTICLE_DEBRIS_BURST.get();
        int debrisCount = Math.max(1, Mth.floor(maxDebris * auraDensity * (0.5f + intensity)));
        
        double yGround = player.getY();
        float debrisAlpha = (float) ClientConfig.AURA_PARTICLE_DEBRIS_ALPHA.get().floatValue();
        
        for (int i = 0; i < debrisCount; i++) {
            double angle = rand.nextDouble() * Math.PI * 2.0;
            double speed = 0.08 + rand.nextDouble() * 0.15;
            double vx = Math.cos(angle) * speed;
            double vz = Math.sin(angle) * speed;
            double vy = 0.15 + rand.nextDouble() * 0.25; // Upward burst
            
            if (KiFx.particlesAllowed(mc())) {
                level.addParticle(ModParticles.KI_DEBRIS.get(),
                        player.getX(), yGround, player.getZ(),
                        vx, vy, vz);
            }
        }
        
        // Also spawn some rocks immediately
        int maxRocks = ClientConfig.AURA_PARTICLE_ROCKS_CHARGING.get();
        int rockCount = Math.max(1, Mth.floor(maxRocks * auraDensity * 0.5f));
        for (int i = 0; i < rockCount && KiRockParticle.canSpawn(); i++) {
            float orbitRadius = (float) ClientConfig.AURA_PARTICLE_ROCK_RADIUS_BASE.get().floatValue() + rand.nextFloat() * 0.2f;
            float orbitSpeed = (0.02f + rand.nextFloat() * 0.03f) * (rand.nextBoolean() ? 1f : -1f);
            float verticalOffset = -0.5f + rand.nextFloat() * 0.5f;
            float riseSpeed = 0.02f + rand.nextFloat() * 0.02f;
            
            if (KiFx.particlesAllowed(mc())) {
                level.addParticle(ModParticles.KI_ROCK.get(), 
                        player.getX(), player.getY(), player.getZ(),
                        orbitRadius, verticalOffset, orbitSpeed);
            }
        }
    }
    
    /** Helper to get Minecraft instance safely */
    private static Minecraft mc() {
        return Minecraft.getInstance();
    }
}
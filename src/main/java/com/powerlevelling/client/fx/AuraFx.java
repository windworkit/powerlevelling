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
import net.minecraft.core.Direction;
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
        KiAuraWispParticle.setMaxAlpha(Math.max(0.72f, ClientConfig.AURA_PARTICLE_WISP_ALPHA.get().floatValue()));
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
        int maxWisps = Math.max(8, ClientConfig.AURA_PARTICLE_WISPS_CHARGING.get());
        int wispCount = Math.max(0, Mth.floor(maxWisps * auraDensity * (0.5f + 0.5f * t)));
        
        float wispRadiusBase = (float) ClientConfig.AURA_PARTICLE_WISP_RADIUS_BASE.get().floatValue();
        float wispRadiusRamp = (float) ClientConfig.AURA_PARTICLE_WISP_RADIUS_RAMP.get().floatValue();
        
        for (int i = 0; i < wispCount && KiAuraWispParticle.canSpawn(); i++) {
            // Orbit radius increases with charge (much smaller now, matches aura layer scale)
            float orbitRadius = wispRadiusBase + t * wispRadiusRamp + rand.nextFloat() * 0.15f;
            // Orbit speed varies
            float orbitSpeed = (0.03f + 0.05f * t + rand.nextFloat() * 0.04f) * (rand.nextBoolean() ? 1f : -1f);
            float startAngle = rand.nextFloat() * Mth.TWO_PI;
            // Vertical position along body
            float verticalOffset = -0.34f * (float)height + rand.nextFloat() * (float)height * 0.42f;
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
        int maxRocks = Math.max(4, ClientConfig.AURA_PARTICLE_ROCKS_CHARGING.get());
        int rockCount = Math.max(0, Mth.floor(maxRocks * auraDensity * (0.5f + 0.5f * t * t)));
        
        if (KiFx.particlesAllowed(mc())) {
            spawnGroundRocks(level, player, rand, rockCount, t, false);
        }
        
        // ===== LARGE ROCKS (dramatic chunks for high charge) =====
        if (t > 0.5f) {
            int maxLargeRocks = Math.max(2, ClientConfig.AURA_PARTICLE_LARGE_ROCKS_CHARGING.get());
            int largeRockCount = Math.max(0, Mth.floor(maxLargeRocks * auraDensity * (t - 0.5f) * 2f));
            
            if (KiFx.particlesAllowed(mc())) {
                spawnGroundRocks(level, player, rand, largeRockCount, t, true);
            }
        }
        
        // ===== GROUND ENERGY RINGS (expanding from feet) =====
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
        int maxWisps = Math.max(12, ClientConfig.AURA_PARTICLE_WISPS_TRANSFORMING.get());
        int wispCount = Math.max(0, Mth.floor(maxWisps * auraDensity * (0.5f + 0.5f * t)));
        
        float wispRadiusBase = (float) ClientConfig.AURA_PARTICLE_WISP_RADIUS_BASE.get().floatValue();
        float wispRadiusRamp = (float) ClientConfig.AURA_PARTICLE_WISP_RADIUS_RAMP.get().floatValue();
        
        for (int i = 0; i < wispCount && KiAuraWispParticle.canSpawn(); i++) {
            float orbitRadius = wispRadiusBase + t * wispRadiusRamp + rand.nextFloat() * 0.2f;
            float orbitSpeed = (0.04f + 0.08f * t + rand.nextFloat() * 0.06f) * (rand.nextBoolean() ? 1f : -1f);
            float startAngle = rand.nextFloat() * Mth.TWO_PI;
            float verticalOffset = -0.38f * (float)height + rand.nextFloat() * (float)height * 0.44f;
            float riseSpeed = 0.03f + 0.06f * t + rand.nextFloat() * 0.04f;
            boolean spiralInward = rand.nextFloat() < 0.4f + 0.4f * t;
            
            if (KiFx.particlesAllowed(mc())) {
                level.addParticle(ModParticles.KI_AURA_WISP.get(), 
                        player.getX(), yCenter, player.getZ(),
                        orbitRadius, verticalOffset, orbitSpeed);
            }
        }
        
        // Rocks
        int maxRocks = Math.max(5, ClientConfig.AURA_PARTICLE_ROCKS_TRANSFORMING.get());
        int rockCount = Math.max(0, Mth.floor(maxRocks * auraDensity * (0.5f + 0.5f * t * t)));
        
        if (KiFx.particlesAllowed(mc())) {
            spawnGroundRocks(level, player, rand, rockCount, t, false);
        }
        
        // Large rocks always during transformation
        int maxLargeRocks = Math.max(2, ClientConfig.AURA_PARTICLE_LARGE_ROCKS_TRANSFORMING.get());
        int largeRockCount = Math.max(0, Mth.floor(maxLargeRocks * auraDensity * (0.5f + 0.5f * t)));
        
        if (KiFx.particlesAllowed(mc())) {
            spawnGroundRocks(level, player, rand, largeRockCount, t, true);
        }
        
        // Frequent ground rings
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
        int maxWisps = Math.max(8, ClientConfig.AURA_PARTICLE_WISPS_SSJ.get());
        int wispCount = Math.max(0, Mth.floor(maxWisps * auraDensity));
        
        float wispRadiusBase = (float) ClientConfig.AURA_PARTICLE_WISP_RADIUS_BASE.get().floatValue();
        
        for (int i = 0; i < wispCount && KiAuraWispParticle.canSpawn(); i++) {
            // SSJ wisps orbit closer and faster
            float orbitRadius = wispRadiusBase + rand.nextFloat() * 0.15f;
            float orbitSpeed = (0.05f + rand.nextFloat() * 0.05f) * (rand.nextBoolean() ? 1f : -1f);
            float startAngle = rand.nextFloat() * Mth.TWO_PI + (float)(time * 0.8f);
            float verticalOffset = -0.32f * (float)height + rand.nextFloat() * (float)height * 0.42f;
            float riseSpeed = 0.04f + rand.nextFloat() * 0.05f;
            boolean spiralInward = rand.nextFloat() < 0.3f;
            
            if (KiFx.particlesAllowed(mc())) {
                level.addParticle(ModParticles.KI_AURA_WISP.get(), 
                        player.getX(), yCenter, player.getZ(),
                        orbitRadius, verticalOffset, orbitSpeed);
            }
        }
        
        // Occasional golden ground ring
// Rare large golden rock (pure energy manifesting as matter)
        if (rand.nextFloat() < 0.005f * auraDensity && KiRockLargeParticle.canSpawn()) {
            if (KiFx.particlesAllowed(mc())) {
                spawnGroundRocks(level, player, rand, 1, 0.5f, true);
            }
        }
    }
    
    /** DRAGON BALL STYLE: Subtle combat stance aura - just a few energy wisps */
    private static void spawnStanceAura(ClientLevel level, Player player, long gameTime, float density, float auraDensity) {
        if (auraDensity <= 0f) return;
        RandomSource rand = level.random;
        // The main body aura remains visible even when no custom character color is set.
        double height = player.getBbHeight();
        double yCenter = player.getY() + height * 0.5;
        
        // Very few wisps
        int maxWisps = Math.max(4, ClientConfig.AURA_PARTICLE_WISPS_STANCE.get());
        int wispCount = Math.max(0, Mth.floor(maxWisps * auraDensity));
        
        float wispRadiusBase = (float) ClientConfig.AURA_PARTICLE_WISP_RADIUS_BASE.get().floatValue();
        
        for (int i = 0; i < wispCount && KiAuraWispParticle.canSpawn(); i++) {
            float orbitRadius = wispRadiusBase + rand.nextFloat() * 0.1f;
            float orbitSpeed = (0.02f + rand.nextFloat() * 0.02f) * (rand.nextBoolean() ? 1f : -1f);
            float startAngle = rand.nextFloat() * Mth.TWO_PI;
            float verticalOffset = -0.32f * (float)height + rand.nextFloat() * (float)height * 0.42f;
            float riseSpeed = 0.015f + rand.nextFloat() * 0.015f;
            boolean spiralInward = false;
            
            if (KiFx.particlesAllowed(mc())) {
                level.addParticle(ModParticles.KI_AURA_WISP.get(), 
                        player.getX(), yCenter, player.getZ(),
                        orbitRadius, verticalOffset, orbitSpeed);
            }
        }
    }
    
    /** Small fragments burst outward from the actual ground around the player's footprint. */
    private static void spawnDebrisBurst(ClientLevel level, Player player, int color, float intensity, float auraDensity) {
        if (auraDensity <= 0f) return;
        RandomSource rand = level.random;
        int maxDebris = Math.max(8, ClientConfig.AURA_PARTICLE_DEBRIS_BURST.get());
        int debrisCount = Math.max(0, Mth.floor(maxDebris * auraDensity * (0.5f + intensity)));
        if (debrisCount <= 0 || !KiFx.particlesAllowed(mc())) return;

        for (int i = 0; i < debrisCount; i++) {
            double angle = rand.nextDouble() * Mth.TWO_PI;
            double distance = 0.55 + rand.nextDouble() * 0.95;
            double x = player.getX() + Math.cos(angle) * distance;
            double z = player.getZ() + Math.sin(angle) * distance;
            double yGround = findGroundSurfaceY(level, player, x, z);
            if (!Double.isFinite(yGround)) continue;

            double speed = 0.08 + rand.nextDouble() * 0.15;
            double vx = Math.cos(angle) * speed;
            double vz = Math.sin(angle) * speed;
            double vy = 0.15 + rand.nextDouble() * 0.25;
            level.addParticle(ModParticles.KI_DEBRIS.get(), x, yGround + 0.015, z, vx, vy, vz);
        }

        int maxRocks = ClientConfig.AURA_PARTICLE_ROCKS_CHARGING.get();
        int rockCount = Math.max(0, Mth.floor(maxRocks * auraDensity * 0.5f));
        if (rockCount > 0) spawnGroundRocks(level, player, rand, rockCount, intensity, false);
    }

    /** Finds the exposed collision surface near the player's feet. */
    private static double findGroundSurfaceY(ClientLevel level, Player player, double x, double z) {
        int blockX = Mth.floor(x);
        int blockZ = Mth.floor(z);
        int baseY = Mth.floor(player.getY() + 0.1);
        for (int y = baseY; y >= baseY - 3; y--) {
            BlockPos pos = new BlockPos(blockX, y, blockZ);
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) continue;
            var shape = state.getCollisionShape(level, pos);
            if (shape.isEmpty()) continue;
            return y + shape.max(Direction.Axis.Y) + 0.015;
        }
        return Double.NaN;
    }

    /** Launches stones from separate points around the player, then lets them rise. */
    private static void spawnGroundRocks(ClientLevel level, Player player, RandomSource rand,
                                         int count, float charge, boolean large) {
        for (int i = 0; i < count; i++) {
            if (large ? !KiRockLargeParticle.canSpawn() : !KiRockParticle.canSpawn()) break;
            double angle = rand.nextDouble() * Mth.TWO_PI;
            double distance = 0.72 + rand.nextDouble() * (0.55 + 0.8 * charge);
            double x = player.getX() + Math.cos(angle) * distance;
            double z = player.getZ() + Math.sin(angle) * distance;
            double y = findGroundSurfaceY(level, player, x, z);
            if (!Double.isFinite(y)) continue;

            float orbitRadius = large ? 0.08f + rand.nextFloat() * 0.12f
                    : 0.035f + rand.nextFloat() * 0.10f;
            float orbitSpeed = (large ? 0.008f : 0.018f) + rand.nextFloat() * (large ? 0.012f : 0.028f);
            if (rand.nextBoolean()) orbitSpeed = -orbitSpeed;

            if (large) {
                level.addParticle(ModParticles.KI_ROCK_LARGE.get(), x, y, z, orbitRadius, 0.0f, orbitSpeed);
            } else {
                level.addParticle(ModParticles.KI_ROCK.get(), x, y, z, orbitRadius, 0.0f, orbitSpeed);
            }
        }
    }

    /** Helper to get Minecraft instance safely */
    private static Minecraft mc() {
        return Minecraft.getInstance();
    }
}
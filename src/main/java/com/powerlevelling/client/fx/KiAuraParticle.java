package com.powerlevelling.client.fx;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * A soft, orbiting aura particle that surrounds the player when charging ki or in combat stance.
 * Uses the player's ki color and gently rotates around them.
 */
public class KiAuraParticle extends TextureSheetParticle {
    public static final int HARD_CAP = 200;
    private static int active;
    /** Sprite set captured from the provider registration. */
    public static SpriteSet PROVIDER_SPRITES;

    private final float baseSize;
    private final float orbitRadius;
    private final float orbitSpeed;
    private final float orbitStartAngle;
    private final float verticalOffset;

    protected KiAuraParticle(ClientLevel level, double x, double y, double z,
                              double dx, double dy, double dz, SpriteSet sprites,
                              float orbitRadius, float orbitSpeed, float orbitStartAngle, float verticalOffset) {
        super(level, x, y, z);
        this.pickSprite(sprites);
        this.xd = dx;
        this.yd = dy;
        this.zd = dz;
        this.gravity = 0f;
        this.hasPhysics = false;
        this.friction = 0.98f;
        this.lifetime = 60 + this.random.nextInt(60); // 60-120 ticks
        this.baseSize = 0.15f + this.random.nextFloat() * 0.15f;
        this.quadSize = baseSize;
        this.orbitRadius = orbitRadius;
        this.orbitSpeed = orbitSpeed;
        this.orbitStartAngle = orbitStartAngle;
        this.verticalOffset = verticalOffset;
        
        // Alpha starts at 0, fades in, then fades out
        this.alpha = 0f;
        active++;
    }

    public static boolean canSpawn() {
        return active < HARD_CAP;
    }

    public static void resetCounter() {
        active = 0;
    }

    @Override
    public void tick() {
        super.tick();
        
        float life = this.age / (float) this.lifetime;
        
        // Fade in during first 20% of life, stay visible, fade out last 30%
        if (life < 0.2f) {
            this.alpha = life / 0.2f * 0.5f;
        } else if (life > 0.7f) {
            this.alpha = Math.max(0f, 0.5f * (1f - (life - 0.7f) / 0.3f));
        } else {
            this.alpha = 0.5f;
        }
        
        // Pulse size slightly
        this.quadSize = baseSize * (1f + 0.15f * Mth.sin(this.age * 0.15f));
        
        // Orbit around center (the position is the center, we orbit around it)
        float angle = orbitStartAngle + this.age * orbitSpeed;
        this.x = this.xo + Mth.cos(angle) * orbitRadius;
        this.z = this.zo + Mth.sin(angle) * orbitRadius;
        this.y = this.yo + verticalOffset + 0.15f * Mth.sin(this.age * 0.1f);
    }

    @Override
    public void remove() {
        super.remove();
        active = Math.max(0, active - 1);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    protected int getLightColor(float partialTick) {
        return LightTexture.FULL_BRIGHT;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
            // Store in our own static for the aura particle to use
            KiAuraParticle.PROVIDER_SPRITES = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                        double xSpeed, double ySpeed, double zSpeed) {
            // xSpeed = orbit radius, ySpeed = vertical offset, zSpeed = orbit speed
            float radius = (float) xSpeed;
            float vOffset = (float) ySpeed;
            float oSpeed = (float) zSpeed;
            float startAngle = level.random.nextFloat() * Mth.TWO_PI;
            
            return new KiAuraParticle(level, x, y, z, 0, 0, 0, sprites, radius, oSpeed, startAngle, vOffset);
        }
    }
}
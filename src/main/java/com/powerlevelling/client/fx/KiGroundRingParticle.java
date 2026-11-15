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

/**
 * Ground energy ring - expanding ring that pulses outward from the character's feet.
 * Dragon Ball style: energy rings that expand from the ground when powering up.
 */
public class KiGroundRingParticle extends TextureSheetParticle {
    public static final int HARD_CAP = 10; // Reduced from 20, config max spawns 5+10+2 per tick
    private static int active;
    public static SpriteSet PROVIDER_SPRITES;

    private final float baseSize;
    private final float maxRadius;
    private final float expandSpeed;
    private final float initialAlpha;
    private float currentRadius;

    protected KiGroundRingParticle(ClientLevel level, double x, double y, double z,
                                    double dx, double dy, double dz, SpriteSet sprites,
                                    float maxRadius, float expandSpeed, float initialAlpha) {
        super(level, x, y, z);
        this.pickSprite(sprites);
        this.xd = dx;
        this.yd = dy;
        this.zd = dz;
        this.gravity = 0f;
        this.hasPhysics = false;
        this.friction = 1f;
        this.lifetime = 60 + this.random.nextInt(20); // 60-80 ticks
        this.baseSize = 0.05f; // Thin ring
        this.quadSize = baseSize;
        this.maxRadius = maxRadius;
        this.expandSpeed = expandSpeed;
        this.initialAlpha = initialAlpha;
        this.currentRadius = 0.1f;
        this.roll = 0f;
        this.oRoll = 0f;
        
        this.alpha = initialAlpha;
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
        
        // Expand radius
        currentRadius += expandSpeed;
        if (currentRadius > maxRadius) currentRadius = maxRadius;
        
        // The quadSize represents the ring thickness - stays thin
        // We'll scale the particle in the render via the radius
        // Actually, for a ring we need a different approach - let's use the size as radius
        this.quadSize = currentRadius;
        
        // Fade out as it expands
        this.alpha = initialAlpha * (1f - life * life);
        
        // Slight rotation
        this.roll = this.age * 0.02f;
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
            KiGroundRingParticle.PROVIDER_SPRITES = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                        double xSpeed, double ySpeed, double zSpeed) {
            // xSpeed = max radius, ySpeed = expand speed, zSpeed = initial alpha
            float maxR = (float) xSpeed;
            float expand = (float) ySpeed;
            float alpha = (float) zSpeed;
            
            return new KiGroundRingParticle(level, x, y, z, 0, 0, 0, sprites,
                    maxR, expand, alpha);
        }
    }
}
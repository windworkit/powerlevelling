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
 * Small debris particle - tiny fragments that burst upward and scatter.
 * Used for the initial ground breakup when starting to charge.
 */
public class KiDebrisParticle extends TextureSheetParticle {
    public static final int HARD_CAP = 25; // Reduced from 120 to match config max (8) * density
    private static int active;
    public static SpriteSet PROVIDER_SPRITES;

    private final float baseSize;
    private final float velocityX;
    private final float velocityY;
    private final float velocityZ;
    private final float rotationSpeed;

    protected KiDebrisParticle(ClientLevel level, double x, double y, double z,
                                double dx, double dy, double dz, SpriteSet sprites,
                                float velocityX, float velocityY, float velocityZ, float rotationSpeed) {
        super(level, x, y, z);
        this.pickSprite(sprites);
        this.xd = dx;
        this.yd = dy;
        this.zd = dz;
        this.gravity = 0.05f; // Falls down
        this.hasPhysics = true;
        this.friction = 0.95f;
        this.lifetime = 40 + this.random.nextInt(30); // 40-70 ticks
        this.baseSize = 0.08f + this.random.nextFloat() * 0.07f;
        this.quadSize = baseSize;
        this.velocityX = velocityX;
        this.velocityY = velocityY;
        this.velocityZ = velocityZ;
        this.rotationSpeed = rotationSpeed;
        this.roll = this.random.nextFloat() * Mth.TWO_PI;
        this.oRoll = this.roll;
        
        this.alpha = 0.5f;
        active++;
    }

    public static boolean canSpawn() {
        return active < HARD_CAP;
    }

    public static void resetCounter() {
        active = 0;
    }

    /** Maximum alpha for debris (default 0.5). */
    private static float maxAlpha = 0.5f;

    public static void setMaxAlpha(float alpha) {
        maxAlpha = Mth.clamp(alpha, 0f, 1f);
    }

    @Override
    public void tick() {
        super.tick(); // Handles physics (gravity, friction)
        
        float life = this.age / (float) this.lifetime;
        
        // Fade out
        if (life > 0.7f) {
            this.alpha = Math.max(0f, maxAlpha * (1f - (life - 0.7f) / 0.3f));
        }
        
        // Rotation
        this.roll += rotationSpeed;
        
        // Shrink over time
        this.quadSize = baseSize * (1f - life * 0.5f);
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
            KiDebrisParticle.PROVIDER_SPRITES = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                        double xSpeed, double ySpeed, double zSpeed) {
            // xSpeed, ySpeed, zSpeed = initial velocity
            float vx = (float) xSpeed;
            float vy = (float) ySpeed;
            float vz = (float) zSpeed;
            float rotSpeed = (level.random.nextFloat() - 0.5f) * 0.4f;
            
            return new KiDebrisParticle(level, x, y, z, vx, vy, vz, sprites,
                    vx, vy, vz, rotSpeed);
        }
    }
}
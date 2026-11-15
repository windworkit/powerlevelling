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
 * Large rock particle - bigger chunks from ground breakup during intense charging.
 * Fewer in number, more dramatic, orbit slower.
 */
public class KiRockLargeParticle extends TextureSheetParticle {
    public static final int HARD_CAP = 10; // Reduced from 30 to match config max (1+2) * density
    private static int active;
    public static SpriteSet PROVIDER_SPRITES;

    private final float baseSize;
    private final float orbitRadius;
    private final float orbitSpeed;
    private final float orbitStartAngle;
    private final float verticalOffset;
    private final float riseSpeed;

    protected KiRockLargeParticle(ClientLevel level, double x, double y, double z,
                                   double dx, double dy, double dz, SpriteSet sprites,
                                   float orbitRadius, float orbitSpeed, float orbitStartAngle, float verticalOffset,
                                   float riseSpeed) {
        super(level, x, y, z);
        this.pickSprite(sprites);
        this.xd = dx;
        this.yd = dy;
        this.zd = dz;
        this.gravity = 0.01f; // Very slight gravity
        this.hasPhysics = false;
        this.friction = 0.99f;
        this.lifetime = 150 + this.random.nextInt(100); // 150-250 ticks
        this.baseSize = 0.3f + this.random.nextFloat() * 0.2f; // Larger
        this.quadSize = baseSize;
        this.orbitRadius = orbitRadius;
        this.orbitSpeed = orbitSpeed;
        this.orbitStartAngle = orbitStartAngle;
        this.verticalOffset = verticalOffset;
        this.riseSpeed = riseSpeed;
        
        this.alpha = 0.5f;
        active++;
    }

    public static boolean canSpawn() {
        return active < HARD_CAP;
    }

    public static void resetCounter() {
        active = 0;
    }

    /** Maximum alpha for large rocks (default 0.5). */
    private static float maxAlpha = 0.5f;

    public static void setMaxAlpha(float alpha) {
        maxAlpha = Mth.clamp(alpha, 0f, 1f);
    }

    @Override
    public void tick() {
        super.tick();
        
        float life = this.age / (float) this.lifetime;
        
        // Fade in/out
        if (life < 0.05f) {
            this.alpha = life / 0.05f * maxAlpha;
        } else if (life > 0.85f) {
            this.alpha = Math.max(0f, maxAlpha * (1f - (life - 0.85f) / 0.15f));
        }
        
        // Orbit radius - large rocks orbit further out
        float currentRadius = orbitRadius * (1f + life * 0.2f);
        
        float angle = orbitStartAngle + this.age * orbitSpeed;
        this.x = this.xo + Mth.cos(angle) * currentRadius;
        this.z = this.zo + Mth.sin(angle) * currentRadius;
        
        // Slow vertical rise
        this.y = this.yo + verticalOffset + this.age * riseSpeed;
        
        // Subtle size pulse
        this.quadSize = baseSize * (1f + 0.03f * Mth.sin(this.age * 0.05f));
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
            KiRockLargeParticle.PROVIDER_SPRITES = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                        double xSpeed, double ySpeed, double zSpeed) {
            float radius = (float) xSpeed;
            float vOffset = (float) ySpeed;
            float oSpeed = (float) zSpeed;
            float startAngle = level.random.nextFloat() * Mth.TWO_PI;
            float rise = 0.008f + level.random.nextFloat() * 0.015f;
            
            return new KiRockLargeParticle(level, x, y, z, 0, 0, 0, sprites,
                    radius, oSpeed, startAngle, vOffset, rise);
        }
    }
}
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
 * Rock/debris particle - small rocks lifted from ground during power up.
 * Dragon Ball style: rocks float up and orbit around the character when charging.
 */
public class KiRockParticle extends TextureSheetParticle {
    public static final int HARD_CAP = 35; // Reduced from 80 to match config max (3+5) * density
    private static int active;
    public static SpriteSet PROVIDER_SPRITES;

    private final float baseSize;
    private final float orbitRadius;
    private final float orbitSpeed;
    private final float orbitStartAngle;
    private final float verticalOffset;
    private final float riseSpeed;

    protected KiRockParticle(ClientLevel level, double x, double y, double z,
                              double dx, double dy, double dz, SpriteSet sprites,
                              float orbitRadius, float orbitSpeed, float orbitStartAngle, float verticalOffset,
                              float riseSpeed) {
        super(level, x, y, z);
        this.pickSprite(sprites);
        this.xd = dx;
        this.yd = dy;
        this.zd = dz;
        this.gravity = 0.02f; // Very slight gravity
        this.hasPhysics = false;
        this.friction = 0.98f;
        this.lifetime = 100 + this.random.nextInt(80); // 100-180 ticks
        this.baseSize = 0.15f + this.random.nextFloat() * 0.15f;
        this.quadSize = baseSize;
        this.orbitRadius = orbitRadius;
        this.orbitSpeed = orbitSpeed;
        this.orbitStartAngle = orbitStartAngle;
        this.verticalOffset = verticalOffset;
        this.riseSpeed = riseSpeed;
        
        this.alpha = 0.45f; // Rocks are more opaque but not blinding
        active++;
    }

    public static boolean canSpawn() {
        return active < HARD_CAP;
    }

    public static void resetCounter() {
        active = 0;
    }

    /** Maximum alpha for rocks, driven by config (default 0.45). */
    private static float maxAlpha = 0.45f;

    public static void setMaxAlpha(float alpha) {
        maxAlpha = Mth.clamp(alpha, 0f, 1f);
    }

    @Override
    public void tick() {
        super.tick();
        
        float life = this.age / (float) this.lifetime;
        
        // Fade in/out
        if (life < 0.1f) {
            this.alpha = life / 0.1f * maxAlpha;
        } else if (life > 0.8f) {
            this.alpha = Math.max(0f, maxAlpha * (1f - (life - 0.8f) / 0.2f));
        }
        
        // Orbit radius increases slightly then stabilizes
        float currentRadius = orbitRadius * (1f + life * 0.3f);
        
        float angle = orbitStartAngle + this.age * orbitSpeed;
        this.x = this.xo + Mth.cos(angle) * currentRadius;
        this.z = this.zo + Mth.sin(angle) * currentRadius;
        
        // Vertical rise with gravity effect
        this.y = this.yo + verticalOffset + this.age * riseSpeed - this.age * this.age * 0.0001f;
        
        // Slight size pulse
        this.quadSize = baseSize * (1f + 0.05f * Mth.sin(this.age * 0.1f));
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
            KiRockParticle.PROVIDER_SPRITES = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                        double xSpeed, double ySpeed, double zSpeed) {
            float radius = (float) xSpeed;
            float vOffset = (float) ySpeed;
            float oSpeed = (float) zSpeed;
            float startAngle = level.random.nextFloat() * Mth.TWO_PI;
            float rise = 0.02f + level.random.nextFloat() * 0.03f;
            
            return new KiRockParticle(level, x, y, z, 0, 0, 0, sprites,
                    radius, oSpeed, startAngle, vOffset, rise);
        }
    }
}
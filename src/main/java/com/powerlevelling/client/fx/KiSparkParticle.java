package com.powerlevelling.client.fx;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.particles.SimpleParticleType;

/** A small glowing spark that rises and fades. The sprite is near-white; the cyan tint is applied here. */
public class KiSparkParticle extends TextureSheetParticle {
    private final float baseSize;

    protected KiSparkParticle(ClientLevel level, double x, double y, double z,
                              double dx, double dy, double dz, SpriteSet sprites) {
        super(level, x, y, z);
        this.pickSprite(sprites);
        this.xd = dx;
        this.yd = dy;
        this.zd = dz;
        this.gravity = 0f;
        this.hasPhysics = false;
        this.friction = 0.96f;
        this.lifetime = 14 + this.random.nextInt(12);
        this.baseSize = 0.05f + this.random.nextFloat() * 0.06f;
        this.quadSize = baseSize;
        // Cyan -> white mix, per particle.
        float mix = this.random.nextFloat();
        this.setColor(0.45f + 0.55f * mix, 0.85f + 0.15f * mix, 1.0f);
        this.alpha = 0.95f;
    }

    @Override
    public void tick() {
        super.tick(); // moves, ages, removes at end of life
        float life = this.age / (float) this.lifetime;
        this.alpha = Math.max(0f, 0.95f * (1f - life * life)); // stays bright, drops off at the end
        this.quadSize = baseSize * (1f - 0.55f * life);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    protected int getLightColor(float partialTick) {
        return LightTexture.FULL_BRIGHT; // glows regardless of ambient light
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        /** Shared with KiFx so tinted particles can use the ki_spark sprite (the particle atlas isn't reachable via getTextureAtlas). */
        public static SpriteSet SHARED;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
            SHARED = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double xSpeed, double ySpeed, double zSpeed) {
            return new KiSparkParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, sprites);
        }
    }
}

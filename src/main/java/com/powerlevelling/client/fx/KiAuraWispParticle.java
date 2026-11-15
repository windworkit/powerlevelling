package com.powerlevelling.client.fx;

import com.powerlevelling.character.CharacterSnapshot;
import com.powerlevelling.client.ClientCharacters;

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
import net.minecraft.world.entity.player.Player;

/**
 * Energy wisp particle - elongated, flowing energy strands that spiral upward.
 * Used for the Dragon Ball style charging aura - looks like pure energy, not experience orbs.
 */
public class KiAuraWispParticle extends TextureSheetParticle {
    public static final int HARD_CAP = 60; // Reduced from 150 to match config max (8+6+12+2) * density
    private static int active;
    /** Sprite set captured from the provider registration. */
    public static SpriteSet PROVIDER_SPRITES;

    private final float baseSize;
    private final float orbitRadius;
    private final float orbitSpeed;
    private final float orbitStartAngle;
    private final float verticalOffset;
    private final float riseSpeed;
    private final boolean spiralInward;

    protected KiAuraWispParticle(ClientLevel level, double x, double y, double z,
                                  double dx, double dy, double dz, SpriteSet sprites,
                                  float orbitRadius, float orbitSpeed, float orbitStartAngle, float verticalOffset,
                                  float riseSpeed, boolean spiralInward, int color) {
        super(level, x, y, z);
        this.pickSprite(sprites);
        this.xd = dx;
        this.yd = dy;
        this.zd = dz;
        this.gravity = 0f;
        this.hasPhysics = false;
        this.friction = 0.97f;
        this.lifetime = 80 + this.random.nextInt(60); // 80-140 ticks
        this.baseSize = 0.18f + this.random.nextFloat() * 0.12f;
        this.quadSize = baseSize;
        this.orbitRadius = orbitRadius;
        this.orbitSpeed = orbitSpeed;
        this.orbitStartAngle = orbitStartAngle;
        this.verticalOffset = verticalOffset;
        this.riseSpeed = riseSpeed;
        this.spiralInward = spiralInward;
        
        // Tint with the player's ki color
        this.setColor(((color >> 16) & 0xFF) / 255f, ((color >> 8) & 0xFF) / 255f, (color & 0xFF) / 255f);
        
        // Alpha starts at 0, fades in quickly
        this.alpha = 0f;
        active++;
    }

    public static boolean canSpawn() {
        return active < HARD_CAP;
    }

    public static void resetCounter() {
        active = 0;
    }

    /** Maximum alpha for wisps, driven by config (default 0.35). */
    private static float maxAlpha = 0.35f;

    public static void setMaxAlpha(float alpha) {
        maxAlpha = Mth.clamp(alpha, 0f, 1f);
    }

    @Override
    public void tick() {
        super.tick();
        
        float life = this.age / (float) this.lifetime;
        
        // Fade in during first 15% of life, stay visible, fade out last 25%
        if (life < 0.15f) {
            this.alpha = life / 0.15f * maxAlpha;
        } else if (life > 0.75f) {
            this.alpha = Math.max(0f, maxAlpha * (1f - (life - 0.75f) / 0.25f));
        } else {
            this.alpha = maxAlpha;
        }
        
        // Pulse size with organic variation
        this.quadSize = baseSize * (1f + 0.2f * Mth.sin(this.age * 0.12f) + 0.1f * Mth.sin(this.age * 0.27f));
        
        // Spiral orbit - radius decreases over time if spiralInward
        float currentRadius = orbitRadius;
        if (spiralInward) {
            currentRadius *= (1f - life * 0.6f);
        }
        
        float angle = orbitStartAngle + this.age * orbitSpeed;
        this.x = this.xo + Mth.cos(angle) * currentRadius;
        this.z = this.zo + Mth.sin(angle) * currentRadius;
        
        // Vertical rise with slight wave
        this.y = this.yo + verticalOffset + this.age * riseSpeed + 0.08f * Mth.sin(this.age * 0.08f);
        
        // Slight drift toward center
        if (spiralInward) {
            double dx = this.xo - this.x;
            double dz = this.zo - this.z;
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > 0.01) {
                this.xd = dx * 0.02;
                this.zd = dz * 0.02;
            }
        }
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

    private static int auraColorAt(ClientLevel level, double x, double y, double z) {
        Player nearest = null;
        double nearestDistance = 16.0;
        for (Player candidate : level.players()) {
            double distance = candidate.distanceToSqr(x, y, z);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = candidate;
            }
        }
        if (nearest != null) {
            CharacterSnapshot snapshot = ClientCharacters.effective(nearest.getId());
            if (snapshot != null && snapshot.created()) {
                int color = snapshot.auraColor() >= 0 ? snapshot.auraColor() : snapshot.kiColor();
                if (color >= 0) return color & 0xFFFFFF;
            }
        }
        return 0xFF5A35;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
            KiAuraWispParticle.PROVIDER_SPRITES = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                        double xSpeed, double ySpeed, double zSpeed) {
            // xSpeed = orbit radius, ySpeed = vertical offset, zSpeed = orbit speed
            float radius = (float) xSpeed;
            float vOffset = (float) ySpeed;
            float oSpeed = (float) zSpeed;
            float startAngle = level.random.nextFloat() * Mth.TWO_PI;
            float rise = 0.03f + level.random.nextFloat() * 0.04f;
            boolean spiral = level.random.nextBoolean();
            // Match the nearest player's configured aura color so optional wisps share
            // the same energy palette as the body-hugging aura mesh.
            int color = auraColorAt(level, x, y, z);
            
            return new KiAuraWispParticle(level, x, y, z, 0, 0, 0, sprites, 
                    radius, oSpeed, startAngle, vOffset, rise, spiral, color);
        }
    }
}
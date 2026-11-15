package com.powerlevelling.client.fx;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/**
 * A glowing particle in any colour, created directly by KiFx (not through a ParticleType, so no registration needed).
 * Used for blast trails, impact sparks, the impact flash and the charging orb. Uses the ki_spark sprite.
 * A static counter enforces the hard cap on live mod particles of this kind (design 8.6: 400).
 */
public class KiTintParticle extends TextureSheetParticle {
    public static final int HARD_CAP = 400;
    private static int active;

    private final float baseSize;
    private final float growth;
    private final float baseAlpha;
    private boolean counted;

    public KiTintParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz,
                          int rgb, float size, int lifetime, float growth, float alpha, TextureAtlasSprite sprite) {
        super(level, x, y, z);
        this.setSprite(sprite);
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.gravity = 0f;
        this.hasPhysics = false;
        this.friction = 0.88f;
        this.lifetime = Math.max(1, lifetime);
        this.baseSize = size;
        this.quadSize = size;
        this.growth = growth;
        this.baseAlpha = alpha;
        this.alpha = alpha;
        this.setColor(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f);
        active++;
        counted = true;
    }

    public static boolean canSpawn() {
        return active < HARD_CAP;
    }

    /** Called when the client level changes: particles are wiped without remove() being called on them. */
    public static void resetCounter() {
        active = 0;
    }

    @Override
    public void tick() {
        super.tick(); // moves, ages, calls remove() at the end of life
        float life = this.age / (float) this.lifetime;
        this.alpha = Math.max(0f, baseAlpha * (1f - life * life));
        this.quadSize = Math.max(0.001f, baseSize * (1f + growth * life));
    }

    @Override
    public void remove() {
        super.remove();
        if (counted) {
            counted = false;
            active = Math.max(0, active - 1);
        }
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    protected int getLightColor(float partialTick) {
        return LightTexture.FULL_BRIGHT;
    }
}

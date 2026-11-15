package com.powerlevelling.registry;

import net.minecraft.core.particles.SimpleParticleType;
import net.neoforged.neoforge.registries.DeferredHolder;

/** Particle types. Fields register into ModRegistries.PARTICLE_TYPES when this class loads. */
public final class ModParticles {
    /** Rising ki-coloured spark used by the charge aura. alwaysShow = false, so the vanilla distance/density limits apply. */
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> KI_SPARK =
            ModRegistries.PARTICLE_TYPES.register("ki_spark", () -> new SimpleParticleType(false));

    /** Soft aura particle around the player when charging ki or in combat stance. */
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> KI_AURA =
            ModRegistries.PARTICLE_TYPES.register("ki_aura", () -> new SimpleParticleType(false));

    /** Energy wisp particle - elongated flowing energy strands for Dragon Ball charging aura. */
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> KI_AURA_WISP =
            ModRegistries.PARTICLE_TYPES.register("ki_aura_wisp", () -> new SimpleParticleType(false));

    /** Rock/debris particle - small rocks lifted from ground during power up. */
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> KI_ROCK =
            ModRegistries.PARTICLE_TYPES.register("ki_rock", () -> new SimpleParticleType(false));

    /** Small debris particle - tiny fragments. */
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> KI_DEBRIS =
            ModRegistries.PARTICLE_TYPES.register("ki_debris", () -> new SimpleParticleType(false));

    /** Large rock particle - bigger chunks from ground breakup. */
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> KI_ROCK_LARGE =
            ModRegistries.PARTICLE_TYPES.register("ki_rock_large", () -> new SimpleParticleType(false));

    /** Ground energy ring - expanding ring from ground burst. */
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> KI_GROUND_RING =
            ModRegistries.PARTICLE_TYPES.register("ki_ground_ring", () -> new SimpleParticleType(false));

    private ModParticles() {
    }

    /** Call from ModRegistries.register so the static fields are initialised before the registry event. */
    public static void init() {
    }
}

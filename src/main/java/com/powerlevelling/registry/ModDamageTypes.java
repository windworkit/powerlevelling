package com.powerlevelling.registry;

import com.powerlevelling.PowerLevelling;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageType;

/** Keys for our data-driven damage types (JSON in data/powerlevelling/damage_type/). */
public final class ModDamageTypes {
    public static final ResourceKey<DamageType> KI = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(PowerLevelling.MODID, "ki"));
    /** M5 melee moves (MeleeEngine). In minecraft:no_knockback: the engine applies its own knockback. */
    public static final ResourceKey<DamageType> MELEE = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(PowerLevelling.MODID, "melee"));

    private ModDamageTypes() {
    }
}

package com.powerlevelling.registry;

import com.powerlevelling.PowerLevelling;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

// Central place for all of this mod's deferred registers. Empty for now; add entries as features are built.
public final class ModRegistries {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, PowerLevelling.MODID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, PowerLevelling.MODID);
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, PowerLevelling.MODID);
    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, PowerLevelling.MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, PowerLevelling.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(PowerLevelling.MODID);

    private ModRegistries() {
    }

    public static void register(IEventBus modEventBus) {
        ModItems.init(); // forces ModItems' static fields to register into ITEMS
        ModParticles.init(); // forces ModParticles' static fields to register into PARTICLE_TYPES
        ModAttachments.init(); // forces ModAttachments' static fields to register into ATTACHMENT_TYPES
        ModEntities.init(); // forces ModEntities' static fields to register into ENTITY_TYPES
        ModSounds.init(); // forces ModSounds' static fields to register into SOUND_EVENTS
        ModCreativeTabs.init(); // forces ModCreativeTabs' static fields to register
        ATTACHMENT_TYPES.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        SOUND_EVENTS.register(modEventBus);
        PARTICLE_TYPES.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        ITEMS.register(modEventBus);
    }
}

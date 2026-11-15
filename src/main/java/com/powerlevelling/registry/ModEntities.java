package com.powerlevelling.registry;

import com.powerlevelling.entity.KiBlastEntity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;

/** Entity types. Fields register into ModRegistries.ENTITY_TYPES when this class loads. */
public final class ModEntities {
    /** Small, fast, short-lived: tracked within 8 chunks, position updates every tick, never saved to disk. */
    public static final DeferredHolder<EntityType<?>, EntityType<KiBlastEntity>> KI_BLAST =
            ModRegistries.ENTITY_TYPES.register("ki_blast",
                    () -> EntityType.Builder.<KiBlastEntity>of(KiBlastEntity::new, MobCategory.MISC)
                            .sized(0.3f, 0.3f)
                            .clientTrackingRange(8)
                            .updateInterval(1)
                            .noSave()
                            .noSummon()
                            .build("ki_blast"));

    /** The spinning disc (K2): bigger box, flat, tracked every tick, never saved. */
    public static final DeferredHolder<EntityType<?>, EntityType<com.powerlevelling.entity.KiDiscEntity>> KI_DISC =
            ModRegistries.ENTITY_TYPES.register("ki_disc",
                    () -> EntityType.Builder.<com.powerlevelling.entity.KiDiscEntity>of(com.powerlevelling.entity.KiDiscEntity::new, MobCategory.MISC)
                            .sized(1.0f, 0.3f)
                            .clientTrackingRange(8)
                            .updateInterval(1)
                            .noSave()
                            .noSummon()
                            .build("ki_disc"));

    /** M5 Training Dummy: player-sized, saved with the world (placed by its spawn egg). */
    public static final DeferredHolder<EntityType<?>, EntityType<com.powerlevelling.entity.TrainingDummyEntity>> TRAINING_DUMMY =
            ModRegistries.ENTITY_TYPES.register("training_dummy",
                    () -> EntityType.Builder.<com.powerlevelling.entity.TrainingDummyEntity>of(
                                    com.powerlevelling.entity.TrainingDummyEntity::new, MobCategory.MISC)
                            .sized(0.6f, 1.95f)
                            .clientTrackingRange(10)
                            .build("training_dummy"));

    private ModEntities() {
    }

    /** Call from ModRegistries.register so the static fields are initialised before the registry event. */
    public static void init() {
    }
}

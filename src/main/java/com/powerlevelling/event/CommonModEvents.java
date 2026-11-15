package com.powerlevelling.event;

import com.powerlevelling.PowerLevelling;
import com.powerlevelling.registry.ModItems;

import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

/** Mod-bus wiring shared by client and server. */
@EventBusSubscriber(modid = PowerLevelling.MODID)
public final class CommonModEvents {
    private CommonModEvents() {
    }

    @SubscribeEvent
    public static void onBuildCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ModItems.REBIRTH_ORB.get());
            event.accept(ModItems.STYLE_CAPSULE.get());
            event.accept(ModItems.ATTUNEMENT_CRYSTAL.get());
            event.accept(ModItems.REINCARNATION_ORB.get());
        }
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(ModItems.TRAINING_DUMMY_SPAWN_EGG.get());
        }
    }

    /** M5: attributes of the Training Dummy (a Mob needs them registered or it crashes on spawn). */
    @SubscribeEvent
    public static void onEntityAttributes(net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent event) {
        event.put(com.powerlevelling.registry.ModEntities.TRAINING_DUMMY.get(),
                com.powerlevelling.entity.TrainingDummyEntity.createAttributes().build());
    }
}

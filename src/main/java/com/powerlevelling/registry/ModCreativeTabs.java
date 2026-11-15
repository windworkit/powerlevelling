package com.powerlevelling.registry;

import com.powerlevelling.PowerLevelling;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;

/** Creative mode tab for Power Levelling items. Registered via ModRegistries.CREATIVE_MODE_TABS. */
public final class ModCreativeTabs {
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> POWER_LEVELLING =
            ModRegistries.CREATIVE_MODE_TABS.register("power_levelling",
                    () -> CreativeModeTab.builder()
                            .title(Component.translatable("itemGroup.powerlevelling"))
                            .icon(() -> new ItemStack(ModItems.REBIRTH_ORB.get()))
                            .displayItems((params, output) -> {
                                output.accept(ModItems.REBIRTH_ORB.get());
                                output.accept(ModItems.STYLE_CAPSULE.get());
                                output.accept(ModItems.ATTUNEMENT_CRYSTAL.get());
                                output.accept(ModItems.REINCARNATION_ORB.get());
                                output.accept(ModItems.TRAINING_DUMMY_SPAWN_EGG.get());
                            })
                            .build());

    private ModCreativeTabs() {
    }

    /** Call from ModRegistries.register so the static field initialises before the registry event. */
    public static void init() {
    }
}
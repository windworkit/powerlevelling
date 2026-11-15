package com.powerlevelling.registry;

import com.powerlevelling.item.AttunementCrystalItem;
import com.powerlevelling.item.RebirthOrbItem;
import com.powerlevelling.item.ReincarnationOrbItem;
import com.powerlevelling.item.StyleCapsuleItem;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredItem;

/** Item registrations. Fields register into ModRegistries.ITEMS when this class loads. */
public final class ModItems {
    public static final DeferredItem<RebirthOrbItem> REBIRTH_ORB = ModRegistries.ITEMS.registerItem("rebirth_orb",
            RebirthOrbItem::new, new Item.Properties().rarity(Rarity.EPIC).stacksTo(1));

    public static final DeferredItem<StyleCapsuleItem> STYLE_CAPSULE = ModRegistries.ITEMS.registerItem("style_capsule",
            StyleCapsuleItem::new, new Item.Properties().rarity(Rarity.UNCOMMON).stacksTo(1));
    public static final DeferredItem<AttunementCrystalItem> ATTUNEMENT_CRYSTAL = ModRegistries.ITEMS.registerItem("attunement_crystal",
            AttunementCrystalItem::new, new Item.Properties().rarity(Rarity.RARE).stacksTo(1));
    public static final DeferredItem<ReincarnationOrbItem> REINCARNATION_ORB = ModRegistries.ITEMS.registerItem("reincarnation_orb",
            ReincarnationOrbItem::new, new Item.Properties().rarity(Rarity.EPIC).stacksTo(1));

    /** M5: places a Training Dummy. DeferredSpawnEggItem also registers its tint colours. */
    public static final DeferredItem<net.neoforged.neoforge.common.DeferredSpawnEggItem> TRAINING_DUMMY_SPAWN_EGG =
            ModRegistries.ITEMS.register("training_dummy_spawn_egg",
                    () -> new net.neoforged.neoforge.common.DeferredSpawnEggItem(ModEntities.TRAINING_DUMMY,
                            0xC8A165, 0x8B2E2E, new Item.Properties()));

    private ModItems() {
    }

    /** Call from ModRegistries.register so the static fields are initialised before the registry event. */
    public static void init() {
    }
}

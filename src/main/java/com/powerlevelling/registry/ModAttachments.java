package com.powerlevelling.registry;

import com.powerlevelling.character.CharacterData;
import com.powerlevelling.data.CombatResources;
import com.powerlevelling.data.CombatState;
import com.powerlevelling.data.KiProgression;

import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;

/** Player data attachments. Fields register into ModRegistries.ATTACHMENT_TYPES when this class loads. */
public final class ModAttachments {
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<KiProgression>> KI_PROGRESSION =
            ModRegistries.ATTACHMENT_TYPES.register("ki_progression",
                    () -> AttachmentType.builder(() -> new KiProgression())
                            .serialize(KiProgression.CODEC)
                            .copyOnDeath()
                            .build());

    // Character (race, ki nature, look, tail). Copied on death; the transient `mods` are rebuilt on respawn.
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<CharacterData>> CHARACTER =
            ModRegistries.ATTACHMENT_TYPES.register("character",
                    () -> AttachmentType.builder(() -> new CharacterData())
                            .serialize(CharacterData.CODEC)
                            .copyOnDeath()
                            .build());

    // Saved, but deliberately NOT copied on death (respawn refills it in PlayerEvents).
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<CombatResources>> RESOURCES =
            ModRegistries.ATTACHMENT_TYPES.register("resources",
                    () -> AttachmentType.builder(() -> new CombatResources())
                            .serialize(CombatResources.CODEC)
                            .build());

    // No serializer = transient.
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<CombatState>> COMBAT_STATE =
            ModRegistries.ATTACHMENT_TYPES.register("combat_state",
                    () -> AttachmentType.builder(() -> new CombatState()).build());

    private ModAttachments() {
    }

    /** Call from ModRegistries.register so the static fields above are initialised before the registry event. */
    public static void init() {
    }
}

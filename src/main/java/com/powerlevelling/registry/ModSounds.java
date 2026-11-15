package com.powerlevelling.registry;

import com.powerlevelling.PowerLevelling;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;

/** Sound event registrations. Fields register into ModRegistries.SOUND_EVENTS when this class loads. */
public final class ModSounds {
    public static final DeferredHolder<SoundEvent, SoundEvent> KI_CHARGE =
            registerSound("ki_charge");
    public static final DeferredHolder<SoundEvent, SoundEvent> KI_BLAST_FIRE =
            registerSound("ki_blast_fire");
    public static final DeferredHolder<SoundEvent, SoundEvent> KI_IMPACT =
            registerSound("ki_impact");
    public static final DeferredHolder<SoundEvent, SoundEvent> STANCE_ENTER =
            registerSound("stance_enter");
    public static final DeferredHolder<SoundEvent, SoundEvent> STANCE_EXIT =
            registerSound("stance_exit");
    // M5 melee (defaults when a combat_move JSON names no sound).
    public static final DeferredHolder<SoundEvent, SoundEvent> MELEE_SWING =
            registerSound("melee_swing");
    public static final DeferredHolder<SoundEvent, SoundEvent> MELEE_HIT =
            registerSound("melee_hit");
    public static final DeferredHolder<SoundEvent, SoundEvent> MELEE_HIT_HEAVY =
            registerSound("melee_hit_heavy");
    /** Dash / dodge whoosh. */
    public static final DeferredHolder<SoundEvent, SoundEvent> DASH =
            registerSound("dash");

    // Guard / block sounds (MT-001)
    public static final DeferredHolder<SoundEvent, SoundEvent> GUARD_START =
            registerSound("guard_start");
    public static final DeferredHolder<SoundEvent, SoundEvent> GUARD_PERFECT =
            registerSound("guard_perfect");
    public static final DeferredHolder<SoundEvent, SoundEvent> GUARD_BREAK =
            registerSound("guard_break");

    // Lock-on targeting sounds (MT-002)
    public static final DeferredHolder<SoundEvent, SoundEvent> LOCKON_ACQUIRE =
            registerSound("lockon_acquire");
    public static final DeferredHolder<SoundEvent, SoundEvent> LOCKON_BREAK =
            registerSound("lockon_break");

    private static DeferredHolder<SoundEvent, SoundEvent> registerSound(String name) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(PowerLevelling.MODID, name);
        return ModRegistries.SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(id));
    }

    private ModSounds() {
    }

    /** Call from ModRegistries.register so the static fields are initialised before the registry event. */
    public static void init() {
    }
}
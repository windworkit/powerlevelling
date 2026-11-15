package com.powerlevelling.character;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.powerlevelling.PowerLevelling;

import net.minecraft.resources.ResourceLocation;

/**
 * Per-player character (Addendum B.1): race, ki nature, look, tail. Mutable; saved through {@link #CODEC}
 * (every field optional with a default, so older saves and hand edits load). {@link #mods} is transient and is
 * rebuilt by CharacterService.recomputeMods.
 */
public class CharacterData {
    public static final int VERSION = 1;
    public static final ResourceLocation DEFAULT_RACE = ResourceLocation.fromNamespaceAndPath(PowerLevelling.MODID, "saiyan");
    public static final ResourceLocation DEFAULT_NATURE = ResourceLocation.fromNamespaceAndPath(PowerLevelling.MODID, "balanced");

    public static final Codec<CharacterData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("version", VERSION).forGetter(d -> d.version),
            Codec.BOOL.optionalFieldOf("created", false).forGetter(d -> d.created),
            ResourceLocation.CODEC.optionalFieldOf("race", DEFAULT_RACE).forGetter(d -> d.race),
            ResourceLocation.CODEC.optionalFieldOf("nature", DEFAULT_NATURE).forGetter(d -> d.nature),
            Appearance.CODEC.optionalFieldOf("appearance", Appearance.DEFAULT).forGetter(d -> d.appearance),
            TailState.CODEC.optionalFieldOf("tail", TailState.NONE).forGetter(d -> d.tail),
            Codec.BOOL.optionalFieldOf("tail_wrapped", false).forGetter(d -> d.tailWrapped),
            Codec.LONG.optionalFieldOf("tail_regrow_at", 0L).forGetter(d -> d.tailRegrowAt),
            Codec.BOOL.optionalFieldOf("free_recreate", false).forGetter(d -> d.freeRecreate)
    ).apply(i, CharacterData::new));

    public int version = VERSION;
    /** False until the player finishes character creation (or an admin sets a race/nature). */
    public boolean created = false;
    public ResourceLocation race = DEFAULT_RACE;
    public ResourceLocation nature = DEFAULT_NATURE;
    public Appearance appearance = Appearance.DEFAULT;
    public TailState tail = TailState.NONE;
    /** Tail worn wrapped around the waist (only meaningful when tail == PRESENT). */
    public boolean tailWrapped = false;
    /** Game time (ticks) when a LOST tail regrows; 0 = not scheduled. */
    public long tailRegrowAt = 0L;
    /** Set when race/nature went missing (datapack changed): the next creation is free and consumes no item. */
    public boolean freeRecreate = false;

    /** Transient, not saved. NEUTRAL until recomputeMods runs. */
    public CharacterMods mods = CharacterMods.NEUTRAL;

    public CharacterData() {
    }

    private CharacterData(int version, boolean created, ResourceLocation race, ResourceLocation nature,
                          Appearance appearance, TailState tail, boolean tailWrapped, long tailRegrowAt, boolean freeRecreate) {
        this.version = version;
        this.created = created;
        this.race = race;
        this.nature = nature;
        this.appearance = appearance;
        this.tail = tail;
        this.tailWrapped = tailWrapped;
        this.tailRegrowAt = tailRegrowAt;
        this.freeRecreate = freeRecreate;
    }
}

package com.powerlevelling.network;

import java.util.HashMap;
import java.util.Map;

import com.powerlevelling.PowerLevelling;
import com.powerlevelling.data.KiProgression;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S2C: race/form, mastery track level/xp (Track ordinal order), character level/EXP, unspent points,
 * the 6 stats (Stat ordinal order), Zenkai cooldown remaining, the config caps the screen needs, and the 4-slot
 * ki attack loadout (attack ids; empty string = empty slot), and (C3) the character the client needs for stat previews:
 * race id, ki nature id, whether the character was created, and the baseBonusHealth config. Owner-only: other players'
 * cosmetics travel in the C4 character sync, never here.
 * K0: {@code attackUses} = uses per ki attack id (per-attack mastery; the client derives the tier with
 * KiProgression.tierForUses). Only sent when the progression is synced (join, level/stat changes, tier-ups), so the
 * client's use count can lag behind the server's between tier-ups; tiers are always exact.
 */
public record ProgressionSyncPayload(ResourceLocation race, ResourceLocation form, int[] levels, int[] xp,
                                     int level, int exp, int expToNext, int unspent, int[] stats,
                                     int zenkaiCooldownTicks, int levelCap, int statCap, String[] loadout,
                                     String characterRace, String characterNature, boolean characterCreated,
                                     float baseBonusHealth, Map<String, Integer> attackUses)
        implements CustomPacketPayload {
    public static final Type<ProgressionSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(PowerLevelling.MODID, "progression_sync"));

    // Hand-written codec: too many fields for StreamCodec.composite.
    public static final StreamCodec<RegistryFriendlyByteBuf, ProgressionSyncPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeResourceLocation(p.race());
                buf.writeResourceLocation(p.form());
                for (int i = 0; i < KiProgression.Track.values().length; i++) {
                    buf.writeVarInt(p.levels()[i]);
                    buf.writeVarInt(p.xp()[i]);
                }
                buf.writeVarInt(p.level());
                buf.writeVarInt(p.exp());
                buf.writeVarInt(p.expToNext());
                buf.writeVarInt(p.unspent());
                for (int i = 0; i < KiProgression.Stat.values().length; i++) {
                    buf.writeVarInt(p.stats()[i]);
                }
                buf.writeVarInt(p.zenkaiCooldownTicks());
                buf.writeVarInt(p.levelCap());
                buf.writeVarInt(p.statCap());
                for (int i = 0; i < KiProgression.LOADOUT_SLOTS; i++) buf.writeUtf(p.loadout()[i]);
                buf.writeUtf(p.characterRace());
                buf.writeUtf(p.characterNature());
                buf.writeBoolean(p.characterCreated());
                buf.writeFloat(p.baseBonusHealth());
                buf.writeVarInt(p.attackUses().size());
                for (Map.Entry<String, Integer> e : p.attackUses().entrySet()) {
                    buf.writeUtf(e.getKey());
                    buf.writeVarInt(e.getValue());
                }
            },
            buf -> {
                ResourceLocation race = buf.readResourceLocation();
                ResourceLocation form = buf.readResourceLocation();
                int n = KiProgression.Track.values().length;
                int[] levels = new int[n];
                int[] xp = new int[n];
                for (int i = 0; i < n; i++) {
                    levels[i] = buf.readVarInt();
                    xp[i] = buf.readVarInt();
                }
                int level = buf.readVarInt();
                int exp = buf.readVarInt();
                int expToNext = buf.readVarInt();
                int unspent = buf.readVarInt();
                int[] stats = new int[KiProgression.Stat.values().length];
                for (int i = 0; i < stats.length; i++) stats[i] = buf.readVarInt();
                int zenkai = buf.readVarInt();
                int levelCap = buf.readVarInt();
                int statCap = buf.readVarInt();
                String[] loadout = new String[KiProgression.LOADOUT_SLOTS];
                for (int i = 0; i < loadout.length; i++) loadout[i] = buf.readUtf();
                String characterRace = buf.readUtf();
                String characterNature = buf.readUtf();
                boolean characterCreated = buf.readBoolean();
                float baseBonusHealth = buf.readFloat();
                int usesCount = buf.readVarInt();
                Map<String, Integer> attackUses = new HashMap<>();
                for (int i = 0; i < usesCount; i++) {
                    String key = buf.readUtf();
                    attackUses.put(key, buf.readVarInt());
                }
                return new ProgressionSyncPayload(race, form, levels, xp, level, exp, expToNext, unspent, stats,
                        zenkai, levelCap, statCap, loadout, characterRace, characterNature, characterCreated, baseBonusHealth, attackUses);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

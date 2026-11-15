package com.powerlevelling.network;

import com.powerlevelling.PowerLevelling;
import com.powerlevelling.character.CharacterSnapshot;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** S2C: how a player's character looks (race, nature, look, tail), sent to the player and everyone tracking them. */
public record CharacterSyncPayload(int entityId, CharacterSnapshot snapshot) implements CustomPacketPayload {
    public static final Type<CharacterSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(PowerLevelling.MODID, "character_sync"));

    public static final StreamCodec<io.netty.buffer.ByteBuf, CharacterSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CharacterSyncPayload::entityId,
            CharacterSnapshot.STREAM_CODEC, CharacterSyncPayload::snapshot,
            CharacterSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

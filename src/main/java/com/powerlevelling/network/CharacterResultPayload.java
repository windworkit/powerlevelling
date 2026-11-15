package com.powerlevelling.network;

import com.powerlevelling.PowerLevelling;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S2C: the verdict on a CharacterSubmitPayload. {@code reasonKey} is empty on success, otherwise the tail of the lang key
 * {@code gui.powerlevelling.character.reason.<reasonKey>}.
 */
public record CharacterResultPayload(boolean ok, String reasonKey) implements CustomPacketPayload {
    public static final Type<CharacterResultPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(PowerLevelling.MODID, "character_result"));

    public static final StreamCodec<io.netty.buffer.ByteBuf, CharacterResultPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, CharacterResultPayload::ok,
            ByteBufCodecs.STRING_UTF8, CharacterResultPayload::reasonKey,
            CharacterResultPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

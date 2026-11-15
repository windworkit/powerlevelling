package com.powerlevelling.network;

import com.powerlevelling.PowerLevelling;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** C2S: spend {@code amount} unspent points on the stat with id {@code statId}. The server validates everything. */
public record SpendStatPayload(String statId, int amount) implements CustomPacketPayload {
    public static final Type<SpendStatPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(PowerLevelling.MODID, "spend_stat"));

    public static final StreamCodec<io.netty.buffer.ByteBuf, SpendStatPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, SpendStatPayload::statId,
            ByteBufCodecs.VAR_INT, SpendStatPayload::amount,
            SpendStatPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

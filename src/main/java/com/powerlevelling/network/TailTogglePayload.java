package com.powerlevelling.network;

import com.powerlevelling.PowerLevelling;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** C2S: wrap / unwrap the tail (no data; rate-limited and only honoured when the tail is PRESENT). */
public record TailTogglePayload() implements CustomPacketPayload {
    public static final Type<TailTogglePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(PowerLevelling.MODID, "tail_toggle"));

    public static final StreamCodec<io.netty.buffer.ByteBuf, TailTogglePayload> STREAM_CODEC =
            StreamCodec.unit(new TailTogglePayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

package com.powerlevelling.network;

import com.powerlevelling.PowerLevelling;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** C2S: the player pressed "Yes" on the Rebirth Orb confirm screen. Carries no data. */
public record RespecConfirmPayload() implements CustomPacketPayload {
    public static final Type<RespecConfirmPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(PowerLevelling.MODID, "respec_confirm"));

    public static final StreamCodec<io.netty.buffer.ByteBuf, RespecConfirmPayload> STREAM_CODEC =
            StreamCodec.unit(new RespecConfirmPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

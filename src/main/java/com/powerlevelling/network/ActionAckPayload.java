package com.powerlevelling.network;

import com.powerlevelling.PowerLevelling;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** S2C: the server's verdict on one CombatInputPayload. A rejected ack makes the client roll back its prediction. */
public record ActionAckPayload(int seq, boolean accepted) implements CustomPacketPayload {
    public static final Type<ActionAckPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(PowerLevelling.MODID, "action_ack"));

    public static final StreamCodec<io.netty.buffer.ByteBuf, ActionAckPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ActionAckPayload::seq,
            ByteBufCodecs.BOOL, ActionAckPayload::accepted,
            ActionAckPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

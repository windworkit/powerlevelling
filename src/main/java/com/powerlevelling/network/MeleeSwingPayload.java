package com.powerlevelling.network;

import com.powerlevelling.PowerLevelling;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S2C to players tracking an attacker (NOT the attacker, who predicts): entity started melee move {@code move}, or its move
 * was cut short ({@code cancel}). Observers play the move's pose for its frame-data length.
 */
public record MeleeSwingPayload(int entityId, String move, boolean cancel) implements CustomPacketPayload {
    public static final Type<MeleeSwingPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(PowerLevelling.MODID, "melee_swing"));

    public static final StreamCodec<io.netty.buffer.ByteBuf, MeleeSwingPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, MeleeSwingPayload::entityId,
            ByteBufCodecs.STRING_UTF8, MeleeSwingPayload::move,
            ByteBufCodecs.BOOL, MeleeSwingPayload::cancel,
            MeleeSwingPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

package com.powerlevelling.network;

import com.powerlevelling.PowerLevelling;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S2C to the attacker (or victim): the server's verdict on melee state. One MeleeInputPayload can get two of these: BUFFERED
 * first, then STARTED or REJECTED when the buffer resolves.
 * <ul>
 * <li>STARTED: input {@code seq} started {@code move} as chain step {@code chain}.</li>
 * <li>BUFFERED: input {@code seq} is waiting for the cancel window.</li>
 * <li>REJECTED: input {@code seq} did nothing (the client cancels its predicted swing).</li>
 * <li>CANCELLED: the server ended the current move (skill cancel, stance exit, hit); seq = -1.</li>
 * <li>STUNNED: you are in hitstun for {@code stunTicks} (move cancelled, inputs blocked); seq = -1.</li>
 * </ul>
 */
public record MeleeStatePayload(int seq, int result, String move, int chain, int stunTicks) implements CustomPacketPayload {
    public static final int STARTED = 0;
    public static final int BUFFERED = 1;
    public static final int REJECTED = 2;
    public static final int CANCELLED = 3;
    public static final int STUNNED = 4;

    public static final Type<MeleeStatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(PowerLevelling.MODID, "melee_state"));

    public static final StreamCodec<io.netty.buffer.ByteBuf, MeleeStatePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, MeleeStatePayload::seq,
            ByteBufCodecs.VAR_INT, MeleeStatePayload::result,
            ByteBufCodecs.STRING_UTF8, MeleeStatePayload::move,
            ByteBufCodecs.VAR_INT, MeleeStatePayload::chain,
            ByteBufCodecs.VAR_INT, MeleeStatePayload::stunTicks,
            MeleeStatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

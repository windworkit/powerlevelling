package com.powerlevelling.network;

import com.powerlevelling.PowerLevelling;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * C2S: one melee press (MeleeInput ordinal). {@code seq} shares the CombatInputPayload counter (ClientCombat.register), so
 * it is unique per client; {@code clientTick} is the client's input tick (debug / later latency grace); yaw/pitch = look
 * at the moment of input (reserved for M7 air moves / M11 lock-on facing; the server aims with the player's live rotation).
 */
public record MeleeInputPayload(int input, int seq, int clientTick, float yaw, float pitch) implements CustomPacketPayload {
    public static final Type<MeleeInputPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(PowerLevelling.MODID, "melee_input"));

    public static final StreamCodec<io.netty.buffer.ByteBuf, MeleeInputPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, MeleeInputPayload::input,
            ByteBufCodecs.VAR_INT, MeleeInputPayload::seq,
            ByteBufCodecs.VAR_INT, MeleeInputPayload::clientTick,
            ByteBufCodecs.FLOAT, MeleeInputPayload::yaw,
            ByteBufCodecs.FLOAT, MeleeInputPayload::pitch,
            MeleeInputPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

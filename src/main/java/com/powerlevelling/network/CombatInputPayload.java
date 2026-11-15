package com.powerlevelling.network;

import com.powerlevelling.PowerLevelling;
import com.powerlevelling.combat.CombatAction;
import com.powerlevelling.combat.Phase;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * C2S: one input edge. {@code seq} increments per client; yaw/pitch are the look direction at the moment of input.
 * {@code moveYaw} is the world yaw of the player's movement input (equal to yaw when standing still).
 */
public record CombatInputPayload(CombatAction action, Phase phase, int seq, float yaw, float pitch, float moveYaw)
        implements CustomPacketPayload {
    public static final Type<CombatInputPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(PowerLevelling.MODID, "combat_input"));

    public static final StreamCodec<io.netty.buffer.ByteBuf, CombatInputPayload> STREAM_CODEC = StreamCodec.composite(
            CombatAction.STREAM_CODEC, CombatInputPayload::action,
            Phase.STREAM_CODEC, CombatInputPayload::phase,
            ByteBufCodecs.VAR_INT, CombatInputPayload::seq,
            ByteBufCodecs.FLOAT, CombatInputPayload::yaw,
            ByteBufCodecs.FLOAT, CombatInputPayload::pitch,
            ByteBufCodecs.FLOAT, CombatInputPayload::moveYaw,
            CombatInputPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

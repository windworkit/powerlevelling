package com.powerlevelling.network;

import com.powerlevelling.PowerLevelling;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S2C: a skill slot's cooldown. Sent when a cooldown starts ({@code remaining == total}) and when the loadout changes
 * ({@code remaining} may be less than {@code total}; 0 clears). The client counts it down itself.
 */
public record CooldownSyncPayload(int slot, int remaining, int total) implements CustomPacketPayload {
    public static final Type<CooldownSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(PowerLevelling.MODID, "cooldown_sync"));

    public static final StreamCodec<io.netty.buffer.ByteBuf, CooldownSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CooldownSyncPayload::slot,
            ByteBufCodecs.VAR_INT, CooldownSyncPayload::remaining,
            ByteBufCodecs.VAR_INT, CooldownSyncPayload::total,
            CooldownSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

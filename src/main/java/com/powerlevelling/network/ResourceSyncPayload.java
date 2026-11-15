package com.powerlevelling.network;

import com.powerlevelling.PowerLevelling;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** S2C: the owner's current combat resources. */
public record ResourceSyncPayload(float ki, float maxKi, float stamina, float maxStamina, float endurance, boolean kiBreak)
        implements CustomPacketPayload {
    public static final Type<ResourceSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(PowerLevelling.MODID, "resource_sync"));

    public static final StreamCodec<io.netty.buffer.ByteBuf, ResourceSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, ResourceSyncPayload::ki,
            ByteBufCodecs.FLOAT, ResourceSyncPayload::maxKi,
            ByteBufCodecs.FLOAT, ResourceSyncPayload::stamina,
            ByteBufCodecs.FLOAT, ResourceSyncPayload::maxStamina,
            ByteBufCodecs.FLOAT, ResourceSyncPayload::endurance,
            ByteBufCodecs.BOOL, ResourceSyncPayload::kiBreak,
            ResourceSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

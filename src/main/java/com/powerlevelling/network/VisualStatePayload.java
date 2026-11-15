package com.powerlevelling.network;

import com.powerlevelling.PowerLevelling;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S2C: what an entity looks like (flags), sent to everyone tracking it. {@code chargeTicksOrStrength} is how long
 * the charge has been running (so late observers continue the aura ramp); clients count on from there themselves.
 */
public record VisualStatePayload(int entityId, int flags, float chargeTicksOrStrength) implements CustomPacketPayload {
    public static final int CHARGING = 1;
    public static final int STANCE = 2;
    public static final int KI_BREAK = 4;
    public static final int SUPER_SAIYAN = 8;
    public static final int TRANSFORMING = 16;
    public static final int GUARDING = 32;
    public static final int PERFECT_GUARD = 64;
    public static final int LOCKED = 128;
    public static final int TURBO = 256;

    public static final Type<VisualStatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(PowerLevelling.MODID, "visual_state"));

    public static final StreamCodec<io.netty.buffer.ByteBuf, VisualStatePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, VisualStatePayload::entityId,
            ByteBufCodecs.VAR_INT, VisualStatePayload::flags,
            ByteBufCodecs.FLOAT, VisualStatePayload::chargeTicksOrStrength,
            VisualStatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

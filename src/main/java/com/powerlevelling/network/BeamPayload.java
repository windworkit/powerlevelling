package com.powerlevelling.network;

import com.powerlevelling.PowerLevelling;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S2C (K7): one beam of a player. START when it fires, UPDATE every server tick while it runs (current, turn-clamped
 * direction and the length to the first block / entity), STOP when it ends for any reason. The client draws it from the
 * owner's hands along (dx, dy, dz) for {@code length} blocks (BeamRenderer); {@code hit} = the tip touches something (block
 * or entity), {@code charge} 0..1 only matters for the look. Colours are already resolved on the server. Sent to the owner
 * and everyone tracking them. Hand-written codec (12 fields, more than StreamCodec.composite takes).
 */
public record BeamPayload(int phase, int ownerId, ResourceLocation attack, float length, float radius, int color,
                          int coreColor, float charge, float dx, float dy, float dz, boolean hit)
        implements CustomPacketPayload {
    public static final int PHASE_START = 0;
    public static final int PHASE_UPDATE = 1;
    public static final int PHASE_STOP = 2;

    public static final Type<BeamPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(PowerLevelling.MODID, "beam"));

    public static final StreamCodec<ByteBuf, BeamPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeByte(p.phase());
                buf.writeInt(p.ownerId());
                ResourceLocation.STREAM_CODEC.encode(buf, p.attack());
                buf.writeFloat(p.length());
                buf.writeFloat(p.radius());
                buf.writeInt(p.color());
                buf.writeInt(p.coreColor());
                buf.writeFloat(p.charge());
                buf.writeFloat(p.dx());
                buf.writeFloat(p.dy());
                buf.writeFloat(p.dz());
                buf.writeBoolean(p.hit());
            },
            buf -> new BeamPayload(buf.readByte(), buf.readInt(), ResourceLocation.STREAM_CODEC.decode(buf),
                    buf.readFloat(), buf.readFloat(), buf.readInt(), buf.readInt(), buf.readFloat(),
                    buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

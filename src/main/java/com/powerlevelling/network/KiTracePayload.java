package com.powerlevelling.network;

import com.powerlevelling.PowerLevelling;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * S2C: a hitscan line (needle). {@code phase} AIM = the faint aim line during the wind-up (re-sent every tick, the client
 * keeps the newest one per shooter for ~2 ticks), FIRE = the shot (fades over 6 ticks). {@code from} is the fingertip,
 * {@code to} the end (block hit or max range); {@code hitCount} = entities hit (FIRE only). Colours are already resolved on
 * the server (the shooter's ki colour if the attack takes it). Sent to the shooter and everyone tracking them.
 * Hand-written codec (8 fields).
 */
public record KiTracePayload(int shooterId, ResourceLocation attack, Vec3 from, Vec3 to, int hitCount, int phase,
                             int color, int coreColor) implements CustomPacketPayload {
    public static final int PHASE_AIM = 0;
    public static final int PHASE_FIRE = 1;

    public static final Type<KiTracePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(PowerLevelling.MODID, "ki_trace"));

    public static final StreamCodec<ByteBuf, KiTracePayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeInt(p.shooterId());
                ResourceLocation.STREAM_CODEC.encode(buf, p.attack());
                buf.writeFloat((float) p.from().x); buf.writeFloat((float) p.from().y); buf.writeFloat((float) p.from().z);
                buf.writeFloat((float) p.to().x); buf.writeFloat((float) p.to().y); buf.writeFloat((float) p.to().z);
                buf.writeByte(p.hitCount());
                buf.writeByte(p.phase());
                buf.writeInt(p.color());
                buf.writeInt(p.coreColor());
            },
            buf -> new KiTracePayload(buf.readInt(), ResourceLocation.STREAM_CODEC.decode(buf),
                    new Vec3(buf.readFloat(), buf.readFloat(), buf.readFloat()),
                    new Vec3(buf.readFloat(), buf.readFloat(), buf.readFloat()),
                    buf.readByte(), buf.readByte(), buf.readInt(), buf.readInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

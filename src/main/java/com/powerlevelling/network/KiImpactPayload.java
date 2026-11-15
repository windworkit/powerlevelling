package com.powerlevelling.network;

import com.powerlevelling.PowerLevelling;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S2C: a ki blast hit something at (x, y, z). The client plays the impact burst, flash and sound itself (colors and
 * sounds come from the attack definition; {@code kiColor} >= 0 overrides the colour, -1 = attack's). {@code size} is the explosion radius, or the blast diameter if none.
 * {@code style} (STYLE_*) picks the extra visual: an expanding ground ring for sphere explosions, the ring + dome of a burst,
 * pierce sparks for a needle. Shake / hitstop / fov punch are read from the attack's present block on the client.
 */
public record KiImpactPayload(float x, float y, float z, ResourceLocation attack, float size, boolean explosion, int kiColor,
                              int style) implements CustomPacketPayload {
    public static final int STYLE_DEFAULT = 0;
    public static final int STYLE_EXPLOSION_RING = 1;
    public static final int STYLE_RING = 2;
    public static final int STYLE_PIERCE = 3;
    /** Barrage hit: a few small sparks, quiet. */
    public static final int STYLE_SPARK_SMALL = 4;
    /** Disc hit: crossed slash lines + a fan of sparks. */
    public static final int STYLE_SLICE = 5;

    /** Old shape (no style). */
    public KiImpactPayload(float x, float y, float z, ResourceLocation attack, float size, boolean explosion, int kiColor) {
        this(x, y, z, attack, size, explosion, kiColor, STYLE_DEFAULT);
    }

    public static final Type<KiImpactPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(PowerLevelling.MODID, "ki_impact"));

    public static final StreamCodec<io.netty.buffer.ByteBuf, KiImpactPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeFloat(p.x()); buf.writeFloat(p.y()); buf.writeFloat(p.z());
                ResourceLocation.STREAM_CODEC.encode(buf, p.attack());
                buf.writeFloat(p.size()); buf.writeBoolean(p.explosion()); buf.writeInt(p.kiColor()); buf.writeByte(p.style());
            },
            buf -> new KiImpactPayload(buf.readFloat(), buf.readFloat(), buf.readFloat(),
                    ResourceLocation.STREAM_CODEC.decode(buf), buf.readFloat(), buf.readBoolean(), buf.readInt(), buf.readByte()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

package com.powerlevelling.combat;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Edge of a key/button: pressed or released. */
public enum Phase {
    PRESS,
    RELEASE;

    public static final StreamCodec<ByteBuf, Phase> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(Phase::fromOrdinal, Phase::ordinal);

    /** Out-of-range ids (a bad packet) fall back to RELEASE, the harmless edge. */
    public static Phase fromOrdinal(int id) {
        Phase[] v = values();
        return id >= 0 && id < v.length ? v[id] : RELEASE;
    }
}

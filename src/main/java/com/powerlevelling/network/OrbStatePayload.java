package com.powerlevelling.network;

import com.powerlevelling.PowerLevelling;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S2C: the "ki forming in the hand" orb of a player (sent to the player and everyone tracking them on start / stop and
 * again to new observers on StartTracking). Clients compute the current radius themselves from
 * (gameTime - startGameTime) with {@link com.powerlevelling.ki.KiOrb#radius}; see there for the meaning of the numbers.
 *
 * @param attackId      the attack being formed (or "" when inactive)
 * @param startGameTime server game time the orb started at
 * @param durationTicks wind-up ticks, or the charge's max_ticks (the orb is "full" after this long)
 * @param growTicks     ticks of the initial ease-out grow from the start size to minRadius (windup: = duration, charge: min_ticks)
 * @param minRadius     orb radius at the end of growTicks (blocks)
 * @param maxRadius     orb radius at durationTicks (blocks; == minRadius for a wind-up)
 * @param color         shell colour (RGB) - already resolved by KiOrbs.resolveOrbColor (later: the player's ki colour)
 * @param coreColor     core colour (RGB)
 * @param hand          0 = main hand, 1 = off hand, 2 = both hands, 3 = finger (small glow at the main fingertip),
 *                      4 = none (no orb: the whole body flares)
 * @param anchor        where the orb sits (ANCHOR_*): palm, chest, overhead (between both hands above the head), fingertip, body
 * @param active        false = the orb is gone (fired, cancelled, ...); all other fields are then meaningless
 */
public record OrbStatePayload(int entityId, String attackId, long startGameTime, int durationTicks, int growTicks,
                              float minRadius, float maxRadius, int color, int coreColor, int hand, int anchor, boolean active)
        implements CustomPacketPayload {
    public static final int HAND_MAIN = 0;
    public static final int HAND_OFF = 1;
    public static final int HAND_BOTH = 2;
    public static final int HAND_FINGER = 3;
    public static final int HAND_NONE = 4;

    public static final int ANCHOR_PALM = 0;
    public static final int ANCHOR_CHEST = 1;
    public static final int ANCHOR_OVERHEAD = 2;
    public static final int ANCHOR_FINGERTIP = 3;
    public static final int ANCHOR_BODY = 4;
    /** Cupped at the hip on the main-hand side (beam charge). */
    public static final int ANCHOR_HIP = 5;

    public static final Type<OrbStatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(PowerLevelling.MODID, "orb_state"));

    /** 12 fields: more than StreamCodec.composite takes, so encoded by hand (field order = record order). */
    public static final StreamCodec<ByteBuf, OrbStatePayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public OrbStatePayload decode(ByteBuf buf) {
            return new OrbStatePayload(
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.VAR_LONG.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.FLOAT.decode(buf),
                    ByteBufCodecs.FLOAT.decode(buf),
                    ByteBufCodecs.INT.decode(buf),
                    ByteBufCodecs.INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf));
        }

        @Override
        public void encode(ByteBuf buf, OrbStatePayload p) {
            ByteBufCodecs.VAR_INT.encode(buf, p.entityId);
            ByteBufCodecs.STRING_UTF8.encode(buf, p.attackId);
            ByteBufCodecs.VAR_LONG.encode(buf, p.startGameTime);
            ByteBufCodecs.VAR_INT.encode(buf, p.durationTicks);
            ByteBufCodecs.VAR_INT.encode(buf, p.growTicks);
            ByteBufCodecs.FLOAT.encode(buf, p.minRadius);
            ByteBufCodecs.FLOAT.encode(buf, p.maxRadius);
            ByteBufCodecs.INT.encode(buf, p.color);
            ByteBufCodecs.INT.encode(buf, p.coreColor);
            ByteBufCodecs.VAR_INT.encode(buf, p.hand);
            ByteBufCodecs.VAR_INT.encode(buf, p.anchor);
            ByteBufCodecs.BOOL.encode(buf, p.active);
        }
    };

    /** The "orb is gone" message. */
    public static OrbStatePayload inactive(int entityId) {
        return new OrbStatePayload(entityId, "", 0L, 0, 0, 0f, 0f, 0xFFFFFF, 0xFFFFFF, HAND_MAIN, ANCHOR_PALM, false);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

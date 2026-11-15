package com.powerlevelling.network;

import com.powerlevelling.PowerLevelling;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** S2C: ask the client to open the character creation screen (join prompt, the clickable chat [Create], /powerlevelling character open). */
public record OpenCharacterScreenPayload() implements CustomPacketPayload {
    public static final Type<OpenCharacterScreenPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(PowerLevelling.MODID, "open_character_screen"));

    public static final StreamCodec<io.netty.buffer.ByteBuf, OpenCharacterScreenPayload> STREAM_CODEC =
            StreamCodec.unit(new OpenCharacterScreenPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

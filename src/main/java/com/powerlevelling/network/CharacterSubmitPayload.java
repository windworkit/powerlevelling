package com.powerlevelling.network;

import com.powerlevelling.PowerLevelling;
import com.powerlevelling.character.CharacterChoice;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * C2S: create or change the character. CREATE = first creation (or a free re-creation); STYLE = look + ki/aura (Style
 * Capsule); NATURE = ki nature only (Attunement Crystal); FULL = everything (Reincarnation Orb). The server validates it all.
 */
public record CharacterSubmitPayload(Mode mode, CharacterChoice choice) implements CustomPacketPayload {
    public enum Mode {
        CREATE, STYLE, NATURE, FULL
    }

    public static final Type<CharacterSubmitPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(PowerLevelling.MODID, "character_submit"));

    private static final StreamCodec<ByteBuf, Mode> MODE_CODEC = ByteBufCodecs.VAR_INT.map(i -> {
        Mode[] all = Mode.values();
        if (i < 0 || i >= all.length) throw new io.netty.handler.codec.DecoderException("Bad character mode " + i);
        return all[i];
    }, Mode::ordinal);

    public static final StreamCodec<ByteBuf, CharacterSubmitPayload> STREAM_CODEC = StreamCodec.composite(
            MODE_CODEC, CharacterSubmitPayload::mode,
            CharacterChoice.STREAM_CODEC, CharacterSubmitPayload::choice,
            CharacterSubmitPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

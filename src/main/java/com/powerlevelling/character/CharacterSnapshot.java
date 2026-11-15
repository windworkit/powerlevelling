package com.powerlevelling.character;

import java.util.ArrayList;
import java.util.List;

import com.powerlevelling.PowerLevelling;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * What the client needs to draw (and, for the owner, route) a character: the synced, immutable view of
 * {@link CharacterData} (Addendum B.1). Height and build travel as one byte each (0.8-1.2 in 255 steps), so a value that
 * went through the wire differs from the server's float by at most ~0.0008. No client-only imports.
 * {@code freeRecreate} is only meaningful for the owner (K opens creation for free); observers just ignore it.
 */
public record CharacterSnapshot(boolean created, ResourceLocation race, ResourceLocation nature, ResourceLocation hairStyle,
                                int hairColor, int eyeColor, int kiColor, int auraColor, int skinColor,
                                float height, float build, int eyeStyle, TailState tail, boolean tailWrapped,
                                List<ResourceLocation> parts, boolean freeRecreate) {

    /** Used when an id from the wire cannot be parsed; never in any registry, so it renders nothing / fails validation. */
    public static final ResourceLocation INVALID_ID = ResourceLocation.fromNamespaceAndPath(PowerLevelling.MODID, "invalid");
    /** Max parts on the wire (slots are few; this only bounds a malformed packet). */
    public static final int MAX_PARTS = 16;
    private static final int ID_MAX_LENGTH = 256;
    private static final StreamCodec<ByteBuf, String> ID_CODEC = ByteBufCodecs.stringUtf8(ID_MAX_LENGTH);

    private static final int FLAG_CREATED = 1;
    private static final int FLAG_FREE = 2;
    private static final int FLAG_WRAPPED = 4;
    private static final int TAIL_SHIFT = 3;

    public CharacterSnapshot {
        parts = List.copyOf(parts);
    }

    public static CharacterSnapshot of(CharacterData d) {
        Appearance a = d.appearance;
        return new CharacterSnapshot(d.created, d.race, d.nature, a.hairStyle(), a.hairColor(), a.eyeColor(), a.kiColor(),
                a.auraColor(), a.skinColor(), a.height(), a.build(), a.eyeStyle(), d.tail, d.tailWrapped, a.parts(),
                d.freeRecreate);
    }

    /** The look part as an Appearance (for previews). */
    public Appearance appearance() {
        return new Appearance(hairStyle, hairColor, eyeColor, kiColor, auraColor, skinColor, height, build, parts, eyeStyle);
    }

    // ---- wire helpers (shared with CharacterChoice) ---------------------------------------------------------------

    static void writeId(ByteBuf buf, ResourceLocation id) {
        ID_CODEC.encode(buf, id.toString());
    }

    /** Malformed ids become {@link #INVALID_ID} instead of throwing (a bad id must not disconnect anyone). */
    static ResourceLocation readId(ByteBuf buf) {
        ResourceLocation id = ResourceLocation.tryParse(ID_CODEC.decode(buf));
        return id == null ? INVALID_ID : id;
    }

    static byte quantize(float v) {
        float clamped = Float.isNaN(v) ? 1.0f : Mth.clamp(v, Race.SIZE_MIN, Race.SIZE_MAX);
        return (byte) Math.round((clamped - Race.SIZE_MIN) / (Race.SIZE_MAX - Race.SIZE_MIN) * 255f);
    }

    static float dequantize(int unsigned) {
        return Race.SIZE_MIN + (unsigned & 0xFF) / 255f * (Race.SIZE_MAX - Race.SIZE_MIN);
    }

    public static final StreamCodec<ByteBuf, CharacterSnapshot> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public CharacterSnapshot decode(ByteBuf buf) {
            int flags = buf.readUnsignedByte();
            ResourceLocation race = readId(buf);
            ResourceLocation nature = readId(buf);
            ResourceLocation hair = readId(buf);
            int hairColor = buf.readInt();
            int eyeColor = buf.readInt();
            int kiColor = buf.readInt();
            int auraColor = buf.readInt();
            int skinColor = buf.readInt();
            float height = dequantize(buf.readUnsignedByte());
            float build = dequantize(buf.readUnsignedByte());
            int eyeStyle = buf.readUnsignedByte();
            int n = ByteBufCodecs.VAR_INT.decode(buf);
            if (n < 0 || n > MAX_PARTS) throw new io.netty.handler.codec.DecoderException("Too many character parts: " + n);
            List<ResourceLocation> parts = new ArrayList<>(n);
            for (int i = 0; i < n; i++) parts.add(readId(buf));
            TailState[] tails = TailState.values();
            TailState tail = tails[Mth.clamp((flags >> TAIL_SHIFT) & 3, 0, tails.length - 1)];
            return new CharacterSnapshot((flags & FLAG_CREATED) != 0, race, nature, hair, hairColor, eyeColor, kiColor,
                    auraColor, skinColor, height, build, eyeStyle, tail, (flags & FLAG_WRAPPED) != 0, parts,
                    (flags & FLAG_FREE) != 0);
        }

        @Override
        public void encode(ByteBuf buf, CharacterSnapshot s) {
            int flags = (s.created ? FLAG_CREATED : 0) | (s.freeRecreate ? FLAG_FREE : 0)
                    | (s.tailWrapped ? FLAG_WRAPPED : 0) | (s.tail.ordinal() << TAIL_SHIFT);
            buf.writeByte(flags);
            writeId(buf, s.race);
            writeId(buf, s.nature);
            writeId(buf, s.hairStyle);
            buf.writeInt(s.hairColor);
            buf.writeInt(s.eyeColor);
            buf.writeInt(s.kiColor);
            buf.writeInt(s.auraColor);
            buf.writeInt(s.skinColor);
            buf.writeByte(quantize(s.height));
            buf.writeByte(quantize(s.build));
            buf.writeByte(Mth.clamp(s.eyeStyle, 0, 255));
            int n = Math.min(s.parts.size(), MAX_PARTS);
            ByteBufCodecs.VAR_INT.encode(buf, n);
            for (int i = 0; i < n; i++) writeId(buf, s.parts.get(i));
        }
    };
}

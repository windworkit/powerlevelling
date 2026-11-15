package com.powerlevelling.character;

import java.util.ArrayList;
import java.util.List;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/**
 * What a player asks for in a CharacterSubmitPayload (Addendum B.7). Untrusted: CharacterService.handleSubmit validates
 * and clamps every field. {@code tailPresent} / {@code tailWrapped} are requests; the race's tail mode decides.
 * Colors are RGB ints (aura/skin: -1 = none). No client-only imports.
 */
public record CharacterChoice(ResourceLocation race, ResourceLocation nature, Appearance appearance,
                              boolean tailPresent, boolean tailWrapped) {

    public static final StreamCodec<ByteBuf, CharacterChoice> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public CharacterChoice decode(ByteBuf buf) {
            ResourceLocation race = CharacterSnapshot.readId(buf);
            ResourceLocation nature = CharacterSnapshot.readId(buf);
            ResourceLocation hair = CharacterSnapshot.readId(buf);
            int hairColor = buf.readInt();
            int eyeColor = buf.readInt();
            int kiColor = buf.readInt();
            int auraColor = buf.readInt();
            int skinColor = buf.readInt();
            float height = buf.readFloat();
            float build = buf.readFloat();
            int eyeStyle = ByteBufCodecs.VAR_INT.decode(buf);
            int n = ByteBufCodecs.VAR_INT.decode(buf);
            if (n < 0 || n > CharacterSnapshot.MAX_PARTS) {
                throw new io.netty.handler.codec.DecoderException("Too many character parts: " + n);
            }
            List<ResourceLocation> parts = new ArrayList<>(n);
            for (int i = 0; i < n; i++) parts.add(CharacterSnapshot.readId(buf));
            boolean tailPresent = buf.readBoolean();
            boolean tailWrapped = buf.readBoolean();
            // Raw floats here: Appearance's codec clamp only applies to disk; handleSubmit rejects NaN and clamps to the race.
            return new CharacterChoice(race, nature, new Appearance(hair, hairColor, eyeColor, kiColor, auraColor,
                    skinColor, height, build, List.copyOf(parts), eyeStyle), tailPresent, tailWrapped);
        }

        @Override
        public void encode(ByteBuf buf, CharacterChoice c) {
            Appearance a = c.appearance;
            CharacterSnapshot.writeId(buf, c.race);
            CharacterSnapshot.writeId(buf, c.nature);
            CharacterSnapshot.writeId(buf, a.hairStyle());
            buf.writeInt(a.hairColor());
            buf.writeInt(a.eyeColor());
            buf.writeInt(a.kiColor());
            buf.writeInt(a.auraColor());
            buf.writeInt(a.skinColor());
            buf.writeFloat(a.height());
            buf.writeFloat(a.build());
            ByteBufCodecs.VAR_INT.encode(buf, a.eyeStyle());
            int n = Math.min(a.parts().size(), CharacterSnapshot.MAX_PARTS);
            ByteBufCodecs.VAR_INT.encode(buf, n);
            for (int i = 0; i < n; i++) CharacterSnapshot.writeId(buf, a.parts().get(i));
            buf.writeBoolean(c.tailPresent);
            buf.writeBoolean(c.tailWrapped);
        }
    };
}

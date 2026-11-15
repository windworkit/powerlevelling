package com.powerlevelling.character;

import java.util.List;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

/** JSON value that is either the wildcard string ("*" or "any") or an explicit list of T. */
public record AnyOr<T>(boolean any, List<T> values) {
    public static <T> AnyOr<T> all() {
        return new AnyOr<>(true, List.of());
    }

    public static <T> AnyOr<T> of(List<T> values) {
        return new AnyOr<>(false, List.copyOf(values));
    }

    /** @param canonical the token written when encoding the wildcard ("*" for styles, "any" for colors). */
    public static <T> Codec<AnyOr<T>> codec(Codec<T> element, String canonical) {
        Codec<String> token = Codec.STRING.validate(s -> "*".equals(s) || "any".equals(s)
                ? DataResult.success(s)
                : DataResult.error(() -> "Expected \"*\", \"any\" or a list, got '" + s + "'"));
        return Codec.either(token, element.listOf()).xmap(
                e -> e.<AnyOr<T>>map(s -> AnyOr.<T>all(), l -> AnyOr.of(l)),
                a -> a.any() ? Either.<String, List<T>>left(canonical) : Either.<String, List<T>>right(a.values()));
    }

    public boolean allows(T value) {
        return any || values.contains(value);
    }
}

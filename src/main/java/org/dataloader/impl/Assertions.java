package org.dataloader.impl;

import org.dataloader.annotations.Internal;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

@Internal
@NullMarked
public class Assertions {

    public static void assertState(boolean state, Supplier<String> message) {
        if (!state) {
            throw new DataLoaderAssertionException(message.get());
        }
    }

    public static <T> T nonNull(@Nullable T t) {
        return nonNull(t, () -> "nonNull object required");
    }

    public static <T> T nonNull(@Nullable T t, Supplier<String> message) {
        if (t == null) {
            throw new NullPointerException(message.get());
        }
        return t;
    }

}

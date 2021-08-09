package org.dataloader.impl;

import org.dataloader.annotations.Internal;

import java.util.function.Supplier;

@Internal
public class Assertions {

    public static void assertState(boolean state, Supplier<String> message) {
        if (!state) {
            throw new DataLoaderAssertionException(message.get());
        }
    }

    public static <T> T nonNull(T t) {
        return nonNull(t, () -> "nonNull object required");
    }

    public static <T> T nonNull(T t, Supplier<String> message) {
        if (t == null) {
            throw new NullPointerException(message.get());
        }
        return t;
    }

}

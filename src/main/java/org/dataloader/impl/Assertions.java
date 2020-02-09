package org.dataloader.impl;

import org.dataloader.Internal;

import java.util.Objects;

@Internal
public class Assertions {

    public static void assertState(boolean state, String message) {
        if (!state) {
            throw new AssertionException(message);
        }
    }

    public static <T> T nonNull(T t) {
        return Objects.requireNonNull(t, "nonNull object required");
    }

    public static <T> T nonNull(T t, String message) {
        return Objects.requireNonNull(t, message);
    }

    private static class AssertionException extends IllegalStateException {
        public AssertionException(String message) {
            super(message);
        }
    }
}

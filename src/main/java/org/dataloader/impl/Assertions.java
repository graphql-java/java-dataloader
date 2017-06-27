package org.dataloader.impl;

import java.util.Objects;

public class Assertions {

    public static void assertState(boolean state, String message) {
        if (!state) {
            throw new AssertionException(message);
        }
    }

    public static <T> T nonNull(T t) {
        return Objects.requireNonNull(t, "nonNull object required");
    }

    private static class AssertionException extends IllegalStateException {
        public AssertionException(String message) {
            super(message);
        }
    }
}

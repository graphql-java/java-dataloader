/*
 * Copyright (c) 2016 The original author or authors
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *      The Eclipse Public License is available at
 *      http://www.eclipse.org/legal/epl-v10.html
 *
 *      The Apache License v2.0 is available at
 *      http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */
package org.dataloader.impl;

import org.dataloader.annotations.Internal;

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

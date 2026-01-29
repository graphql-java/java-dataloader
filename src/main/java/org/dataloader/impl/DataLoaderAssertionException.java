package org.dataloader.impl;

import org.jspecify.annotations.NullMarked;

@NullMarked
public class DataLoaderAssertionException extends IllegalStateException {
    public DataLoaderAssertionException(String message) {
        super(message);
    }
}

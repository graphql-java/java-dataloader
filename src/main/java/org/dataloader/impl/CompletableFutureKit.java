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

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static java.util.stream.Collectors.toList;

/**
 * Some really basic helpers when working with CompletableFutures
 */
@Internal
public class CompletableFutureKit {

    public static <V> CompletableFuture<V> failedFuture(Exception e) {
        CompletableFuture<V> future = new CompletableFuture<>();
        future.completeExceptionally(e);
        return future;
    }

    public static <V> Throwable cause(CompletableFuture<V> completableFuture) {
        if (!completableFuture.isCompletedExceptionally()) {
            return null;
        }
        try {
            completableFuture.get();
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return e;
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause != null) {
                return cause;
            }
            return e;
        }
    }

    public static <V> boolean succeeded(CompletableFuture<V> future) {
        return future.isDone() && !future.isCompletedExceptionally();
    }

    public static <V> boolean failed(CompletableFuture<V> future) {
        return future.isDone() && future.isCompletedExceptionally();
    }

    public static <T> CompletableFuture<List<T>> allOf(List<CompletableFuture<T>> cfs) {
        return CompletableFuture.allOf(cfs.toArray(new CompletableFuture[0]))
                .thenApply(v -> cfs.stream()
                        .map(CompletableFuture::join)
                        .collect(toList())
                );
    }
}

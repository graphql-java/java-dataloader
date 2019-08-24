package org.dataloader.impl;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static java.util.stream.Collectors.toList;

/**
 * Some really basic helpers when working with CompletableFutures
 */
public class CompletableFutureKit {

    public static <V> CompletableFuture<V> failedFuture(Exception e) {
        CompletableFuture<V> future = new CompletableFuture<>();
        future.completeExceptionally(e);
        return future;
    }

    public static Throwable cause(CompletableFuture completableFuture) {
        if (!completableFuture.isCompletedExceptionally()) {
            return null;
        }
        try {
            completableFuture.get();
            return null;
        } catch (InterruptedException e) {
            return e;
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause != null) {
                return cause;
            }
            return e;
        }
    }

    public static boolean succeeded(CompletableFuture future) {
        return future.isDone() && !future.isCompletedExceptionally();
    }

    public static boolean failed(CompletableFuture future) {
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

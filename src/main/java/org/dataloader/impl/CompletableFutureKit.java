package org.dataloader.impl;

import org.dataloader.annotations.Internal;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

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
        return CompletableFuture.allOf(cfs.toArray(CompletableFuture[]::new))
                .thenApply(v -> cfs.stream()
                        .map(CompletableFuture::join)
                        .collect(toList())
                );
    }

    public static <K, V> CompletableFuture<Map<K, V>> allOf(Map<K, CompletableFuture<V>> cfs) {
        return CompletableFuture.allOf(cfs.values().toArray(CompletableFuture[]::new))
                .thenApply(v -> cfs.entrySet().stream()
                        .collect(
                                Collectors.toMap(
                                        Map.Entry::getKey,
                                        task -> task.getValue().join())
                        )
                );
    }
}

package org.dataloader.impl;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class FutureKit {
    public static <V> CompletableFuture<V> future() {
        return new CompletableFuture<>();
    }

    public static <V> CompletableFuture<V> failedFuture(Exception e) {
        CompletableFuture<V> future = future();
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
}

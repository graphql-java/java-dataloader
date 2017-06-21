package org.dataloader.impl;

import org.dataloader.CombinedFutures;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * This allows multiple {@link CompletableFuture}s to be combined together and completed
 * as one and instead of throwing {@link CompletionException}s when trying to get values
 * it captures the cause and returns null for the data.
 *
 * This idea of this class was original in the vertex core package but it was re-implemented
 * to use {@link CompletableFuture}s instead
 */
public class CombinedFuturesImpl implements CombinedFutures {

    private final CompletableFuture<?>[] futures;
    private final CompletableFuture<Void> controller;
    private AtomicReference<Throwable> cause;

    private CombinedFuturesImpl(CompletableFuture<?>[] cfs) {
        this.futures = cfs;
        this.cause = new AtomicReference<>();
        this.controller = CompletableFuture.allOf(this.futures);
    }

    private CombinedFuturesImpl(CombinedFuturesImpl other, CompletableFuture<Void> controller) {
        this.futures = other.futures;
        this.cause = other.cause;
        this.controller = controller;
    }

    public static CombinedFutures combineAllOf(CompletableFuture<?>... cfs) {
        return new CombinedFuturesImpl(cfs);
    }


    /**
     * When the all the futures complete, this call back will be invoked with this CombinedFutures as a parameter
     *
     * @param handler the call back which will be given this object
     *
     * @return the new CombinedFutures
     */
    @Override
    public CombinedFutures thenAccept(Consumer<CombinedFutures> handler) {
        CompletableFuture<Void> newController = controller.handle((result, throwable) -> {
            if (throwable != null) {
                cause.set(throwable);
            }
            if (handler != null) {
                handler.accept(this);
            }
            return null;
        });
        return new CombinedFuturesImpl(this, newController);
    }


    /**
     * @return true if all the futures completed successfully
     */
    @Override
    public boolean succeeded() {
        return FutureKit.succeeded(controller);
    }

    /**
     * @return true if any of the the futures completed unsuccessfully
     */
    @Override
    public boolean failed() {
        return FutureKit.failed(controller);
    }

    /**
     * The true if the all the futures (and hence this {@link CombinedFuturesImpl}) have completed
     *
     * @return true if all the futures have completed
     */
    @Override
    public boolean isDone() {
        return controller.isDone();
    }

    /**
     * The exception cause or null if it didn't fail
     *
     * @return an exception or null if the future did not fail
     */
    @Override
    public Throwable cause() {
        return cause.get();
    }

    /**
     * The true if the future at the specified index succeeded
     *
     * @param index the index of the future
     *
     * @return true if the future at the specified index succeeded
     */
    @Override
    public boolean succeeded(int index) {
        return FutureKit.succeeded(futures[index]);
    }

    /**
     * The exception cause at the specified index or null if it didn't fail
     *
     * @param index the index of the future
     *
     * @return an exception or null if the future did not fail
     */
    @Override
    public Throwable cause(int index) {
        return FutureKit.cause(futures[index]);
    }


    /**
     * The result at index or null if it failed
     *
     * @param index the index of the future
     * @param <V>   the type of object
     *
     * @return the value of the future
     */
    @Override
    @SuppressWarnings("unchecked")
    public <V> V resultAt(int index) {
        try {
            CompletableFuture future = futures[index];
            return (V) future.get();
        } catch (InterruptedException | ExecutionException e) {
            return null;
        }
    }

    /**
     * Returns the underlying future values as a list
     *
     * @param <V> the type of values
     *
     * @return the list of underlying values
     */
    @Override
    public <V> List<V> list() {
        int size = size();
        List<V> list = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            list.add(resultAt(index));
        }
        return list;
    }

    /**
     * @return the number of {@link CompletableFuture}s under the covers
     */
    @Override
    public int size() {
        return futures.length;
    }

    /**
     * @return this as a {@link CompletableFuture}
     */
    @Override
    public CompletableFuture<Void> toCompletableFuture() {
        return controller;
    }
}

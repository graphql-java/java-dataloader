package org.dataloader.impl;

import org.dataloader.PromisedValues;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.dataloader.impl.Assertions.assertState;
import static org.dataloader.impl.Assertions.nonNull;

public class PromisedValuesImpl<T> implements PromisedValues<T> {

    private final List<CompletableFuture<T>> futures;
    private final CompletableFuture<Void> controller;
    private AtomicReference<Throwable> cause;

    private PromisedValuesImpl(List<CompletableFuture<T>> cfs) {
        this.futures = nonNull(cfs);
        this.cause = new AtomicReference<>();
        CompletableFuture[] futuresArray = cfs.toArray(new CompletableFuture[cfs.size()]);
        this.controller = CompletableFuture.allOf(futuresArray);
    }

    private PromisedValuesImpl(PromisedValuesImpl<T> other, CompletableFuture<Void> controller) {
        this.futures = other.futures;
        this.cause = other.cause;
        this.controller = controller;
    }

    public static <T> PromisedValues<T> combineAllOf(List<CompletableFuture<T>> cfs) {
        return new PromisedValuesImpl<>(nonNull(cfs));
    }


    @Override
    public PromisedValues<T> thenAccept(Consumer<PromisedValues<T>> handler) {
        CompletableFuture<Void> newController = controller.handle((result, throwable) -> {
            if (throwable != null) {
                cause.set(throwable);
            }
            if (handler != null) {
                handler.accept(this);
            }
            return null;
        });
        return new PromisedValuesImpl<>(this, newController);
    }


    @Override
    public boolean succeeded() {
        return CompletableFutureKit.succeeded(controller);
    }

    @Override
    public boolean failed() {
        return CompletableFutureKit.failed(controller);
    }

    @Override
    public boolean isDone() {
        return controller.isDone();
    }

    @Override
    public Throwable cause() {
        return cause.get();
    }

    @Override
    public boolean succeeded(int index) {
        return CompletableFutureKit.succeeded(futures.get(index));
    }

    @Override
    public Throwable cause(int index) {
        return CompletableFutureKit.cause(futures.get(index));
    }

    @Override
    @SuppressWarnings("unchecked")
    public T get(int index) {
        assertState(isDone(), "The PromisedValues MUST be complete before calling the get() method");
        try {
            CompletableFuture<T> future = futures.get(index);
            return future.get();
        } catch (InterruptedException | ExecutionException e) {
            return null;
        }
    }

    @Override
    public List<T> toList() {
        assertState(isDone(), "The PromisedValues MUST be complete before calling the toList() method");
        int size = size();
        List<T> list = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            list.add(get(index));
        }
        return list;
    }

    @Override
    public int size() {
        return futures.size();
    }

    @Override
    public CompletableFuture<List<T>> toCompletableFuture() {
        return controller.thenApply(v -> toList());
    }
}

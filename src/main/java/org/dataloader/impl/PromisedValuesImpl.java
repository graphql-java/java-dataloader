package org.dataloader.impl;

import org.dataloader.Internal;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.dataloader.impl.Assertions.assertState;
import static org.dataloader.impl.Assertions.nonNull;

@Internal
public class PromisedValuesImpl<T> implements PromisedValues<T> {

    private final List<? extends CompletionStage<T>> futures;
    private final CompletionStage<Void> controller;
    private final AtomicReference<Throwable> cause;

    private PromisedValuesImpl(List<? extends CompletionStage<T>> cs) {
        this.futures = nonNull(cs);
        this.cause = new AtomicReference<>();
        CompletableFuture[] futuresArray = cs.stream().map(CompletionStage::toCompletableFuture).toArray(CompletableFuture[]::new);
        this.controller = CompletableFuture.allOf(futuresArray).handle((result, throwable) -> {
            setCause(throwable);
            return null;
        });
    }

    private PromisedValuesImpl(PromisedValuesImpl<T> other, CompletionStage<Void> controller) {
        this.futures = other.futures;
        this.cause = other.cause;
        this.controller = controller;
    }

    public static <T> PromisedValues<T> combineAllOf(List<? extends CompletionStage<T>> cfs) {
        return new PromisedValuesImpl<>(nonNull(cfs));
    }

    public static <T> PromisedValues<T> combinePromisedValues(List<PromisedValues<T>> promisedValues) {
        List<CompletionStage<T>> cfs = promisedValues.stream()
                .map(pv -> (PromisedValuesImpl<T>) pv)
                .flatMap(pv -> pv.futures.stream())
                .collect(Collectors.toList());
        return new PromisedValuesImpl<>(cfs);
    }

    private void setCause(Throwable throwable) {
        if (throwable != null) {
            if (throwable instanceof CompletionException && throwable.getCause() != null) {
                cause.set(throwable.getCause());
            } else {
                cause.set(throwable);
            }
        }
    }

    @Override
    public PromisedValues<T> thenAccept(Consumer<PromisedValues<T>> handler) {
        nonNull(handler);
        CompletionStage<Void> newController = controller.handle((result, throwable) -> {
            setCause(throwable);
            handler.accept(this);
            return result;
        });
        return new PromisedValuesImpl<>(this, newController);
    }


    @Override
    public boolean succeeded() {
        return isDone() && cause.get() == null;
    }

    @Override
    public boolean failed() {
        return isDone() && cause.get() != null;
    }

    @Override
    public boolean isDone() {
        return controller.toCompletableFuture().isDone();
    }

    @Override
    public Throwable cause() {
        return cause.get();
    }

    @Override
    public boolean succeeded(int index) {
        return CompletableFutureKit.succeeded(futures.get(index).toCompletableFuture());
    }

    @Override
    public Throwable cause(int index) {
        return CompletableFutureKit.cause(futures.get(index).toCompletableFuture());
    }

    @Override
    public T get(int index) {
        assertState(isDone(), "The PromisedValues MUST be complete before calling the get() method");
        try {
            CompletionStage<T> future = futures.get(index);
            return future.toCompletableFuture().get();
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
    public List<T> join() {
        controller.toCompletableFuture().join();
        return toList();
    }

    @Override
    public CompletableFuture<List<T>> toCompletableFuture() {
        return controller.thenApply(v -> toList()).toCompletableFuture();
    }

}

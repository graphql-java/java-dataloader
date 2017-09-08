package org.dataloader;

import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.dataloader.impl.Assertions.nonNull;

/**
 * Try is class that allows you to hold the result of computation or the throwable it produced.
 *
 * This class is useful in {@link org.dataloader.BatchLoader}s so you can mix a batch of calls where some of
 * the calls succeeded and some of them failed.  You would make your batch loader declaration like :
 *
 * <pre>
 * {@code BatchLoader<K,Try<V> batchLoader = new BatchLoader() { ... } }
 * </pre>
 *
 * {@link org.dataloader.DataLoader} understands the use of Try and will take the exceptional path and complete
 * the value promise with that exception value.
 */
public class Try<V> {
    private static Throwable NIL = new Throwable() {
        @Override
        public synchronized Throwable fillInStackTrace() {
            return this;
        }
    };

    private final Throwable throwable;
    private final V value;


    @SuppressWarnings("unchecked")
    private Try(Throwable throwable) {
        this.throwable = nonNull(throwable);
        this.value = (V) NIL;
    }

    private Try(V value) {
        this.value = value;
        this.throwable = null;
    }

    public static <V> Try<V> succeeded(V value) {
        return new Try<>(value);
    }

    public static <V> Try<V> failed(Throwable throwable) {
        return new Try<>(throwable);
    }

    public static <V> Try<V> tryCall(Callable<V> callable) {
        try {
            return Try.succeeded(callable.call());
        } catch (Exception e) {
            return Try.failed(e);
        }
    }

    public static <V> CompletionStage<Try<V>> tryStage(CompletionStage<V> completionStage) {
        return completionStage.handle((value, throwable) -> {
            if (throwable != null) {
                return failed(throwable);
            }
            return succeeded(value);
        });
    }

    public V get() {
        if (isFailure()) {
            throw new UnsupportedOperationException("You have called Try.get() with a failed Try", throwable);
        }
        return value;
    }

    public Throwable getThrowable() {
        if (isSuccess()) {
            throw new UnsupportedOperationException("You have called Try.getThrowable() with a failed Try", throwable);
        }
        return throwable;
    }

    public boolean isSuccess() {
        return value != NIL;
    }

    public boolean isFailure() {
        return value == NIL;
    }


    public <U> Try<U> map(Function<? super V, U> mapper) {
        if (isSuccess()) {
            return succeeded(mapper.apply(value));
        }
        return failed(this.throwable);
    }

    public <U> Try<U> flatMap(Function<? super V, Try<U>> mapper) {
        if (isSuccess()) {
            return mapper.apply(value);
        }
        return failed(this.throwable);
    }

    public Optional<V> toOptional() {
        return isSuccess() ? Optional.ofNullable(value) : Optional.empty();
    }

    public V orElse(V other) {
        return isSuccess() ? value : other;
    }

    public V orElseGet(Supplier<V> otherSupplier) {
        return isSuccess() ? value : otherSupplier.get();
    }

    public V reThrow() throws Throwable {
        if (isSuccess()) {
            throw new UnsupportedOperationException("You have called Try.reThrow() with a successful Try.  There is nothing to rethrow");
        }
        throw throwable;
    }

}

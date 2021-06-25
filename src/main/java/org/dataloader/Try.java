package org.dataloader;

import org.dataloader.annotations.PublicApi;

import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
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
@PublicApi
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

    /**
     * Creates a Try that has succeeded with the provided value
     *
     * @param value the successful value
     * @param <V>   the value type
     *
     * @return a successful Try
     */
    public static <V> Try<V> succeeded(V value) {
        return new Try<>(value);
    }

    /**
     * Creates a Try that has failed with the provided throwable
     *
     * @param throwable the failed throwable
     * @param <V>       the value type
     *
     * @return a failed Try
     */
    public static <V> Try<V> failed(Throwable throwable) {
        return new Try<>(throwable);
    }

    /**
     * Calls the callable and if it returns a value, the Try is successful with that value or if throws
     * and exception the Try captures that
     *
     * @param callable the code to call
     * @param <V>      the value type
     *
     * @return a Try which is the result of the call
     */
    public static <V> Try<V> tryCall(Callable<V> callable) {
        try {
            return Try.succeeded(callable.call());
        } catch (Exception e) {
            return Try.failed(e);
        }
    }

    /**
     * Creates a CompletionStage that, when it completes, will capture into a Try whether the given completionStage
     * was successful or not
     *
     * @param completionStage the completion stage that will complete
     * @param <V>             the value type
     *
     * @return a Try which is the result of the call
     */
    public static <V> CompletionStage<Try<V>> tryStage(CompletionStage<V> completionStage) {
        return completionStage.handle((value, throwable) -> {
            if (throwable != null) {
                return failed(throwable);
            }
            return succeeded(value);
        });
    }

    /**
     * @return the successful value of this try
     *
     * @throws UnsupportedOperationException if the Try is in fact in the unsuccessful state
     */
    public V get() {
        if (isFailure()) {
            throw new UnsupportedOperationException("You have called Try.get() with a failed Try", throwable);
        }
        return value;
    }

    /**
     * @return the failed throwable of this try
     *
     * @throws UnsupportedOperationException if the Try is in fact in the successful state
     */
    public Throwable getThrowable() {
        if (isSuccess()) {
            throw new UnsupportedOperationException("You have called Try.getThrowable() with a failed Try", throwable);
        }
        return throwable;
    }

    /**
     * @return true if this Try succeeded and therefore has a value
     */
    public boolean isSuccess() {
        return value != NIL;
    }

    /**
     * @return true if this Try failed and therefore has a throwable
     */
    public boolean isFailure() {
        return value == NIL;
    }


    /**
     * Maps the Try into another Try with a different type
     *
     * @param mapper the function to map the current Try to a new Try
     * @param <U>    the target type
     *
     * @return the mapped Try
     */
    public <U> Try<U> map(Function<? super V, U> mapper) {
        if (isSuccess()) {
            return succeeded(mapper.apply(value));
        }
        return failed(this.throwable);
    }

    /**
     * Flats maps the Try into another Try type
     *
     * @param mapper the flat map function
     * @param <U>    the target type
     *
     * @return a new Try
     */
    public <U> Try<U> flatMap(Function<? super V, Try<U>> mapper) {
        if (isSuccess()) {
            return mapper.apply(value);
        }
        return failed(this.throwable);
    }

    /**
     * Converts the Try into an Optional where unsuccessful tries are empty
     *
     * @return a new optional
     */
    public Optional<V> toOptional() {
        return isSuccess() ? Optional.ofNullable(value) : Optional.empty();
    }

    /**
     * Returns the successful value of the Try or other if it failed
     *
     * @param other the other value if the Try failed
     *
     * @return the value of the Try or an alternative
     */
    public V orElse(V other) {
        return isSuccess() ? value : other;
    }

    /**
     * Returns the successful value of the Try or the supplied other if it failed
     *
     * @param otherSupplier the other value supplied if the Try failed
     *
     * @return the value of the Try or an alternative
     */
    public V orElseGet(Supplier<V> otherSupplier) {
        return isSuccess() ? value : otherSupplier.get();
    }

    /**
     * Rethrows the underlying throwable inside the unsuccessful Try
     *
     * @throws Throwable                     if the Try was in fact throwable
     * @throws UnsupportedOperationException if the try was in fact a successful Try
     */
    public void reThrow() throws Throwable {
        if (isSuccess()) {
            throw new UnsupportedOperationException("You have called Try.reThrow() with a successful Try.  There is nothing to rethrow");
        }
        throw throwable;
    }

    /**
     * Called the action if the Try has a successful value
     *
     * @param action the action to call if the Try is successful
     */
    void forEach(Consumer<? super V> action) {
        if (isSuccess()) {
            action.accept(value);
        }
    }

    /**
     * Called the recover function of the Try failed otherwise returns this if it was successful.
     *
     * @param recoverFunction the function to recover from a throwable into a new value
     *
     * @return a Try of the same type
     */
    public Try<V> recover(Function<Throwable, V> recoverFunction) {
        if (isFailure()) {
            return succeeded(recoverFunction.apply(throwable));
        }
        return this;
    }


}

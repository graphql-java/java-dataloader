package org.dataloader;

import org.dataloader.impl.PromisedValuesImpl;

import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

import static java.util.Arrays.asList;

/**
 * This allows multiple {@link CompletableFuture}s to be combined together and completed
 * as one and should something go wrong, instead of throwing {@link CompletionException}s it captures the cause and returns null for that
 * data value, other wise it allows you to access them as a list of values.
 *
 * This class really encapsulate a list of promised values.  It is considered finished when all of the underlying futures
 * are finished.
 *
 * You can get that list of values via {@link #toList()}.  You can
 * also compose a {@link CompletableFuture} of that list of values via {@link #toCompletableFuture()}
 */
public interface PromisedValues<T> {
    /**
     * Returns a new {@link PromisedValues} that is completed when all of
     * the given {@link CompletableFuture}s complete.  If any of the given
     * {@link CompletableFuture}s complete exceptionally, then the returned
     * {@link PromisedValues} also does so.
     *
     * @param cfs the {@link CompletableFuture}s to combine
     * @param <T> the type of values
     *
     * @return a new CombinedFutures
     */
    static <T> PromisedValues<T> allOf(List<CompletableFuture<T>> cfs) {
        return PromisedValuesImpl.combineAllOf(cfs);
    }

    /**
     * Returns a new {@link PromisedValues} that is completed when all of
     * the given {@link CompletableFuture}s complete.  If any of the given
     * {@link CompletableFuture}s complete exceptionally, then the returned
     * {@link PromisedValues} also does so.
     *
     * @param f1  the 1st completable future
     * @param f2  the 2nd completable future
     * @param <T> the type of values
     *
     * @return a new CombinedFutures
     */
    static <T> PromisedValues<T> allOf(CompletableFuture<T> f1, CompletableFuture<T> f2) {
        return PromisedValuesImpl.combineAllOf(asList(f1, f2));
    }

    /**
     * Returns a new {@link PromisedValues} that is completed when all of
     * the given {@link CompletableFuture}s complete.  If any of the given
     * {@link CompletableFuture}s complete exceptionally, then the returned
     * {@link PromisedValues} also does so.
     *
     * @param f1  the 1st completable future
     * @param f2  the 2nd completable future
     * @param f3  the 3rd completable future
     * @param <T> the type of values
     *
     * @return a new CombinedFutures
     */
    static <T> PromisedValues allOf(CompletableFuture<T> f1, CompletableFuture<T> f2, CompletableFuture<T> f3) {
        return PromisedValuesImpl.combineAllOf(asList(f1, f2, f3));
    }

    /**
     * Returns a new {@link PromisedValues} that is completed when all of
     * the given {@link CompletableFuture}s complete.  If any of the given
     * {@link CompletableFuture}s complete exceptionally, then the returned
     * {@link PromisedValues} also does so.
     *
     * @param f1  the 1st completable future
     * @param f2  the 2nd completable future
     * @param f3  the 3rd completable future
     * @param f4  the 4th completable future
     * @param <T> the type of values
     *
     * @return a new CombinedFutures
     */
    static <T> PromisedValues allOf(CompletableFuture<T> f1, CompletableFuture<T> f2, CompletableFuture<T> f3, CompletableFuture<T> f4) {
        return PromisedValuesImpl.combineAllOf(asList(f1, f2, f3, f4));
    }

    /**
     * When the all the futures complete, this call back will be invoked with this {@link PromisedValues} as a parameter
     *
     * @param handler the call back which will be given this object
     *
     * @return a new {@link PromisedValues} which you can compose more computations with
     */
    PromisedValues<T> thenAccept(Consumer<PromisedValues<T>> handler);

    /**
     * @return true if all the futures completed successfully
     */
    boolean succeeded();

    /**
     * @return true if any of the the futures completed unsuccessfully
     */
    boolean failed();

    /**
     * The true if the all the futures have completed (and hence this {@link PromisedValues} has completed)
     *
     * @return true if all the futures have completed
     */
    boolean isDone();

    /**
     * The exception cause or null if it didn't fail
     *
     * @return an exception or null if the future did not fail
     */
    Throwable cause();

    /**
     * The true if the {@link CompletableFuture} at the specified index succeeded
     *
     * @param index the index of the {@link CompletableFuture}
     *
     * @return true if the future at the specified index succeeded
     */
    boolean succeeded(int index);

    /**
     * The exception cause at the specified index or null if it didn't fail
     *
     * @param index the index of the {@link CompletableFuture}
     *
     * @return an exception or null if the future did not fail
     */
    Throwable cause(int index);

    /**
     * The value at index or null if it failed
     *
     * @param index the index of the future
     *
     * @return the value of the future
     */
    @SuppressWarnings("unchecked")
    T get(int index);

    /**
     * Returns the underlying values as a list
     *
     * @return the list of underlying values
     */
    List<T> toList();

    /**
     * @return the number of {@link CompletableFuture}s under the covers
     */
    int size();

    /**
     * Waits for the underlying futures to complete.  To better
     * conform with the use of common functional forms, if a
     * computation involved in the completion of this
     * CompletableFuture threw an exception, this method throws an
     * (unchecked) {@link CompletionException} with the underlying
     * exception as its cause.
     *
     * @throws CancellationException if the computation was cancelled
     * @throws CompletionException   if this future completed
     *                               exceptionally or a completion computation threw an exception
     */
    void join();

    /**
     * @return this as a {@link CompletableFuture} that returns the list of underlying values
     */
    CompletableFuture<List<T>> toCompletableFuture();
}

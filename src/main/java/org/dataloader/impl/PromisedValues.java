package org.dataloader.impl;

import org.dataloader.Internal;

import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

import static java.util.Arrays.asList;

/**
 * This allows multiple {@link CompletionStage}s to be combined together and completed
 * as one and should something go wrong, instead of throwing {@link CompletionException}s it captures the cause and returns null for that
 * data value, other wise it allows you to access them as a list of values.
 * <p>
 * This class really encapsulate a list of promised values.  It is considered finished when all of the underlying futures
 * are finished.
 * <p>
 * You can get that list of values via {@link #toList()}.  You can also compose a {@link CompletableFuture} of that
 * list of values via {@link #toCompletableFuture()} ()}
 *
 * @author <a href="https://github.com/bbakerman/">Brad Baker</a>
 */
@Internal
public interface PromisedValues<T> {

    /**
     * Returns a new {@link PromisedValues} that is completed when all of
     * the given {@link CompletionStage}s complete.  If any of the given
     * {@link CompletionStage}s complete exceptionally, then the returned
     * {@link PromisedValues} also does so.
     *
     * @param cfs the {@link CompletionStage}s to combine
     * @param <T> the type of values
     *
     * @return a new PromisedValues
     */
    static <T> PromisedValues<T> allOf(List<? extends CompletionStage<T>> cfs) {
        return PromisedValuesImpl.combineAllOf(cfs);
    }

    /**
     * Returns a new {@link PromisedValues} that is completed when all of
     * the given {@link CompletionStage}s complete.  If any of the given
     * {@link CompletionStage}s complete exceptionally, then the returned
     * {@link PromisedValues} also does so.
     *
     * @param f1  the 1st completable future
     * @param f2  the 2nd completable future
     * @param <T> the type of values
     *
     * @return a new PromisedValues
     */
    static <T> PromisedValues<T> allOf(CompletionStage<T> f1, CompletionStage<T> f2) {
        return PromisedValuesImpl.combineAllOf(asList(f1, f2));
    }

    /**
     * Returns a new {@link PromisedValues} that is completed when all of
     * the given {@link CompletionStage}s complete.  If any of the given
     * {@link CompletionStage}s complete exceptionally, then the returned
     * {@link PromisedValues} also does so.
     *
     * @param f1  the 1st completable future
     * @param f2  the 2nd completable future
     * @param f3  the 3rd completable future
     * @param <T> the type of values
     *
     * @return a new PromisedValues
     */
    static <T> PromisedValues<T> allOf(CompletionStage<T> f1, CompletionStage<T> f2, CompletionStage<T> f3) {
        return PromisedValuesImpl.combineAllOf(asList(f1, f2, f3));
    }


    /**
     * Returns a new {@link PromisedValues} that is completed when all of
     * the given {@link CompletionStage}s complete.  If any of the given
     * {@link CompletionStage}s complete exceptionally, then the returned
     * {@link PromisedValues} also does so.
     *
     * @param f1  the 1st completable future
     * @param f2  the 2nd completable future
     * @param f3  the 3rd completable future
     * @param f4  the 4th completable future
     * @param <T> the type of values
     *
     * @return a new PromisedValues
     */
    static <T> PromisedValues<T> allOf(CompletionStage<T> f1, CompletionStage<T> f2, CompletionStage<T> f3, CompletionStage<T> f4) {
        return PromisedValuesImpl.combineAllOf(asList(f1, f2, f3, f4));
    }


    /**
     * Returns a new {@link PromisedValues} that is completed when all of
     * the given {@link PromisedValues}s complete.  If any of the given
     * {@link PromisedValues}s complete exceptionally, then the returned
     * {@link PromisedValues} also does so.
     *
     * @param cfs the list to combine
     * @param <T> the type of values
     *
     * @return a new PromisedValues
     */
    static <T> PromisedValues<T> allPromisedValues(List<PromisedValues<T>> cfs) {
        return PromisedValuesImpl.combinePromisedValues(cfs);
    }

    /**
     * Returns a new {@link PromisedValues} that is completed when all of
     * the given {@link PromisedValues}s complete.  If any of the given
     * {@link PromisedValues}s complete exceptionally, then the returned
     * {@link PromisedValues} also does so.
     *
     * @param pv1 the 1st promised value
     * @param pv2 the 2nd promised value
     * @param <T> the type of values
     *
     * @return a new PromisedValues
     */
    static <T> PromisedValues<T> allPromisedValues(PromisedValues<T> pv1, PromisedValues<T> pv2) {
        return PromisedValuesImpl.combinePromisedValues(asList(pv1, pv2));
    }

    /**
     * Returns a new {@link PromisedValues} that is completed when all of
     * the given {@link PromisedValues}s complete.  If any of the given
     * {@link PromisedValues}s complete exceptionally, then the returned
     * {@link PromisedValues} also does so.
     *
     * @param pv1 the 1st promised value
     * @param pv2 the 2nd promised value
     * @param pv3 the 3rd promised value
     * @param <T> the type of values
     *
     * @return a new PromisedValues
     */
    static <T> PromisedValues<T> allPromisedValues(PromisedValues<T> pv1, PromisedValues<T> pv2, PromisedValues<T> pv3) {
        return PromisedValuesImpl.combinePromisedValues(asList(pv1, pv2, pv3));
    }

    /**
     * Returns a new {@link PromisedValues} that is completed when all of
     * the given {@link PromisedValues}s complete.  If any of the given
     * {@link PromisedValues}s complete exceptionally, then the returned
     * {@link PromisedValues} also does so.
     *
     * @param pv1 the 1st promised value
     * @param pv2 the 2nd promised value
     * @param pv3 the 3rd promised value
     * @param pv4 the 4th promised value
     * @param <T> the type of values
     *
     * @return a new PromisedValues
     */
    static <T> PromisedValues<T> allPromisedValues(PromisedValues<T> pv1, PromisedValues<T> pv2, PromisedValues<T> pv3, PromisedValues<T> pv4) {
        return PromisedValuesImpl.combinePromisedValues(asList(pv1, pv2, pv3, pv4));
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
     * The true if the {@link CompletionStage} at the specified index succeeded
     *
     * @param index the index of the {@link CompletionStage}
     *
     * @return true if the future at the specified index succeeded
     */
    boolean succeeded(int index);

    /**
     * The exception cause at the specified index or null if it didn't fail
     *
     * @param index the index of the {@link CompletionStage}
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
     * @return the number of {@link CompletionStage}s under the covers
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
     * @return the list of completed values similar to {@link #toList()}
     *
     * @throws CancellationException if the computation was cancelled
     * @throws CompletionException   if this future completed
     *                               exceptionally or a completion computation threw an exception
     */
    List<T> join();

    /**
     * @return this as a {@link CompletableFuture} that returns the list of underlying values
     */
    CompletableFuture<List<T>> toCompletableFuture();
}

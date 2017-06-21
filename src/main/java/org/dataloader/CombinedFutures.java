package org.dataloader;

import org.dataloader.impl.CombinedFuturesImpl;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

/**
 * This allows multiple {@link CompletableFuture}s to be combined together and completed
 * as one and instead of throwing {@link CompletionException}s when trying to get values
 * it captures the cause and returns null for the data.
 */
public interface CombinedFutures {
    /**
     * Returns a new CombinedFutures that is completed when all of
     * the given {@link CompletableFuture}s complete.  If any of the given
     * {@link CompletableFuture}s complete exceptionally, then the returned
     * CombinedFutures also does so.
     *
     * @param cfs the {@link CompletableFuture}s to combine
     *
     * @return a new CombinedFutures
     */
    static CombinedFutures allOf(List<CompletableFuture<?>> cfs) {
        return CombinedFuturesImpl.combineAllOf(cfs.toArray(new CompletableFuture[cfs.size()]));
    }

    /**
     * Returns a new CombinedFutures that is completed when all of
     * the given {@link CompletableFuture}s complete.  If any of the given
     * {@link CompletableFuture}s complete exceptionally, then the returned
     * CombinedFutures also does so.
     *
     * @param f1   the 1st completable future
     * @param f2   the 2nd completable future
     * @param <T1> the type of f1
     * @param <T2> the type of f2
     *
     * @return a new CombinedFutures
     */
    static <T1, T2> CombinedFutures allOf(CompletableFuture<T1> f1, CompletableFuture<T2> f2) {
        return CombinedFuturesImpl.combineAllOf(f1, f2);
    }

    /**
     * Returns a new CombinedFutures that is completed when all of
     * the given {@link CompletableFuture}s complete.  If any of the given
     * {@link CompletableFuture}s complete exceptionally, then the returned
     * CombinedFutures also does so.
     *
     * @param f1   the 1st completable future
     * @param f2   the 2nd completable future
     * @param f3   the 3rd completable future
     * @param <T1> the type of f1
     * @param <T2> the type of f2
     * @param <T3> the type of f3
     *
     * @return a new CombinedFutures
     */
    static <T1, T2, T3> CombinedFutures allOf(CompletableFuture<T1> f1, CompletableFuture<T2> f2, CompletableFuture<T3> f3) {
        return CombinedFuturesImpl.combineAllOf(f1, f2, f3);
    }

    /**
     * Returns a new CombinedFutures that is completed when all of
     * the given {@link CompletableFuture}s complete.  If any of the given
     * {@link CompletableFuture}s complete exceptionally, then the returned
     * CombinedFutures also does so.
     *
     * @param f1   the 1st completable future
     * @param f2   the 2nd completable future
     * @param f3   the 3rd completable future
     * @param f4   the 3rd completable future
     * @param <T1> the type of f1
     * @param <T2> the type of f2
     * @param <T3> the type of f3
     * @param <T4> the type of f4
     *
     * @return a new CombinedFutures
     */
    static <T1, T2, T3, T4> CombinedFutures allOf(CompletableFuture<T1> f1, CompletableFuture<T2> f2, CompletableFuture<T3> f3, CompletableFuture<T4> f4) {
        return CombinedFuturesImpl.combineAllOf(f1, f2, f3, f4);
    }

    /**
     * When the all the futures complete, this call back will be invoked with this CombinedFutures as a parameter
     *
     * @param handler the call back which will be given this object
     *
     * @return the new CombinedFutures
     */
    CombinedFutures thenAccept(Consumer<CombinedFutures> handler);

    /**
     * @return true if all the futures completed successfully
     */
    boolean succeeded();

    /**
     * @return true if any of the the futures completed unsuccessfully
     */
    boolean failed();

    /**
     * The true if the all the futures (and hence this {@link CombinedFutures}) have completed
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
     * The true if the future at the specified index succeeded
     *
     * @param index the index of the future
     *
     * @return true if the future at the specified index succeeded
     */
    boolean succeeded(int index);

    /**
     * The exception cause at the specified index or null if it didn't fail
     *
     * @param index the index of the future
     *
     * @return an exception or null if the future did not fail
     */
    Throwable cause(int index);

    /**
     * The result at index or null if it failed
     *
     * @param index the index of the future
     * @param <V>   the type of object
     *
     * @return the value of the future
     */
    @SuppressWarnings("unchecked")
    <V> V resultAt(int index);

    /**
     * Returns the underlying future values as a list
     *
     * @param <V> the type of values
     *
     * @return the list of underlying values
     */
    <V> List<V> list();

    /**
     * @return the number of {@link CompletableFuture}s under the covers
     */
    int size();

    /**
     * @return this as a {@link CompletableFuture}
     */
    CompletableFuture<Void> toCompletableFuture();
}

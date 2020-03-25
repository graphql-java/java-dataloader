package org.dataloader;

import org.dataloader.annotations.PublicApi;
import org.dataloader.stats.Statistics;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Data loader is a utility class that allows batch loading of data that is identified by a set of unique keys. For
 * each key that is loaded a separate {@link CompletableFuture} is returned, that completes as the batch function completes.
 * <p>
 * With batching enabled the execution will start after calling {@link DataLoader#dispatch()}, causing the queue of
 * loaded keys to be sent to the batch function, clears the queue, and returns a promise to the values.
 * <p>
 * As {@link org.dataloader.BatchLoader} batch functions are executed the resulting futures are cached using a cache
 * implementation of choice, so they will only execute once. Individual cache keys can be cleared, so they will
 * be re-fetched when referred to again.
 * <p>
 * It is also possible to clear the cache entirely, and prime it with values before they are used.
 * <p>
 * Both caching and batching can be disabled. Configuration of the data loader is done by providing a
 * {@link DataLoaderOptions} instance on creation.
 * <p>
 * A call to the batch loader might result in individual exception failures for item with the returned list.  if
 * you want to capture these specific item failures then use {@link org.dataloader.Try} as a return value and
 * create the data loader with {@link org.dataloader.DataLoaderFactory#newDataLoaderWithTry(BatchLoader)} form.
 * The Try values will be interpreted as either success values or cause the {@link #load(Object)} promise to
 * complete exceptionally.
 *
 * @param <K> type parameter indicating the type of the data load keys
 * @param <V> type parameter indicating the type of the data that is returned
 * @author <a href="https://github.com/aschrijver/">Arnold Schrijver</a>
 * @author <a href="https://github.com/bbakerman/">Brad Baker</a>
 */
@PublicApi
public interface DataLoader<K, V> {
    /**
     * Requests to load the data with the specified key asynchronously, and returns a future of the resulting value.
     * <p>
     * If batching is enabled (the default), you'll have to call {@link org.dataloader.DataLoader#dispatch()} at a later stage to
     * start batch execution. If you forget this call the future will never be completed (unless already completed,
     * and returned from cache).
     *
     * @param key the key to load
     * @return the future of the value
     */
    CompletableFuture<V> load(K key);

    /**
     * Requests to load the data with the specified key asynchronously, and returns a future of the resulting value.
     * <p>
     * If batching is enabled (the default), you'll have to call {@link org.dataloader.DataLoader#dispatch()} at a later stage to
     * start batch execution. If you forget this call the future will never be completed (unless already completed,
     * and returned from cache).
     * <p>
     * The key context object may be useful in the batch loader interfaces such as {@link org.dataloader.BatchLoaderWithContext} or
     * {@link org.dataloader.MappedBatchLoaderWithContext} to help retrieve data.
     *
     * @param key        the key to load
     * @param keyContext a context object that is specific to this key
     * @return the future of the value
     */
    CompletableFuture<V> load(K key, Object keyContext);

    /**
     * Requests to load the list of data provided by the specified keys asynchronously, and returns a composite future
     * of the resulting values.
     * <p>
     * If batching is enabled (the default), you'll have to call {@link org.dataloader.DataLoader#dispatch()} at a later stage to
     * start batch execution. If you forget this call the future will never be completed (unless already completed,
     * and returned from cache).
     *
     * @param keys the list of keys to load
     * @return the composite future of the list of values
     */
    CompletableFuture<List<V>> loadMany(List<K> keys);

    /**
     * Requests to load the list of data provided by the specified keys asynchronously, and returns a composite future
     * of the resulting values.
     * <p>
     * If batching is enabled (the default), you'll have to call {@link org.dataloader.DataLoader#dispatch()} at a later stage to
     * start batch execution. If you forget this call the future will never be completed (unless already completed,
     * and returned from cache).
     * <p>
     * The key context object may be useful in the batch loader interfaces such as {@link org.dataloader.BatchLoaderWithContext} or
     * {@link org.dataloader.MappedBatchLoaderWithContext} to help retrieve data.
     *
     * @param keys        the list of keys to load
     * @param keyContexts the list of key calling context objects
     * @return the composite future of the list of values
     */
    CompletableFuture<List<V>> loadMany(List<K> keys, List<Object> keyContexts);

    /**
     * This will return an optional promise to a value previously loaded via a {@link #load(Object)} call or empty if not call has been made for that key.
     * <p>
     * If you do get a present CompletableFuture it does not mean it has been dispatched and completed yet.  It just means
     * its at least pending and in cache.
     * <p>
     * If caching is disabled there will never be a present Optional returned.
     * <p>
     * NOTE : This will NOT cause a data load to happen.  You must called {@link #load(Object)} for that to happen.
     *
     * @param key the key to check
     * @return an Optional to the future of the value
     */
    Optional<CompletableFuture<V>> getIfPresent(K key);

    /**
     * This will return an optional promise to a value previously loaded via a {@link #load(Object)} call that has in fact been completed or empty
     * if no call has been made for that key or the promise has not completed yet.
     * <p>
     * If you do get a present CompletableFuture it means it has been dispatched and completed.  Completed is defined as
     * {@link java.util.concurrent.CompletableFuture#isDone()} returning true.
     * <p>
     * If caching is disabled there will never be a present Optional returned.
     * <p>
     * NOTE : This will NOT cause a data load to happen.  You must called {@link #load(Object)} for that to happen.
     *
     * @param key the key to check
     * @return an Optional to the future of the value
     */
    Optional<CompletableFuture<V>> getIfCompleted(K key);


    /**
     * Dispatches the queued load requests to the batch execution function and returns both the promise of the results
     * and the number of entries that were dispatched.
     * <p>
     * If batching is disabled, or there are no queued requests, then a succeeded promise with no entries dispatched is
     * returned.
     *
     * @return the promise of the queued load requests and the number of keys dispatched.
     */
    DispatchResult<V> dispatch();

    /**
     * Normally {@link #dispatch()} is an asynchronous operation but this version will 'join' on the
     * results if dispatch and wait for them to complete.  If the {@link CompletableFuture} callbacks make more
     * calls to this data loader then the {@link #dispatchDepth()} will be &gt; 0 and this method will loop
     * around and wait for any other extra batch loads to occur.
     *
     * @return the list of all results when the {@link #dispatchDepth()} reached 0
     */
    List<V> dispatchAndJoin();

    /**
     * @return the depth of the batched key loads that need to be dispatched
     */
    int dispatchDepth();

    /**
     * Gets the statistics associated with this data loader.  These will have been gather via
     * the {@link org.dataloader.stats.StatisticsCollector} passed in via {@link DataLoaderOptions#getStatisticsCollector()}
     *
     * @return statistics for this data loader
     */
    Statistics getStatistics();


    /**
     * Clears the future with the specified key from the cache, if caching is enabled, so it will be re-fetched
     * on the next load request.
     *
     * @param key the key to remove
     * @return the data loader for fluent coding
     */
    DataLoader<K, V> clear(K key);

    /**
     * Clears the entire cache map of the loader.
     *
     * @return the data loader for fluent coding
     */
    DataLoader<K, V> clearAll();

    /**
     * Primes the cache with the given key and value.
     *
     * @param key   the key
     * @param value the value
     * @return the data loader for fluent coding
     */
    DataLoader<K, V> prime(K key, V value);

    /**
     * Primes the cache with the given key and error.
     *
     * @param key   the key
     * @param error the exception to prime instead of a value
     * @return the data loader for fluent coding
     */
    DataLoader<K, V> prime(K key, Exception error);

    /**
     * Gets the object that is used in the internal cache map as key, by applying the cache key function to
     * the provided key.
     * <p>
     * If no cache key function is present in {@link DataLoaderOptions}, then the returned value equals the input key.
     *
     * @param key the input key
     * @return the cache key after the input is transformed with the cache key function
     */
    Object getCacheKey(K key);

}

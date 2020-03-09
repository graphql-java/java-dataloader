package org.dataloader;

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
    CompletableFuture<V> load(K key);

    CompletableFuture<V> load(K key, Object keyContext);

    CompletableFuture<List<V>> loadMany(List<K> keys);

    CompletableFuture<List<V>> loadMany(List<K> keys, List<Object> keyContexts);

    Optional<CompletableFuture<V>> getIfPresent(K key);

    Optional<CompletableFuture<V>> getIfCompleted(K key);


    CompletableFuture<List<V>> dispatch();

    DispatchResult<V> dispatchWithCounts();

    List<V> dispatchAndJoin();

    int dispatchDepth();

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

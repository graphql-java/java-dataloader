package org.dataloader;

import org.dataloader.annotations.PublicSpi;
import org.dataloader.impl.NoOpCachedValueStore;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Cache value store for data loaders that use caching and want a long-lived or external cache.
 * <p>
 * The default implementation is a no-op store which replies with the key always missing and doesn't
 * store any actual results. This is to avoid duplicating the stored data between the {@link CacheMap}
 * and the store.
 * <p>
 * The API signature uses completable futures because the backing implementation MAY be a remote external cache
 * and hence exceptions may happen in retrieving values.
 *
 * @param <K> the type of cache keys
 * @param <V> the type of cache values
 *
 * @author <a href="https://github.com/craig-day">Craig Day</a>
 */
@PublicSpi
public interface CachedValueStore<K, V> {


    /**
     * Creates a new store, using the default no-op implementation.
     *
     * @param <K> the type of cache keys
     * @param <V> the type of cache values
     *
     * @return the cache store
     */
    static <K, V> CachedValueStore<K, V> defaultStore() {
        //noinspection unchecked
        return (CachedValueStore<K, V>) NoOpCachedValueStore.NOOP;
    }

    /**
     * Checks whether the specified key is contained in the store.
     * <p>
     * {@link DataLoader} first calls {@link #containsKey(Object)} and then calls {@link #get(Object)}.  If the
     * backing cache implementation cannot answer the `containsKey` call then simply return true and the
     * following `get` call can complete exceptionally to cause the {@link DataLoader}
     * to enqueue the key to the {@link BatchLoader#load(List)} call since it is not present in cache.
     *
     * @param key the key to check if its present in the cache
     *
     * @return {@code true} if the cache contains the key, {@code false} otherwise
     */
    CompletableFuture<Boolean> containsKey(K key);

    /**
     * Gets the specified key from the store.
     *
     * @param key the key to retrieve
     *
     * @return a future containing the cached value (which maybe null) or an exception if the key does
     * not exist in the cache.
     *
     * IMPORTANT: The future may fail if the key does not exist depending on implementation. Implementations should
     * return an exceptional future if the key is not present in the cache, not null which is a valid value
     * for a key.
     */
    CompletableFuture<V> get(K key);

    /**
     * Stores the value with the specified key, or updates it if the key already exists.
     *
     * @param key   the key to store
     * @param value the value to store
     *
     * @return a future containing the stored value for fluent composition
     */
    CompletableFuture<V> set(K key, V value);

    /**
     * Deletes the entry with the specified key from the store, if it exists.
     *
     * @param key the key to delete
     *
     * @return a void future for error handling and fluent composition
     */
    CompletableFuture<Void> delete(K key);

    /**
     * Clears all entries from the store.
     *
     * @return a void future for error handling and fluent composition
     */
    CompletableFuture<Void> clear();
}
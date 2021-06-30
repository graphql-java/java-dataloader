package org.dataloader;

import org.dataloader.annotations.PublicSpi;
import org.dataloader.impl.NoOpCachedValueStore;

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
    static <K, V> CachedValueStore<K, V> defaultCachedValueStore() {
        //noinspection unchecked
        return (CachedValueStore<K, V>) NoOpCachedValueStore.NOOP;
    }

    /**
     * Gets the specified key from the store.  if the key si not present, then the implementation MUST return an exceptionally completed future
     * and not null because null is a valid cacheable value.  Any exception is will cause {@link DataLoader} to load the key via batch loading
     * instead.
     *
     * @param key the key to retrieve
     *
     * @return a future containing the cached value (which maybe null) or exceptionally completed future if the key does
     * not exist in the cache.
     *
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
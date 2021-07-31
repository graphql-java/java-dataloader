package org.dataloader;

import org.dataloader.annotations.PublicSpi;
import org.dataloader.impl.NoOpValueCache;

import java.util.concurrent.CompletableFuture;

/**
 * The {@link ValueCache} is used by data loaders that use caching and want a long-lived or external cache
 * of values.  The {@link ValueCache} is used as a place to cache values when they come back from an async
 * cache store.
 * <p>
 * It differs from {@link CacheMap} which is in fact a cache of promised values aka {@link CompletableFuture}&lt;V&gt;'s.
 * <p>
 * {@link ValueCache} is more suited to be a wrapper of a long-lived or externallly cached values.  {@link CompletableFuture}s cant
 * be easily placed in an external cache outside the JVM say, hence the need for the {@link ValueCache}.
 * <p>
 * {@link DataLoader}s use a two stage cache strategy if caching is enabled.  If the {@link CacheMap} already has the promise to a value
 * that is used.  If not then the {@link ValueCache} is asked for a value, if it has one then that is returned (and cached as a promise in the {@link CacheMap}.
 * <p>
 * If there is no value then the key is queued and loaded via the {@link BatchLoader} calls.  The returned values will then be stored in
 * the {@link ValueCache} and the promises to those values are also stored in the {@link CacheMap}.
 * <p>
 * The default implementation is a no-op store which replies with the key always missing and doesn't
 * store any actual results. This is to avoid duplicating the stored data between the {@link CacheMap}
 * out of the box.
 * <p>
 * The API signature uses {@link CompletableFuture}s because the backing implementation MAY be a remote external cache
 * and hence exceptions may happen in retrieving values and they may take time to complete.
 *
 * @param <K> the type of cache keys
 * @param <V> the type of cache values
 *
 * @author <a href="https://github.com/craig-day">Craig Day</a>
 * @author <a href="https://github.com/bbakerman/">Brad Baker</a>
 */
@PublicSpi
public interface ValueCache<K, V> {

    /**
     * Creates a new value cache, using the default no-op implementation.
     *
     * @param <K> the type of cache keys
     * @param <V> the type of cache values
     *
     * @return the cache store
     */
    static <K, V> ValueCache<K, V> defaultValueCache() {
        //noinspection unchecked
        return (ValueCache<K, V>) NoOpValueCache.NOOP;
    }

    /**
     * Gets the specified key from the value cache.  If the key is not present, then the implementation MUST return an exceptionally completed future
     * and not null because null is a valid cacheable value.  An exceptionally completed future will cause {@link DataLoader} to load the key via batch loading
     * instead.
     * <p>
     * NOTE: Your implementation MUST not throw exceptions, rather it should return a CompletableFuture that has completed exceptionally.  Failure
     * to do this may cause the {@link DataLoader} code to not run properly.
     *
     * @param key the key to retrieve
     *
     * @return a future containing the cached value (which maybe null) or exceptionally completed future if the key does
     * not exist in the cache.
     */
    CompletableFuture<V> get(K key);

    /**
     * Stores the value with the specified key, or updates it if the key already exists.
     * <p>
     * NOTE: Your implementation MUST not throw exceptions, rather it should return a CompletableFuture that has completed exceptionally.  Failure
     * to do this may cause the {@link DataLoader} code to not run properly.
     *
     * @param key   the key to store
     * @param value the value to store
     *
     * @return a future containing the stored value for fluent composition
     */
    CompletableFuture<V> set(K key, V value);

    /**
     * Deletes the entry with the specified key from the value cache, if it exists.
     * <p>
     * NOTE: Your implementation MUST not throw exceptions, rather it should return a CompletableFuture that has completed exceptionally.  Failure
     * to do this may cause the {@link DataLoader} code to not run properly.
     *
     * @param key the key to delete
     *
     * @return a void future for error handling and fluent composition
     */
    CompletableFuture<Void> delete(K key);

    /**
     * Clears all entries from the value cache.
     * <p>
     * NOTE: Your implementation MUST not throw exceptions, rather it should return a CompletableFuture that has completed exceptionally.  Failure
     * to do this may cause the {@link DataLoader} code to not run properly.
     *
     * @return a void future for error handling and fluent composition
     */
    CompletableFuture<Void> clear();
}
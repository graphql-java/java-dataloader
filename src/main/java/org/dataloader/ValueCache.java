package org.dataloader;

import org.dataloader.annotations.PublicSpi;
import org.dataloader.impl.CompletableFutureKit;
import org.dataloader.impl.NoOpValueCache;

import java.util.ArrayList;
import java.util.List;
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
     *
     * @param key the key to retrieve
     *
     * @return a future containing the cached value (which maybe null) or exceptionally completed future if the key does
     * not exist in the cache.
     */
    CompletableFuture<V> get(K key);

    /**
     * Gets the specified keys from the value cache, in a batch call.  If your underlying cache cant do batch caching retrieval
     * then do not implement this method and it will delegate back to {@link #get(Object)} for you
     * <p>
     * Each item in the returned list of values is a {@link Try}.  If the key could not be found then a failed Try just be returned otherwise
     * a successful Try contain the cached value is returned.
     * <p>
     * You MUST return a List that is the same size as the keys passed in.  The code will assert if you do not.
     * <p>
     * If your cache does not have anything in it at all, and you want to quickly short-circuit this method and avoid any object allocation
     * then throw {@link ValueCachingNotSupported} and the code will know there is nothing in cache at this time.
     *
     * @param keys the list of keys to get cached values for.
     *
     * @return a future containing a list of {@link Try} cached values for each key passed in.
     *
     * @throws ValueCachingNotSupported if this cache wants to short-circuit this method completely
     */
    default CompletableFuture<List<Try<V>>> getValues(List<K> keys) throws ValueCachingNotSupported {
        List<CompletableFuture<Try<V>>> cacheLookups = new ArrayList<>(keys.size());
        for (K key : keys) {
            CompletableFuture<Try<V>> cacheTry = Try.tryFuture(get(key));
            cacheLookups.add(cacheTry);
        }
        return CompletableFutureKit.allOf(cacheLookups);
    }

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
     * Stores the value with the specified keys, or updates it if the keys if they already exist.  If your underlying cache cant do batch caching setting
     * then do not implement this method and it will delegate back to {@link #set(Object, Object)} for you
     *
     * @param keys   the keys to store
     * @param values the values to store
     *
     * @return a future containing the stored values for fluent composition
     *
     * @throws ValueCachingNotSupported if this cache wants to short-circuit this method completely
     */
    default CompletableFuture<List<V>> setValues(List<K> keys, List<V> values) throws ValueCachingNotSupported {
        List<CompletableFuture<V>> cacheSets = new ArrayList<>();
        for (int i = 0; i < keys.size(); i++) {
            K k = keys.get(i);
            V v = values.get(i);
            CompletableFuture<V> setCall = set(k, v);
            CompletableFuture<V> set = Try.tryFuture(setCall).thenApply(ignored -> v);
            cacheSets.add(set);
        }
        return CompletableFutureKit.allOf(cacheSets);
    }

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


    /**
     * This special exception can be used to short-circuit a caching method
     */
    class ValueCachingNotSupported extends UnsupportedOperationException {
        @Override
        public Throwable fillInStackTrace() {
            return this;
        }
    }
}
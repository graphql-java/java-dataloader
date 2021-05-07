package org.dataloader;

import org.dataloader.impl.DefaultCacheStore;

import java.util.concurrent.CompletableFuture;

/**
 * Remote cache store for data loaders that use caching and want a long-lived or external cache.
 * <p>
 * The default implementation is a no-op store which replies with the key always missing and doesn't
 * store any actual results. This is to avoid duplicating the stored data between the {@link CacheMap}
 * and the store.
 *
 * @param <K> the type of cache keys
 * @param <V> the type of cache values
 *
 * @author <a href="https://github.com/craig-day">Craig Day</a>
 */
@PublicSpi
public interface CacheStore<K, V> {

  /**
   * Creates a new store, using the default no-op implementation.
   *
   * @param <K> the type of cache keys
   * @param <V> the type of cache values
   *
   * @return the cache store
   */
  static <K, V> CacheStore<K, V> defaultStore() {
    return new DefaultCacheStore<>();
  }

  /**
   * Checks whether the specified key is contained in the store.
   *
   * @param key the key to check
   *
   * @return {@code true} if the cache contains the key, {@code false} otherwise
   */
  CompletableFuture<Boolean> has(K key);

  /**
   * Gets the specified key from the store.
   *
   * @apiNote The future may fail if the key does not exist depending on implementation. It is
   * recommended to compose this call with {@link #has(Object)}.
   *
   * @param key the key to retrieve
   *
   * @return a future containing the cached value, or {@code null} if not found (depends on implementation)
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

package org.dataloader;

import java.util.concurrent.CompletableFuture;
import org.dataloader.impl.DefaultCacheStore;

public interface CacheStore<K, V> {

  static <K, V> CacheStore<K, V> defaultStore() {
    return new DefaultCacheStore<>();
  }

  CompletableFuture<Boolean> has(K key);

  CompletableFuture<V> get(K key);

  CompletableFuture<V> set(K key, V value);

  CompletableFuture<Void> delete(K key);

  CompletableFuture<Void> clear();
}
